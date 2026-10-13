package com.example.networktraffic.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.networktraffic.entities.Alert;
import com.example.networktraffic.repositories.AlertRepository;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {
    @Mock AlertRepository alertRepository;

    @Test
    void acknowledgeMarksAlertAndPersistsIt() {
        Alert alert = new Alert();
        alert.setId(9L);
        alert.setAcknowledged(false);
        when(alertRepository.findById(9L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(alert)).thenReturn(alert);

        Alert updated = new AlertController(alertRepository).acknowledge(9L);

        assertThat(updated.getAcknowledged()).isTrue();
        verify(alertRepository).save(alert);
    }
}
