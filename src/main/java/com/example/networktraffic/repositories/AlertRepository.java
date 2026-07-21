package com.example.networktraffic.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.networktraffic.entities.Alert;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    
}