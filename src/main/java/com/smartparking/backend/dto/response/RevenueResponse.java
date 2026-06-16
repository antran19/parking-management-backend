package com.smartparking.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RevenueResponse {
    private LocalDateTime from;
    private LocalDateTime to;
    private BigDecimal totalRevenue;
    private long totalSessions;
    private String currency;
}