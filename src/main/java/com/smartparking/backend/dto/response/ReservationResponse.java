package com.smartparking.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {
    private UUID id;
    private String reservationCode;
    private UUID zoneId;
    private String zoneCode;
    private String zoneName;
    private String floorName;
    private String vehicleTypeName;
    private String licensePlate;
    private LocalDateTime reservedFrom;
    private LocalDateTime reservedTo;
    private String status;
    private LocalDateTime createdAt;
}
