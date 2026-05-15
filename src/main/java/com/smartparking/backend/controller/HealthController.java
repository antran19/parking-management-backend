package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.success("AI Study Hub Backend is running!", Map.of(
                "status", "OK",
                "timestamp", LocalDateTime.now().toString(),
                "version", "1.0.0"
        ));
    }
}
