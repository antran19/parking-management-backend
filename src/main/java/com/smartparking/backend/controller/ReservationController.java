package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.CreateReservationRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.ReservationResponse;
import com.smartparking.backend.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ReservationController — API đặt chỗ trước cho Driver.
 *
 * Endpoints:
 * - POST   /api/v1/reservations        → tạo đặt chỗ
 * - GET    /api/v1/reservations/my     → xem lịch sử đặt chỗ của tôi
 * - DELETE /api/v1/reservations/{id}   → hủy đặt chỗ đang pending
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Driver tạo đặt chỗ trước.
     *
     * Yêu cầu:
     * - Đã đăng nhập.
     * - Slot phải AVAILABLE.
     * - Slot phải đúng vehicleType.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            Authentication authentication
    ) {
        ReservationResponse response = reservationService.createReservation(request, authentication);

        return ResponseEntity.ok(ApiResponse.success(
                "Đặt chỗ thành công",
                response
        ));
    }

    /**
     * Driver xem các reservation của chính mình.
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getMyReservations(
            Authentication authentication
    ) {
        List<ReservationResponse> response = reservationService.getMyReservations(authentication);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Driver hủy reservation của chính mình.
     */
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelReservation(
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        ReservationResponse response = reservationService.cancelReservation(reservationId, authentication);

        return ResponseEntity.ok(ApiResponse.success(
                "Hủy đặt chỗ thành công",
                response
        ));
    }
}