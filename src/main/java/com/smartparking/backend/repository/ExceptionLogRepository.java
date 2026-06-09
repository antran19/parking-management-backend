package com.smartparking.backend.repository;

import com.smartparking.backend.entity.ExceptionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExceptionLogRepository extends JpaRepository<ExceptionLog, UUID> {
    java.util.List<ExceptionLog> findAllByOrderByResolvedAtDesc();
}
