package com.example.networktraffic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.example.networktraffic.entities.Device;
import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.entities.Protocol;
import com.example.networktraffic.parser.PcapParser;
import com.example.networktraffic.repositories.AlertRepository;
import com.example.networktraffic.repositories.DeviceRepository;
import com.example.networktraffic.repositories.NetworkRepository;

@ExtendWith(MockitoExtension.class)
class PacketIngestionServiceTest {
    @Mock NetworkRepository networkRepository;
    @Mock PcapParser parser;
    @Mock DeviceRepository deviceRepository;
    @Mock AlertRepository alertRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock AnomalyDetectionService anomalyDetectionService;

    @Test
    void ingestsParsedBatchesAndReturnsPersistedPacketCount() throws IOException {
        Packet packet = new Packet();
        packet.setTimeStamp(Instant.parse("2026-01-01T00:00:00Z"));
        packet.setSourceIp("10.0.0.1");
        packet.setDestIp("10.0.0.2");
        packet.setProtocol(Protocol.TCP);
        Device device = new Device();
        device.setIpAddress("10.0.0.1");
        when(deviceRepository.findByIpAddress("10.0.0.1")).thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);
        when(networkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            Consumer<List<Packet>> consumer = invocation.getArgument(2);
            consumer.accept(List.of(packet));
            return null;
        }).when(parser).readBatches(eq("capture.pcap"), eq(500), any());

        PacketIngestionService service = new PacketIngestionService(networkRepository, parser,
                deviceRepository, alertRepository, messagingTemplate, anomalyDetectionService);

        assertThat(service.ingest("capture.pcap")).isEqualTo(1);
        assertThat(packet.getDevice()).isSameAs(device);
        verify(networkRepository).saveAll(List.of(packet));
        verify(messagingTemplate).convertAndSend("/topic/packets", packet);
        verify(anomalyDetectionService).evaluate(packet, device);
    }
}
