package com.example.networktraffic.services;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.networktraffic.entities.Alert;
import com.example.networktraffic.entities.Device;
import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.repositories.AlertRepository;

@Service
public class AnomalyDetectionService {

    private static final int TCP_SYN = 0x02;
    private static final int TCP_ACK = 0x10;

    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final long windowSeconds;
    private final long cooldownSeconds;
    private final int trafficSpikePacketCount;
    private final int portScanUniquePorts;
    private final int synFloodSynCount;
    private final int largePacketBytes;
    private final int dnsVolumePacketCount;
    private final Set<Integer> allowedPorts;

    private final Map<String, Deque<Instant>> packetTimesBySource = new HashMap<>();
    private final Map<String, Deque<TimedPort>> destPortsBySource = new HashMap<>();
    private final Map<String, Deque<Instant>> synTimesBySource = new HashMap<>();
    private final Map<String, Deque<Instant>> dnsTimesBySource = new HashMap<>();

    public AnomalyDetectionService(
            AlertRepository alertRepository,
            SimpMessagingTemplate messagingTemplate,
            @Value("${anomaly.window-seconds:10}") long windowSeconds,
            @Value("${anomaly.cooldown-seconds:60}") long cooldownSeconds,
            @Value("${anomaly.traffic-spike.packet-count:100}") int trafficSpikePacketCount,
            @Value("${anomaly.port-scan.unique-ports:20}") int portScanUniquePorts,
            @Value("${anomaly.syn-flood.syn-count:50}") int synFloodSynCount,
            @Value("${anomaly.large-packet.bytes:1400}") int largePacketBytes,
            @Value("${anomaly.dns-volume.packet-count:50}") int dnsVolumePacketCount,
            @Value("${anomaly.allowed-destination-ports:80,443,53,22}") String allowedDestinationPorts) {
        this.alertRepository = alertRepository;
        this.messagingTemplate = messagingTemplate;
        this.windowSeconds = windowSeconds;
        this.cooldownSeconds = cooldownSeconds;
        this.trafficSpikePacketCount = trafficSpikePacketCount;
        this.portScanUniquePorts = portScanUniquePorts;
        this.synFloodSynCount = synFloodSynCount;
        this.largePacketBytes = largePacketBytes;
        this.dnsVolumePacketCount = dnsVolumePacketCount;
        this.allowedPorts = parsePorts(allowedDestinationPorts);
    }

    public void evaluate(Packet packet, Device device) {
        Instant timestamp = packet.getTimeStamp();
        String sourceIp = packet.getSourceIp();

        Deque<Instant> packetTimes = packetTimesBySource.computeIfAbsent(sourceIp, key -> new ArrayDeque<>());
        packetTimes.addLast(timestamp);
        pruneInstants(packetTimes, timestamp);
        if (packetTimes.size() >= trafficSpikePacketCount) {
            emit(device, Alert.AlertType.TRAFFIC_SPIKE, timestamp,
                    "Traffic spike: " + packetTimes.size() + " packets from " + sourceIp
                            + " in " + windowSeconds + "s");
        }

        Integer destPort = packet.getDestPort();
        if (destPort != null) {
            Deque<TimedPort> destPorts = destPortsBySource.computeIfAbsent(sourceIp, key -> new ArrayDeque<>());
            destPorts.addLast(new TimedPort(timestamp, destPort));
            pruneTimedPorts(destPorts, timestamp);
            int uniquePorts = uniqueDestPorts(destPorts);
            if (uniquePorts >= portScanUniquePorts) {
                emit(device, Alert.AlertType.PORT_SCAN, timestamp,
                        "Port scan: " + uniquePorts + " distinct destination ports from " + sourceIp);
            }
        }

        if (isSynWithoutAck(packet.getTcpFlags())) {
            Deque<Instant> synTimes = synTimesBySource.computeIfAbsent(sourceIp, key -> new ArrayDeque<>());
            synTimes.addLast(timestamp);
            pruneInstants(synTimes, timestamp);
            if (synTimes.size() >= synFloodSynCount) {
                emit(device, Alert.AlertType.SYN_FLOOD, timestamp,
                        "SYN flood: " + synTimes.size() + " SYN packets from " + sourceIp
                                + " in " + windowSeconds + "s");
            }
        }

        Integer packetSize = packet.getPacketSize();
        if (packetSize != null && packetSize >= largePacketBytes) {
            emit(device, Alert.AlertType.LARGE_PACKET, timestamp,
                    "Large packet: " + packetSize + " bytes from " + sourceIp);
        }

        if (isDns(packet)) {
            Deque<Instant> dnsTimes = dnsTimesBySource.computeIfAbsent(sourceIp, key -> new ArrayDeque<>());
            dnsTimes.addLast(timestamp);
            pruneInstants(dnsTimes, timestamp);
            if (dnsTimes.size() >= dnsVolumePacketCount) {
                emit(device, Alert.AlertType.DNS_VOLUME, timestamp,
                        "High DNS volume: " + dnsTimes.size() + " DNS packets from " + sourceIp
                                + " in " + windowSeconds + "s");
            }
        }

        if (destPort != null && !allowedPorts.contains(destPort)) {
            emit(device, Alert.AlertType.UNUSUAL_PORT, timestamp,
                    "Unusual port detected: " + destPort);
        }
    }

    private void emit(Device device, Alert.AlertType type, Instant timestamp, String message) {
        Instant cooldownStart = timestamp.minusSeconds(cooldownSeconds);
        if (alertRepository.existsByDeviceAndTypeAndTimeStampAfter(device, type, cooldownStart)) {
            return;
        }
        Alert alert = new Alert();
        alert.setType(type);
        alert.setMessage(message);
        alert.setTimeStamp(timestamp);
        alert.setDevice(device);
        alertRepository.save(alert);
        messagingTemplate.convertAndSend("/topic/alerts", alert);
    }

    private void pruneInstants(Deque<Instant> times, Instant now) {
        Instant cutoff = now.minusSeconds(windowSeconds);
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
            times.removeFirst();
        }
    }

    private void pruneTimedPorts(Deque<TimedPort> ports, Instant now) {
        Instant cutoff = now.minusSeconds(windowSeconds);
        while (!ports.isEmpty() && ports.peekFirst().time().isBefore(cutoff)) {
            ports.removeFirst();
        }
    }

    private int uniqueDestPorts(Deque<TimedPort> ports) {
        Set<Integer> unique = new HashSet<>();
        for (TimedPort timedPort : ports) {
            unique.add(timedPort.port());
        }
        return unique.size();
    }

    private boolean isSynWithoutAck(String tcpFlags) {
        if (tcpFlags == null || tcpFlags.isBlank()) {
            return false;
        }
        try {
            int flags = Integer.parseInt(tcpFlags);
            return (flags & TCP_SYN) != 0 && (flags & TCP_ACK) == 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean isDns(Packet packet) {
        Integer destPort = packet.getDestPort();
        Integer sourcePort = packet.getSourcePort();
        return Integer.valueOf(53).equals(destPort) || Integer.valueOf(53).equals(sourcePort);
    }

    private Set<Integer> parsePorts(String configuredPorts) {
        if (configuredPorts == null || configuredPorts.isBlank()) return Set.of();
        try {
            Set<Integer> ports = java.util.Arrays.stream(configuredPorts.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toUnmodifiableSet());
            if (ports.stream().anyMatch(port -> port < 1 || port > 65535)) {
                throw new IllegalArgumentException("Allowed destination ports must be between 1 and 65535");
            }
            return ports;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Allowed destination ports must be comma-separated integers", ex);
        }
    }

    private record TimedPort(Instant time, int port) {
    }
}
