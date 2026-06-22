package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ReservationRequest {
    @NotNull
    private UUID zoneId;

    @NotNull
    private UUID vehicleTypeId;

    @NotBlank
    private String licensePlate;

    private LocalDateTime reservedFrom;

    private LocalDateTime reservedTo;
}
