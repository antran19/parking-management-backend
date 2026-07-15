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

    /*
     * Xe máy / ô tô / xe tải: frontend gửi biển số.
     * Xe đạp: frontend gửi null hoặc rỗng, backend tự tạo mã 4 số.
     */
    private String licensePlate;

    private LocalDateTime reservedFrom;

    private LocalDateTime reservedTo;
}
