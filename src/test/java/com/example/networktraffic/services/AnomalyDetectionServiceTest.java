package com.example.networktraffic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.example.networktraffic.entities.Alert;
import com.example.networktraffic.entities.Device;
import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.entities.Protocol;
import com.example.networktraffic.repositories.AlertRepository;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private final List<Alert> savedAlerts = new ArrayList<>();
    private AnomalyDetectionService service;
    private Device device;

    @BeforeEach
    void setUp() {
        savedAlerts.clear();
        device = new Device();
        device.setId(1L);
        device.setIpAddress("10.0.0.1");

        lenient().when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            savedAlerts.add(alert);
            return alert;
        });
        lenient().when(alertRepository.existsByDeviceAndTypeAndTimeStampAfter(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Device lookupDevice = invocation.getArgument(0);
                    Alert.AlertType type = invocation.getArgument(1);
                    Instant after = invocation.getArgument(2);
                    return savedAlerts.stream().anyMatch(alert ->
                            alert.getDevice() == lookupDevice
                                    && alert.getType() == type
                                    && alert.getTimeStamp().isAfter(after));
                });

        service = new AnomalyDetectionService(
                alertRepository,
                messagingTemplate,
                10,
                60,
                3,
                3,
                3,
                1400,
                3);
    }

    @Test
    void unusualPortAlerts() {
        Packet packet = packet(Instant.parse("2026-01-01T00:00:00Z"), 8080, null, 100, null);

        service.evaluate(packet, device);

        assertThat(types()).contains(Alert.AlertType.UNUSUAL_PORT);
        assertThat(savedAlerts.get(0).getMessage()).isEqualTo("Unusual port detected: 8080");
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), any(Alert.class));
    }

    @Test
    void trafficSpikeAlertsAfterThreshold() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        service.evaluate(httpPacket(start, 80), device);
        service.evaluate(httpPacket(start.plusSeconds(1), 80), device);
        assertThat(types()).doesNotContain(Alert.AlertType.TRAFFIC_SPIKE);

        service.evaluate(httpPacket(start.plusSeconds(2), 80), device);

        assertThat(types()).contains(Alert.AlertType.TRAFFIC_SPIKE);
    }

    @Test
    void portScanAlertsOnDistinctDestPorts() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        service.evaluate(httpPacket(start, 80), device);
        service.evaluate(httpPacket(start.plusSeconds(1), 443), device);
        assertThat(types()).doesNotContain(Alert.AlertType.PORT_SCAN);

        service.evaluate(httpPacket(start.plusSeconds(2), 22), device);

        assertThat(types()).contains(Alert.AlertType.PORT_SCAN);
    }

    @Test
    void synFloodAlertsOnSynWithoutAck() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        service.evaluate(synPacket(start), device);
        service.evaluate(synPacket(start.plusSeconds(1)), device);
        assertThat(types()).doesNotContain(Alert.AlertType.SYN_FLOOD);

        service.evaluate(synPacket(start.plusSeconds(2)), device);

        assertThat(types()).contains(Alert.AlertType.SYN_FLOOD);
    }

    @Test
    void largePacketAlertsOnSize() {
        Packet packet = packet(Instant.parse("2026-01-01T00:00:00Z"), 80, null, 1400, null);

        service.evaluate(packet, device);

        assertThat(types()).contains(Alert.AlertType.LARGE_PACKET);
        assertThat(savedAlerts.get(0).getMessage()).isEqualTo("Large packet: 1400 bytes from 10.0.0.1");
    }

    @Test
    void dnsVolumeAlertsAfterThreshold() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        service.evaluate(httpPacket(start, 53), device);
        service.evaluate(httpPacket(start.plusSeconds(1), 53), device);
        assertThat(types()).doesNotContain(Alert.AlertType.DNS_VOLUME);

        service.evaluate(httpPacket(start.plusSeconds(2), 53), device);

        assertThat(types()).contains(Alert.AlertType.DNS_VOLUME);
    }

    @Test
    void cooldownSuppressesSecondIdenticalAlert() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Packet first = packet(start, 80, null, 1500, null);
        Packet second = packet(start.plusSeconds(1), 80, null, 1600, null);

        service.evaluate(first, device);
        service.evaluate(second, device);

        long largePacketAlerts = savedAlerts.stream()
                .filter(alert -> alert.getType() == Alert.AlertType.LARGE_PACKET)
                .count();
        assertThat(largePacketAlerts).isEqualTo(1);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/alerts"), any(Alert.class));
    }

    @Test
    void alertUsesCaptureTimestamp() {
        Instant captured = Instant.parse("2024-06-01T12:00:00Z");
        service.evaluate(packet(captured, 8080, null, 100, null), device);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getTimeStamp()).isEqualTo(captured);
    }

    private List<Alert.AlertType> types() {
        return savedAlerts.stream().map(Alert::getType).toList();
    }

    private Packet httpPacket(Instant timestamp, int destPort) {
        return packet(timestamp, destPort, 12345, 100, null);
    }

    private Packet synPacket(Instant timestamp) {
        return packet(timestamp, 80, 12345, 60, String.valueOf(0x02));
    }

    private Packet packet(Instant timestamp, Integer destPort, Integer sourcePort, Integer size, String tcpFlags) {
        Packet packet = new Packet();
        packet.setTimeStamp(timestamp);
        packet.setSourceIp("10.0.0.1");
        packet.setDestIp("10.0.0.2");
        packet.setDestPort(destPort);
        packet.setSourcePort(sourcePort);
        packet.setPacketSize(size);
        packet.setTcpFlags(tcpFlags);
        packet.setProtocol(Protocol.TCP);
        packet.setDevice(device);
        return packet;
    }
}
