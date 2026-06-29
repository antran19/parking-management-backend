package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.*;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.dto.response.BlacklistPlateResponse;
import com.smartparking.backend.service.BlacklistService;
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
    public ResponseEntity<ApiResponse<RevenueResponse>> getRevenue(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(managerService.getRevenueBetween(type, from, to)));
    }

    @GetMapping("/dashboard/visits")
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
    public ResponseEntity<ApiResponse<BuildingOccupancyResponse>> getOccupanciesByBuilding(
            @PathVariable("id") UUID buildingId) {
        return ResponseEntity.ok(ApiResponse.success(managerService.getBuildingOccupancy(buildingId)));
    }

    @GetMapping("/dashboard/floors/{id}/occupancy")
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
    public ResponseEntity<ApiResponse<List<PaymentDetailResponse>>> getPayments() {
        return ResponseEntity.ok(ApiResponse.success(managerService.getPayments()));
    }

    @GetMapping("/dashboard/payments/{id}")
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
    public ResponseEntity<ApiResponse<SecurityIncidentSummary>> getSecuritySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.success(managerService.getSecuritySummary(from, to)));
    }

    @GetMapping("/blacklist")
    public ResponseEntity<ApiResponse<List<BlacklistPlateResponse>>> getBlacklistPlate() {
        List<BlacklistPlateResponse> list = blacklistService.getAllBlacklist();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /*
     * =============================================================================
     * ================================
     * CRUD PricingRule
     * =============================================================================
     * ================================
     */
    @PostMapping("/pricing-rules")
    public ResponseEntity<ApiResponse<PricingRule>> createPricingRule(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạo bảng giá", managerService.createPricingRule(body)));
    }

    @PutMapping("/pricing-rules/{id}")
    public ResponseEntity<ApiResponse<PricingRule>> updatePricingRule(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity
                .ok(ApiResponse.success("Đã cập nhật bảng giá", managerService.updatePricingRule(id, body)));
    }

    @DeleteMapping("/pricing-rules/{id}")
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
    @PostMapping("/gate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createGate(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạo cổng", managerService.createGate(body)));
    }

    @PutMapping("/gate/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateGate(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật cổng", managerService.updateGate(id, body)));
    }

    @DeleteMapping("/gate/{id}")
    public ResponseEntity<ApiResponse<String>> deleteGate(@PathVariable UUID id) {
        managerService.deleteGate(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa cổng", id.toString()));
    }

}
