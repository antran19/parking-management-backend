package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.PaymentRequest;
import com.smartparking.backend.dto.response.PaymentResponse;
import com.smartparking.backend.entity.ParkingPass;
import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.entity.Payment;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.ParkingPassRepository;
import com.smartparking.backend.repository.ParkingSessionRepository;
import com.smartparking.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final ParkingSessionRepository sessionRepository;
    private final ParkingPassRepository parkingPassRepository;

    /**
     * Khởi tạo giao dịch thanh toán (PENDING).
     * Áp dụng chống lỗi thanh toán trùng lặp (Idempotency):
     * - Nếu tham chiếu (phiên gửi hoặc vé) đã có hóa đơn COMPLETED -> Chặn ngay lập tức.
     * - Nếu đã có hóa đơn PENDING -> Cập nhật lại phương thức/số tiền và dùng lại để tránh rác DB.
     */
    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request) {
        log.info("Initiating payment for type: {}, id: {}, amount: {}", 
                request.getReferenceType(), request.getReferenceId(), request.getAmount());

        // 1. Kiểm tra tính hợp lệ của tham chiếu
        validateReference(request.getReferenceType(), request.getReferenceId());

        // 2. Kiểm tra hóa đơn thanh toán trùng lặp
        List<Payment> existingPayments = paymentRepository
                .findByReferenceTypeAndReferenceId(request.getReferenceType(), request.getReferenceId());

        for (Payment p : existingPayments) {
            if (p.getStatus() == Payment.PaymentStatus.COMPLETED) {
                throw new BusinessException("Giao dịch này đã được thanh toán hoàn tất từ trước. Không thể thực hiện lại.");
            }
        }

        // 3. Tìm giao dịch PENDING cũ để tái sử dụng
        Payment payment = existingPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                .findFirst()
                .orElse(null);

        Payment.PaymentMethod method;
        String rawMethod = request.getPaymentMethod().toUpperCase();
        if ("BANK_TRANSFER".equals(rawMethod)) {
            method = Payment.PaymentMethod.QR_CODE;
        } else {
            try {
                method = Payment.PaymentMethod.valueOf(rawMethod);
            } catch (IllegalArgumentException e) {
                method = Payment.PaymentMethod.CASH;
            }
        }

        if (payment != null) {
            // Cập nhật lại thông tin mới
            payment.setPaymentMethod(method);
            payment.setAmount(request.getAmount());
            log.info("Reusing existing pending payment: {}", payment.getId());
        } else {
            // Tạo mới hóa đơn PENDING
            payment = Payment.builder()
                    .referenceType(request.getReferenceType().toUpperCase())
                    .referenceId(request.getReferenceId())
                    .amount(request.getAmount())
                    .paymentMethod(method)
                    .status(Payment.PaymentStatus.PENDING)
                    .build();
            log.info("Creating new pending payment");
        }

        payment = paymentRepository.save(payment);
        return mapToResponse(payment);
    }

    /**
     * Xác nhận thanh toán thành công (COMPLETED).
     * Cơ chế an toàn: đồng bộ hóa (synchronized) và bọc trong Transaction để chống lỗi race condition.
     */
    @Transactional
    public synchronized PaymentResponse confirmPayment(UUID paymentId) {
        log.info("Confirming payment with ID: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Hóa đơn thanh toán không tồn tại"));

        // Nếu hóa đơn đã hoàn tất, trả về luôn để đảm bảo tính idempotent
        if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
            log.info("Payment {} was already completed.", paymentId);
            return mapToResponse(payment);
        }

        if (payment.getStatus() == Payment.PaymentStatus.FAILED || payment.getStatus() == Payment.PaymentStatus.REFUNDED) {
            throw new BusinessException("Hóa đơn này ở trạng thái không thể thanh toán (Thất bại / Đã hoàn tiền).");
        }

        // 1. Cập nhật hóa đơn sang COMPLETED
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        payment.setTransactionId("TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        payment = paymentRepository.save(payment);

        // 2. Cập nhật trạng thái đối tượng gốc tương ứng
        updateReferencedEntity(payment.getReferenceType(), payment.getReferenceId(), payment.getAmount());

        log.info("Successfully confirmed payment {} with transaction ID {}", paymentId, payment.getTransactionId());
        return mapToResponse(payment);
    }

    /**
     * Tra cứu chi tiết hóa đơn thanh toán.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentDetails(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Hóa đơn thanh toán không tồn tại"));
        return mapToResponse(payment);
    }

    // Helper kiểm tra tính tồn tại của thực thể tham chiếu
    private void validateReference(String type, UUID id) {
        if ("SESSION".equalsIgnoreCase(type)) {
            if (!sessionRepository.existsById(id)) {
                throw new ResourceNotFoundException("Phiên gửi xe (Session) không tồn tại");
            }
        } else if ("MONTHLY_PASS".equalsIgnoreCase(type)) {
            if (!parkingPassRepository.existsById(id)) {
                throw new ResourceNotFoundException("Vé xe (ParkingPass) không tồn tại");
            }
        } else {
            throw new BusinessException("Loại tham chiếu không hợp lệ. Chỉ chấp nhận SESSION hoặc MONTHLY_PASS");
        }
    }

    // Helper cập nhật trạng thái thực thể gốc sau khi thanh toán thành công
    private void updateReferencedEntity(String type, UUID id, BigDecimal amount) {
        if ("SESSION".equalsIgnoreCase(type)) {
            ParkingSession session = sessionRepository.findById(id).orElse(null);
            if (session != null) {
                session.setStatus(ParkingSession.SessionStatus.COMPLETED);
                session.setTotalFee(amount);
                sessionRepository.save(session);
                log.info("Parking session {} status updated to COMPLETED", id);
            }
        } else if ("MONTHLY_PASS".equalsIgnoreCase(type)) {
            ParkingPass pass = parkingPassRepository.findById(id).orElse(null);
            if (pass != null) {
                pass.setStatus(ParkingPass.PassStatus.ACTIVE);
                parkingPassRepository.save(pass);
                log.info("Parking pass {} status updated to ACTIVE", id);
            }
        }
    }

    // Helper map sang Response DTO và tự động sinh mã VietQR nếu chuyển khoản
    private PaymentResponse mapToResponse(Payment payment) {
        String qrCodeUrl = null;
        if (payment.getPaymentMethod() == Payment.PaymentMethod.ONLINE || 
            payment.getPaymentMethod() == Payment.PaymentMethod.QR_CODE) {
            
            // Build mã QR động VietQR hướng dẫn thanh toán ngân hàng (ACP Bank, Số tài khoản: 123456789)
            String memo = "Pay+" + payment.getReferenceType() + "+" + payment.getReferenceId().toString().substring(0, 8);
            qrCodeUrl = String.format("https://img.vietqr.io/image/970415-123456789-qr_only.png?amount=%s&addInfo=%s&accountName=SmartParking+Tower",
                    payment.getAmount().toPlainString(), memo);
        }

        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .referenceType(payment.getReferenceType())
                .referenceId(payment.getReferenceId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod().name())
                .status(payment.getStatus().name())
                .qrCodeUrl(qrCodeUrl)
                .transactionId(payment.getTransactionId())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
