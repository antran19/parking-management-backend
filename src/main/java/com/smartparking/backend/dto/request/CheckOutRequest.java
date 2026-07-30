package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotNull;
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
    // Validated in service layer to support bicycle empty plates
    private String licensePlate;   // Tìm theo biển số

    private String exitPlate;      // Biển số xe nhận dạng thực tế lúc ra (để đối soát)

    @NotNull(message = "Cổng ra không được để trống")
    private UUID gateExitId;

    // Phương thức thanh toán: CASH, VNPAY, MOMO
    private String paymentMethod = "CASH";

}
