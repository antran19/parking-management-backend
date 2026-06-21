package com.smartparking.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SecurityIncidentSummary {
    private long totalIncidents;
    private long unresolvedIncidents;
    private java.util.Map<String, Long> byType; // ExceptionType -> count
}
