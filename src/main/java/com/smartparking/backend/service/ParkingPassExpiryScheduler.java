package com.smartparking.backend.service;

import com.smartparking.backend.entity.ParkingPass;
import com.smartparking.backend.entity.Payment;
import com.smartparking.backend.repository.ParkingPassRepository;
import com.smartparking.backend.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ParkingPassExpiryScheduler — Background Job tự động hủy các gói (vé tháng/quý/năm)
 * đang ở trạng thái PENDING_PAYMENT quá 15 phút.
 *
 * Flow:
 * 1. Driver tạo gói / hủy thanh toán giữa chừng trên VNPay -> Gói giữ trạng thái PENDING_PAYMENT.
 * 2. Driver có 15 phút để quay lại tiếp tục thanh toán.
 * 3. Nếu quá 15 phút không thanh toán thành công, Scheduler này sẽ tự động đổi trạng thái gói sang CANCELLED (Đã hủy).
 */
@Component
public class ParkingPassExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ParkingPassExpiryScheduler.class);

    private final ParkingPassRepository parkingPassRepository;
    private final PaymentRepository paymentRepository;

    public ParkingPassExpiryScheduler(
            ParkingPassRepository parkingPassRepository,
            PaymentRepository paymentRepository) {
        this.parkingPassRepository = parkingPassRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Chạy định kỳ mỗi 60 giây để kiểm tra và hủy các gói PENDING_PAYMENT quá 15 phút.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cancelOverduePendingPasses() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(15);

        List<ParkingPass> overduePasses = parkingPassRepository
                .findByStatusAndCreatedAtBefore(ParkingPass.PassStatus.PENDING_PAYMENT, cutoffTime);

        if (overduePasses.isEmpty()) {
            return;
        }

        for (ParkingPass pass : overduePasses) {
            // Chuyển trạng thái gói sang CANCELLED (Đã hủy đơn)
            pass.setStatus(ParkingPass.PassStatus.CANCELLED);
            parkingPassRepository.save(pass);

            // Cập nhật Payment tương ứng nếu vẫn ở trạng thái PENDING
            List<Payment> payments = paymentRepository.findByReferenceTypeAndReferenceId("PASS", pass.getId());
            if (payments.isEmpty()) {
                payments = paymentRepository.findByReferenceTypeAndReferenceId("MONTHLY_PASS", pass.getId());
            }

            for (Payment payment : payments) {
                if (payment.getStatus() == Payment.PaymentStatus.PENDING) {
                    payment.setStatus(Payment.PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                }
            }

            log.info("Auto-cancelled overdue pending ParkingPass code: {}, plate: {}",
                    pass.getParkingPassCode(), pass.getLicensePlate());
        }

        log.info("Expired/Cancelled {} overdue pending parking pass(es)", overduePasses.size());
    }
}
