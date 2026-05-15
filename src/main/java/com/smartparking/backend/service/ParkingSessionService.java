package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.CheckInRequest;
import com.smartparking.backend.dto.request.CheckOutRequest;
import com.smartparking.backend.dto.response.SessionResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.entity.ParkingSession.SessionStatus;
import com.smartparking.backend.entity.Slot.SlotStatus;
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
 * ParkingSessionService — Core Business Logic.
 *
 * Luồng chính:
 *   checkIn()  → Tạo session + gán slot + broadcast trạng thái
 *   checkOut() → Tính phí + ghi payment + giải phóng slot + broadcast
 */
@Service
public class ParkingSessionService {

    private static final Logger log = LoggerFactory.getLogger(ParkingSessionService.class);

    private final ParkingSessionRepository sessionRepository;
    private final SlotRepository slotRepository;
    private final SlotAssignmentService slotAssignmentService;
    private final PricingService pricingService;
    private final GateRepository gateRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final PaymentRepository paymentRepository;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket broadcast

    public ParkingSessionService(
            ParkingSessionRepository sessionRepository,
            SlotRepository slotRepository,
            SlotAssignmentService slotAssignmentService,
            PricingService pricingService,
            GateRepository gateRepository,
            VehicleTypeRepository vehicleTypeRepository,
            PaymentRepository paymentRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.slotAssignmentService = slotAssignmentService;
        this.pricingService = pricingService;
        this.gateRepository = gateRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.paymentRepository = paymentRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CHECK-IN (UC-04)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Xử lý xe vào bãi:
     *  1. Kiểm tra biển số không bị trùng session đang ACTIVE
     *  2. Gọi SlotAssignment tìm + lock slot tối ưu
     *  3. Tạo ParkingSession mới
     *  4. Broadcast thay đổi slot qua WebSocket
     *  5. Trả response hướng dẫn tài xế đến slot
     */
    @Transactional
    public SessionResponse checkIn(CheckInRequest request) {
        // Bước 1: Validate — biển số không được có session đang mở (BR-06)
        sessionRepository.findByLicensePlateAndStatus(request.getLicensePlate(), SessionStatus.ACTIVE)
                .ifPresent(s -> {
                    throw new BusinessException(
                            "Biển số " + request.getLicensePlate() + " đang có phiên gửi xe chưa kết thúc (Mã: " +
                            s.getSessionCode() + "). Vui lòng check-out trước.");
                });

        // Bước 2: Tìm entity references
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Loại phương tiện không tồn tại"));
        Gate gateEntry = gateRepository.findById(request.getGateEntryId())
                .orElseThrow(() -> new ResourceNotFoundException("Cổng vào không tồn tại"));

        // Bước 3: Sinh mã session duy nhất
        String sessionCode = generateSessionCode();

        // Bước 4: Tạo session trước (cần ID để pass cho slotAssignment lock)
        ParkingSession session = ParkingSession.builder()
                .sessionCode(sessionCode)
                .licensePlate(request.getLicensePlate())
                .vehicleType(vehicleType)
                .gateEntry(gateEntry)
                .entryTime(LocalDateTime.now())
                .status(SessionStatus.ACTIVE)
                .notes(request.getNotes())
                .build();
        session = sessionRepository.save(session);

        // Bước 5: Gọi thuật toán phân bổ slot (core AI)
        Slot assignedSlot;
        try {
            assignedSlot = slotAssignmentService.assignOptimalSlot(vehicleType, session.getId());
        } catch (BusinessException e) {
            // Nếu không còn slot → xóa session vừa tạo
            sessionRepository.delete(session);
            throw e;
        }

        // Bước 6: Gán slot cho session
        session.setSlot(assignedSlot);
        session = sessionRepository.save(session);

        // Bước 7: Broadcast slot status change qua WebSocket
        broadcastSlotChange(assignedSlot);

        log.info("CHECK-IN: plate={}, session={}, slot={}, floor={}",
                request.getLicensePlate(), sessionCode,
                assignedSlot.getSlotCode(), assignedSlot.getFloor().getFloorName());

        // Bước 8: Build response
        return buildSessionResponse(session, assignedSlot, null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CHECK-OUT (UC-05)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Xử lý xe ra bãi:
     *  1. Tìm session (theo sessionId / sessionCode / licensePlate)
     *  2. Tính thời gian + phí
     *  3. Tạo Payment record
     *  4. Đóng session + giải phóng slot
     *  5. Broadcast slot change
     */
    @Transactional
    public SessionResponse checkOut(CheckOutRequest request) {
        // Bước 1: Tìm parking session đang ACTIVE
        ParkingSession session = findActiveSession(request);

        Gate gateExit = gateRepository.findById(request.getGateExitId())
                .orElseThrow(() -> new ResourceNotFoundException("Cổng ra không tồn tại"));

        // Bước 2: Tính thời gian gửi
        LocalDateTime exitTime = LocalDateTime.now();
        int durationMinutes = (int) ChronoUnit.MINUTES.between(session.getEntryTime(), exitTime);
        if (durationMinutes < 1) durationMinutes = 1; // Tối thiểu 1 phút

        // Bước 3: Tính phí
        UUID buildingId = session.getSlot().getFloor().getBuilding().getId();
        UUID vehicleTypeId = session.getVehicleType().getId();
        BigDecimal totalFee = pricingService.calculateFee(buildingId, vehicleTypeId, durationMinutes);

        // Bước 4: Cập nhật session
        session.setExitTime(exitTime);
        session.setGateExit(gateExit);
        session.setDurationMinutes(durationMinutes);
        session.setTotalFee(totalFee);
        session.setStatus(SessionStatus.COMPLETED);

        // Bước 5: Tạo Payment record
        Payment.PaymentMethod paymentMethod;
        try {
            paymentMethod = Payment.PaymentMethod.valueOf(
                    request.getPaymentMethod() != null ? request.getPaymentMethod().toUpperCase() : "CASH");
        } catch (IllegalArgumentException e) {
            paymentMethod = Payment.PaymentMethod.CASH;
        }

        Payment payment = Payment.builder()
                .referenceType("SESSION")
                .referenceId(session.getId())
                .amount(totalFee)
                .paymentMethod(paymentMethod)
                .status(Payment.PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now())
                .build();
        paymentRepository.save(payment);

        // Bước 6: Giải phóng slot
        Slot slot = session.getSlot();
        if (slot != null) {
            slot.setStatus(SlotStatus.AVAILABLE);
            slotRepository.save(slot);
            slotAssignmentService.releaseSlotLock(slot.getId());

            // Broadcast slot change
            broadcastSlotChange(slot);
        }

        session = sessionRepository.save(session);

        log.info("CHECK-OUT: plate={}, session={}, duration={}min, fee={}đ",
                session.getLicensePlate(), session.getSessionCode(),
                durationMinutes, totalFee);

        return buildSessionResponse(session, slot, "COMPLETED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // XEM SESSION HIỆN TẠI (UC-14)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Xem thông tin phiên gửi xe đang diễn ra — cho Driver hoặc Staff tra cứu.
     */
    public SessionResponse getActiveSession(String licensePlate) {
        ParkingSession session = sessionRepository
                .findByLicensePlateAndStatus(licensePlate, SessionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiên gửi xe đang hoạt động cho biển số: " + licensePlate));

        // Tính phí tạm tính
        int currentMinutes = (int) ChronoUnit.MINUTES.between(session.getEntryTime(), LocalDateTime.now());
        UUID buildingId = session.getSlot().getFloor().getBuilding().getId();
        BigDecimal estimatedFee = pricingService.estimateFee(
                buildingId, session.getVehicleType().getId(), currentMinutes);

        SessionResponse response = buildSessionResponse(session, session.getSlot(), null);
        response.setDurationMinutes(currentMinutes);
        response.setTotalFee(estimatedFee);
        response.setGuideMessage("Phí tạm tính: " + estimatedFee + "đ (chưa bao gồm thời gian còn lại)");
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tìm session đang ACTIVE bằng 1 trong 3 tiêu chí.
     */
    private ParkingSession findActiveSession(CheckOutRequest request) {
        if (request.getSessionId() != null) {
            return sessionRepository.findById(request.getSessionId())
                    .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                    .orElseThrow(() -> new ResourceNotFoundException("Session không tồn tại hoặc đã đóng"));
        }
        if (request.getSessionCode() != null && !request.getSessionCode().isBlank()) {
            return sessionRepository.findBySessionCode(request.getSessionCode())
                    .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy session đang hoạt động với mã: " + request.getSessionCode()));
        }
        if (request.getLicensePlate() != null && !request.getLicensePlate().isBlank()) {
            return sessionRepository.findByLicensePlateAndStatus(request.getLicensePlate(), SessionStatus.ACTIVE)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy session đang hoạt động với biển số: " + request.getLicensePlate()));
        }
        throw new BusinessException("Phải cung cấp ít nhất 1 trong: sessionId, sessionCode, hoặc licensePlate");
    }

    /**
     * Sinh mã session duy nhất: PS + yyyyMMdd + 3 ký tự ngẫu nhiên.
     * Ví dụ: PS20260516-A3F
     */
    private String generateSessionCode() {
        String date = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = UUID.randomUUID().toString().substring(0, 3).toUpperCase();
        return "PS" + date + "-" + random;
    }

    /**
     * Build response DTO từ entity.
     */
    private SessionResponse buildSessionResponse(ParkingSession session, Slot slot, String paymentStatus) {
        SessionResponse.SessionResponseBuilder builder = SessionResponse.builder()
                .sessionId(session.getId())
                .sessionCode(session.getSessionCode())
                .licensePlate(session.getLicensePlate())
                .vehicleType(session.getVehicleType().getName())
                .entryTime(session.getEntryTime())
                .exitTime(session.getExitTime())
                .durationMinutes(session.getDurationMinutes())
                .totalFee(session.getTotalFee())
                .status(session.getStatus())
                .paymentStatus(paymentStatus);

        if (slot != null) {
            builder.slotCode(slot.getSlotCode())
                   .floorName(slot.getFloor().getFloorName())
                   .zoneName("Khu " + slot.getSlotCode().split("-")[0])
                   .guideMessage(String.format(
                           "Vui lòng đến Tầng %s - Ô số %s",
                           slot.getFloor().getFloorName(),
                           slot.getSlotCode()));
        }

        return builder.build();
    }

    /**
     * Broadcast thay đổi trạng thái slot qua WebSocket.
     * Client subscribe: /topic/slots/{buildingId}
     */
    private void broadcastSlotChange(Slot slot) {
        try {
            UUID buildingId = slot.getFloor().getBuilding().getId();
            Map<String, Object> message = Map.of(
                    "slotId", slot.getId().toString(),
                    "slotCode", slot.getSlotCode(),
                    "status", slot.getStatus().name(),
                    "floorName", slot.getFloor().getFloorName(),
                    "timestamp", LocalDateTime.now().toString()
            );
            messagingTemplate.convertAndSend("/topic/slots/" + buildingId, message);
        } catch (Exception e) {
            log.warn("Failed to broadcast slot change: {}", e.getMessage());
        }
    }
}
