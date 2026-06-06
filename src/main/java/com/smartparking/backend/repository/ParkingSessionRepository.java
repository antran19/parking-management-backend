package com.smartparking.backend.repository;

import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.entity.ParkingSession.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParkingSessionRepository extends JpaRepository<ParkingSession, UUID> {
    Optional<ParkingSession> findBySessionCode(String sessionCode);
    Optional<ParkingSession> findByLicensePlateAndStatus(String licensePlate, SessionStatus status);
    Page<ParkingSession> findByStatus(SessionStatus status, Pageable pageable);

    // For manager statistics
    long countByStatus(SessionStatus status);

    long countByEntryTimeBetween(LocalDateTime from, LocalDateTime to);

    long countByExitTimeBetween(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(p.totalFee), 0) FROM ParkingSession p WHERE p.exitTime BETWEEN :from AND :to")
    BigDecimal sumTotalFeeBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
