package com.smartparking.backend.repository;

import com.smartparking.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = com.smartparking.backend.entity.Payment.PaymentStatus.COMPLETED AND p.paidAt >= :start AND p.paidAt < :end")
    BigDecimal sumCompletedPaymentsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status AND p.paidAt >= :start AND p.paidAt < :end")
    BigDecimal sumAmountByStatusAndPaidAtBetween(
            @Param("status") Payment.PaymentStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
