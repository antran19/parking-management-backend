package com.smartparking.backend.service;

import com.smartparking.backend.entity.*;
import com.smartparking.backend.repository.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * PaymentConfirmationService — Xác nhận thanh toán chuyển khoản (Toàn phụ trách)
 *
 * TODO (Toàn): Implement:
 * - confirmPayment(sessionCode, licensePlate)
 *   + Tìm session ACTIVE → Tính phí → Tạo Payment COMPLETED
 *   + Đóng session → Giải phóng zone → WebSocket broadcast /topic/payments/confirmed
 */
@Service
public class PaymentConfirmationService {

    // TODO: Inject repositories + SimpMessagingTemplate
    // TODO: Implement confirmPayment()
}
