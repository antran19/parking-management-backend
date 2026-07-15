package com.smartparking.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TimeSeriesEntry {
    private String label; // e.g., 2026-06-15 or 2026-06
    private LocalDate date; // nullable for month/year grouping
    private BigDecimal amount; // for revenue
    private Long count; // for visits
}
