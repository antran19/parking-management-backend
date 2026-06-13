package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.ReservationRequest;
import com.smartparking.backend.dto.response.ReservationResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ReservationService — Đặt giữ chỗ zone (Quảng phụ trách)
 *
 * TODO (Quảng): Implement các method sau:
 * 1. createReservation(user, request)  → Validate zone còn chỗ → Tăng reservedCount → Tạo reservation
 * 2. getUserReservations(user)         → Lấy danh sách reservation theo user
 * 3. cancelReservation(user, id)       → Hủy reservation → Giảm reservedCount
 *
 * Lưu ý:
 * - Kiểm tra biển số đã có reservation PENDING/CONFIRMED chưa
 * - Kiểm tra loại xe có khớp zone không
 * - Kiểm tra zone còn chỗ: currentCount + reservedCount < capacity
 * - Sinh mã reservation: "RS" + yyyyMMdd + "-" + random 4 ký tự
 */
@Service
public class ReservationService {

    // TODO: Inject repositories
    // TODO: Constructor injection
    // TODO: Implement methods
}
