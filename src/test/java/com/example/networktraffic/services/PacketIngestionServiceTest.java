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
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.example.networktraffic.entities.Device;
import com.example.networktraffic.entities.IngestionJob;
import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.entities.Protocol;
import com.example.networktraffic.parser.PcapParser;
import com.example.networktraffic.repositories.AlertRepository;
import com.example.networktraffic.repositories.DeviceRepository;
import com.example.networktraffic.repositories.IngestionJobRepository;
import com.example.networktraffic.repositories.NetworkRepository;

@ExtendWith(MockitoExtension.class)
class PacketIngestionServiceTest {
    @Mock NetworkRepository networkRepository;
    @Mock PcapParser parser;
    @Mock DeviceRepository deviceRepository;
    @Mock AlertRepository alertRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock AnomalyDetectionService anomalyDetectionService;
    @Mock IngestionJobRepository ingestionJobRepository;
    @Mock TaskExecutor ingestionTaskExecutor;

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
                deviceRepository, alertRepository, messagingTemplate, anomalyDetectionService,
                ingestionJobRepository, ingestionTaskExecutor);

        assertThat(service.ingest("capture.pcap")).isEqualTo(1);
        assertThat(packet.getDevice()).isSameAs(device);
        verify(networkRepository).saveAll(List.of(packet));
        verify(messagingTemplate).convertAndSend("/topic/packets", packet);
        verify(anomalyDetectionService).evaluate(packet, device);
    }

    @Test
    void recordsSuccessfulAsynchronousIngestionJob() throws IOException {
        IngestionJob[] persistedJob = {null};
        when(ingestionJobRepository.save(any(IngestionJob.class))).thenAnswer(invocation -> {
            IngestionJob saved = invocation.getArgument(0);
            saved.setId(17L);
            persistedJob[0] = saved;
            return saved;
        });
        when(ingestionJobRepository.findById(17L)).thenAnswer(invocation -> Optional.of(persistedJob[0]));
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(ingestionTaskExecutor).execute(any(Runnable.class));
        doAnswer(invocation -> null).when(parser).readBatches(eq("capture.pcap"), eq(500), any());

        PacketIngestionService service = new PacketIngestionService(networkRepository, parser,
                deviceRepository, alertRepository, messagingTemplate, anomalyDetectionService,
                ingestionJobRepository, ingestionTaskExecutor);

        IngestionJob started = service.startIngestion("capture.pcap", "capture.pcap", false);

        assertThat(started.getId()).isEqualTo(17L);
        assertThat(persistedJob[0].getStatus()).isEqualTo(IngestionJob.Status.COMPLETED);
        assertThat(persistedJob[0].getStartedAt()).isNotNull();
        assertThat(persistedJob[0].getFinishedAt()).isNotNull();
        verify(ingestionJobRepository, org.mockito.Mockito.times(3)).save(persistedJob[0]);
    }
}
