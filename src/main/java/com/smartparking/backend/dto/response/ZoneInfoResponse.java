package com.smartparking.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneInfoResponse {
    private UUID id;
    private String zoneCode;
    private String zoneName;
    private String floorName;
    private int capacity;
    private int currentCount;
    private String status;
}
