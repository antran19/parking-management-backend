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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Security", description = "APIs for Security role: exception logging, SOS emergency, and vehicle blacklist management")
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
    @Operation(summary = "Báo cáo sự cố an ninh", description = "Ghi nhận sự cố mới: xe không vé, tranh chấp, v.v.")
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
    @Operation(summary = "Xem danh sách sự cố", description = "Toàn bộ sự cố an ninh, mới nhất trước")
    @GetMapping("/exceptions")
    @PreAuthorize("hasAnyRole('SECURITY', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ExceptionLogResponse>>> getAllExceptions() {
        List<ExceptionLogResponse> list = securityExceptionService.getAllExceptions();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * Cập nhật sự cố an ninh đã ghi nhận (VD: sửa loại, mô tả, ảnh đính kèm)
     * PUT /api/v1/security/exceptions/{id}
     */
    @Operation(summary = "Cập nhật sự cố", description = "Sửa loại, mô tả, ảnh đính kèm của sự cố")
    @PutMapping("/exceptions/{id}")
    @PreAuthorize("hasAnyRole('SECURITY', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExceptionLogResponse>> updateException(
            @PathVariable UUID id,
            @RequestBody SecurityExceptionRequest request) {
        ExceptionLogResponse exceptionLog = securityExceptionService.updateException(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật sự cố an ninh", exceptionLog));
    }

    /**
     * Đánh dấu sự cố đã được giải quyết
     * PUT /api/v1/security/exceptions/{id}/resolve
     */
    @Operation(summary = "Giải quyết sự cố", description = "Đánh dấu sự cố đã được xử lý bởi nhân viên")
    @PutMapping("/exceptions/{id}/resolve")
    @PreAuthorize("hasAnyRole('SECURITY', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExceptionLogResponse>> resolveException(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        // Lấy handledByUserId từ body, đây thường là user id của nhân viên đang xử lý
        UUID handledByUserId = null;
        if (body.get("handledByUserId") != null) {
            handledByUserId = UUID.fromString(String.valueOf(body.get("handledByUserId")));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Thiếu thông tin người xử lý (handledByUserId)"));
        }

        String resolution = null;
        if (body.get("resolution") != null) {
            resolution = String.valueOf(body.get("resolution"));
        }

        List<String> resolutionImageUrls = null;
        if (body.get("resolutionImageUrls") != null) {
            resolutionImageUrls = (List<String>) body.get("resolutionImageUrls");
        }

        ExceptionLogResponse exceptionLog = securityExceptionService.resolveException(id, handledByUserId, resolution, resolutionImageUrls);
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu sự cố là đã giải quyết", exceptionLog));
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
    @Operation(summary = "Kích hoạt SOS khẩn cấp", description = "Mở toàn bộ barrier, phát còi báo")
    @PostMapping("/emergency/activate")
    public ResponseEntity<ApiResponse<EmergencyStatusResponse>> activateEmergency(
            @Valid @RequestBody EmergencyActivateRequest request) {
        EmergencyStatusResponse response = emergencyService.activateEmergency(request);
        emergencyService.broadcastEmergencyStatus(response, "ACTIVATED");
        return ResponseEntity.ok(ApiResponse.success("SOS đã kích hoạt. Toàn bộ barrier đã mở.", response));
    }

    /**
     * Hủy SOS đang hoạt động
     * POST /api/v1/security/emergency/deactivate
     * 
     * ĐÃ TEST http://localhost:8080/api/v1/security/emergency/deactivate
     */
    @Operation(summary = "Hủy SOS", description = "Hệ thống trở lại bình thường")
    @PostMapping("/emergency/deactivate")
    public ResponseEntity<ApiResponse<EmergencyStatusResponse>> deactivateEmergency(
            @Valid @RequestBody EmergencyDeactivateRequest request) {
        EmergencyStatusResponse response = emergencyService.deactivateEmergency(request);
        emergencyService.broadcastEmergencyStatus(response, "DEACTIVATED");
        return ResponseEntity.ok(ApiResponse.success("SOS đã được hủy. Hệ thống trở lại bình thường.", response));
    }

    /**
     * Lấy trạng thái SOS hiện tại
     * GET /api/v1/security/emergency/status
     * Cho phép tất cả role xem được (SECURITY, STAFF, DRIVER, MANAGER, ADMIN)
     * 
     * ĐÃ TEST http://localhost:8080/api/v1/security/emergency/status
     */
    @Operation(summary = "Trạng thái SOS hiện tại", description = "Tất cả role đều xem được")
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
    @Operation(summary = "Lịch sử SOS", description = "Toàn bộ lịch sử kích hoạt/hủy SOS")
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
    @Operation(summary = "Lấy cấu hình SOS", description = "Kiểm tra SOS đang bật hay tắt")
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
    @Operation(summary = "Cập nhật cấu hình SOS", description = "Bật hoặc tắt tính năng SOS")
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
    @Operation(summary = "Xem danh sách đen", description = "Toàn bộ biển số bị chặn")
    @GetMapping("/blacklist")
    public ResponseEntity<ApiResponse<List<BlacklistPlateResponse>>> getBlacklist() {
        List<BlacklistPlateResponse> list = blacklistService.getAllBlacklist();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * Thêm một biển số xe vào danh sách đen
     * POST /api/v1/security/blacklist
     */
    @Operation(summary = "Thêm biển số vào danh sách đen", description = "Blacklist biển số xe vi phạm")
    @PostMapping("/blacklist")
    public ResponseEntity<ApiResponse<BlacklistPlateResponse>> addBlacklistPlate(
            @Valid @RequestBody BlacklistPlateRequest request) {
        BlacklistPlateResponse response = blacklistService.addToBlacklist(request);
        return ResponseEntity.ok(ApiResponse.success("Đã thêm biển số vào danh sách đen", response));
    }

    /**
     * Cập nhật thông tin một bản ghi blacklist (biển số, lý do, mô tả)
     * PUT /api/v1/security/blacklist/{id}
     */
    @Operation(summary = "Cập nhật blacklist", description = "Sửa biển số, lý do, mô tả")
    @PutMapping("/blacklist/{id}")
    public ResponseEntity<ApiResponse<BlacklistPlateResponse>> updateBlacklistPlate(
            @PathVariable UUID id,
            @RequestBody BlacklistPlateRequest request) {
        BlacklistPlateResponse response = blacklistService.updateBlacklist(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật thông tin blacklist", response));
    }

    /**
     * Gỡ bỏ một biển số xe khỏi danh sách đen
     * DELETE /api/v1/security/blacklist/{id}
     */
    @Operation(summary = "Gỡ biển số khỏi blacklist", description = "Chỉ Admin/Manager mới được gỡ")
    @DeleteMapping("/blacklist/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BlacklistPlateResponse>> removeBlacklistPlate(
            @PathVariable UUID id,
            @Valid @RequestBody BlacklistRemoveRequest request) {
        BlacklistPlateResponse response = blacklistService.removeFromBlacklist(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đã gỡ biển số khỏi danh sách đen", response));
    }

}