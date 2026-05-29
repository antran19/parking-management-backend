package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ReservationRequest {

    @NotNull(message = "Vui lòng chọn khu vực đỗ xe (Zone)")
    private UUID zoneId;

    @NotNull(message = "Vui lòng chọn loại phương tiện")
    private UUID vehicleTypeId;

    @NotBlank(message = "Biển số xe không được để trống")
    private String licensePlate;

    @NotNull(message = "Thời gian bắt đầu đỗ xe không được để trống")
    private LocalDateTime reservedFrom;

    @NotNull(message = "Thời gian dự kiến ra không được để trống")
    private LocalDateTime reservedTo;
}
