package com.example.networktraffic.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.networktraffic.entities.Packet;


public interface NetworkRepository extends JpaRepository<Packet, Long>{ //Jpa repository instead of crud to get time stamped ranges
    
}
