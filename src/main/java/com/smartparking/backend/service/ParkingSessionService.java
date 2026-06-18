package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.CheckInRequest;
import com.smartparking.backend.dto.request.CheckOutRequest;
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
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service xử lý các phiên gửi xe (Parking Session) - Nghiệp vụ cốt lõi của hệ thống.
 *
 * Các luồng chính:
 * 1. checkIn(): Xe vào cổng, kiểm tra blacklist/SOS, gợi ý và chiếm chỗ tại Zone, tạo phiên.
 * 2. checkOut(): Xe ra cổng, tính thời gian đỗ, tính phí gửi xe, tạo giao dịch thanh toán, giải phóng chỗ tại Zone.
 * 3. initiateDriverVnPayCheckout() & completeOnlineCheckoutPayment(): Khởi tạo và xử lý thanh toán VNPay trực tuyến.
 */
@Service
public class ParkingSessionService {

    private static final Logger log = LoggerFactory.getLogger(ParkingSessionService.class);

    private final ParkingSessionRepository sessionRepository;
    private final ZoneSuggestionService zoneSuggestionService;
    private final PricingService pricingService;
    private final GateRepository gateRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final BlacklistService blacklistService;
    private final EmergencyService emergencyService;
    private final VnPayService vnPayService;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket broadcast

    public ParkingSessionService(
            ParkingSessionRepository sessionRepository,
            ZoneSuggestionService zoneSuggestionService,
            PricingService pricingService,
            GateRepository gateRepository,
            VehicleTypeRepository vehicleTypeRepository,
            PaymentRepository paymentRepository,
            ReservationRepository reservationRepository,
            BlacklistService blacklistService,
            EmergencyService emergencyService,
            VnPayService vnPayService,
            SimpMessagingTemplate messagingTemplate) {
        this.sessionRepository = sessionRepository;
        this.zoneSuggestionService = zoneSuggestionService;
        this.pricingService = pricingService;
        this.gateRepository = gateRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.blacklistService = blacklistService;
        this.emergencyService = emergencyService;
        this.vnPayService = vnPayService;
        this.messagingTemplate = messagingTemplate;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CHECK-IN (UC-04)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Xử lý xe vào bãi (Check-in):
     * 1. Kiểm tra trạng thái khẩn cấp (SOS) - Nếu có thì chặn.
     * 2. Kiểm tra biển số xem có đang đỗ trong bãi không (tránh trùng lặp phiên gửi).
     * 3. Kiểm tra biển số trong danh sách đen (Blacklist) - Nếu có thì phát cảnh báo WebSocket và chặn xe.
     * 4. Kiểm tra mã đặt chỗ trước (Reservation):
     *    - Nếu có đặt chỗ: Gán Zone đã đặt trước đó, cập nhật lại số lượng chỗ đặt trước/chỗ hiện tại.
     *    - Nếu không đặt chỗ: Tự động gợi ý Zone trống tốt nhất và tăng số lượng xe của Zone đó.
     * 5. Tạo ParkingSession mới ở trạng thái ACTIVE.
     * 6. Broadcast thay đổi sức chứa của Zone qua WebSocket.
     */
    @Transactional
    public SessionResponse checkIn(CheckInRequest request) {
        // Chặn nghiệp vụ nếu bãi đỗ đang trong tình trạng khẩn cấp
        emergencyService.ensureNormalOperation();

        // Bước 1: Validate — biển số không được có session đang hoạt động
        sessionRepository.findByLicensePlateAndStatus(request.getLicensePlate(), SessionStatus.ACTIVE)
                .ifPresent(s -> {
                    throw new BusinessException(
                            "Biển số " + request.getLicensePlate() + " đang có phiên gửi xe chưa kết thúc (Mã: " +
                                     s.getSessionCode() + "). Vui lòng check-out trước.");
                });

        // Bước 2: Tìm các thông tin liên quan
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Loại phương tiện không tồn tại"));
        Gate mainGate = gateRepository.findById(request.getGateEntryId())
                .orElseThrow(() -> new ResourceNotFoundException("Cổng vào không tồn tại"));

        // Bước 3: Kiểm tra Blacklist
        blacklistService.findActiveByPlate(request.getLicensePlate()).ifPresent(blacklistPlate -> {
            blacklistService.alertBlacklistAttempt(request.getLicensePlate(), blacklistPlate, mainGate);
            throw new BusinessException("Biển số " + request.getLicensePlate()
                    + " đang nằm trong blacklist. Lý do: " + blacklistPlate.getReason());
        });

        Reservation reservation = null;
        Zone assignedZone;
        
        // Bước 4: Xử lý gán Zone đỗ xe
        if (request.getReservationCode() != null && !request.getReservationCode().trim().isEmpty()) {
            // Trường hợp có đặt chỗ trước
            reservation = reservationRepository.findByReservationCode(request.getReservationCode().trim().toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Reservation không tồn tại"));
            if (reservation.getStatus() != Reservation.ReservationStatus.CONFIRMED && reservation.getStatus() != Reservation.ReservationStatus.PENDING) {
                throw new BusinessException("Reservation không còn hiệu lực");
            }
            String reservedPlate = reservation.getLicensePlate().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            String requestPlate = request.getLicensePlate().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            if (!reservedPlate.equals(requestPlate)) {
                throw new BusinessException("Biển số không khớp với reservation");
            }
            assignedZone = reservation.getZone();
            if (assignedZone.getReservedCount() > 0) {
                assignedZone.setReservedCount(assignedZone.getReservedCount() - 1);
            }
            assignedZone.setCurrentCount(assignedZone.getCurrentCount() + 1);
            if (assignedZone.getCurrentCount() + assignedZone.getReservedCount() >= assignedZone.getCapacity()) {
                assignedZone.setStatus(Zone.ZoneStatus.FULL);
            } else if (assignedZone.getStatus() == Zone.ZoneStatus.FULL) {
                assignedZone.setStatus(Zone.ZoneStatus.ACTIVE);
            }
        } else {
            // Trường hợp khách vãng lai, hệ thống tự gợi ý zone trống gần và thoáng nhất
            assignedZone = zoneSuggestionService.suggestZone(vehicleType);
            assignedZone = zoneSuggestionService.enterZone(assignedZone);
        }

        // Bước 5: Sinh mã session duy nhất
        String sessionCode = generateSessionCode();

        // Xác định phân loại Driver
        ParkingSession.DriverType driverType = ParkingSession.DriverType.WALK_IN;
        if (request.getReservationCode() != null && !request.getReservationCode().trim().isEmpty()) {
            driverType = ParkingSession.DriverType.PRE_BOOKED;
        } else if (request.getDriverType() != null && !request.getDriverType().trim().isEmpty()) {
            try {
                driverType = ParkingSession.DriverType.valueOf(request.getDriverType().toUpperCase());
            } catch (IllegalArgumentException e) {
                driverType = ParkingSession.DriverType.WALK_IN;
            }
        }

        // Bước 6: Lưu phiên đỗ xe
        ParkingSession session = ParkingSession.builder()
                .sessionCode(sessionCode)
                .licensePlate(request.getLicensePlate())
                .vehicleType(vehicleType)
                .zone(assignedZone)
                .driverType(driverType)
                .entryMainGate(mainGate)
                .entryTime(LocalDateTime.now())
                .zoneEntryTime(LocalDateTime.now())
                .qrCode(sessionCode)
                .status(SessionStatus.ACTIVE)
                .notes(request.getNotes())
                .build();
        session = sessionRepository.save(session);
        if (reservation != null) {
            reservation.setStatus(Reservation.ReservationStatus.COMPLETED);
            reservationRepository.save(reservation);
        }

        // Broadcast WebSocket thông báo thay đổi số xe trong Zone
        broadcastZoneChange(assignedZone);

        log.info("CHECK-IN: plate={}, session={}, zone={}, floor={}",
                request.getLicensePlate(), sessionCode,
                assignedZone.getZoneCode(), assignedZone.getFloor().getFloorName());

        return buildSessionResponse(session, assignedZone, null);
    }

    /*
    // ═══════════════════════════════════════════════════════════════════════════
    // CHECK-OUT (UC-05)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Xử lý xe ra bãi (Check-out trực tiếp/Tại quầy):
     * 1. Tìm phiên gửi xe đang hoạt động.
     * 2. Tính toán số phút gửi và áp dụng bảng giá tương ứng của tòa nhà để tính tổng phí gửi xe.
     * 3. Đánh dấu phiên gửi xe đã hoàn thành (COMPLETED).
     * 4. Tạo bản ghi thanh toán hoàn thành (Payment).
     * 5. Giải phóng số lượng đỗ tại Zone và broadcast WebSocket thay đổi.
     */
    @Transactional
    public SessionResponse checkOut(CheckOutRequest request) {
        emergencyService.ensureNormalOperation();

        // Bước 1: Tìm parking session đang ACTIVE
        ParkingSession session = findActiveSession(request);

        Gate exitGate = gateRepository.findById(request.getGateExitId())
                .orElseThrow(() -> new ResourceNotFoundException("Cổng ra không tồn tại"));

        // Bước 2: Tính thời gian gửi
        LocalDateTime exitTime = LocalDateTime.now();
        int durationMinutes = calculateDurationMinutes(session, exitTime);

        // Bước 3: Tính phí
        BigDecimal totalFee = calculateSessionFee(session, durationMinutes);

        // Bước 4: Cập nhật thông tin phiên gửi
        session.setExitTime(exitTime);
        session.setZoneExitTime(exitTime);
        session.setExitMainGate(exitGate);
        session.setDurationMinutes(durationMinutes);
        session.setTotalFee(totalFee);
        session.setStatus(SessionStatus.COMPLETED);

        // Bước 5: Lưu thông tin Payment
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

        // Bước 6: Giải phóng zone
        Zone zone = session.getZone();
        if (zone != null) {
            zone = zoneSuggestionService.exitZone(zone);
            broadcastZoneChange(zone);
        }

        session = sessionRepository.save(session);

        log.info("CHECK-OUT: plate={}, session={}, duration={}min, fee={}đ",
                session.getLicensePlate(), session.getSessionCode(),
                durationMinutes, totalFee);

        return buildSessionResponse(session, zone, "COMPLETED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // XEM SESSION HIỆN TẠI (UC-14)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tra cứu thông tin phiên gửi xe đang hoạt động dựa vào biển số xe.
     * Tính toán và hiển thị phí tạm tính dựa trên số phút thực tế đã gửi.
     */
    public SessionResponse getActiveSession(String licensePlate) {
        String normalizedInput = licensePlate.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();

        ParkingSession session = sessionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                .filter(s -> {
                    String normPlate = s.getLicensePlate().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
                    return normPlate.equals(normalizedInput);
                })
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiên gửi xe đang hoạt động cho biển số: " + licensePlate));

        // Tính phí tạm tính dựa vào khoảng thời gian từ lúc vào tới thời điểm hiện tại
        int currentMinutes = (int) ChronoUnit.MINUTES.between(session.getEntryTime(), LocalDateTime.now());
        UUID buildingId = session.getZone().getFloor().getBuilding().getId();
        BigDecimal estimatedFee = pricingService.estimateFee(
                buildingId, session.getVehicleType().getId(), currentMinutes);

        SessionResponse response = buildSessionResponse(session, session.getZone(), null);
        response.setDurationMinutes(currentMinutes);
        response.setTotalFee(estimatedFee);
        response.setGuideMessage("Phí tạm tính: " + estimatedFee + "đ (chưa bao gồm thời gian còn lại)");
        return response;
    }

    /**
     * Khởi tạo liên kết thanh toán trực tuyến qua VNPay cho Driver tự check-out:
     * 1. Tính toán phí tạm tính đến thời điểm hiện tại.
     * 2. Tạo hoặc tái sử dụng bản ghi Payment trạng thái PENDING.
     * 3. Gọi dịch vụ VNPay để sinh URL cổng thanh toán Sandbox.
     */
    @Transactional
    public Map<String, Object> initiateDriverVnPayCheckout(Map<String, String> body, HttpServletRequest request) {
        emergencyService.ensureNormalOperation();

        CheckOutRequest lookup = new CheckOutRequest();
        lookup.setSessionCode(body.get("sessionCode"));
        lookup.setLicensePlate(body.get("licensePlate"));
        ParkingSession session = findActiveSession(lookup);

        LocalDateTime exitTime = LocalDateTime.now();
        int durationMinutes = calculateDurationMinutes(session, exitTime);
        BigDecimal totalFee = calculateSessionFee(session, durationMinutes);
        String orderCode = "SESSION-" + session.getId().toString().replace("-", "").substring(0, 16).toUpperCase();

        Payment payment = paymentRepository.findByReferenceTypeAndReferenceId("SESSION", session.getId()).stream()
                .filter(p -> p.getPaymentMethod() == Payment.PaymentMethod.ONLINE)
                .filter(p -> p.getStatus() != Payment.PaymentStatus.COMPLETED)
                .findFirst()
                .orElseGet(() -> paymentRepository.save(Payment.builder()
                        .referenceType("SESSION")
                        .referenceId(session.getId())
                        .amount(totalFee)
                        .paymentMethod(Payment.PaymentMethod.ONLINE)
                        .status(Payment.PaymentStatus.PENDING)
                        .transactionId(orderCode)
                        .build()));

        payment.setAmount(totalFee);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setTransactionId(orderCode);
        payment = paymentRepository.save(payment);

        String orderInfo = "Thanh toan checkout phien " + session.getSessionCode() + " bien so " + session.getLicensePlate();
        String paymentUrl = vnPayService.createPaymentUrl(payment.getTransactionId(), totalFee, orderInfo, request);

        SessionResponse sessionResponse = buildSessionResponse(session, session.getZone(), "PENDING");
        sessionResponse.setDurationMinutes(durationMinutes);
        sessionResponse.setTotalFee(totalFee);
        sessionResponse.setGuideMessage("Chuyển tới VNPay sandbox để thanh toán phí ra bãi");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session", sessionResponse);
        response.put("payment", payment);
        response.put("paymentUrl", paymentUrl);
        response.put("orderCode", payment.getTransactionId());
        response.put("expiresAt", LocalDateTime.now().plusMinutes(15));
        return response;
    }

    /**
     * Xác nhận thanh toán online thành công và hoàn tất phiên gửi xe:
     * (Gọi bởi VnPayService sau khi webhook nhận callback IPN hợp lệ từ VNPay).
     * 1. Cập nhật trạng thái session sang COMPLETED.
     * 2. Giải phóng chỗ trống tại Zone đỗ xe.
     * 3. Phát tin nhắn thông báo xác nhận thanh toán thành công qua WebSocket.
     */
    @Transactional
    public SessionResponse completeOnlineCheckoutPayment(Payment payment) {
        ParkingSession session = sessionRepository.findById(payment.getReferenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên gửi xe cho đơn thanh toán"));

        if (session.getStatus() == SessionStatus.COMPLETED) {
            return buildSessionResponse(session, session.getZone(), "COMPLETED");
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new BusinessException("Phiên gửi xe không còn ở trạng thái hoạt động");
        }

        LocalDateTime exitTime = LocalDateTime.now();
        int durationMinutes = calculateDurationMinutes(session, exitTime);
        BigDecimal totalFee = calculateSessionFee(session, durationMinutes);
        Gate exitGate = findDefaultExitGate();

        session.setExitTime(exitTime);
        session.setZoneExitTime(exitTime);
        if (exitGate != null) {
            session.setExitMainGate(exitGate);
        }
        session.setDurationMinutes(durationMinutes);
        session.setTotalFee(totalFee);
        session.setStatus(SessionStatus.COMPLETED);

        payment.setAmount(totalFee);
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        Zone zone = session.getZone();
        if (zone != null) {
            zone = zoneSuggestionService.exitZone(zone);
            broadcastZoneChange(zone);
        }

        session = sessionRepository.save(session);

        Map<String, Object> paymentNotification = Map.of(
                "type", "PAYMENT_CONFIRMED",
                "sessionCode", session.getSessionCode(),
                "licensePlate", session.getLicensePlate(),
                "totalFee", totalFee.toString(),
                "durationMinutes", durationMinutes,
                "vehicleType", session.getVehicleType().getName(),
                "paymentMethod", "VNPAY",
                "paidAt", LocalDateTime.now().toString(),
                "exitGate", exitGate != null ? exitGate.getGateName() : "Cổng chính"
        );
        messagingTemplate.convertAndSend("/topic/payments/confirmed", paymentNotification);

        log.info("VNPAY CHECKOUT COMPLETED: plate={}, session={}, duration={}min, fee={}đ",
                session.getLicensePlate(), session.getSessionCode(), durationMinutes, totalFee);

        return buildSessionResponse(session, zone, "COMPLETED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private int calculateDurationMinutes(ParkingSession session, LocalDateTime exitTime) {
        int durationMinutes = (int) ChronoUnit.MINUTES.between(session.getEntryTime(), exitTime);
        return Math.max(durationMinutes, 1);
    }

    private BigDecimal calculateSessionFee(ParkingSession session, int durationMinutes) {
        if (session.getDriverType() == ParkingSession.DriverType.SUBSCRIBER) {
            return BigDecimal.ZERO;
        }
        UUID buildingId = session.getZone().getFloor().getBuilding().getId();
        UUID vehicleTypeId = session.getVehicleType().getId();
        return pricingService.calculateFee(buildingId, vehicleTypeId, durationMinutes);
    }

    private Gate findDefaultExitGate() {
        return gateRepository.findAll().stream()
                .filter(Gate::getIsActive)
                .filter(g -> g.getGateType() == Gate.GateType.MAIN_EXIT || g.getGateType() == Gate.GateType.MAIN_BOTH)
                .findFirst()
                .orElse(null);
    }

    /**
     * Tìm session đang ACTIVE bằng 1 trong 3 tiêu chí: sessionId, sessionCode, hoặc biển số xe.
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
            String normalizedInput = request.getLicensePlate().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            return sessionRepository.findAll().stream()
                    .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                    .filter(s -> {
                        String normPlate = s.getLicensePlate().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
                        return normPlate.equals(normalizedInput);
                    })
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy session đang hoạt động với biển số: " + request.getLicensePlate()));
        }
        throw new BusinessException("Phải cung cấp ít nhất 1 trong: sessionId, sessionCode, hoặc licensePlate");
    }
    */

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
    private SessionResponse buildSessionResponse(ParkingSession session, Zone zone, String paymentStatus) {
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

        if (zone != null) {
            builder.zoneCode(zone.getZoneCode())
                    .floorName(zone.getFloor().getFloorName())
                    .zoneName(zone.getZoneName())
                    .guideMessage(String.format(
                            "Vui lòng đến Tầng %s - %s",
                            zone.getFloor().getFloorName(),
                            zone.getZoneName()));
        }

        return builder.build();
    }

    private void broadcastZoneChange(Zone zone) {
        try {
            UUID buildingId = zone.getFloor().getBuilding().getId();
            Map<String, Object> message = Map.of(
                    "zoneId", zone.getId().toString(),
                    "zoneCode", zone.getZoneCode(),
                    "zoneName", zone.getZoneName(),
                    "status", zone.getStatus().name(),
                    "currentCount", zone.getCurrentCount(),
                    "capacity", zone.getCapacity(),
                    "floorName", zone.getFloor().getFloorName(),
                    "timestamp", LocalDateTime.now().toString());
            messagingTemplate.convertAndSend("/topic/zones/" + buildingId, message);
        } catch (Exception e) {
            log.warn("Failed to broadcast zone change: {}", e.getMessage());
        }
    }

    /*
     * Lấy lịch sử gửi xe theo biển số — dùng cho Driver xem history.
     * (Tạm thời đóng phục vụ Milestone 1)
    public List<SessionResponse> getSessionHistory(String licensePlate) {
        List<ParkingSession> sessions = sessionRepository
                .findByLicensePlateOrderByEntryTimeDesc(licensePlate);

        return sessions.stream().map(session -> {
            Zone zone = session.getZone();
            String paymentStatus = session.getStatus() == SessionStatus.COMPLETED ? "PAID" : "PENDING";
            return buildSessionResponse(session, zone, paymentStatus);
        }).toList();
    }
    */

    /*
     * Lấy toàn bộ danh sách lịch sử tất cả các phiên gửi xe cho Staff/Manager/Admin.
     * (Tạm thời đóng phục vụ Milestone 1)
    public List<SessionResponse> getAllSessions() {
        return sessionRepository
                .findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        "entryTime"))
                .stream()
                .map(session -> {
                    Zone zone = session.getZone();
                    String paymentStatus = session.getStatus() == SessionStatus.COMPLETED ? "PAID" : "PENDING";
                    return buildSessionResponse(session, zone, paymentStatus);
                })
                .toList();
    }
    */
}
