package com.smartparking.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionLogResponse {
    private UUID id;
    private UUID sessionId;
    private String licensePlate;
    private String exceptionType;
    private String description;
    private String handledBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}