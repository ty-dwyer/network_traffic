package com.example.networktraffic.controllers;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.example.networktraffic.entities.IngestionJob;
import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.entities.Protocol;
import com.example.networktraffic.repositories.NetworkRepository;
import com.example.networktraffic.repositories.PacketSpecifications;
import com.example.networktraffic.services.PacketIngestionService;
import com.example.networktraffic.util.Pagination;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class PacketController {
    private final NetworkRepository networkRepository;
    private final PacketIngestionService ingestionService;

    public PacketController(NetworkRepository networkRepository, PacketIngestionService ingestionService) {
        this.networkRepository = networkRepository;
        this.ingestionService = ingestionService;
    }

    /** Legacy unpaged endpoint retained for the current dashboard. Prefer /packets/search for large captures. */
    @GetMapping("/packets")
    public List<Packet> getAllPackets() {
        return networkRepository.findAll();
    }

    @GetMapping("/packets/search")
    public Page<Packet> searchPackets(
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String destIp,
            @RequestParam(required = false) Protocol protocol,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return networkRepository.findAll(PacketSpecifications.matching(sourceIp, destIp, protocol, from, to),
                Pagination.pageable(page, size, "timeStamp"));
    }

    @PostMapping("/ingest")
    public ResponseEntity<String> ingestPackets(@RequestParam String path) {
        IngestionJob job = ingestionService.startIngestion(path, Path.of(path).getFileName().toString(), false);
        return acceptedJob(job);
    }

    @PostMapping("/ingest/upload")
    public ResponseEntity<String> uploadAndIngest(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Uploaded capture is empty");
        Path tempFile = Files.createTempFile("upload-", ".pcap");
        try {
            file.transferTo(tempFile.toFile());
            String fileName = file.getOriginalFilename() == null ? "capture.pcap"
                    : Path.of(file.getOriginalFilename().replace('\\', '/')).getFileName().toString();
            IngestionJob job = ingestionService.startIngestion(tempFile.toString(), fileName, true);
            return acceptedJob(job);
        } catch (RuntimeException | IOException ex) {
            Files.deleteIfExists(tempFile);
            throw ex;
        }
    }

    private ResponseEntity<String> acceptedJob(IngestionJob job) {
        URI statusUri = URI.create("/ingestion-jobs/" + job.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).location(statusUri)
                .body("Ingestion job " + job.getId() + " queued");
    }
}
