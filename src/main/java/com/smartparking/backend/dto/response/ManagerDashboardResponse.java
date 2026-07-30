package com.smartparking.backend.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerDashboardResponse {
    private BigDecimal todayRevenue;
    private long activeSessions;
    private double occupancyPercent;
    private long completedSessionsToday;
    private long securityIncidentsToday;
    private boolean activeEmergency;
}
