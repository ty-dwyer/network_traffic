package com.example.networktraffic.services;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.parser.PcapParser;
import com.example.networktraffic.repositories.NetworkRepository;

@Service
public class PacketIngestionService {
    private final NetworkRepository networkRepository;
    private final PcapParser pcapParser;

    public PacketIngestionService(NetworkRepository networkRepository, PcapParser pcapParser) {
        this.networkRepository = networkRepository;
        this.pcapParser = pcapParser;
    }

    public int ingest(String filePath) throws IOException {
        List<Packet> packets = pcapParser.openFile(filePath);

        for (Packet packet : packets) {
            networkRepository.save(packet);
        }

        return packets.size();
    }
}