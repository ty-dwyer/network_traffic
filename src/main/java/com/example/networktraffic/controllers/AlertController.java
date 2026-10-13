package com.example.networktraffic.controllers;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.example.networktraffic.entities.Alert;
import com.example.networktraffic.repositories.AlertRepository;
import com.example.networktraffic.repositories.AlertSpecifications;
import com.example.networktraffic.util.Pagination;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class AlertController {
    private final AlertRepository alertRepository;

    public AlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /** Legacy unpaged endpoint retained for the current dashboard. Prefer /alerts/search for large datasets. */
    @GetMapping("/alerts")
    public List<Alert> getAllAlerts() {
        return alertRepository.findAll();
    }

    @GetMapping("/alerts/search")
    public Page<Alert> searchAlerts(
            @RequestParam(required = false) Alert.AlertType type,
            @RequestParam(required = false) Boolean acknowledged,
            @RequestParam(required = false) String deviceIp,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return alertRepository.findAll(AlertSpecifications.matching(type, acknowledged, deviceIp, from, to),
                Pagination.pageable(page, size, "timeStamp"));
    }

    @PatchMapping("/alerts/{id}/acknowledge")
    public Alert acknowledge(@PathVariable long id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        alert.setAcknowledged(true);
        return alertRepository.save(alert);
    }
}
