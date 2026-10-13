package com.example.networktraffic.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntConsumer;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import com.example.networktraffic.entities.Alert;
import com.example.networktraffic.entities.Device;
import com.example.networktraffic.entities.IngestionJob;
import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.parser.PcapParser;
import com.example.networktraffic.repositories.AlertRepository;
import com.example.networktraffic.repositories.DeviceRepository;
import com.example.networktraffic.repositories.IngestionJobRepository;
import com.example.networktraffic.repositories.NetworkRepository;

@Service
public class PacketIngestionService {
    private static final int INGEST_BATCH_SIZE = 500;
    private final NetworkRepository networkRepository;
    private final PcapParser pcapParser;
    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AnomalyDetectionService anomalyDetectionService;
    private final IngestionJobRepository ingestionJobRepository;
    private final TaskExecutor ingestionTaskExecutor;

    public PacketIngestionService(NetworkRepository networkRepository, PcapParser pcapParser,
            DeviceRepository deviceRepository, AlertRepository alertRepository,
            SimpMessagingTemplate messagingTemplate, AnomalyDetectionService anomalyDetectionService,
            IngestionJobRepository ingestionJobRepository, TaskExecutor ingestionTaskExecutor) {
        this.networkRepository = networkRepository;
        this.pcapParser = pcapParser;
        this.deviceRepository= deviceRepository;
        this.alertRepository = alertRepository;
        this.messagingTemplate = messagingTemplate;
        this.anomalyDetectionService = anomalyDetectionService;
        this.ingestionJobRepository = ingestionJobRepository;
        this.ingestionTaskExecutor = ingestionTaskExecutor;
    }

    public int ingest(String filePath) throws IOException {
        return processCapture(filePath, ignored -> {});
    }

    public IngestionJob startIngestion(String filePath, String fileName, boolean deleteFileOnCompletion) {
        IngestionJob job = new IngestionJob();
        job.setFileName(fileName == null || fileName.isBlank() ? "capture.pcap" : fileName);
        job.setStatus(IngestionJob.Status.PENDING);
        job.setPacketCount(0);
        job.setCreatedAt(Instant.now());
        job = ingestionJobRepository.save(job);
        Long jobId = job.getId();
        try {
            ingestionTaskExecutor.execute(() -> runJob(jobId, filePath, deleteFileOnCompletion));
        } catch (RuntimeException ex) {
            markFailed(job, ex);
            deleteTemporaryFile(filePath, deleteFileOnCompletion);
            throw ex;
        }
        return job;
    }

    private void runJob(Long jobId, String filePath, boolean deleteFileOnCompletion) {
        try {
            IngestionJob job = ingestionJobRepository.findById(jobId).orElseThrow();
            job.setStatus(IngestionJob.Status.RUNNING);
            job.setStartedAt(Instant.now());
            ingestionJobRepository.save(job);
            processCapture(filePath, packetCount -> {
                job.setPacketCount(packetCount);
                ingestionJobRepository.save(job);
            });
            job.setStatus(IngestionJob.Status.COMPLETED);
            job.setFinishedAt(Instant.now());
            ingestionJobRepository.save(job);
        } catch (Exception ex) {
            ingestionJobRepository.findById(jobId).ifPresent(job -> markFailed(job, ex));
        } finally {
            deleteTemporaryFile(filePath, deleteFileOnCompletion);
        }
    }

    private void markFailed(IngestionJob job, Exception ex) {
        job.setStatus(IngestionJob.Status.FAILED);
        job.setFinishedAt(Instant.now());
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        job.setErrorMessage(message.substring(0, Math.min(1000, message.length())));
        ingestionJobRepository.save(job);
    }

    private void deleteTemporaryFile(String filePath, boolean deleteFile) {
        if (!deleteFile) return;
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException ignored) {
            // The job result is the primary outcome; cleanup can be retried by the OS temp-file policy.
        }
    }

    private int processCapture(String filePath, IntConsumer countListener) throws IOException {
        int[] count = {0};
        pcapParser.readBatches(filePath, INGEST_BATCH_SIZE, packets -> {
            Map<String, Device> devicesByIp = new HashMap<>();
            for (Packet packet : packets) {
                Device device = devicesByIp.computeIfAbsent(packet.getSourceIp(), this::findOrCreateDevice);
                packet.setDevice(device);
            }
            List<Packet> savedPackets = networkRepository.saveAll(packets);
            for (Packet savedPacket : savedPackets) {
                messagingTemplate.convertAndSend("/topic/packets", savedPacket);
                anomalyDetectionService.evaluate(savedPacket, savedPacket.getDevice());
            }
            count[0] += savedPackets.size();
            countListener.accept(count[0]);
        });
        return count[0];
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
