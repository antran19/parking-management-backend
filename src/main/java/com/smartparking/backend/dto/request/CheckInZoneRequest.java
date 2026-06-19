package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Request DTO cho việc Check-in lần 2 tại cổng Zone (Cổng phụ).
 */
@Data
public class CheckInZoneRequest {

    @NotBlank(message = "Mã vé hoặc biển số xe không được để trống")
    private String sessionCode; // Có thể là mã vé (sessionCode) hoặc mã QR (qrCode) hoặc biển số xe

    @NotNull(message = "Cổng phụ (Zone Gate) không được để trống")
    private UUID gateEntryId; // Cổng phụ nơi khách quét QR để vào zone
}
