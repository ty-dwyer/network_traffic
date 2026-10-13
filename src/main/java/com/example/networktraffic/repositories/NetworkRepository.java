package com.example.networktraffic.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.networktraffic.entities.Packet;


public interface NetworkRepository extends JpaRepository<Packet, Long>, JpaSpecificationExecutor<Packet> {
    
}
