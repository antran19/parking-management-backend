package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.CheckInRequest;
import com.smartparking.backend.dto.request.CheckOutRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.SessionResponse;
import com.smartparking.backend.service.ParkingSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SessionController — API quản lý phiên gửi xe (Parking Session).
 * Được thiết kế theo chuẩn RESTful chuyên nghiệp, phân tách rõ nhóm Staff và Driver.
 *
 * Endpoints:
 *   - POST /api/v1/staff/sessions/checkin   → Check-in xe vào bãi (Chỉ STAFF trở lên)
 *   - POST /api/v1/staff/sessions/checkout  → Check-out xe ra bãi (Chỉ STAFF trở lên)
 *   - GET  /api/v1/driver/sessions/active   → Xem session đang mở của tài xế (Tài xế + Staff)
 *   - GET  /api/v1/driver/sessions/history  → Lịch sử gửi xe theo biển số (Tài xế + Staff)
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

    /*
     * Check-out xe ra bãi — chỉ STAFF trở lên.
     * (Tạm thời đóng phục vụ Milestone 1)
    @PostMapping("/staff/sessions/checkout")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> checkOut(
            @Valid @RequestBody CheckOutRequest request) {
        SessionResponse response = parkingSessionService.checkOut(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Check-out thành công. Tổng phí: " + response.getTotalFee() + "đ", response));
    }
    */

    /*
     * Lấy toàn bộ danh sách phiên gửi xe cho Staff/Manager/Admin xem.
     * (Tạm thời đóng phục vụ Milestone 1)
    @GetMapping("/staff/sessions/history")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<SessionResponse>>> getAllSessions() {
        java.util.List<SessionResponse> history = parkingSessionService.getAllSessions();
        return ResponseEntity.ok(ApiResponse.success(history));
    }
    */

    /*
     * Driver khởi tạo thanh toán VNPay sandbox để check-out phiên gửi xe đang hoạt động.
     * (Tạm thời đóng phục vụ Milestone 1)
    @PostMapping("/driver/sessions/checkout/vnpay")
    @PreAuthorize("hasAnyRole('DRIVER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateVnPayCheckout(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Map<String, Object> response = parkingSessionService.initiateDriverVnPayCheckout(body, request);
        return ResponseEntity.ok(ApiResponse.success("Đã tạo liên kết thanh toán VNPay cho phiên gửi xe", response));
    }
    */

    /*
     * Xem session đang hoạt động của Driver — Mọi authenticated user (Driver/Staff) có quyền truy cập.
     * (Tạm thời đóng phục vụ Milestone 1)
    @GetMapping("/driver/sessions/active")
    @PreAuthorize("hasAnyRole('DRIVER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> getActiveSession(
            @RequestParam("plate") String licensePlate) {
        SessionResponse response = parkingSessionService.getActiveSession(licensePlate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    */

    /*
     * Lịch sử gửi xe theo biển số — Mọi authenticated user (Driver/Staff) có quyền truy cập.
     * (Tạm thời đóng phục vụ Milestone 1)
    @GetMapping("/driver/sessions/history")
    @PreAuthorize("hasAnyRole('DRIVER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<SessionResponse>>> getSessionHistory(
            @RequestParam("plate") String licensePlate) {
        java.util.List<SessionResponse> history = parkingSessionService.getSessionHistory(licensePlate);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
    */
}
