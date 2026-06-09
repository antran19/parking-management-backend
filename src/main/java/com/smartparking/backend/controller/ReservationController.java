package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.ReservationRequest;
import com.smartparking.backend.dto.response.*;
import com.smartparking.backend.service.ReservationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
}
