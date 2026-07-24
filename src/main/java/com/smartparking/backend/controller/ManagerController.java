package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.BlacklistRemoveRequest;
import com.smartparking.backend.dto.response.*;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.service.BlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.smartparking.backend.service.ManagerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.entity.PricingRule;

@RestController
@Tag(name = "Manager", description = "Manager APIs")
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor

public class ManagerController {

    private final ManagerService managerService;
    private final BlacklistService blacklistService;

    /*
     * =============================================================================
     * ==============================
     * DASHBOARD TỔNG QUAN
     * =============================================================================
     * ==============================
     */
    @GetMapping("/dashboard")
    @Operation(summary = "Lấy dữ liệu tổng quan dashboard cho manager")
    public ResponseEntity<ApiResponse<ManagerDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(managerService.getDashboard()));
    }

    /*
     * =============================================================================
     * ==============================
     * DOANH THU & LƯỢT GỬI XE
     * =============================================================================
     * ==============================
     */
    @GetMapping("/dashboard/revenue")
    @Operation(summary = "Lấy báo cáo doanh thu theo khoảng thời gian")
    public ResponseEntity<ApiResponse<RevenueResponse>> getRevenue(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(managerService.getRevenueBetween(type, from, to)));
    }

    @GetMapping("/dashboard/visits")
    @Operation(summary = "Lấy thống kê lượt xe gửi theo khoảng thời gian")
    public ResponseEntity<ApiResponse<RevenueResponse>> getVisits(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(managerService.getVisits(type, from, to)));
    }

    /*
     * =============================================================================
     * =================================
     * CÔNG SUẤT
     * =============================================================================
     * =================================
     */
    @GetMapping("/dashboard/buildings/{id}/occupancy")
    @Operation(summary = "Lấy công suất đỗ xe theo tòa nhà")
    public ResponseEntity<ApiResponse<BuildingOccupancyResponse>> getOccupanciesByBuilding(
            @PathVariable("id") UUID buildingId) {
        return ResponseEntity.ok(ApiResponse.success(managerService.getBuildingOccupancy(buildingId)));
    }

    @GetMapping("/dashboard/floors/{id}/occupancy")
    @Operation(summary = "Lấy công suất đỗ xe theo tầng")
    public ResponseEntity<ApiResponse<FloorOccupancyResponse>> getOccupanciesFloor(@PathVariable("id") UUID floorId) {
        return ResponseEntity.ok(ApiResponse.success(managerService.getFloorOccupancy(floorId)));
    }

    /*
     * =============================================================================
     * ================================
     * THANH TOÁN
     * =============================================================================
     * ================================
     */
    @GetMapping("/dashboard/payments")
    @Operation(summary = "Lấy danh sách lịch sử giao dịch thanh toán")
    public ResponseEntity<ApiResponse<List<PaymentDetailResponse>>> getPayments() {
        return ResponseEntity.ok(ApiResponse.success(managerService.getPayments()));
    }

    @GetMapping("/dashboard/payments/{id}")
    @Operation(summary = "Xem chi tiết giao dịch thanh toán")
    public ResponseEntity<ApiResponse<PaymentDetailResponse>> getPaymentDetail(@PathVariable UUID id) {
        PaymentDetailResponse d = managerService.getPaymentDetail(id);
        if (d == null)
            throw new ResourceNotFoundException("Payment not found");
        return ResponseEntity.ok(ApiResponse.success(d));
    }

    /*
     * =============================================================================
     * ================================
     * SỰ CỐ AN NINH
     * =============================================================================
     * ================================
     */
    @GetMapping("/security/summary")
    @Operation(summary = "Lấy thống kê tổng quan về các sự cố an ninh")
    public ResponseEntity<ApiResponse<SecurityIncidentSummary>> getSecuritySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.success(managerService.getSecuritySummary(from, to)));
    }

    @GetMapping("/security/incidents")
    @Operation(summary = "Lấy danh sách các sự cố an ninh (Exception Logs)")
    public ResponseEntity<ApiResponse<List<ExceptionLogResponse>>> getSecurityIncidents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.success(managerService.getSecurityIncidents(from, to)));
    }

    @GetMapping("/security/incidents/{id}")
    @Operation(summary = "Xem chi tiết một sự cố an ninh cụ thể")
    public ResponseEntity<ApiResponse<ExceptionLogResponse>> getSecurityIncidentDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(managerService.getSecurityIncidentDetail(id)));
    }

    @GetMapping("/blacklist")
    @Operation(summary = "Lấy danh sách đen các biển số xe bị chặn")
    public ResponseEntity<ApiResponse<List<BlacklistPlateResponse>>> getBlacklistPlate() {
        List<BlacklistPlateResponse> list = blacklistService.getAllBlacklist();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PutMapping("/blacklist/{id}/status")
    @Operation(summary = "Gỡ cấm biển số xe khỏi danh sách đen")
    public ResponseEntity<ApiResponse<BlacklistPlateResponse>> removeFromBlacklist(
            @PathVariable UUID id,
            @RequestBody BlacklistRemoveRequest request) {
        BlacklistPlateResponse updated = blacklistService.removeFromBlacklist(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    /*
     * =============================================================================
     * ================================
     * CRUD PricingRule
     * =============================================================================
     * ================================
     */
    @PostMapping("/pricing-rules")
    @Operation(summary = "Tạo mới biểu phí gửi xe")
    public ResponseEntity<ApiResponse<PricingRule>> createPricingRule(@RequestBody Map<String, Object> body) {
        try {
            PricingRule rule = managerService.createPricingRule(body);
            return ResponseEntity.ok(ApiResponse.success("Đã tạo bảng giá", rule));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/pricing-rules/{id}")
    @Operation(summary = "Cập nhật biểu phí gửi xe")
    public ResponseEntity<ApiResponse<PricingRule>> updatePricingRule(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity
                .ok(ApiResponse.success("Đã cập nhật bảng giá", managerService.updatePricingRule(id, body)));
    }

    @DeleteMapping("/pricing-rules/{id}")
    @Operation(summary = "Xóa biểu phí gửi xe")
    public ResponseEntity<ApiResponse<String>> deletePricingRule(@PathVariable UUID id) {
        managerService.deletePricingRule(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa bảng giá", id.toString()));
    }

    /*
     * =============================================================================
     * ================================
     * CRUD GATE
     * =============================================================================
     * ================================
     */
    // @PostMapping("/gate")
    // public ResponseEntity<ApiResponse<Map<String, Object>>>
    // createGate(@RequestBody Map<String, Object> body) {
    // return ResponseEntity.ok(ApiResponse.success("Đã tạo cổng",
    // managerService.createGate(body)));
    // }

    // @DeleteMapping("/gate/{id}")
    // public ResponseEntity<ApiResponse<String>> deleteGate(@PathVariable UUID id)
    // {
    // managerService.deleteGate(id);
    // return ResponseEntity.ok(ApiResponse.success("Đã xóa cổng", id.toString()));
    // }

    @PutMapping("/gate/{id}")
    @Operation(summary = "Cập nhật thông tin/trạng thái hoạt động của cổng")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateGate(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật cổng", managerService.updateGate(id, body)));
    }

}
