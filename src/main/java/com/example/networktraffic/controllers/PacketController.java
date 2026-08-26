package com.example.networktraffic.controllers;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.networktraffic.entities.Alert;
import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.repositories.AlertRepository;
import com.example.networktraffic.repositories.NetworkRepository;
import com.example.networktraffic.services.PacketIngestionService;

@RestController
public class PacketController {
  private final NetworkRepository networkRepository;
  private final PacketIngestionService ingestionService;
  private final AlertRepository alertRepository;

  public PacketController(final NetworkRepository networkRepository, final PacketIngestionService ingestionService, final AlertRepository alertRepository){
    this.networkRepository = networkRepository;
    this.ingestionService = ingestionService;
    this.alertRepository = alertRepository;
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

  @GetMapping("/alerts")
  public List<Alert> getAllAlerts(){
    return alertRepository.findAll();
  }

@PostMapping("/ingest/upload")
public String uploadAndIngest(@RequestParam("file") MultipartFile file) throws IOException {
    File tempFile = File.createTempFile("upload-", ".pcap");
    file.transferTo(tempFile);

    int count = ingestionService.ingest(tempFile.getAbsolutePath());

    tempFile.delete();

    return count + " packets ingested";
}

}