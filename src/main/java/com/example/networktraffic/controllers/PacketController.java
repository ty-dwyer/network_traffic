package com.example.networktraffic.controllers;

import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.repositories.NetworkRepository;
import com.example.networktraffic.services.PacketIngestionService;

@RestController
public class PacketController {
  private final NetworkRepository networkRepository;
  private final PacketIngestionService ingestionService;

  public PacketController(final NetworkRepository networkRepository, final PacketIngestionService ingestionService){
    this.networkRepository = networkRepository;
    this.ingestionService = ingestionService;
  }

  @GetMapping("/packets")
  public List<Packet> getAllPackets(){
    return (List<Packet>) this.networkRepository.findAll();
  }

  @PostMapping("/ingest")
  public String ingestPackets(@RequestParam String path) throws IOException {
    int count = ingestionService.ingest(path);
    return count + " packets ingested";
  }
}