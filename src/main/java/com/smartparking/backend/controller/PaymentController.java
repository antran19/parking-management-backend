package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.SessionResponse;
import com.smartparking.backend.entity.ParkingPass;
import com.smartparking.backend.entity.Payment;
import com.smartparking.backend.repository.ParkingPassRepository;
import com.smartparking.backend.repository.PaymentRepository;
import com.smartparking.backend.service.ParkingSessionService;
import com.smartparking.backend.service.PaymentConfirmationService;
import com.smartparking.backend.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PaymentController — API xác nhận thanh toán chuyển khoản + VNPay.
 *
 * Luồng real-time:
 *   1. Driver chuyển khoản xong → gọi POST /api/v1/driver/payments/confirm
 *   2. Backend xác nhận → tự động check-out → broadcast WebSocket tới Staff
 *   3. Staff nhận được thông báo real-time → hiển thị hóa đơn thành công
 */
@RestController
@RequestMapping("/api/v1/driver/payments")
@Tag(name = "Payment", description = "APIs for payment confirmation and VNPay integration")
public class PaymentController {

    private final PaymentConfirmationService paymentConfirmationService;
    private final ParkingPassRepository parkingPassRepository;
    private final PaymentRepository paymentRepository;
    private final VnPayService vnPayService;
    private final ParkingSessionService parkingSessionService;

    public PaymentController(PaymentConfirmationService paymentConfirmationService,
                             ParkingPassRepository parkingPassRepository,
                             PaymentRepository paymentRepository,
                             VnPayService vnPayService,
                             ParkingSessionService parkingSessionService) {
        this.paymentConfirmationService = paymentConfirmationService;
        this.parkingPassRepository = parkingPassRepository;
        this.paymentRepository = paymentRepository;
        this.vnPayService = vnPayService;
        this.parkingSessionService = parkingSessionService;
    }

    /**
     * Driver xác nhận đã chuyển khoản thành công.
     * Body: { "sessionCode": "PS20260526-A3F", "licensePlate": "30A-12345" }
     */
    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('DRIVER', 'STAFF', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Confirm bank transfer payment for a session")
    public ResponseEntity<ApiResponse<SessionResponse>> confirmPayment(
            @RequestBody Map<String, String> body) {
        String sessionCode = body.get("sessionCode");
        String licensePlate = body.get("licensePlate");

        SessionResponse response = paymentConfirmationService.confirmPayment(sessionCode, licensePlate);
        return ResponseEntity.ok(ApiResponse.success("Thanh toán đã được xác nhận thành công!", response));
    }

    @GetMapping("/vnpay-return")
    @Transactional
    @Operation(summary = "VNPay redirect callback (user returns from VNPay)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> vnpayReturn(HttpServletRequest request) {
        Map<String, String> params = vnPayService.extractParams(request);
        Map<String, Object> response = processPassPaymentCallback(params, true);
        return ResponseEntity.ok(ApiResponse.success("Đã nhận kết quả thanh toán VNPay", response));
    }

    @GetMapping("/vnpay-ipn")
    @Transactional
    @Operation(summary = "VNPay IPN callback (server-to-server)")
    public ResponseEntity<Map<String, String>> vnpayIpn(HttpServletRequest request) {
        Map<String, String> params = vnPayService.extractParams(request);
        Map<String, Object> result = processPassPaymentCallback(params, true);

        Map<String, String> response = new LinkedHashMap<>();
        response.put("RspCode", Boolean.TRUE.equals(result.get("success")) ? "00" : "99");
        response.put("Message", String.valueOf(result.getOrDefault("message", "Processed")));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> processPassPaymentCallback(Map<String, String> params, boolean mutateState) {
        Map<String, Object> response = new LinkedHashMap<>();
        boolean validSignature = vnPayService.verifySignature(params);
        response.put("validSignature", validSignature);
        response.put("vnpResponseCode", params.get("vnp_ResponseCode"));
        response.put("transactionStatus", params.get("vnp_TransactionStatus"));
        response.put("orderCode", params.get("vnp_TxnRef"));

        if (!validSignature) {
            response.put("success", false);
            response.put("message", "Sai chữ ký VNPay");
            return response;
        }

        String orderCode = params.get("vnp_TxnRef");
        Payment payment = orderCode == null
                ? null
                : paymentRepository.findByTransactionId(orderCode).orElse(null);

        if (payment == null) {
            response.put("success", false);
            response.put("message", "Không tìm thấy đơn thanh toán");
            return response;
        }

        boolean paid = "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));

        if ("SESSION".equalsIgnoreCase(payment.getReferenceType())) {
            if (mutateState) {
                if (paid) {
                    SessionResponse session = parkingSessionService.completeOnlineCheckoutPayment(payment);
                    response.put("session", session);
                } else {
                    payment.setStatus(Payment.PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                }
            }
            response.put("success", paid);
            response.put("message", paid ? "Thanh toán phí gửi xe thành công" : "Thanh toán phí gửi xe không thành công");
            response.put("payment", payment);
            response.put("paymentType", "SESSION");
            return response;
        }

        ParkingPass pass = parkingPassRepository.findById(payment.getReferenceId()).orElse(null);
        if (pass == null) {
            response.put("success", false);
            response.put("message", "Không tìm thấy gói hội viên");
            return response;
        }

        if (mutateState) {
            if (paid) {
                payment.setStatus(Payment.PaymentStatus.COMPLETED);
                payment.setPaidAt(LocalDateTime.now());
                paymentRepository.save(payment);

                int months = pass.getPassType() == ParkingPass.PassType.YEARLY ? 12 : pass.getPassType() == ParkingPass.PassType.QUARTERLY ? 3 : 1;
                LocalDate startDate = LocalDate.now();
                pass.setStartDate(startDate);
                pass.setEndDate(startDate.plusMonths(months));
                pass.setStatus(ParkingPass.PassStatus.ACTIVE);
                parkingPassRepository.save(pass);
            } else {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        }

        response.put("success", paid);
        response.put("message", paid ? "Thanh toán thành công" : "Thanh toán không thành công");
        response.put("payment", payment);
        response.put("pass", pass);
        response.put("paymentType", "PASS");
        return response;
    }
}
