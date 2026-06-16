package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

/**
 * Request DTO cho UC-04: Check-in xe vào bãi.
 * Staff nhập thông tin xe → hệ thống tạo Parking Session.
 */
@Data
public class CheckInRequest {

    @NotBlank(message = "Biển số xe không được để trống")
    @Pattern(regexp = "^\\s*\\d{2}[A-Za-z]{1,2}\\d?-\\d{3}(\\.\\d{2}|\\d{2})\\s*$", message = "Biển số không đúng định dạng. Ví dụ: 51F-123.45, 30A-12345 hoặc 59X1-12345")
    private String licensePlate; // "51A-12345"

    @NotNull(message = "Loại phương tiện không được để trống")
    private UUID vehicleTypeId;

    @NotNull(message = "Cổng vào không được để trống")
    private UUID gateEntryId;

    // Optional: mã đặt chỗ trước (nếu có pre-booking)
    private String reservationCode;

    // Optional: Loại tài xế (WALK_IN, PRE_BOOKED, SUBSCRIBER)
    private String driverType;

    // Optional: ghi chú đặc biệt
    private String notes;

    private String entryPlateImageUrl;
    private String entryFaceImageUrl;
}
