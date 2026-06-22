package com.smartparking.backend.repository;

import com.smartparking.backend.entity.BlacklistPlate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlacklistPlateRepository extends JpaRepository<BlacklistPlate, UUID> {
    Optional<BlacklistPlate> findByNormalizedPlateAndIsActiveTrue(String normalizedPlate);

    boolean existsByNormalizedPlateAndIsActiveTrue(String normalizedPlate);

    List<BlacklistPlate> findAllByOrderByAddedAtDesc();
}
