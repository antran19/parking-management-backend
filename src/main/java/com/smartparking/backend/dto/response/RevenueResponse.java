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
    private java.util.List<ChartDataPoint> chartData;

    public RevenueResponse() {
    }

    public RevenueResponse(LocalDateTime from, LocalDateTime to, BigDecimal totalRevenue, long totalSessions, java.util.List<ChartDataPoint> chartData) {
        this.from = from;
        this.to = to;
        this.totalRevenue = totalRevenue;
        this.totalSessions = totalSessions;
        this.chartData = chartData;
    }

    public java.util.List<ChartDataPoint> getChartData() {
        return chartData;
    }

    public void setChartData(java.util.List<ChartDataPoint> chartData) {
        this.chartData = chartData;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public void setFrom(LocalDateTime from) {
        this.from = from;
    }

    public LocalDateTime getTo() {
        return to;
    }

    public void setTo(LocalDateTime to) {
        this.to = to;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public long getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(long totalSessions) {
        this.totalSessions = totalSessions;
    }
}