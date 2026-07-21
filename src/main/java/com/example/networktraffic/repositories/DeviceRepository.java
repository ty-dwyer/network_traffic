package com.example.networktraffic.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.networktraffic.entities.Device;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByIpAddress(String ipAddress);
}