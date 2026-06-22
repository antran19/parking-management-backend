package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class EmergencyDeactivateRequest {

    @NotNull(message = "Người hủy SOS không được để trống")
    private UUID deactivatedByUserId;

    private String notes;
}
