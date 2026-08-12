package com.example.networktraffic.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.networktraffic.entities.Alert;
import com.example.networktraffic.entities.Device;

public interface AlertRepository extends JpaRepository<Alert, Long> {
        boolean existsByDeviceAndType(Device device, Alert.AlertType type);
}