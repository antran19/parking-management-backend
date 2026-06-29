package com.smartparking.backend.dto.response;

import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibleZoneResponse {
    private UUID zoneId;
    private String zoneCode;
    private String zoneName;
    private String floorName;
    private Integer capacity;
    private Integer currentCount;
    private Integer reservedCount;
    private Integer priority;          // 1, 2, 3...
    private Boolean isMaintenance;
    private Boolean isSelectable;
}
