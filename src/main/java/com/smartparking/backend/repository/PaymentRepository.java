package com.smartparking.backend.repository;

import com.smartparking.backend.entity.Payment;
import com.smartparking.backend.repository.projection.ChartDataProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
        List<Payment> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

        Optional<Payment> findByTransactionId(String transactionId);

        @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status AND p.paidAt BETWEEN :from AND :to")
        BigDecimal sumAmountByStatusAndPaidAtBetween(
                        @Param("status") Payment.PaymentStatus status,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        @Query(value = "SELECT TO_CHAR(paid_at, 'HH24:00') as label, SUM(amount) as value " +
                        "FROM payments WHERE status = 'COMPLETED' AND paid_at BETWEEN :from AND :to " +
                        "GROUP BY TO_CHAR(paid_at, 'HH24:00') ORDER BY label ASC", nativeQuery = true)
        List<ChartDataProjection> sumRevenueGroupedByHour(@Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        @Query(value = "SELECT TO_CHAR(paid_at, 'DD/MM') as label, SUM(amount) as value " +
                        "FROM payments WHERE status = 'COMPLETED' AND paid_at BETWEEN :from AND :to " +
                        "GROUP BY TO_CHAR(paid_at, 'DD/MM') ORDER BY MIN(paid_at) ASC", nativeQuery = true)
        List<ChartDataProjection> sumRevenueGroupedByDay(@Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        @Query(value = "SELECT TO_CHAR(paid_at, 'MM/YYYY') as label, SUM(amount) as value " +
                        "FROM payments WHERE status = 'COMPLETED' AND paid_at BETWEEN :from AND :to " +
                        "GROUP BY TO_CHAR(paid_at, 'MM/YYYY') ORDER BY MIN(paid_at) ASC", nativeQuery = true)
        List<ChartDataProjection> sumRevenueGroupedByMonth(@Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = com.smartparking.backend.entity.Payment.PaymentStatus.COMPLETED AND p.paidAt >= :start AND p.paidAt < :end")
        BigDecimal sumCompletedPaymentsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

        // Lấy danh sách payment trong khoảng thời gian (dùng để nhóm theo
        // ngày/tháng/năm ở Service)
        List<Payment> findByPaidAtBetween(LocalDateTime from, LocalDateTime to);

        // Phân trang danh sách payment
        Page<Payment> findAll(Pageable pageable);
}
