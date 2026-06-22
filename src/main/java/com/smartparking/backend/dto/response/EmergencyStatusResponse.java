package com.smartparking.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class EmergencyStatusResponse {
    private boolean active;
    private UUID eventId;
    private UUID buildingId;
    private String buildingName;
    private String reason;
    private String activatedBy;
    private LocalDateTime activatedAt;
    private String deactivatedBy;
    private LocalDateTime deactivatedAt;
    private String message;
}
