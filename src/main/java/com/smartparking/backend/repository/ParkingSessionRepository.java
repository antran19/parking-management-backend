package com.smartparking.backend.repository;

import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.entity.ParkingSession.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParkingSessionRepository extends JpaRepository<ParkingSession, UUID> {
    Optional<ParkingSession> findBySessionCode(String sessionCode);
    Optional<ParkingSession> findByLicensePlateAndStatus(String licensePlate, SessionStatus status);
    Page<ParkingSession> findByStatus(SessionStatus status, Pageable pageable);
    List<ParkingSession> findByLicensePlateOrderByEntryTimeDesc(String licensePlate);

    // Lấy các session có entryTime trong khoảng (dùng để thống kê lượt gửi theo khoảng)
    List<ParkingSession> findByEntryTimeBetween(LocalDateTime from, LocalDateTime to);

    long countByEntryTimeBetween(LocalDateTime from, LocalDateTime to);

    // Số session hiện đang active theo exitTime null (realtime)
    long countByExitTimeIsNull();

    long countByStatus(SessionStatus status);
}
