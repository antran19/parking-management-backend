package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.ReservationRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.ReservationResponse;
import com.smartparking.backend.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ReservationController — API đặt giữ chỗ (Quảng phụ trách)
 *
 * TODO (Quảng): Implement các endpoint sau:
 * - POST   /driver/reservations      → Tạo reservation (đặt giữ chỗ zone)
 * - GET    /driver/reservations      → Xem danh sách reservation của mình
 * - DELETE /driver/reservations/{id} → Hủy reservation
 */

@RestController
@RequestMapping("/api/v1/driver/reservations")
@PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
public class ReservationController {

    
    // TODO: Inject ReservationService

    // TODO: Implement endpoints

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            Authentication authentication,
            @Valid @RequestBody ReservationRequest request
    ) {
        ReservationResponse response = reservationService.createReservation(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Đặt chỗ thành công", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getMyReservations(
            Authentication authentication
    ) {
        List<ReservationResponse> responses = reservationService.getUserReservations(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đặt chỗ thành công", responses));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> cancelReservation(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        List<ReservationResponse> responses = reservationService.cancelReservation(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Hủy đặt chỗ thành công", responses));
    }
}