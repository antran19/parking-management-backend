package com.smartparking.backend.repository;

import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.entity.ParkingSession.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParkingSessionRepository extends JpaRepository<ParkingSession, UUID> {
    Optional<ParkingSession> findBySessionCode(String sessionCode);
    Optional<ParkingSession> findByLicensePlateAndStatus(String licensePlate, SessionStatus status);
    Page<ParkingSession> findByStatus(SessionStatus status, Pageable pageable);
    List<ParkingSession> findByLicensePlateOrderByEntryTimeDesc(String licensePlate);

    long countByEntryTimeBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);
}
