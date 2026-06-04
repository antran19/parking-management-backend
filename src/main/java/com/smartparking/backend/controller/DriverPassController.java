package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.ParkingPassRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.ParkingPassResponse;
import com.smartparking.backend.service.ParkingPassService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/driver/passes")
@PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')") // Phân quyền bảo vệ: Tài xế hoặc ADMIN
public class DriverPassController {

    private final ParkingPassService parkingPassService;

    public DriverPassController(ParkingPassService parkingPassService) {
        this.parkingPassService = parkingPassService;
    }

    /**
     * Mua gói đăng ký vé gửi xe (MONTHLY/QUARTERLY/YEARLY)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ParkingPassResponse>> buyPass(
            @Valid @RequestBody ParkingPassRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        ParkingPassResponse response = parkingPassService.buyPass(request, email);
        return ResponseEntity.ok(ApiResponse.success("Mua gói đăng ký vé xe thành công", response));
    }

    /**
     * Xem danh sách tất cả vé gửi xe của Driver đang đăng nhập
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ParkingPassResponse>>> getMyPasses() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<ParkingPassResponse> response = parkingPassService.getMyPasses(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách vé xe thành công", response));
    }

    /**
     * Xem danh sách vé gửi xe đang hoạt động (ACTIVE và còn trong hạn dùng) của Driver đang đăng nhập
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ParkingPassResponse>>> getMyActivePasses() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<ParkingPassResponse> response = parkingPassService.getMyActivePasses(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách vé xe đang hoạt động thành công", response));
    }
}
