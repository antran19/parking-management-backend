package com.smartparking.backend.dto.response;
;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FloorOccupancyResponse {
    private String id;
    private String floorName;
    private int capacity;
    private int occupied;
    private int availableSlots;
    private double percent;
    private LocalDate reportDate;


}
