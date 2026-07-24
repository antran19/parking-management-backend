package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Dùng polymorphic reference thay vì FK cứng
    @Column(name = "reference_type", nullable = false, length = 20)
    private String referenceType; // SESSION | MONTHLY_PASS | RESERVATION

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 15)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "transaction_id", length = 100)
    private String transactionId; // Mã giao dịch từ VNPay/Momo

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum PaymentMethod {
        CASH, ONLINE, QR_CODE, VNPAY, VIETQR, NCB
    }

    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED, REFUNDED
    }
}
