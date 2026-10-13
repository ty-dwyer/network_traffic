package com.example.networktraffic.repositories;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import com.example.networktraffic.entities.Alert;

public final class AlertSpecifications {
    private AlertSpecifications() {}

    public static Specification<Alert> matching(Alert.AlertType type, Boolean acknowledged,
            String deviceIp, Instant from, Instant to) {
        Specification<Alert> spec = (root, query, cb) -> cb.conjunction();
        if (type != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        if (acknowledged != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("acknowledged"), acknowledged));
        }
        if (deviceIp != null && !deviceIp.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.join("device").get("ipAddress"), deviceIp));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timeStamp"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("timeStamp"), to));
        }
        return spec;
    }
}
