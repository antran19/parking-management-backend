package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.ReservationRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.ReservationResponse;
import com.smartparking.backend.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/driver/reservations")
@PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')") // Phân quyền: Chỉ cho tài xế hoặc ADMIN thực hiện đặt chỗ
public class DriverReservationController {

    private final ReservationService reservationService;

    public DriverReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Đăng ký đặt chỗ trước theo Zone
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @Valid @RequestBody ReservationRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        ReservationResponse response = reservationService.createReservation(request, email);
        return ResponseEntity.ok(ApiResponse.success("Đặt chỗ thành công", response));
    }

    /**
     * Xem danh sách đặt chỗ của Driver đang đăng nhập
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getMyReservations() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<ReservationResponse> response = reservationService.getMyReservations(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đặt chỗ thành công", response));
    }

    /**
     * Hủy đặt chỗ
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(@PathVariable UUID id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        reservationService.cancelReservation(id, email);
        return ResponseEntity.ok(ApiResponse.success("Hủy đặt chỗ thành công", null));
    }
}
