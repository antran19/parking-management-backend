package com.smartparking.backend.repository;

import com.smartparking.backend.entity.ExceptionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExceptionLogRepository extends JpaRepository<ExceptionLog, UUID> {
    java.util.List<ExceptionLog> findAllByOrderByResolvedAtDesc();

    // Đếm sự cố chưa được giải quyết
    long countByResolvedAtIsNull();

    // Lấy sự cố trong khoảng thời gian
    List<ExceptionLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(e) FROM ExceptionLog e WHERE e.createdAt >= :start AND e.createdAt < :end")
    long countExceptionsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(e) FROM ExceptionLog e WHERE e.licensePlate = :licensePlate AND e.exceptionType = com.smartparking.backend.entity.ExceptionLog.ExceptionType.WRONG_ZONE AND e.createdAt >= :since")
    long countWrongZoneByLicensePlateSince(@Param("licensePlate") String licensePlate, @Param("since") LocalDateTime since);
}
