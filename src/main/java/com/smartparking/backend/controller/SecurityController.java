package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.BlacklistPlateRequest;
import com.smartparking.backend.dto.request.BlacklistRemoveRequest;
import com.smartparking.backend.dto.request.EmergencyActivateRequest;
import com.smartparking.backend.dto.request.EmergencyDeactivateRequest;
import com.smartparking.backend.dto.request.SecurityExceptionRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.BlacklistPlateResponse;
import com.smartparking.backend.dto.response.EmergencyStatusResponse;
import com.smartparking.backend.dto.response.ExceptionLogResponse;
import com.smartparking.backend.entity.ExceptionLog;
import com.smartparking.backend.service.BlacklistService;
import com.smartparking.backend.service.EmergencyService;
import com.smartparking.backend.service.SecurityExceptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SecurityController — API cho bảo vệ/an ninh (Thiên phụ trách)
 *
 * Base URL: /api/v1/security
 *
 * Danh sách endpoint:
 * POST /exceptions → Báo cáo sự cố an ninh
 * GET /exceptions → Xem danh sách sự cố
 * POST /emergency/activate → Kích hoạt SOS khẩn cấp
 * POST /emergency/deactivate → Hủy SOS
 * GET /emergency/status → Trạng thái SOS hiện tại
 * GET /emergency/history → Lịch sử SOS
 * GET /emergency/settings → Lấy cấu hình SOS (sosEnabled)
 * PUT /emergency/settings → Bật/tắt SOS
 * GET /blacklist → Xem danh sách đen biển số
 * POST /blacklist → Thêm biển số vào blacklist
 * DELETE /blacklist/{id} → Gỡ biển số khỏi blacklist
 */
@RestController
@RequestMapping("/api/v1/security")
@PreAuthorize("hasAnyRole('SECURITY', 'MANAGER', 'ADMIN')")
public class SecurityController {

    private final SecurityExceptionService securityExceptionService;
    private final EmergencyService emergencyService;
    private final BlacklistService blacklistService;
    private final SimpMessagingTemplate messagingTemplate;

    // Constructor injection — theo quy tắc bắt buộc của project
    public SecurityController(SecurityExceptionService securityExceptionService,
            EmergencyService emergencyService,
            BlacklistService blacklistService,
            SimpMessagingTemplate messagingTemplate) {
        this.securityExceptionService = securityExceptionService;
        this.emergencyService = emergencyService;
        this.blacklistService = blacklistService;
        this.messagingTemplate = messagingTemplate;
    }

    // =====================================================================
    // SỰ CỐ AN NINH (EXCEPTION LOG)
    // =====================================================================

    /**
     * Báo cáo một sự cố an ninh mới (xe không vé, tranh chấp, v.v.)
     * POST /api/v1/security/exceptions
     */
    @PostMapping("/exceptions")
    @PreAuthorize("hasAnyRole('SECURITY', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExceptionLogResponse>> logException(
            @Valid @RequestBody SecurityExceptionRequest request) {
        ExceptionLogResponse exceptionLog = securityExceptionService.logException(request);
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận sự cố an ninh", exceptionLog));
    }

    /**
     * Lấy toàn bộ danh sách sự cố an ninh, mới nhất trước
     * GET /api/v1/security/exceptions
     * 
     * đã test postman http://localhost:8080/api/v1/security/exceptions
     * 
     */
    @GetMapping("/exceptions")
    @PreAuthorize("hasAnyRole('SECURITY', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ExceptionLogResponse>>> getAllExceptions() {
        List<ExceptionLogResponse> list = securityExceptionService.getAllExceptions();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    // =====================================================================
    // SOS KHẨN CẤP (EMERGENCY)
    // =====================================================================

    /**
     * Kích hoạt SOS khẩn cấp — mở toàn bộ barrier
     * POST /api/v1/security/emergency/activate
     * 
     * ĐÃ TEST http://localhost:8080/api/v1/security/emergency/activate
     * {
     * }
     */
    @PostMapping("/emergency/activate")
    public ResponseEntity<ApiResponse<EmergencyStatusResponse>> activateEmergency(
            @Valid @RequestBody EmergencyActivateRequest request) {
        EmergencyStatusResponse response = emergencyService.activateEmergency(request);
        return ResponseEntity.ok(ApiResponse.success("SOS đã kích hoạt. Toàn bộ barrier đã mở.", response));
    }

    /**
     * Hủy SOS đang hoạt động
     * POST /api/v1/security/emergency/deactivate
     * 
     * ĐÃ TEST http://localhost:8080/api/v1/security/emergency/deactivate
     */
    @PostMapping("/emergency/deactivate")
    public ResponseEntity<ApiResponse<EmergencyStatusResponse>> deactivateEmergency(
            @Valid @RequestBody EmergencyDeactivateRequest request) {
        EmergencyStatusResponse response = emergencyService.deactivateEmergency(request);
        return ResponseEntity.ok(ApiResponse.success("SOS đã được hủy. Hệ thống trở lại bình thường.", response));
    }

    /**
     * Lấy trạng thái SOS hiện tại
     * GET /api/v1/security/emergency/status
     * Cho phép tất cả role xem được (SECURITY, STAFF, DRIVER, MANAGER, ADMIN)
     * 
     * ĐÃ TEST http://localhost:8080/api/v1/security/emergency/status
     */
    @GetMapping("/emergency/status")
    @PreAuthorize("hasAnyRole('SECURITY', 'STAFF', 'DRIVER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<EmergencyStatusResponse>> getEmergencyStatus() {
        EmergencyStatusResponse response = emergencyService.getCurrentStatus();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Lấy toàn bộ lịch sử SOS
     * GET /api/v1/security/emergency/history
     */
    @GetMapping("/emergency/history")
    public ResponseEntity<ApiResponse<List<EmergencyStatusResponse>>> getEmergencyHistory() {
        List<EmergencyStatusResponse> history = emergencyService.getHistory();
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    /**
     * [HÀM THÊM MỚI SO VỚI DỰ ÁN CŨ — ENDPOINT LẤY CẤU HÌNH SOS]
     * Lấy cấu hình SOS hiện tại (sosEnabled đang bật hay tắt)
     * GET /api/v1/security/emergency/settings
     */
    @GetMapping("/emergency/settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEmergencySettings() {
        boolean sosEnabled = emergencyService.isSosEnabled();
        return ResponseEntity.ok(ApiResponse.success(Map.of("sosEnabled", sosEnabled)));
    }

    /**
     * [HÀM THÊM MỚI SO VỚI DỰ ÁN CŨ — ENDPOINT CẬP NHẬT CẤU HÌNH SOS]
     * Bật hoặc tắt tính năng SOS
     * PUT /api/v1/security/emergency/settings
     * Body: { "sosEnabled": true/false }
     */
    @PutMapping("/emergency/settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateEmergencySettings(
            @RequestBody Map<String, Object> body) {
        // Lấy giá trị sosEnabled từ body, mặc định là true nếu không truyền
        boolean sosEnabled = Boolean.parseBoolean(String.valueOf(body.getOrDefault("sosEnabled", true)));
        boolean saved = emergencyService.updateSosEnabled(sosEnabled);

        String message;
        if (saved) {
            message = "Đã bật chức năng SOS";
        } else {
            message = "Đã tắt chức năng SOS";
        }

        return ResponseEntity.ok(ApiResponse.success(message, Map.of("sosEnabled", saved)));
    }

    // =====================================================================
    // BLACKLIST BIỂN SỐ XE
    // =====================================================================

    /**
     * Lấy toàn bộ danh sách đen biển số xe
     * GET /api/v1/security/blacklist
     */
    @GetMapping("/blacklist")
    public ResponseEntity<ApiResponse<List<BlacklistPlateResponse>>> getBlacklist() {
        List<BlacklistPlateResponse> list = blacklistService.getAllBlacklist();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * Thêm một biển số xe vào danh sách đen
     * POST /api/v1/security/blacklist
     */
    @PostMapping("/blacklist")
    public ResponseEntity<ApiResponse<BlacklistPlateResponse>> addBlacklistPlate(
            @Valid @RequestBody BlacklistPlateRequest request) {
        BlacklistPlateResponse response = blacklistService.addToBlacklist(request);
        return ResponseEntity.ok(ApiResponse.success("Đã thêm biển số vào danh sách đen", response));
    }

}