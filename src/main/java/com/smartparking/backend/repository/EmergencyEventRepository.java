package com.smartparking.backend.repository;

import com.smartparking.backend.entity.EmergencyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmergencyEventRepository extends JpaRepository<EmergencyEvent, UUID> {
    Optional<EmergencyEvent> findFirstByStatusOrderByActivatedAtDesc(EmergencyEvent.EmergencyStatus status);

    List<EmergencyEvent> findAllByOrderByActivatedAtDesc();
}
