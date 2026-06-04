package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.PaymentRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.PaymentResponse;
import com.smartparking.backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@PreAuthorize("hasAnyRole('DRIVER', 'STAFF', 'MANAGER', 'ADMIN')") // Phân quyền chung cho tài xế và nhân viên
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Khởi tạo hóa đơn thanh toán (PENDING) cho phiên gửi xe hoặc đăng ký vé tháng.
     * Trả về thông tin hóa đơn và kèm đường dẫn QR Code VietQR chuyển khoản (nếu chọn chuyển khoản).
     */
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(request);
        return ResponseEntity.ok(ApiResponse.success("Khởi tạo hóa đơn thanh toán thành công", response));
    }

    /**
     * Xác nhận thanh toán thành công (COMPLETED) cho hóa đơn.
     * Giả lập việc nhận tiền mặt từ khách hoặc webhook báo có tiền từ phía Ngân hàng.
     */
    @PostMapping("/{paymentId}/confirm")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmPayment(
            @PathVariable UUID paymentId) {
        PaymentResponse response = paymentService.confirmPayment(paymentId);
        return ResponseEntity.ok(ApiResponse.success("Xác nhận thanh toán thành công", response));
    }

    /**
     * Tra cứu thông tin chi tiết hóa đơn thanh toán.
     */
    @GetMapping("/{paymentId}/details")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentDetails(
            @PathVariable UUID paymentId) {
        PaymentResponse response = paymentService.getPaymentDetails(paymentId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin hóa đơn thành công", response));
    }
}
