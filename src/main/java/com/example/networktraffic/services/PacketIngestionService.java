package com.example.networktraffic.services;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.networktraffic.entities.Device;
import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.parser.PcapParser;
import com.example.networktraffic.repositories.DeviceRepository;
import com.example.networktraffic.repositories.NetworkRepository;

@Service
public class PacketIngestionService {
    private final NetworkRepository networkRepository;
    private final PcapParser pcapParser;
    private final DeviceRepository deviceRepository;

    public PacketIngestionService(NetworkRepository networkRepository, PcapParser pcapParser, DeviceRepository deviceRepository) {
        this.networkRepository = networkRepository;
        this.pcapParser = pcapParser;
        this.deviceRepository= deviceRepository;
    }

    public int ingest(String filePath) throws IOException {
        List<Packet> packets = pcapParser.openFile(filePath);

        for (Packet packet : packets) {
            Device device = findOrCreateDevice(packet.getSourceIp());
            packet.setDevice(device);
            networkRepository.save(packet);
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
        else{
            Device newDevice = new Device();
            newDevice.setIpAddress(ipAddress);
            newDevice.setFirstSeen(Instant.now());
            newDevice.setLastSeen(Instant.now());
            newDevice.setTrusted(false);
            return deviceRepository.save(newDevice);
        }

    }
}