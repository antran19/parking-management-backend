package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.CheckInRequest;
import com.smartparking.backend.dto.request.CheckOutRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.SessionResponse;
import com.smartparking.backend.service.ParkingSessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * SessionController — API cho Staff xử lý xe vào/ra.
 *
 * Endpoints:
 *   POST /api/v1/staff/sessions/checkin   → Check-in xe vào bãi (UC-04)
 *   POST /api/v1/staff/sessions/checkout  → Check-out xe ra bãi (UC-05)
 *   GET  /api/v1/sessions/active?plate=   → Xem session đang mở (UC-14)
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private final ParkingSessionService parkingSessionService;

    public SessionController(ParkingSessionService parkingSessionService) {
        this.parkingSessionService = parkingSessionService;
    }

    /**
     * Check-in xe vào bãi — chỉ STAFF trở lên.
     */
    @PostMapping("/staff/sessions/checkin")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> checkIn(
            @Valid @RequestBody CheckInRequest request) {
        SessionResponse response = parkingSessionService.checkIn(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Check-in thành công. " + response.getGuideMessage(), response));
    }

    /**
     * Check-out xe ra bãi — chỉ STAFF trở lên.
     */
    @PostMapping("/staff/sessions/checkout")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> checkOut(
            @Valid @RequestBody CheckOutRequest request) {
        SessionResponse response = parkingSessionService.checkOut(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Check-out thành công. Tổng phí: " + response.getTotalFee() + "đ", response));
    }

    /**
     * Xem session đang hoạt động — tất cả user đã đăng nhập.
     */
    @GetMapping("/sessions/active")
    public ResponseEntity<ApiResponse<SessionResponse>> getActiveSession(
            @RequestParam("plate") String licensePlate) {
        SessionResponse response = parkingSessionService.getActiveSession(licensePlate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
