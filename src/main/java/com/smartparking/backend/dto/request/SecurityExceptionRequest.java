package com.smartparking.backend.dto.request;

import com.smartparking.backend.entity.ExceptionLog.ExceptionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;
import java.util.List;

@Data
public class SecurityExceptionRequest {

    private UUID sessionId;

    @NotNull(message = "Loại ngoại lệ không được để trống")
    private ExceptionType exceptionType;

    private String description;

    @Pattern(regexp = "^([0-9]{2}[A-ZĐ0-9]{1,2}-[0-9]{3}\\.[0-9]{2}|[0-9]{2}[A-ZĐ0-9]{1,2}-[0-9]{4})$", message = "Biển số xe không đúng định dạng (VD: 59A1-123.45 hoặc 59A-1234)")
    private String licensePlate;

    private UUID handledByUserId;
    private List<String> imageUrls;
}