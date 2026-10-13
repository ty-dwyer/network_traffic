package com.example.networktraffic.repositories;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.entities.Protocol;

public final class PacketSpecifications {
    private PacketSpecifications() {}

    public static Specification<Packet> matching(String sourceIp, String destIp, Protocol protocol,
            Instant from, Instant to) {
        Specification<Packet> spec = (root, query, cb) -> cb.conjunction();
        if (sourceIp != null && !sourceIp.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sourceIp"), sourceIp));
        }
        if (destIp != null && !destIp.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("destIp"), destIp));
        }
        if (protocol != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("protocol"), protocol));
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
