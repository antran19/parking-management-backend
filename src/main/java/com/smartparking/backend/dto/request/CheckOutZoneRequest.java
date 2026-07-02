package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Request DTO cho việc Check-out tại cổng Zone (Cổng phụ ra).
 */
@Data
public class CheckOutZoneRequest {

    @NotBlank(message = "Mã vé hoặc biển số xe không được để trống")
    private String sessionCode;

    @NotNull(message = "Cổng phụ ra (Zone Exit Gate) không được để trống")
    private UUID gateExitId;
}
