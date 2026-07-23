package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.BlacklistPlateResponse;
import com.smartparking.backend.dto.response.ExceptionLogResponse;
import com.smartparking.backend.dto.response.PaymentDetailResponse;
import com.smartparking.backend.service.BlacklistService;
import com.smartparking.backend.service.ExcelExportService;
import com.smartparking.backend.service.ManagerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@Tag(name = "Export", description = "Export Data APIs")
@RequestMapping("/api/v1/manager/export")
@RequiredArgsConstructor
public class ExportController {

    private final ManagerService managerService;
    private final BlacklistService blacklistService;
    private final ExcelExportService excelExportService;
    private final com.smartparking.backend.service.ParkingSessionService parkingSessionService;

    @GetMapping("/{type}")
    @Operation(summary = "Xuất dữ liệu ra Excel")
    public ResponseEntity<byte[]> exportData(
            @PathVariable String type,
            @RequestParam(required = false) String filterType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) throws IOException {
        byte[] excelData;
        String filename;

        switch (type.toLowerCase()) {
            case "payments":
                List<PaymentDetailResponse> payments = managerService.getPayments();
                String[] pHeaders = {"Mã Giao Dịch", "Biển Số", "Loại Xe", "Số Tiền", "Phương Thức", "Trạng Thái", "Giờ Vào", "Giờ Ra"};
                String[] pFields = {"transactionId", "licensePlate", "vehicleTypeName", "amount", "paymentMethod", "status", "entryTime", "exitTime"};
                excelData = excelExportService.exportToExcel(payments, pHeaders, pFields);
                filename = "GiaoDichThanhToan.xlsx";
                break;
            case "incidents":
                List<ExceptionLogResponse> incidents = managerService.getSecurityIncidents(null, null);
                String[] iHeaders = {"Biển Số", "Loại Sự Cố", "Mô Tả", "Người Xử Lý", "Thời Gian Tạo", "Thời Gian Giải Quyết", "Phương Án Giải Quyết"};
                String[] iFields = {"licensePlate", "exceptionType", "description", "handledBy", "createdAt", "resolvedAt", "resolution"};
                excelData = excelExportService.exportToExcel(incidents, iHeaders, iFields);
                filename = "SuCoAnNinh.xlsx";
                break;
            case "blacklist":
                List<BlacklistPlateResponse> blacklist = blacklistService.getAllBlacklist();
                String[] bHeaders = {"Biển Số", "Lý Do", "Ngày Thêm", "Người Thêm", "Trạng Thái"};
                String[] bFields = {"licensePlate", "reason", "addedAt", "addedBy", "isActive"};
                excelData = excelExportService.exportToExcel(blacklist, bHeaders, bFields);
                filename = "BienSoDen.xlsx";
                break;
            case "sessions":
                List<com.smartparking.backend.dto.response.SessionResponse> sessions = parkingSessionService.getAllSessions();
                String[] sHeaders = {"Mã Phiên", "Biển Số", "Loại Xe", "Trạng Thái", "Giờ Vào", "Giờ Ra", "Tổng Phí"};
                String[] sFields = {"id", "licensePlate", "vehicleTypeName", "status", "entryTime", "exitTime", "totalFee"};
                excelData = excelExportService.exportToExcel(sessions, sHeaders, sFields);
                filename = "LuotGuiXe.xlsx";
                break;
            case "revenue":
                String revenueType = filterType != null ? filterType : "today";
                java.time.LocalDate fromDate = (from != null && !from.isEmpty()) ? java.time.LocalDate.parse(from) : null;
                java.time.LocalDate toDate = (to != null && !to.isEmpty()) ? java.time.LocalDate.parse(to) : null;
                com.smartparking.backend.dto.response.RevenueResponse revenueRes = managerService.getRevenueBetween(revenueType, fromDate, toDate);
                List<com.smartparking.backend.dto.response.ChartDataPoint> chartData = revenueRes.getChartData();
                String[] rHeaders = {"Thời Gian", "Doanh Thu (VNĐ)"};
                String[] rFields = {"label", "value"};
                excelData = excelExportService.exportToExcel(chartData, rHeaders, rFields);
                filename = "BaoCaoDoanhThu.xlsx";
                break;
            default:
                return ResponseEntity.badRequest().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }
}
