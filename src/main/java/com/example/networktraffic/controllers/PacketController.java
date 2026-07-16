package com.example.networktraffic.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.repositories.NetworkRepository;


@RestController
public class PacketController {
  private final NetworkRepository networkRepository;

  public PacketController(final NetworkRepository networkRepository){
    this.networkRepository = networkRepository;
  }
  
  @GetMapping("/packets")
  public List<Packet> getAllPackets(){
    return (List<Packet>) this.networkRepository.findAll();
  }
}
