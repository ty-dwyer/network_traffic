package com.example.networktraffic.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.networktraffic.entities.IngestionJob;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {
}
