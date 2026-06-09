package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

/**
 * Request DTO cho UC-05: Check-out xe ra bãi.
 * Staff nhập biển số hoặc session ID → hệ thống tính phí và đóng session.
 */
@Data
public class CheckOutRequest {

    // Có thể tìm session bằng 1 trong 3 cách (ít nhất 1 field phải có)
    private UUID sessionId;        // ID session trực tiếp
    private String sessionCode;    // Mã vé in trên phiếu (PS20240513001)
    @Pattern(regexp = "^\\s*$|^\\s*\\d{2}[A-Za-z]{1,2}\\d?-\\d{3}(\\.\\d{2}|\\d{2})\\s*$", message = "Biển số không đúng định dạng. Ví dụ: 51F-123.45, 30A-12345 hoặc 59X1-12345")
    private String licensePlate;   // Tìm theo biển số

    @NotNull(message = "Cổng ra không được để trống")
    private UUID gateExitId;

    // Phương thức thanh toán: CASH, VNPAY, MOMO
    private String paymentMethod = "CASH";

    // Optional: ghi chú ngoại lệ (mất thẻ, sai biển số...)
    private String exceptionNote;
}
