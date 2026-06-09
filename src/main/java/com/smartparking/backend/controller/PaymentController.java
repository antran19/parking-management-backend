package com.smartparking.backend.controller;

import com.smartparking.backend.service.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

/**
 * PaymentController — API thanh toán VNPAY + chuyển khoản (Toàn phụ trách)
 *
 * TODO (Toàn): Implement các endpoint sau:
 * - POST /driver/payments/confirm            → Xác nhận thanh toán chuyển khoản
 * - POST /driver/payments/checkout-vnpay      → Tạo URL thanh toán checkout qua VNPAY
 * - GET  /driver/payments/vnpay-return        → VNPAY redirect callback
 * - GET  /driver/payments/vnpay-ipn           → VNPAY server-to-server callback
 */
@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    // TODO: Inject PaymentConfirmationService, ParkingSessionService, VnPayService

    // TODO: Implement endpoints
}
