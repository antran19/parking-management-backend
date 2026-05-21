package com.smartparking.backend.dto.response;

import com.smartparking.backend.entity.Reservation;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response trả về cho FE sau khi đặt chỗ / xem đặt chỗ.
 */
@Getter
@Setter
@Builder
public class ReservationResponse {

    private UUID reservationId;

    private UUID slotId;

    private String slotCode;

    private String floorName;

    private UUID vehicleTypeId;

    private String vehicleTypeName;

    private String licensePlate;

    private LocalDateTime reservedFrom;

    private LocalDateTime reservedTo;

    private Reservation.ReservationStatus status;

    private String reservationCode;

    private String message;
}