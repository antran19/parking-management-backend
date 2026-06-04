package com.smartparking.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID paymentId;
    private String referenceType;
    private UUID referenceId;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private String qrCodeUrl;      // Đường dẫn ảnh QR ngân hàng (nếu là BANK_TRANSFER)
    private String transactionId;  // Mã giao dịch giả lập
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
