package com.example.networktraffic.services;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.networktraffic.entities.Alert;
import com.example.networktraffic.entities.Device;
import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.parser.PcapParser;
import com.example.networktraffic.repositories.AlertRepository;
import com.example.networktraffic.repositories.DeviceRepository;
import com.example.networktraffic.repositories.NetworkRepository;

@Service
public class PacketIngestionService {
    private final NetworkRepository networkRepository;
    private final PcapParser pcapParser;
    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PacketIngestionService(NetworkRepository networkRepository, PcapParser pcapParser, DeviceRepository deviceRepository, AlertRepository alertRepository, SimpMessagingTemplate messagingTemplate) {
        this.networkRepository = networkRepository;
        this.pcapParser = pcapParser;
        this.deviceRepository= deviceRepository;
        this.alertRepository = alertRepository;
        this.messagingTemplate = messagingTemplate;
    }

    private static final Set<Integer> ALLOWED_PORTS = Set.of(80, 443, 53, 22);

    public int ingest(String filePath) throws IOException {
        List<Packet> packets = pcapParser.openFile(filePath);

        for (Packet packet : packets) {
            Device device = findOrCreateDevice(packet.getSourceIp());
            packet.setDevice(device);
            Packet savedPacket = networkRepository.save(packet);
            messagingTemplate.convertAndSend("/topic/packets", savedPacket);

            Integer destPort = savedPacket.getDestPort();
            if (destPort != null && !ALLOWED_PORTS.contains(destPort)) {
                if (!alertRepository.existsByDeviceAndType(device, Alert.AlertType.UNUSUAL_PORT)) {
                    Alert newAlert = new Alert();
                    newAlert.setType(Alert.AlertType.UNUSUAL_PORT);
                    newAlert.setMessage("Unusual port detected: " + destPort);
                    newAlert.setTimeStamp(Instant.now());
                    newAlert.setDevice(device);
                    alertRepository.save(newAlert);
                    messagingTemplate.convertAndSend("/topic/alerts", newAlert);
                }
        }
        }

        return packets.size();
    }


    public Device findOrCreateDevice(String ipAddress){
        Optional<Device> optionalAddress = deviceRepository.findByIpAddress(ipAddress);

        if (optionalAddress.isPresent()) {
            Device device = optionalAddress.get();
            device.setLastSeen(Instant.now());
            return deviceRepository.save(device);
        }
        else {
            Device newDevice = new Device();
            newDevice.setIpAddress(ipAddress);
            newDevice.setFirstSeen(Instant.now());
            newDevice.setLastSeen(Instant.now());
            newDevice.setTrusted(false);
            Device savedDevice = deviceRepository.save(newDevice);
            
            Alert newAlert = new Alert();
            newAlert.setType(Alert.AlertType.NEW_DEVICE);
            newAlert.setMessage("New device detected: " + ipAddress);
            newAlert.setTimeStamp(Instant.now());
            newAlert.setDevice(savedDevice);
            alertRepository.save(newAlert);
            messagingTemplate.convertAndSend("/topic/alerts", newAlert);
        
            return savedDevice;
        }

    }
}