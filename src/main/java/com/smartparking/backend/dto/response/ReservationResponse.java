package com.smartparking.backend.dto.response;

import com.smartparking.backend.entity.Reservation.ReservationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ReservationResponse {
    private UUID reservationId;
    private String reservationCode;
    private UUID zoneId;
    private String zoneCode;
    private String zoneName;
    private String floorName;
    private UUID vehicleTypeId;
    private String vehicleTypeName;
    private String licensePlate;
    private LocalDateTime reservedFrom;
    private LocalDateTime reservedTo;
    private ReservationStatus status;
    private LocalDateTime createdAt;
    private String customerName;
}
