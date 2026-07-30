package com.smartparking.backend.dto.request;

import com.smartparking.backend.entity.ExceptionLog;
import com.smartparking.backend.entity.ExceptionLog.ExceptionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SecurityExceptionRequest {

    @NotNull(message = "Loại ngoại lệ không được để trống")
    private ExceptionType exceptionType;

    private String description;
    private String licensePlate;
    private String vehicleType;
    private UUID handledByUserId;
    private List<String> imageUrls;
    private ExceptionLog.ExceptionStatus status;
    private String resolution;
    private List<String> resolutionImageUrls;
}