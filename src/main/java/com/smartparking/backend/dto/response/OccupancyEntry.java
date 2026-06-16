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
public class OccupancyEntry {
    private String id; // UUID as string or code
    private String name; // zone/floor/building name
    private int capacity;
    private int occupied;
    private double percent;
    private String level; // building/floor/zone
    private LocalDate reportDate;
    private int defaultCapacity;
    private int currentOccupancy;
    private int availableSlots;
}
