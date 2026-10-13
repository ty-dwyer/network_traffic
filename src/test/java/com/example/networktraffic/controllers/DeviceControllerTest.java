package com.example.networktraffic.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.networktraffic.dto.TrustDeviceRequest;
import com.example.networktraffic.entities.Device;
import com.example.networktraffic.repositories.DeviceRepository;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {
    @Mock DeviceRepository deviceRepository;

    @Test
    void updatesTrustedStateAndPersistsDevice() {
        Device device = new Device();
        device.setId(3L);
        when(deviceRepository.findById(3L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);

        Device updated = new DeviceController(deviceRepository).setTrusted(3L, new TrustDeviceRequest(true));

        assertThat(updated.getTrusted()).isTrue();
        verify(deviceRepository).save(device);
    }
}
