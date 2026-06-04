package com.smartparking.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParkingPassResponse {
    private UUID id;
    private String userEmail;
    private String userName;
    private String buildingName;
    private String vehicleTypeName;
    private String licensePlate;
    private String qrCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private String passType;
    private BigDecimal fee;
    private String status;
    private LocalDateTime createdAt;
}
