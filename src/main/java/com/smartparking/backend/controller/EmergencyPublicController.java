package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.EmergencyStatusResponse;
import com.smartparking.backend.service.EmergencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EmergencyPublicController — API SOS công khai (Thiên phụ trách)
 *
 * Endpoint này cho phép các thiết bị IoT (Barrier) hoặc hệ thống ngoại vi
 * kiểm tra trạng thái SOS mà không cần đăng nhập.
 */
@RestController
@RequestMapping({"/api/v1/emergency", "/api/v1/public/emergency"})
public class EmergencyPublicController {

    private final EmergencyService emergencyService;

    // Constructor injection
    public EmergencyPublicController(EmergencyService emergencyService) {
        this.emergencyService = emergencyService;
    }

    /**
     * Lấy trạng thái SOS hiện tại (không yêu cầu token)
     * GET /api/v1/emergency/status
     * GET /api/v1/public/emergency/status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<EmergencyStatusResponse>> getPublicEmergencyStatus() {
        EmergencyStatusResponse response = emergencyService.getCurrentStatus();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}