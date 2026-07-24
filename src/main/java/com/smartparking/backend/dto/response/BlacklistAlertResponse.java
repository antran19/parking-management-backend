package com.smartparking.backend.dto.response;

import com.smartparking.backend.entity.BlacklistPlate;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BlacklistAlertResponse {
    private String type;
    private String licensePlate;
    private String normalizedPlate;
    private String vehicleType;
    private BlacklistPlate.BlacklistReason reason;
    private String description;
    private UUID gateId;
    private String gateCode;
    private String gateName;
    private UUID buildingId;
    private String buildingName;
    private LocalDateTime detectedAt;
    private String message;
}
