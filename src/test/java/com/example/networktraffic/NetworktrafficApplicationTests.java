package com.example.networktraffic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.example.networktraffic.entities.Device;
import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.entities.Protocol;
import com.example.networktraffic.repositories.DeviceRepository;
import com.example.networktraffic.repositories.NetworkRepository;
import com.example.networktraffic.repositories.PacketSpecifications;

@SpringBootTest(properties = "security.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
class NetworktrafficApplicationTests {
	@Autowired NetworkRepository networkRepository;
	@Autowired DeviceRepository deviceRepository;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void packetSearchFiltersAndPagesPersistedPackets() {
		Device device = new Device();
		device.setIpAddress("198.51.100.11");
		device.setTrusted(false);
		device = deviceRepository.save(device);

		Packet packet = new Packet();
		packet.setTimeStamp(Instant.parse("2026-01-01T00:00:00Z"));
		packet.setSourceIp("198.51.100.11");
		packet.setDestIp("203.0.113.7");
		packet.setProtocol(Protocol.TCP);
		packet.setDevice(device);
		networkRepository.save(packet);

		var results = networkRepository.findAll(PacketSpecifications.matching("198.51.100.11", null,
				Protocol.TCP, Instant.parse("2025-12-31T00:00:00Z"), null), PageRequest.of(0, 10));

		assertThat(results.getTotalElements()).isEqualTo(1);
		assertThat(results.getContent()).extracting(Packet::getDestIp).containsExactly("203.0.113.7");
	}

}
