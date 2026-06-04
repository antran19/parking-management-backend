package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.CheckInRequest;
import com.smartparking.backend.dto.request.CheckOutRequest;
import com.smartparking.backend.dto.response.SessionResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.entity.ParkingSession.DriverType;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * ParkingSessionService — Core Business Logic.
 *
 * Luồng chính:
 * checkIn() → Tạo session + gán slot + broadcast trạng thái
 * checkOut() → Tính phí + ghi payment + giải phóng slot + broadcast
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
        private final SimpMessagingTemplate messagingTemplate; // WebSocket broadcast
        // Inject thêm fields:
        private final ReservationRepository reservationRepository;
        private final ZoneRepository zoneRepository;
        private final ParkingPassRepository parkingPassRepository;

        // Sửa constructor để nhận thêm 2 tham số:
        public ParkingSessionService(
                        ParkingSessionRepository sessionRepository,
                        ZoneSuggestionService zoneSuggestionService,
                        PricingService pricingService,
                        GateRepository gateRepository,
                        VehicleTypeRepository vehicleTypeRepository,
                        PaymentRepository paymentRepository,
                        SimpMessagingTemplate messagingTemplate,
                        ReservationRepository reservationRepository, // <--- THÊM
                        ZoneRepository zoneRepository, // <--- THÊM
                        ParkingPassRepository parkingPassRepository
        ) {
                this.sessionRepository = sessionRepository;
                this.zoneSuggestionService = zoneSuggestionService;
                this.pricingService = pricingService;
                this.gateRepository = gateRepository;
                this.vehicleTypeRepository = vehicleTypeRepository;
                this.paymentRepository = paymentRepository;
                this.messagingTemplate = messagingTemplate;
                this.reservationRepository = reservationRepository; // <--- THÊM
                this.zoneRepository = zoneRepository; // <--- THÊM
                this.parkingPassRepository = parkingPassRepository;
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // CHECK-IN (UC-04)
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Xử lý xe vào bãi:
         * 1. Kiểm tra biển số không bị trùng session đang ACTIVE
         * 2. Gợi ý zone phù hợp theo loại xe
         * 3. Tạo ParkingSession mới
         * 4. Broadcast thay đổi zone qua WebSocket
         * 5. Trả response hướng dẫn tài xế đến khu
         */
        @Transactional // nếu bất kỳ bước nào lỗi, toàn bộ sẽ rollback (không lưu nửa chừng)
        public SessionResponse checkIn(CheckInRequest request) {
                // Bước 1: Validate — biển số không được có session đang mở (BR-06)
                sessionRepository.findByLicensePlateAndStatus(request.getLicensePlate(), SessionStatus.ACTIVE)
                                .ifPresent(s -> {
                                        throw new BusinessException(
                                                        "Biển số " + request.getLicensePlate()
                                                                        + " đang có phiên gửi xe chưa kết thúc (Mã: " +
                                                                        s.getSessionCode()
                                                                        + "). Vui lòng check-out trước.");
                                });

                // Bước 2: Tìm entity references
                VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                                .orElseThrow(() -> new ResourceNotFoundException("Loại phương tiện không tồn tại"));
                Gate mainGate = gateRepository.findById(request.getGateEntryId())
                                .orElseThrow(() -> new ResourceNotFoundException("Cổng vào không tồn tại"));

                // Bước 3: Gợi ý zone phù hợp theo loại xe và sức chứa còn trống
                // ==================== KHỞI ĐẦU LUỒNG CHECK-IN TÍCH HỢP ĐẶT CHỖ & VÉ THÁNG ====================
                // Kiểm tra xem biển số xe này có đặt trước CONFIRMED nào chưa sử dụng không
                java.util.Optional<Reservation> reservationOpt = reservationRepository
                                .findFirstByLicensePlateAndVehicleTypeAndStatusOrderByReservedFromAsc(
                                                request.getLicensePlate(), vehicleType,
                                                Reservation.ReservationStatus.CONFIRMED);

                // Kiểm tra xem biển số xe này có gói đăng ký (vé tháng/quý/năm) đang hoạt động tại tòa nhà này không
                java.util.Optional<ParkingPass> activePassOpt = parkingPassRepository.findActivePass(
                                request.getLicensePlate(), mainGate.getBuilding().getId(), vehicleType.getId(), LocalDate.now());

                Zone assignedZone;
                DriverType driverType;

                if (activePassOpt.isPresent()) {
                        // Xe có vé tháng/quý/năm hoạt động
                        driverType = DriverType.SUBSCRIBER;

                        // Nếu có lịch đặt trước, hoàn thành lịch đặt và lấy khu đã đặt
                        if (reservationOpt.isPresent()) {
                                Reservation reservation = reservationOpt.get();
                                assignedZone = reservation.getZone();

                                // Giảm số lượng chỗ dành riêng (reservedCount) và tăng đỗ thực tế (currentCount)
                                if (assignedZone.getReservedCount() > 0) {
                                        assignedZone.setReservedCount(assignedZone.getReservedCount() - 1);
                                }
                                assignedZone.setCurrentCount(assignedZone.getCurrentCount() + 1);

                                if (assignedZone.getCurrentCount() + assignedZone.getReservedCount() >= assignedZone.getCapacity()) {
                                        assignedZone.setStatus(Zone.ZoneStatus.FULL);
                                }
                                assignedZone = zoneRepository.save(assignedZone);

                                // Hoàn tất lịch đặt
                                reservation.setStatus(Reservation.ReservationStatus.COMPLETED);
                                reservationRepository.save(reservation);

                                log.info("CHECK-IN VÉ THÁNG (CÓ ĐẶT TRƯỚC): Biển số={}, Lịch hẹn={}, Khu đỗ={}",
                                                request.getLicensePlate(), reservation.getReservationCode(), assignedZone.getZoneCode());
                        } else {
                                // Gợi ý zone bình thường cho subscriber
                                assignedZone = zoneSuggestionService.suggestZone(vehicleType);
                                assignedZone = zoneSuggestionService.enterZone(assignedZone);
                                log.info("CHECK-IN VÉ THÁNG: Biển số={}, Khu đỗ={}", request.getLicensePlate(), assignedZone.getZoneCode());
                        }
                } else if (reservationOpt.isPresent()) {
                        Reservation reservation = reservationOpt.get();
                        assignedZone = reservation.getZone();

                        // Chuyển đổi chỗ: giảm đặt trước (reservedCount) và tăng đỗ thực tế (currentCount)
                        if (assignedZone.getReservedCount() > 0) {
                                assignedZone.setReservedCount(assignedZone.getReservedCount() - 1);
                        }
                        assignedZone.setCurrentCount(assignedZone.getCurrentCount() + 1);

                        // Cập nhật trạng thái Zone nếu đạt capacity tối đa
                        if (assignedZone.getCurrentCount() + assignedZone.getReservedCount() >= assignedZone
                                        .getCapacity()) {
                                assignedZone.setStatus(Zone.ZoneStatus.FULL);
                        }
                        assignedZone = zoneRepository.save(assignedZone);

                        // Chuyển trạng thái lịch đặt thành COMPLETED (đã đỗ xe)
                        reservation.setStatus(Reservation.ReservationStatus.COMPLETED);
                        reservationRepository.save(reservation);

                        // Đánh dấu phiên gửi xe là PRE_BOOKED
                        driverType = DriverType.PRE_BOOKED;
                        log.info("CHECK-IN ĐẶT TRƯỚC: Biển số={}, Lịch hẹn={}, Khu đỗ={}",
                                        request.getLicensePlate(), reservation.getReservationCode(),
                                        assignedZone.getZoneCode());
                } else {
                        // Không có đặt trước -> Tiến hành gợi ý và đỗ xe vãng lai bình thường (WALK_IN)
                        assignedZone = zoneSuggestionService.suggestZone(vehicleType);
                        assignedZone = zoneSuggestionService.enterZone(assignedZone);
                        driverType = DriverType.WALK_IN;
                }
                // ==================== KẾT THÚC LUỒNG CHECK-IN TÍCH HỢP ĐẶT CHỖ & VÉ THÁNG ====================

                // Bước 4: Sinh mã session duy nhất
                String sessionCode = generateSessionCode();

                // Bước 5: Tạo session theo zone
                ParkingSession session = ParkingSession.builder()
                                .sessionCode(sessionCode)
                                .licensePlate(request.getLicensePlate())
                                .vehicleType(vehicleType)
                                .zone(assignedZone)
                                .entryMainGate(mainGate)
                                .entryTime(LocalDateTime.now())
                                .zoneEntryTime(LocalDateTime.now())
                                .qrCode(sessionCode)
                                .status(SessionStatus.ACTIVE)
                                .driverType(driverType) // <--- THÊM DÒNG NÀY ĐỂ LƯU DRIVER_TYPE
                                .notes(request.getNotes())
                                .build();

                session = sessionRepository.save(session);

                broadcastZoneChange(assignedZone);

                log.info("CHECK-IN: plate={}, session={}, zone={}, floor={}",
                                request.getLicensePlate(), sessionCode,
                                assignedZone.getZoneCode(), assignedZone.getFloor().getFloorName());

                return buildSessionResponse(session, assignedZone, null);
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // CHECK-OUT (UC-05)
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Xử lý xe ra bãi:
         * 1. Tìm session (theo sessionId / sessionCode / licensePlate)
         * 2. Tính thời gian + phí
         * 3. Tạo Payment record
         * 4. Đóng session + giải phóng zone
         * 5. Broadcast zone change
         */
        @Transactional
        public SessionResponse checkOut(CheckOutRequest request) {
                // Bước 1: Tìm parking session đang ACTIVE
                ParkingSession session = findActiveSession(request);

                Gate exitGate = gateRepository.findById(request.getGateExitId())
                                .orElseThrow(() -> new ResourceNotFoundException("Cổng ra không tồn tại"));

                // Bước 2: Tính thời gian gửi
                LocalDateTime exitTime = LocalDateTime.now();
                int durationMinutes = (int) ChronoUnit.MINUTES.between(session.getEntryTime(), exitTime);
                if (durationMinutes < 1)
                        durationMinutes = 1; // Tối thiểu 1 phút

                // Bước 3: Tính phí (Miễn phí hoàn toàn nếu là Subscriber - có vé đăng ký hoạt động)
                BigDecimal totalFee = BigDecimal.ZERO;
                if (session.getDriverType() != DriverType.SUBSCRIBER) {
                        UUID buildingId = session.getZone().getFloor().getBuilding().getId();
                        UUID vehicleTypeId = session.getVehicleType().getId();
                        totalFee = pricingService.calculateFee(buildingId, vehicleTypeId, durationMinutes);
                } else {
                        log.info("CHECK-OUT VÉ THÁNG (SUBSCRIBER): Biển số={}, phí = 0 VNĐ", session.getLicensePlate());
                }

                // Bước 4: Cập nhật session
                session.setExitTime(exitTime);
                session.setZoneExitTime(exitTime);
                session.setExitMainGate(exitGate);
                session.setDurationMinutes(durationMinutes);
                session.setTotalFee(totalFee);
                session.setStatus(SessionStatus.COMPLETED);

                // Bước 5: Tạo Payment record
                Payment.PaymentMethod paymentMethod;
                try {
                        paymentMethod = Payment.PaymentMethod.valueOf(
                                        request.getPaymentMethod() != null ? request.getPaymentMethod().toUpperCase()
                                                        : "CASH");
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

                // Bước 6: Giải phóng sức chứa zone
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
         * Xem thông tin phiên gửi xe đang diễn ra — cho Driver hoặc Staff tra cứu.
         */
        public SessionResponse getActiveSession(String licensePlate) {
                ParkingSession session = sessionRepository
                                .findByLicensePlateAndStatus(licensePlate, SessionStatus.ACTIVE)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy phiên gửi xe đang hoạt động cho biển số: "
                                                                + licensePlate));

                // Tính phí tạm tính
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
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                        "Session không tồn tại hoặc đã đóng"));
                }
                if (request.getSessionCode() != null && !request.getSessionCode().isBlank()) {
                        return sessionRepository.findBySessionCode(request.getSessionCode())
                                        .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                        "Không tìm thấy session đang hoạt động với mã: "
                                                                        + request.getSessionCode()));
                }
                if (request.getLicensePlate() != null && !request.getLicensePlate().isBlank()) {
                        return sessionRepository
                                        .findByLicensePlateAndStatus(request.getLicensePlate(), SessionStatus.ACTIVE)
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                        "Không tìm thấy session đang hoạt động với biển số: "
                                                                        + request.getLicensePlate()));
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
}
