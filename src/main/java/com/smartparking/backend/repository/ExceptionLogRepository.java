package com.smartparking.backend.repository;

import com.smartparking.backend.entity.ExceptionLog;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
