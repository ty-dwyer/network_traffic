package com.example.networktraffic.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

import com.example.networktraffic.dto.TrustDeviceRequest;
import com.example.networktraffic.entities.Device;
import com.example.networktraffic.repositories.DeviceRepository;
import com.example.networktraffic.util.Pagination;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {
    private final DeviceRepository deviceRepository;

    public DeviceController(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @GetMapping("/devices")
    public Page<Device> listDevices(@RequestParam(required = false) String ip,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = Pagination.pageable(page, size, "lastSeen");
        return ip == null || ip.isBlank() ? deviceRepository.findAll(pageable)
                : deviceRepository.findByIpAddressContainingIgnoreCase(ip, pageable);
    }

    @PatchMapping("/devices/{id}/trust")
    public Device setTrusted(@PathVariable long id, @Valid @RequestBody TrustDeviceRequest request) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        device.setTrusted(request.trusted());
        return deviceRepository.save(device);
    }
}
