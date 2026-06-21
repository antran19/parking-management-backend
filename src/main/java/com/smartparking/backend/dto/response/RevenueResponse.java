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

    public RevenueResponse() {
    }

    public RevenueResponse(LocalDateTime from, LocalDateTime to, BigDecimal totalRevenue, long totalSessions,
            String currency) {
        this.from = from;
        this.to = to;
        this.totalRevenue = totalRevenue;
        this.totalSessions = totalSessions;
        this.currency = currency;
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

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}