package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.ReservationRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.ReservationResponse;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.UserRepository;
import com.smartparking.backend.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
/**
 * ReservationController — API đặt giữ chỗ cho Driver (Quảng phụ trách).
 *
 * Đã implement theo đúng scope Driver:
 * - POST   /api/v1/driver/reservations      → Tạo reservation
 * - GET    /api/v1/driver/reservations      → Xem danh sách reservation của mình
 * - DELETE /api/v1/driver/reservations/{id} → Hủy reservation
 *
 * NOTE:
 * Controller chỉ nhận request, lấy user hiện tại từ JWT rồi gọi ReservationService.
 */
@RestController
@RequestMapping("/api/v1/driver/reservations")
@PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
public class ReservationController {

    private final ReservationService reservationService;
    private final UserRepository userRepository;

    public ReservationController(
            ReservationService reservationService,
            UserRepository userRepository
    ) {
        this.reservationService = reservationService;
        this.userRepository = userRepository;
    }

    /**
     * Tạo đặt giữ chỗ cho driver.
     *
     * Method: POST
     * Endpoint: /api/v1/driver/reservations
     */
    @PostMapping
    public ApiResponse<ReservationResponse> createReservation(
            Authentication authentication,
            @Valid @RequestBody ReservationRequest request
    ) {
        User currentUser = getCurrentUser(authentication);
        ReservationResponse response = reservationService.createReservation(currentUser, request);

        return ApiResponse.success("Tạo đặt chỗ thành công", response);
    }

    /**
     * Lấy danh sách đặt chỗ của driver đang đăng nhập.
     *
     * Method: GET
     * Endpoint: /api/v1/driver/reservations
     */
    @GetMapping
    public ApiResponse<List<ReservationResponse>> getMyReservations(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        List<ReservationResponse> responses = reservationService.getUserReservations(currentUser);

        return ApiResponse.success("Lấy danh sách đặt chỗ thành công", responses);
    }

    /**
     * Hủy đặt chỗ của driver đang đăng nhập.
     *
     * Method: DELETE
     * Endpoint: /api/v1/driver/reservations/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<ReservationResponse> cancelReservation(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        User currentUser = getCurrentUser(authentication);
        ReservationResponse response = reservationService.cancelReservation(currentUser, id);

        return ApiResponse.success("Hủy đặt chỗ thành công", response);
    }

    /**
     * Lấy user hiện tại từ JWT.
     */
    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException("Bạn cần đăng nhập để sử dụng chức năng này");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản driver"));
    }
}