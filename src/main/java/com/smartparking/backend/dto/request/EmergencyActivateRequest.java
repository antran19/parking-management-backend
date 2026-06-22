package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class EmergencyActivateRequest {

    private UUID buildingId;

    @NotNull(message = "Người kích hoạt không được để trống")
    private UUID activatedByUserId;

    private String reason;

    private String notes;
}
