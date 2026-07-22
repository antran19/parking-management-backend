package com.smartparking.backend.repository;

import com.smartparking.backend.entity.Gate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GateRepository extends JpaRepository<Gate, UUID> {
    List<Gate> findByBuildingId(UUID buildingId);

    List<Gate> findByZoneId(UUID zoneId);

    List<Gate> findByIsActiveTrueAndGateTypeIn(List<Gate.GateType> gateTypes);
}
