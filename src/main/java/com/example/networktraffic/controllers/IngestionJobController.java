package com.example.networktraffic.controllers;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.example.networktraffic.entities.IngestionJob;
import com.example.networktraffic.repositories.IngestionJobRepository;
import com.example.networktraffic.util.Pagination;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class IngestionJobController {
    private final IngestionJobRepository jobRepository;

    public IngestionJobController(IngestionJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @GetMapping("/ingestion-jobs/{id}")
    public IngestionJob getJob(@PathVariable long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion job not found"));
    }

    @GetMapping("/ingestion-jobs")
    public Page<IngestionJob> listJobs(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return jobRepository.findAll(Pagination.pageable(page, size, "createdAt"));
    }
}
