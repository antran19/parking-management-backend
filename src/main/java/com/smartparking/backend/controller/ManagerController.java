package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.*;
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
GET	        /api/v1/manager/reports/revenue	                Báo cáo doanh thu theo thời gian
GET	        /api/v1/manager/reports/occupancy	            Báo cáo công suất zone/floor
GET	        /api/v1/manager/reports/sessions	            Báo cáo lượt gửi xe
GET	        /api/v1/manager/reports/payments	            Tổng hợp thanh toán
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
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
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
    /*
    ==============================================================================================================
                                                      CÔNG SUẤT
    ==============================================================================================================
    */

    //lấy công suất theo id tòa nhà(Cong suất mặc định), công suất hiện tại
    @GetMapping("/dashboard/buildings/{id}/occupancy")
    public ResponseEntity<ApiResponse<OccupancyEntry>> getOccupanciesByBuilding(
            @PathVariable("id") UUID buildingId) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        managerService.getBuildingOccupancy(buildingId)
                )
        );
    }

    @GetMapping("/dashboard/floors/{id}/occupancy")
    public ResponseEntity<ApiResponse<OccupancyEntry>> getOccupanciesFloor(
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
    //?groupBy=day
    //
    //?groupBy=month
    //
    //?groupBy=year
    //
    //?from=2026-06-01&to=2026-06-15

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
}
