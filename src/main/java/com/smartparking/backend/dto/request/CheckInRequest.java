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

    // Validated in service layer to support bicycle empty plates
    private String licensePlate; // "51A-12345" hoặc rỗng nếu là xe đạp

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

}
