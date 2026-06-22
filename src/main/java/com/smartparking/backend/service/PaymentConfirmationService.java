package com.smartparking.backend.service;

import com.smartparking.backend.dto.response.SessionResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.entity.ParkingSession.SessionStatus;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * PaymentConfirmationService — Xử lý xác nhận thanh toán chuyển khoản real-time.
 *
 * Khi Driver xác nhận đã chuyển khoản:
 *   1. Tìm session ACTIVE
 *   2. Tính phí + tạo Payment record
 *   3. Đóng session (COMPLETED)
 *   4. Broadcast qua WebSocket → Staff nhận real-time
 */
@Service
public class PaymentConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmationService.class);

    private final ParkingSessionRepository sessionRepository;
    private final GateRepository gateRepository;
    private final PaymentRepository paymentRepository;
    private final PricingService pricingService;
    private final ZoneSuggestionService zoneSuggestionService;
    private final SimpMessagingTemplate messagingTemplate;

    public PaymentConfirmationService(
            ParkingSessionRepository sessionRepository,
            GateRepository gateRepository,
            PaymentRepository paymentRepository,
            PricingService pricingService,
            ZoneSuggestionService zoneSuggestionService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.sessionRepository = sessionRepository;
        this.gateRepository = gateRepository;
        this.paymentRepository = paymentRepository;
        this.pricingService = pricingService;
        this.zoneSuggestionService = zoneSuggestionService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public SessionResponse confirmPayment(String sessionCode, String licensePlate) {
        // Bước 1: Tìm session đang ACTIVE
        ParkingSession session;
        if (sessionCode != null && !sessionCode.isBlank()) {
            session = sessionRepository.findBySessionCode(sessionCode)
                    .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy phiên gửi xe đang hoạt động với mã: " + sessionCode));
        } else if (licensePlate != null && !licensePlate.isBlank()) {
            String normalizedInput = licensePlate.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            session = sessionRepository.findAll().stream()
                    .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                    .filter(s -> {
                        String normPlate = s.getLicensePlate().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
                        return normPlate.equals(normalizedInput);
                    })
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy phiên gửi xe đang hoạt động với biển số: " + licensePlate));
        } else {
            throw new BusinessException("Phải cung cấp sessionCode hoặc licensePlate");
        }

        // Bước 2: Tính thời gian và phí
        LocalDateTime exitTime = LocalDateTime.now();
        int durationMinutes = (int) ChronoUnit.MINUTES.between(session.getEntryTime(), exitTime);
        if (durationMinutes < 1) durationMinutes = 1;

        UUID buildingId = session.getZone().getFloor().getBuilding().getId();
        UUID vehicleTypeId = session.getVehicleType().getId();
        BigDecimal totalFee = pricingService.calculateFee(buildingId, vehicleTypeId, durationMinutes);

        // Bước 3: Tìm cổng ra mặc định (MAIN_EXIT hoặc MAIN_BOTH)
        Gate exitGate = gateRepository.findAll().stream()
                .filter(Gate::getIsActive)
                .filter(g -> g.getGateType() == Gate.GateType.MAIN_EXIT || g.getGateType() == Gate.GateType.MAIN_BOTH)
                .findFirst()
                .orElse(null);

        // Bước 4: Cập nhật session → COMPLETED
        session.setExitTime(exitTime);
        session.setZoneExitTime(exitTime);
        if (exitGate != null) {
            session.setExitMainGate(exitGate);
        }
        session.setDurationMinutes(durationMinutes);
        session.setTotalFee(totalFee);
        session.setStatus(SessionStatus.COMPLETED);

        // Bước 5: Tạo Payment record
        Payment payment = Payment.builder()
                .referenceType("SESSION")
                .referenceId(session.getId())
                .amount(totalFee)
                .paymentMethod(Payment.PaymentMethod.BANK_TRANSFER)
                .status(Payment.PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now())
                .transactionId("VQR-" + System.currentTimeMillis())
                .build();
        paymentRepository.save(payment);

        // Bước 6: Giải phóng zone
        Zone zone = session.getZone();
        if (zone != null) {
            zone = zoneSuggestionService.exitZone(zone);
        }

        session = sessionRepository.save(session);

        log.info("PAYMENT CONFIRMED (REAL-TIME): plate={}, session={}, fee={}đ",
                session.getLicensePlate(), session.getSessionCode(), totalFee);

        // Bước 7: Build response
        SessionResponse response = SessionResponse.builder()
                .sessionId(session.getId())
                .sessionCode(session.getSessionCode())
                .licensePlate(session.getLicensePlate())
                .vehicleType(session.getVehicleType().getName())
                .entryTime(session.getEntryTime())
                .exitTime(exitTime)
                .durationMinutes(durationMinutes)
                .totalFee(totalFee)
                .status(session.getStatus())
                .paymentStatus("COMPLETED")
                .build();

        if (zone != null) {
            response.setZoneCode(zone.getZoneCode());
            response.setFloorName(zone.getFloor().getFloorName());
            response.setZoneName(zone.getZoneName());
        }

        // Bước 8: Broadcast real-time cho Staff qua WebSocket
        Map<String, Object> paymentNotification = Map.of(
                "type", "PAYMENT_CONFIRMED",
                "sessionCode", session.getSessionCode(),
                "licensePlate", session.getLicensePlate(),
                "totalFee", totalFee.toString(),
                "durationMinutes", durationMinutes,
                "vehicleType", session.getVehicleType().getName(),
                "paymentMethod", "BANK_TRANSFER",
                "paidAt", LocalDateTime.now().toString(),
                "exitGate", exitGate != null ? exitGate.getGateName() : "Cổng chính"
        );

        // Broadcast tới topic chung cho tất cả Staff đang mở trang Check-out
        messagingTemplate.convertAndSend("/topic/payments/confirmed", paymentNotification);

        log.info("WebSocket broadcast sent to /topic/payments/confirmed for session: {}", session.getSessionCode());

        return response;
    }
}
