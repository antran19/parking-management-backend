package com.smartparking.backend.dto.response;

import com.smartparking.backend.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentDetailResponse {
    private UUID id;
    private String referenceType;
    private UUID referenceId;
    private BigDecimal amount;
    private Payment.PaymentMethod paymentMethod;
    private Payment.PaymentStatus status;
    private String transactionId;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
