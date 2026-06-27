package com.smartparking.backend.repository;

import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.entity.ParkingSession.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

        // Lấy các session có entryTime trong khoảng (dùng để thống kê lượt gửi theo
        // khoảng)
        List<ParkingSession> findByEntryTimeBetween(LocalDateTime from, LocalDateTime to);

        long countByEntryTimeBetween(LocalDateTime from, LocalDateTime to);

        // Số session hiện đang active theo exitTime null (realtime)
        long countByExitTimeIsNull();

        long countByStatus(SessionStatus status);

    // Aggregation queries for chart data using unified projection
    @Query(value = "SELECT TO_CHAR(entry_time, 'HH24:00') as label, COUNT(*) as value " +
            "FROM parking_sessions WHERE entry_time BETWEEN :from AND :to " +
            "GROUP BY TO_CHAR(entry_time, 'HH24:00') ORDER BY label ASC", nativeQuery = true)
    List<com.smartparking.backend.repository.projection.ChartDataProjection> countVisitsGroupedByHour(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT TO_CHAR(entry_time, 'DD/MM') as label, COUNT(*) as value " +
            "FROM parking_sessions WHERE entry_time BETWEEN :from AND :to " +
            "GROUP BY TO_CHAR(entry_time, 'DD/MM') ORDER BY MIN(entry_time) ASC", nativeQuery = true)
    List<com.smartparking.backend.repository.projection.ChartDataProjection> countVisitsGroupedByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT TO_CHAR(entry_time, 'MM/YYYY') as label, COUNT(*) as value " +
            "FROM parking_sessions WHERE entry_time BETWEEN :from AND :to " +
            "GROUP BY TO_CHAR(entry_time, 'MM/YYYY') ORDER BY MIN(entry_time) ASC", nativeQuery = true)
    List<com.smartparking.backend.repository.projection.ChartDataProjection> countVisitsGroupedByMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
