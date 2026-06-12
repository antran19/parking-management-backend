package com.smartparking.backend.service;

import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * VnPayService — Tích hợp VNPAY (Toàn phụ trách)
 *
 * TODO (Toàn): Implement:
 * - createPaymentUrl(amount, orderInfo, returnUrl, ipAddress) → Tạo URL redirect sang VNPAY
 * - validateCallback(params) → Xác minh chữ ký VNPAY (vnp_SecureHash)
 *
 * Cấu hình VNPAY đọc từ application.yml:
 *   smartparking.vnpay.vnp_TmnCode
 *   smartparking.vnpay.vnp_HashSecret
 *   smartparking.vnpay.payment-url
 */
@Service
public class VnPayService {

    // TODO: @Value inject cấu hình VNPAY từ application.yml
    // TODO: Implement createPaymentUrl()
    // TODO: Implement validateCallback()
}
