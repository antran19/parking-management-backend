package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.*;
import com.smartparking.backend.entity.Gate;
import com.smartparking.backend.entity.PricingRule;
import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.service.ManagerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/*
 * TODO (Toàn):
- Xem dashboard tổng quan vận hành
- Xem doanh thu theo ngày/tháng/khoảng thời gian
- Xem công suất từng zone/floor/building
- Xem số lượt gửi xe
- Xem thống kê thanh toán
- Xem tổng hợp sự cố security
*
*
Method	            Endpoint	                                    Mục đích
GET     	/api/v1/manager/dashboard	                    Tổng quan vận hành
GET	        /api/v1/manager/dashboard/revenue	                Báo cáo doanh thu theo thời gian
GET	        /api/v1/manager/dashboard/occupancy	            Báo cáo công suất zone/floor
GET	        /api/v1/manager/dashboard/sessions	            Báo cáo lượt gửi xe
GET	        /api/v1/manager/dashboard/payments	            Tổng hợp thanh toán
GET	        /api/v1/manager/security/incidents-summary	    Tổng hợp sự cố an ninh
*
*
*/
@RestController
@Tag(name = "Manager", description = "Manager APIs")
@RequestMapping("/api/v1/manager")
@PreAuthorize("hasAnyRole('MANAGER')")
@RequiredArgsConstructor
public class ManagerController {
    private final ManagerService managerService;
    /*
    ===========================================================================================================
                                            DOANH THU
    ===========================================================================================================
    */
    // Xem TỔNG doanh thu theo khoảng thời gian
    @GetMapping("/dashboard/revenue")
    public ResponseEntity<ApiResponse<RevenueResponse>> getRevenue(

            @RequestParam(required = false) String type,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        managerService.getRevenueBetween(type, from, to)
                )
        );
    }
    /*
   =============================================================================================================
                                                     THANH TOÁN
   =============================================================================================================
   */
    // Xem chi tiết một phiên giao dịch
    @GetMapping("/dashboard/payments/{id}")
    public ResponseEntity<ApiResponse<PaymentDetailResponse>> getPaymentDetail(@PathVariable UUID id) {
        PaymentDetailResponse d = managerService.getPaymentDetail(id);
        if (d == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Payment not found"));
        return ResponseEntity.ok(ApiResponse.success(d));
    }
    @GetMapping("/dashboard/payments")
    public ResponseEntity<ApiResponse<List<PaymentDetailResponse>>> getPayments() {
        List<PaymentDetailResponse> list = managerService.getPayments();
        if (list == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Payment not found"));
        return ResponseEntity.ok(ApiResponse.success(list));
    }



    /*
    ==============================================================================================================
                                                      CÔNG SUẤT
    ==============================================================================================================
    */
    //lấy công suất theo id tòa nhà(Cong suất mặc định), công suất hiện tại
    @GetMapping("/dashboard/buildings/{id}/occupancy")
    public ResponseEntity<ApiResponse<BuildingOccupancyResponse>> getOccupanciesByBuilding(
            @PathVariable("id") UUID buildingId) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        managerService.getBuildingOccupancy(buildingId)
                )
        );
    }

    @GetMapping("/dashboard/floors/{id}/occupancy")
    public ResponseEntity<ApiResponse<FloorOccupancyResponse>> getOccupanciesFloor(
            @PathVariable("id") UUID floorId) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        managerService.getFloorOccupancy(floorId)
                )
        );
    }

    /*
    ========================================================================================================
                                             LƯỢT GỬI XE
    ========================================================================================================
    */
    //Lấy số liệu card tổng quan
    @GetMapping("/dashboard/visits")
    public ResponseEntity<ApiResponse<RevenueResponse>> getVisits(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        managerService.getVisits(type, from, to)
                )
        );
    }
    /*
    =============================================================================================================
                                                     SỰ CỐ AN NINH
    =============================================================================================================
    */
    // Tổng hợp sự cố: tổng, chưa giải quyết, phân loại theo loại
    @GetMapping("/security/summary")
    public ResponseEntity<ApiResponse<SecurityIncidentSummary>> getSecuritySummary(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime to) {
        SecurityIncidentSummary s = managerService.getSecuritySummary(from, to);
        return ResponseEntity.ok(ApiResponse.success(s));
    }
    /*
    =============================================================================================================
                                                     CRUD ZONE
    =============================================================================================================
    */
    @PostMapping("/zones")
    public ResponseEntity<ApiResponse<Zone>> createZone(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạo zone", managerService.createZone(body)));
    }

    @PutMapping("/zones/{id}")
    public ResponseEntity<ApiResponse<Zone>> updateZone(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật zone", managerService.updateZone(id, body)));
    }

    @DeleteMapping("/zones/{id}")
    public ResponseEntity<ApiResponse<String>> deleteZone(@PathVariable UUID id) {
        managerService.deleteZone(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa zone", id.toString()));
    }
    /*
    =============================================================================================================
                                                     CRUD PricingRule
    =============================================================================================================
    */
    @PostMapping("/pricing-rules")
    public ResponseEntity<ApiResponse<PricingRule>> createPricingRule(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạo bảng giá", managerService.createPricingRule(body)));
    }

    @PutMapping("/pricing-rules/{id}")
    public ResponseEntity<ApiResponse<PricingRule>> updatePricingRule(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật bảng giá", managerService.updatePricingRule(id, body)));
    }

    @DeleteMapping("/pricing-rules/{id}")
    public ResponseEntity<ApiResponse<String>> deletePricingRule(@PathVariable UUID id) {
        managerService.deletePricingRule(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa bảng giá", id.toString()));
    }
    /*
    =============================================================================================================
                                                     CRUD GATE
    =============================================================================================================
    */
    @PostMapping("/gate")
    public ResponseEntity<ApiResponse<Gate>> createGate(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạo cổng", managerService.createGate(body)));
    }

    @PutMapping("/gate/{id}")
    public ResponseEntity<ApiResponse<Gate>> updateGate(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật cổng", managerService.updateGate(id, body)));
    }

    @DeleteMapping("/gate/{id}")
    public ResponseEntity<ApiResponse<String>> deleteGate(@PathVariable UUID id) {
        managerService.deleteGate(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa cổng", id.toString()));
    }
}

