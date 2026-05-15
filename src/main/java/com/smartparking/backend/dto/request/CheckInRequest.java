package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Request DTO cho UC-04: Check-in xe vào bãi.
 * Staff nhập thông tin xe → hệ thống tạo Parking Session.
 */
@Data
public class CheckInRequest {

    @NotBlank(message = "Biển số xe không được để trống")
    private String licensePlate; // "51A-12345"

    @NotNull(message = "Loại phương tiện không được để trống")
    private UUID vehicleTypeId;

    @NotNull(message = "Cổng vào không được để trống")
    private UUID gateEntryId;

    // Optional: mã đặt chỗ trước (nếu có pre-booking)
    private String reservationCode;

    // Optional: ghi chú đặc biệt
    private String notes;
}
