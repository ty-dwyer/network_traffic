package com.example.networktraffic.dto;

import jakarta.validation.constraints.NotNull;

public record TrustDeviceRequest(@NotNull Boolean trusted) {
}
