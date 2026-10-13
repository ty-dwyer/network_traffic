package com.example.networktraffic.services;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private static final int INGEST_BATCH_SIZE = 500;
    private final NetworkRepository networkRepository;
    private final PcapParser pcapParser;
    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AnomalyDetectionService anomalyDetectionService;

    public PacketIngestionService(NetworkRepository networkRepository, PcapParser pcapParser, DeviceRepository deviceRepository, AlertRepository alertRepository, SimpMessagingTemplate messagingTemplate, AnomalyDetectionService anomalyDetectionService) {
        this.networkRepository = networkRepository;
        this.pcapParser = pcapParser;
        this.deviceRepository= deviceRepository;
        this.alertRepository = alertRepository;
        this.messagingTemplate = messagingTemplate;
        this.anomalyDetectionService = anomalyDetectionService;
    }

    public int ingest(String filePath) throws IOException {
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
