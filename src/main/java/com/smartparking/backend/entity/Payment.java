package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
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

    @Convert(converter = PaymentMethodConverter.class)
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

    // Ánh xạ an toàn payment_method: các giá trị enum cũ bị đổi tên (vd BANK_TRANSFER -> VIETQR)
    // hoặc không xác định sẽ được map về giá trị hợp lệ thay vì ném lỗi và làm sập cả request đọc payment.
    @Converter(autoApply = false)
    public static class PaymentMethodConverter implements AttributeConverter<PaymentMethod, String> {

        private static final Map<String, PaymentMethod> LEGACY_ALIASES = Map.of(
                "BANK_TRANSFER", PaymentMethod.VIETQR
        );

        @Override
        public String convertToDatabaseColumn(PaymentMethod attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public PaymentMethod convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            try {
                return PaymentMethod.valueOf(dbData);
            } catch (IllegalArgumentException e) {
                PaymentMethod fallback = LEGACY_ALIASES.get(dbData);
                if (fallback != null) {
                    return fallback;
                }
                System.err.println("Unknown payment_method value in DB: '" + dbData + "' - falling back to CASH");
                return PaymentMethod.CASH;
            }
        }
    }
}
