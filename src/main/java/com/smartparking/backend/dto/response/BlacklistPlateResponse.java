package com.smartparking.backend.dto.response;

import com.smartparking.backend.entity.BlacklistPlate;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BlacklistPlateResponse {
    private UUID id;
    private String licensePlate;
    private String normalizedPlate;
    private BlacklistPlate.BlacklistReason reason;
    private String description;
    private Boolean isActive;
    private String addedBy;
    private LocalDateTime addedAt;
    private String removedBy;
    private LocalDateTime removedAt;
}
