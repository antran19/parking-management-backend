package com.smartparking.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BuildingOccupancyResponse {
    private String id;
    private String name;
    private int totalCapacity;
    private int totalOccupied;
    private int availableSlots;
    private double percent;
    private LocalDate reportDate;
}