package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.CheckInRequest;
import com.smartparking.backend.dto.request.CheckOutRequest;
import com.smartparking.backend.dto.request.CheckInZoneRequest;
import com.smartparking.backend.dto.request.CheckOutZoneRequest;
import com.smartparking.backend.dto.response.SessionResponse;
import com.smartparking.backend.util.LicensePlateUtil;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.smartparking.backend.dto.response.EligibleZoneResponse;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service xử lý các phiên gửi xe (Parking Session) - Nghiệp vụ cốt lõi của hệ
 * thống.
 *
 * Các luồng chính:
 * 1. checkIn(): Xe vào cổng, kiểm tra blacklist/SOS, gợi ý và chiếm chỗ tại
 * Zone, tạo phiên.
 * 2. checkOut(): Xe ra cổng, tính thời gian đỗ, tính phí gửi xe, tạo giao dịch
 * thanh toán, giải phóng chỗ tại Zone.
 * 3. initiateDriverVnPayCheckout() & completeOnlineCheckoutPayment(): Khởi tạo
 * và xử lý thanh toán VNPay trực tuyến.
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
    private final ParkingPassRepository parkingPassRepository;
    private final BlacklistService blacklistService;
    private final EmergencyService emergencyService;
    private final VnPayService vnPayService;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket broadcast
    private final ExceptionLogRepository exceptionLogRepository;
    private final EmergencyEventRepository emergencyEventRepository;
    private final ZoneRepository zoneRepository;
    private final UserLicensePlateRepository userLicensePlateRepository;
    private final UniqueCodeGeneratorService uniqueCodeGeneratorService;

    public ParkingSessionService(
            ParkingSessionRepository sessionRepository,
            ZoneSuggestionService zoneSuggestionService,
            PricingService pricingService,
            GateRepository gateRepository,
            VehicleTypeRepository vehicleTypeRepository,
            PaymentRepository paymentRepository,
            ReservationRepository reservationRepository,
            ParkingPassRepository parkingPassRepository,
            BlacklistService blacklistService,
            EmergencyService emergencyService,
            VnPayService vnPayService,
            SimpMessagingTemplate messagingTemplate,
            ExceptionLogRepository exceptionLogRepository,
            EmergencyEventRepository emergencyEventRepository,
            ZoneRepository zoneRepository,
            UserLicensePlateRepository userLicensePlateRepository,
            UniqueCodeGeneratorService uniqueCodeGeneratorService) {
        this.sessionRepository = sessionRepository;
        this.zoneSuggestionService = zoneSuggestionService;
        this.pricingService = pricingService;
        this.gateRepository = gateRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.parkingPassRepository = parkingPassRepository;
        this.blacklistService = blacklistService;
        this.emergencyService = emergencyService;
        this.vnPayService = vnPayService;
        this.messagingTemplate = messagingTemplate;
        this.exceptionLogRepository = exceptionLogRepository;
        this.emergencyEventRepository = emergencyEventRepository;
        this.zoneRepository = zoneRepository;
        this.userLicensePlateRepository = userLicensePlateRepository;
        this.uniqueCodeGeneratorService = uniqueCodeGeneratorService;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CHECK-IN (UC-04)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Xử lý xe vào bãi (Check-in):
     * 1. Kiểm tra trạng thái khẩn cấp (SOS) - Nếu có thì chặn.
     * 2. Kiểm tra biển số xem có đang đỗ trong bãi không (tránh trùng lặp phiên
     * gửi).
     * 3. Kiểm tra biển số trong danh sách đen (Blacklist) - Nếu có thì phát cảnh
     * báo WebSocket và chặn xe.
     * 4. Kiểm tra mã đặt chỗ trước (Reservation):
     * - Nếu có đặt chỗ: Gán Zone đã đặt trước đó, cập nhật lại số lượng chỗ đặt
     * trước/chỗ hiện tại.
     * - Nếu không đặt chỗ: Tự động gợi ý Zone trống tốt nhất và tăng số lượng xe
     * của Zone đó.
     * 5. Tạo ParkingSession mới ở trạng thái ACTIVE.
     * 6. Broadcast thay đổi sức chứa của Zone qua WebSocket.
     */
    @Transactional
    public SessionResponse checkIn(CheckInRequest request) {
        // Chặn nghiệp vụ nếu bãi đỗ đang trong tình trạng khẩn cấp
        emergencyService.ensureNormalOperation();

        // 1. Phân tích mã đặt chỗ (Reservation Code) trước và thực hiện đối soát chéo
        // loại phương tiện/biển số
        if (request.getReservationCode() != null && !request.getReservationCode().trim().isEmpty()) {
            Reservation reservation = reservationRepository
                    .findByReservationCode(request.getReservationCode().trim().toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Mã đặt giữ chỗ không tồn tại"));

            if (reservation.getStatus() != Reservation.ReservationStatus.CONFIRMED
                    && reservation.getStatus() != Reservation.ReservationStatus.PENDING) {
                throw new BusinessException(
                        "Mã đặt giữ chỗ không khả dụng (Trạng thái: " + reservation.getStatus() + ")");
            }

            // Đối soát biển số xe đặt chỗ với thực tế nhận diện
            if (request.getLicensePlate() != null && !request.getLicensePlate().isBlank()) {
                String resPlate = LicensePlateUtil.normalize(reservation.getLicensePlate());
                String requestPlate = LicensePlateUtil.normalize(request.getLicensePlate());
                if (!resPlate.equals(requestPlate)) {
                    throw new BusinessException("Biển số xe thực tế (" + requestPlate
                            + ") không trùng khớp với biển số trên vé đặt chỗ (" + resPlate + ")");
                }
            } else {
                // Tự động điền biển số từ đặt chỗ (cho xe không biển như xe đạp)
                request.setLicensePlate(reservation.getLicensePlate());
            }

            // Đối soát loại phương tiện thực tế chọn trên màn hình với vé đặt chỗ
            if (request.getVehicleTypeId() != null
                    && !request.getVehicleTypeId().equals(reservation.getVehicleType().getId())) {
                String reqTypeName = vehicleTypeRepository.findById(request.getVehicleTypeId())
                        .map(VehicleType::getName).orElse("Không xác định");
                throw new BusinessException("Loại phương tiện chọn trên màn hình (" + reqTypeName
                        + ") không khớp với loại xe đăng ký trên vé đặt chỗ (" + reservation.getVehicleType().getName()
                        + ")");
            }

            request.setVehicleTypeId(reservation.getVehicleType().getId());
            request.setDriverType("PRE_BOOKED");
        }

        // 2. Phân tích mã vé tháng (Parking Pass Code) trước và thực hiện đối soát chéo
        // loại phương tiện/biển số
        if (request.getParkingPassCode() != null && !request.getParkingPassCode().trim().isEmpty()) {
            ParkingPass pass = parkingPassRepository
                    .findByParkingPassCode(request.getParkingPassCode().trim().toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Vé tháng không tồn tại"));

            if (pass.getStatus() != ParkingPass.PassStatus.ACTIVE) {
                throw new BusinessException("Vé tháng này không hoạt động (Trạng thái: " + pass.getStatus() + ")");
            }

            java.time.LocalDate today = java.time.LocalDate.now();
            if (today.isBefore(pass.getStartDate()) || today.isAfter(pass.getEndDate())) {
                throw new BusinessException("Vé tháng này nằm ngoài hạn sử dụng (" + pass.getStartDate() + " đến "
                        + pass.getEndDate() + ")");
            }

            // Đối soát biển số đăng ký của vé tháng với biển số xe thực tế nhận diện từ
            // camera
            if (request.getLicensePlate() != null && !request.getLicensePlate().isBlank()) {
                String passPlate = LicensePlateUtil.normalize(pass.getLicensePlate());
                String requestPlate = LicensePlateUtil.normalize(request.getLicensePlate());
                if (!passPlate.equals(requestPlate)) {
                    throw new BusinessException("Biển số xe nhận dạng thực tế (" + requestPlate
                            + ") không trùng khớp với biển số xe đăng ký của vé tháng (" + passPlate + ")");
                }
            } else {
                // Tự động điền biển số xe từ vé tháng (như xe đạp)
                request.setLicensePlate(pass.getLicensePlate());
            }

            // Đối soát loại phương tiện thực tế chọn trên màn hình với vé tháng
            if (request.getVehicleTypeId() != null
                    && !request.getVehicleTypeId().equals(pass.getVehicleType().getId())) {
                String reqTypeName = vehicleTypeRepository.findById(request.getVehicleTypeId())
                        .map(VehicleType::getName).orElse("Không xác định");
                throw new BusinessException("Loại phương tiện chọn trên màn hình (" + reqTypeName
                        + ") không khớp với loại xe đăng ký trên vé tháng (" + pass.getVehicleType().getName() + ")");
            }

            request.setVehicleTypeId(pass.getVehicleType().getId());
            request.setDriverType("SUBSCRIBER");
        }

        // Bước 2: Tìm các thông tin liên quan
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Loại phương tiện không tồn tại"));
        Gate mainGate = gateRepository.findById(request.getGateEntryId())
                .orElseThrow(() -> new ResourceNotFoundException("Cổng vào không tồn tại"));
        if (!Boolean.TRUE.equals(mainGate.getIsActive())) {
            throw new BusinessException("Cổng vào " + mainGate.getGateName() + " đang tạm khóa hoặc bảo trì.");
        }

        boolean isBicycle = isBicycleVehicleType(vehicleType);

        if (isBicycle
                && (request.getLicensePlate() == null || request.getLicensePlate().isBlank())
                && request.getReservationCode() != null
                && !request.getReservationCode().isBlank()) {

            Reservation reservationByCode = reservationRepository
                    .findByReservationCode(request.getReservationCode().trim().toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Reservation không tồn tại"));

            if (reservationByCode.getLicensePlate() != null
                    && !reservationByCode.getLicensePlate().isBlank()) {
                request.setLicensePlate(reservationByCode.getLicensePlate());
            }
        }

        String licensePlate;
        if (isBicycle) {
            licensePlate = resolveBicycleIdentifier(request.getLicensePlate());
            request.setLicensePlate(licensePlate);
        } else {
            String rawPlate = request.getLicensePlate();
            if (rawPlate == null || rawPlate.isBlank()) {
                throw new BusinessException("Biển số xe không được để trống");
            }
            String validationError = LicensePlateUtil.getLicensePlateValidationError(rawPlate, vehicleType.getName());
            if (validationError != null) {
                throw new BusinessException(validationError);
            }
            licensePlate = LicensePlateUtil.normalize(rawPlate);
            request.setLicensePlate(licensePlate);
        }

        // Bước 3: Validate — biển số không được có session đang hoạt động của cùng loại
        // phương tiện
        List<ParkingSession> activeSessionsForPlate = sessionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                .filter(s -> LicensePlateUtil.normalize(s.getLicensePlate()).equals(licensePlate))
                .toList();

        for (ParkingSession s : activeSessionsForPlate) {
            if (s.getVehicleType().getId().equals(vehicleType.getId())) {
                throw new BusinessException(
                        "Biển số " + licensePlate + " (" + vehicleType.getName()
                                + ") đang có phiên gửi xe chưa kết thúc (Mã: " +
                                s.getSessionCode() + "). Vui lòng check-out trước.");
            }
        }

        // Bước 4: Kiểm tra Blacklist (chỉ kiểm tra nếu không phải xe đạp)
        if (!isBicycle) {
            blacklistService.findActiveByPlate(licensePlate).ifPresent(blacklistPlate -> {
                blacklistService.alertBlacklistAttempt(licensePlate, blacklistPlate, mainGate);
                throw new BusinessException("Biển số " + licensePlate
                        + " đang nằm trong blacklist. Lý do: " + blacklistPlate.getReason());
            });
        }

        // Tự động kiểm tra Vé tháng (ParkingPass) hoạt động tại Tòa nhà này
        boolean hasValidPass = false;
        List<ParkingPass> activePasses = parkingPassRepository.findByLicensePlateAndBuildingAndStatus(
                request.getLicensePlate(), mainGate.getBuilding(), ParkingPass.PassStatus.ACTIVE);
        java.time.LocalDate today = java.time.LocalDate.now();
        ParkingPass matchedPass = null;
        boolean hasAnyDateValidPass = false;
        String passVehicleTypeName = null;
        for (ParkingPass pass : activePasses) {
            if (!today.isBefore(pass.getStartDate()) && !today.isAfter(pass.getEndDate())) {
                hasAnyDateValidPass = true;
                passVehicleTypeName = pass.getVehicleType().getName();
                if (pass.getVehicleType().getId().equals(vehicleType.getId())) {
                    matchedPass = pass;
                    break;
                }
            }
        }
        if (matchedPass != null) {
            hasValidPass = true;
        } else if (hasAnyDateValidPass) {
            throw new BusinessException(
                    "Cảnh báo lệch pha: Loại phương tiện nhận diện thực tế (" + vehicleType.getName()
                            + ") không khớp với loại xe đã đăng ký của vé tháng (" + passVehicleTypeName + ").");
        }

        Reservation reservation = null;
        Zone assignedZone;

        // Bước 4: Xử lý gán Zone đỗ xe
        if (request.getReservationCode() != null && !request.getReservationCode().trim().isEmpty()) {
            // Trường hợp có đặt chỗ trước (FE truyền mã trực tiếp)
            reservation = reservationRepository.findByReservationCode(request.getReservationCode().trim().toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Reservation không tồn tại"));
            if (reservation.getStatus() != Reservation.ReservationStatus.CONFIRMED
                    && reservation.getStatus() != Reservation.ReservationStatus.PENDING) {
                throw new BusinessException("Reservation không còn hiệu lực");
            }
            String reservedPlate = reservation.getLicensePlate().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            String requestPlate = request.getLicensePlate().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            if (!reservedPlate.equals(requestPlate)) {
                throw new BusinessException("Biển số không khớp với reservation");
            }
            if (!reservation.getVehicleType().getId().equals(vehicleType.getId())) {
                throw new BusinessException(
                        "Cảnh báo lệch pha: Loại phương tiện chọn trên màn hình (" + vehicleType.getName()
                                + ") không khớp với loại xe đăng ký trên vé đặt chỗ ("
                                + reservation.getVehicleType().getName() + ").");
            }
        } else {
            // Tự động khớp reservation theo biển số xe
            LocalDateTime now = LocalDateTime.now();
            String normalizedPlate = LicensePlateUtil.normalize(request.getLicensePlate());

            List<Reservation> activeReservations = reservationRepository.findByLicensePlateAndStatusIn(
                    normalizedPlate,
                    List.of(Reservation.ReservationStatus.CONFIRMED, Reservation.ReservationStatus.PENDING));

            Reservation matchedReservation = null;
            boolean hasAnyTimeValidRes = false;
            String resVehicleTypeName = null;
            for (Reservation res : activeReservations) {
                if (res.getZone().getFloor().getBuilding().getId().equals(mainGate.getBuilding().getId())) {
                    // Xem khung giờ có hợp lệ không: vào sớm tối đa 60 phút và trước khi hết giờ
                    // đặt
                    LocalDateTime allowedFrom = res.getReservedFrom().minusMinutes(60);
                    LocalDateTime allowedTo = res.getReservedTo();

                    if (!now.isBefore(allowedFrom) && !now.isAfter(allowedTo)) {
                        hasAnyTimeValidRes = true;
                        resVehicleTypeName = res.getVehicleType().getName();
                        if (res.getVehicleType().getId().equals(vehicleType.getId())) {
                            matchedReservation = res;
                            break;
                        }
                    }
                }
            }
            if (matchedReservation != null) {
                reservation = matchedReservation;
            } else if (hasAnyTimeValidRes) {
                throw new BusinessException(
                        "Cảnh báo lệch pha: Loại phương tiện nhận diện thực tế (" + vehicleType.getName()
                                + ") không khớp với loại xe đã đăng ký đặt chỗ (" + resVehicleTypeName + ").");
            }
        }

        // Bước 4: Xử lý gán Zone đỗ xe
        if (request.getZoneId() != null) {
            assignedZone = zoneRepository.findById(request.getZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Phân khu được chọn không tồn tại"));
        } else if (reservation != null) {
            assignedZone = reservation.getZone();
        } else {
            assignedZone = zoneSuggestionService.suggestZone(vehicleType);
        }

        // Xác định phân loại Driver
        ParkingSession.DriverType driverType = ParkingSession.DriverType.WALK_IN;
        if (hasValidPass) {
            driverType = ParkingSession.DriverType.SUBSCRIBER;
        } else if (reservation != null) {
            driverType = ParkingSession.DriverType.PRE_BOOKED;
        } else if (request.getDriverType() != null && !request.getDriverType().trim().isEmpty()) {
            try {
                driverType = ParkingSession.DriverType.valueOf(request.getDriverType().toUpperCase());
            } catch (IllegalArgumentException e) {
                // driverType đã có giá trị mặc định là WALK_IN
            }
        }

        // Nếu chỉ là tìm kiếm/xem trước thông tin đỗ (isPreview = true)
        if (Boolean.TRUE.equals(request.getIsPreview())) {
            List<EligibleZoneResponse> eligibleZones = buildEligibleZonesForVehicleType(vehicleType.getId());
            String pType = null;
            String custName = null;
            if (hasValidPass && !activePasses.isEmpty()) {
                ParkingPass pass = activePasses.get(0);
                pType = pass.getPassType() != null ? pass.getPassType().name() : null;
                custName = pass.getUser() != null ? pass.getUser().getFullName() : "Thuê bao tháng";
            }
            return SessionResponse.builder()
                    .licensePlate(licensePlate)
                    .vehicleTypeId(vehicleType.getId())
                    .vehicleType(vehicleType.getName())
                    .driverType(driverType)
                    .passType(pType)
                    .customerName(custName)
                    .reservationCode(reservation != null ? reservation.getReservationCode() : null)
                    .zoneId(assignedZone.getId())
                    .zoneCode(assignedZone.getZoneCode())
                    .zoneName(assignedZone.getZoneName())
                    .floorName(assignedZone.getFloor().getFloorName())
                    .buildingId(mainGate.getBuilding().getId())
                    .buildingName(mainGate.getBuilding().getName())
                    .entryMainGateCode(mainGate.getGateCode())
                    .entryMainGateName(mainGate.getGateName())
                    .entryTime(LocalDateTime.now())
                    .status(SessionStatus.ACTIVE)
                    .eligibleZones(eligibleZones)
                    .notes(request.getNotes())
                    .build();
        }

        // Thực hiện lưu thật vào DB (nếu không phải preview)
        if (request.getZoneId() != null) {
            // Đổi chỗ từ reservedCount sang currentCount nếu là zone của reservation
            if (reservation != null && reservation.getZone().getId().equals(assignedZone.getId())) {
                if (assignedZone.getReservedCount() > 0) {
                    assignedZone.setReservedCount(assignedZone.getReservedCount() - 1);
                }
            }
            assignedZone.setCurrentCount(assignedZone.getCurrentCount() + 1);
        } else if (reservation != null) {
            // Đổi chỗ từ reservedCount sang currentCount
            if (assignedZone.getReservedCount() > 0) {
                assignedZone.setReservedCount(assignedZone.getReservedCount() - 1);
            }
            assignedZone.setCurrentCount(assignedZone.getCurrentCount() + 1);
        } else {
            // Cộng trực tiếp currentCount của Zone được gợi ý
            assignedZone.setCurrentCount(assignedZone.getCurrentCount() + 1);
        }

        // Cập nhật trạng thái Zone nếu đầy
        if (assignedZone.getCurrentCount() + assignedZone.getReservedCount() >= assignedZone.getCapacity()) {
            assignedZone.setStatus(Zone.ZoneStatus.FULL);
        }
        zoneRepository.save(assignedZone);

        // Bước 5: Sinh mã session duy nhất
        String sessionCode = generateSessionCode();

        // Bước 6: Lưu phiên đỗ xe
        ParkingSession session = ParkingSession.builder()
                .sessionCode(sessionCode)
                .licensePlate(request.getLicensePlate())
                .vehicleType(vehicleType)
                .zone(assignedZone)
                .driverType(driverType)
                .entryMainGate(mainGate)
                .entryTime(LocalDateTime.now())
                .zoneEntryTime(null) // Chưa quét vào zone
                .status(SessionStatus.ACTIVE)
                .notes(request.getNotes())
                .build();
        session = sessionRepository.save(session);
        if (reservation != null) {
            reservation.setStatus(Reservation.ReservationStatus.COMPLETED);
            reservationRepository.save(reservation);
        }

        log.info("CHECK-IN STAGE 1: plate={}, session={}, suggestedZone={}, floor={}",
                request.getLicensePlate(), sessionCode,
                assignedZone.getZoneCode(), assignedZone.getFloor().getFloorName());

        return buildSessionResponse(session, assignedZone, null);
    }

    /**
     * Cập nhật URL ảnh (từ Cloudinary) cho Session (sau khi Check-in/Check-out
     * thành công).
     */
    @Transactional
    public void updateSessionImages(UUID sessionId, String plateUrl, String faceUrl, boolean isEntry) {
        ParkingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên gửi xe"));

        if (isEntry) {
            if (plateUrl != null && !plateUrl.isEmpty())
                session.setEntryPlateImageUrl(plateUrl);
            if (faceUrl != null && !faceUrl.isEmpty())
                session.setEntryFaceImageUrl(faceUrl);
        } else {
            if (plateUrl != null && !plateUrl.isEmpty())
                session.setExitPlateImageUrl(plateUrl);
            if (faceUrl != null && !faceUrl.isEmpty())
                session.setExitFaceImageUrl(faceUrl);
        }

        sessionRepository.save(session);
    }

    /**
     * Xử lý xe đi vào Zone cụ thể (Check-in lần 2):
     * 1. Tìm phiên gửi xe đang hoạt động bằng Session Code hoặc biển số.
     * 2. Kiểm tra nếu đã check-in vào zone trước đó.
     * 3. Xác thực cổng phụ và zone tương ứng của cổng phụ đó.
     * 4. Kiểm tra loại phương tiện và sức chứa của Zone.
     * 5. Nếu đi sai Zone gợi ý:
     * - Nếu là khách đặt trước (PRE_BOOKED) -> Chặn tuyệt đối.
     * - Nếu là vãng lai/vé tháng -> Kiểm tra giới hạn 3 lần lỗi/30 ngày. Nếu vượt
     * quá -> Chặn.
     * - Nếu hợp lệ -> Ghi log lỗi WRONG_ZONE và tự động điều chuyển Zone.
     * 6. Cập nhật số xe đỗ thực tế của Zone.
     * 7. Cập nhật cổng phụ vào và thời gian vào Zone của phiên gửi.
     */
    @Transactional
    public SessionResponse checkInZone(CheckInZoneRequest request) {
        // 1. Tìm phiên gửi xe đang hoạt động
        ParkingSession session = findActiveSessionForZoneEntry(request.getSessionCode());

        // 2. Kiểm tra nếu xe đã chính thức check-in vào zone rồi
        if (session.getZoneEntryTime() != null) {
            throw new BusinessException("Xe " + session.getLicensePlate() + " đã check-in vào khu vực đỗ trước đó.");
        }

        // 3. Tìm cổng phụ (Zone Gate)
        Gate zoneGate = gateRepository.findById(request.getGateEntryId())
                .orElseThrow(() -> new ResourceNotFoundException("Cổng phụ không tồn tại"));
        if (!Boolean.TRUE.equals(zoneGate.getIsActive())) {
            throw new BusinessException("Cổng phụ " + zoneGate.getGateName() + " đang tạm khóa hoặc bảo trì.");
        }

        if (zoneGate.getGateType() != Gate.GateType.ZONE_ENTRY && zoneGate.getGateType() != Gate.GateType.ZONE_BOTH) {
            throw new BusinessException("Cổng " + zoneGate.getGateName() + " không phải là cổng vào Zone.");
        }

        Zone targetZone = zoneGate.getZone();
        if (targetZone == null) {
            throw new BusinessException(
                    "Cổng phụ " + zoneGate.getGateName() + " chưa được liên kết với Zone đỗ xe cụ thể.");
        }

        // 4. Kiểm tra loại xe có khớp với Zone không
        if (!targetZone.getVehicleType().getId().equals(session.getVehicleType().getId())) {
            throw new BusinessException("Sai làn! Cổng này dành cho " + targetZone.getVehicleType().getName()
                    + ", xe của bạn là " + session.getVehicleType().getName() + ".");
        }

        Zone suggestedZone = session.getZone();
        boolean isWrongZone = !targetZone.getId().equals(suggestedZone.getId());

        // 5. Nếu đi sai Zone gợi ý -> Từ chối thẳng, không cho vào
        if (isWrongZone) {
            throw new BusinessException("Từ chối vào cổng: Sai Zone đỗ xe chỉ định! Zone đỗ xe trên vé của bạn là "
                    + suggestedZone.getZoneName() + ", vui lòng di chuyển sang đúng cổng Zone này.");
        }

        // 7. Cập nhật Session
        session.setEntryZoneGate(zoneGate);
        session.setZoneEntryTime(LocalDateTime.now());
        ParkingSession savedSession = sessionRepository.save(session);

        // 8. Phát WebSocket cập nhật sức chứa
        broadcastZoneChange(targetZone);

        log.info("CHECK-IN STAGE 2 (Zone Entered): plate={}, session={}, actualZone={}",
                session.getLicensePlate(), session.getSessionCode(), targetZone.getZoneCode());

        SessionResponse response = buildSessionResponse(savedSession, targetZone, null);
        response.setWrongZoneCount(0);
        response.setWrongZoneDetected(false);
        response.setGuideMessage("Đã đỗ đúng Zone gợi ý. Barie mở.");
        return response;
    }

    @Transactional
    public SessionResponse checkOutZone(CheckOutZoneRequest request) {
        // 1. Tìm phiên gửi xe đang hoạt động
        ParkingSession session = findActiveSessionForZoneEntry(request.getSessionCode());

        // 2. Kiểm tra nếu xe chưa check-in vào zone nào
        if (session.getZoneEntryTime() == null) {
            throw new BusinessException("Xe " + session.getLicensePlate() + " chưa check-in vào khu vực đỗ.");
        }

        // 3. Kiểm tra nếu xe đã check-out ra khỏi zone rồi
        if (session.getZoneExitTime() != null) {
            throw new BusinessException(
                    "Xe " + session.getLicensePlate() + " đã check-out ra khỏi khu vực đỗ trước đó.");
        }

        // 4. Tìm cổng phụ ra (Zone Exit Gate)
        Gate zoneGate = gateRepository.findById(request.getGateExitId())
                .orElseThrow(() -> new ResourceNotFoundException("Cổng phụ ra không tồn tại"));
        if (!Boolean.TRUE.equals(zoneGate.getIsActive())) {
            throw new BusinessException("Cổng phụ " + zoneGate.getGateName() + " đang tạm khóa hoặc bảo trì.");
        }

        if (zoneGate.getGateType() != Gate.GateType.ZONE_EXIT && zoneGate.getGateType() != Gate.GateType.ZONE_BOTH) {
            throw new BusinessException("Cổng " + zoneGate.getGateName() + " không phải là cổng ra Zone.");
        }

        Zone currentZone = session.getZone();
        if (currentZone == null) {
            throw new BusinessException("Không tìm thấy thông tin phân khu đỗ xe cho phiên gửi này.");
        }

        // 5. Cập nhật Session
        session.setExitZoneGate(zoneGate);
        session.setZoneExitTime(LocalDateTime.now());
        ParkingSession savedSession = sessionRepository.save(session);

        // 6. Giải phóng vị trí đỗ trong Zone (Giảm currentCount)
        Zone updatedZone = zoneSuggestionService.exitZone(currentZone);

        // 7. Phát WebSocket cập nhật sức chứa
        broadcastZoneChange(updatedZone);

        log.info("CHECK-OUT STAGE 2 (Zone Exited): plate={}, session={}, actualZone={}",
                session.getLicensePlate(), session.getSessionCode(), currentZone.getZoneCode());

        SessionResponse response = buildSessionResponse(savedSession, currentZone, null);
        response.setGuideMessage("Đã check-out ra khỏi Zone thành công. Barie mở.");
        return response;
    }

    private ParkingSession findActiveSessionForZoneEntry(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Mã vé hoặc biển số không được để trống");
        }
        String cleanCode = code.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();

        // 1. Tìm bằng sessionCode trực tiếp
        Optional<ParkingSession> opt = sessionRepository.findBySessionCode(code.trim())
                .filter(s -> s.getStatus() == SessionStatus.ACTIVE);
        if (opt.isPresent())
            return opt.get();

        // 2. Tìm bằng biển số
        return sessionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                .filter(s -> {
                    String normPlate = s.getLicensePlate().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
                    return normPlate.equals(cleanCode);
                })
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiên gửi xe đang hoạt động với mã hoặc biển số: " + code));
    }

    /*
     * //
     * ═══════════════════════════════════════════════════════════════════════════
     * // CHECK-OUT (UC-05)
     * //
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * /**
     * Xử lý xe ra bãi (Check-out trực tiếp/Tại quầy):
     * 1. Tìm phiên gửi xe đang hoạt động.
     * 2. Tính toán số phút gửi và áp dụng bảng giá tương ứng của tòa nhà để tính
     * tổng phí gửi xe.
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
        if (!Boolean.TRUE.equals(exitGate.getIsActive())) {
            throw new BusinessException("Cổng ra " + exitGate.getGateName() + " đang tạm khóa hoặc bảo trì.");
        }

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

        // Bước 6: Giải phóng zone (nếu chưa giải phóng ở cổng phụ ra zone)
        Zone zone = session.getZone();
        if (zone != null && session.getExitZoneGate() == null) {
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
     * Tra cứu thông tin phiên gửi xe đang hoạt động dựa vào biển số xe hoặc mã vé
     * (sessionCode).
     * Tính toán và hiển thị phí tạm tính dựa trên số phút thực tế đã gửi.
     */
    public SessionResponse getActiveSession(String licensePlate, String code, UUID vehicleTypeId) {
        String normalizedPlate = (licensePlate != null && !licensePlate.isBlank())
                ? LicensePlateUtil.normalize(licensePlate)
                : "";
        String cleanCode = (code != null && !code.isBlank()) ? code.trim().toUpperCase() : null;

        ParkingSession session;

        // Nếu có truyền mã vé (sessionCode) - Ưu tiên tìm trực tiếp bằng mã vé
        if (cleanCode != null) {
            session = sessionRepository.findBySessionCode(cleanCode)
                    .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy phiên gửi xe đang hoạt động với mã vé: " + code));

            // Nếu xe có biển (độ dài biển số nhận diện thực tế không trống), thực hiện đối
            // soát biển số xe
            if (!normalizedPlate.isEmpty()) {
                String normSessionPlate = LicensePlateUtil.normalize(session.getLicensePlate());
                if (!normSessionPlate.equals(normalizedPlate)) {
                    throw new BusinessException("Cảnh báo lệch pha: Mã vé khớp nhưng biển số đăng ký của vé ("
                            + session.getLicensePlate() + ") không khớp với biển số nhận diện thực tế (" + licensePlate
                            + ").");
                }
            }

            // Đối soát loại phương tiện nếu được gửi kèm
            if (vehicleTypeId != null && !session.getVehicleType().getId().equals(vehicleTypeId)) {
                String reqTypeName = vehicleTypeRepository.findById(vehicleTypeId)
                        .map(VehicleType::getName).orElse("Không xác định");
                throw new BusinessException("Cảnh báo lệch pha: Mã vé khớp nhưng loại xe của vé ("
                        + session.getVehicleType().getName() + ") không khớp với loại xe thực tế (" + reqTypeName
                        + ").");
            }
        } else {
            // Không quét QR, không gửi kèm mã vé -> Tìm bằng biển số và loại xe
            if (normalizedPlate.isEmpty()) {
                throw new BusinessException("Vui lòng nhập biển số xe hoặc quét mã vé để tìm kiếm.");
            }

            List<ParkingSession> activeSessions = sessionRepository.findAll().stream()
                    .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                    .filter(s -> LicensePlateUtil.normalize(s.getLicensePlate()).equals(normalizedPlate))
                    .toList();

            if (activeSessions.isEmpty()) {
                throw new ResourceNotFoundException(
                        "Không tìm thấy phiên gửi xe đang hoạt động với biển số: " + licensePlate);
            }

            if (vehicleTypeId != null) {
                session = activeSessions.stream()
                        .filter(s -> s.getVehicleType().getId().equals(vehicleTypeId))
                        .findFirst()
                        .orElseThrow(() -> {
                            String reqTypeName = vehicleTypeRepository.findById(vehicleTypeId)
                                    .map(VehicleType::getName).orElse("Không xác định");
                            return new BusinessException("Biển số " + licensePlate
                                    + " đang có phiên đỗ nhưng không khớp với loại xe được chọn (" + reqTypeName
                                    + ").");
                        });
            } else {
                if (activeSessions.size() > 1) {
                    throw new BusinessException("Phát hiện nhiều hơn một phiên gửi xe đang hoạt động cho biển số "
                            + licensePlate + ". Vui lòng chọn loại xe để xác định chính xác.");
                }
                session = activeSessions.get(0);
            }
        }

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

    public String findPlateByCodeForDriver(String code) {
        String cleanCode = code.trim().toUpperCase();
        return sessionRepository.findBySessionCode(cleanCode)
                .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                .map(ParkingSession::getLicensePlate)
                .map(LicensePlateUtil::normalize)
                .orElse("");
    }

    /**
     * Khởi tạo liên kết thanh toán trực tuyến qua VNPay cho Driver tự check-out:
     * 1. Tính toán phí tạm tính đến thời điểm hiện tại.
     * 2. Tạo hoặc tái sử dụng bản ghi Payment trạng thái PENDING.
     * 3. Gọi dịch vụ VNPay để sinh URL cổng thanh toán Sandbox.
     */
    @Transactional
    public Map<String, Object> initiateDriverVnPayCheckout(Map<String, String> body, HttpServletRequest request) {
        // emergencyService.ensureNormalOperation();

        CheckOutRequest lookup = new CheckOutRequest();
        lookup.setSessionCode(body.get("sessionCode"));
        lookup.setLicensePlate(body.get("licensePlate"));
        ParkingSession session = findActiveSession(lookup);

        LocalDateTime exitTime = LocalDateTime.now();
        int durationMinutes = calculateDurationMinutes(session, exitTime);
        BigDecimal calculatedFee = calculateSessionFee(session, durationMinutes);

        // Nếu phí = 0 (trong khoảng miễn phí) → không cần thanh toán online, checkout
        // tại quầy
        if (calculatedFee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Phí gửi xe = 0đ (trong thời gian miễn phí). Vui lòng check-out trực tiếp tại quầy.");
        }

        final BigDecimal totalFee = calculatedFee;

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

        String orderInfo = "Thanh toan checkout phien " + session.getSessionCode() + " bien so "
                + session.getLicensePlate();
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
        if (zone != null && session.getExitZoneGate() == null) {
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
                "exitGate", exitGate != null ? exitGate.getGateName() : "Cổng chính");
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
     * Tìm session đang ACTIVE bằng 1 trong 3 tiêu chí: sessionId, sessionCode, hoặc
     * biển số xe.
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

    /**
     * Sinh mã session duy nhất: PS + yyyyMMdd + 3 ký tự ngẫu nhiên.
     * Ví dụ: PS20260516-A3F
     */
    private String generateSessionCode() {
        return uniqueCodeGeneratorService.generateSessionCode();
    }

    /**
     * Build response DTO từ entity.
     */
    private SessionResponse buildSessionResponse(ParkingSession session, Zone zone, String paymentStatus) {
        VehicleType vehicleType = session.getVehicleType();

        Building building = null;
        if (zone != null && zone.getFloor() != null) {
            building = zone.getFloor().getBuilding();
        } else if (session.getEntryMainGate() != null) {
            building = session.getEntryMainGate().getBuilding();
        }

        SessionResponse.SessionResponseBuilder builder = SessionResponse.builder()
                .sessionId(session.getId())
                .sessionCode(session.getSessionCode())
                .sessionCreatedAt(session.getCreatedAt())
                .licensePlate(LicensePlateUtil.normalize(session.getLicensePlate()))
                .vehicleTypeId(vehicleType != null ? vehicleType.getId() : null)
                .vehicleType(vehicleType != null ? vehicleType.getName() : null)
                .entryTime(session.getEntryTime())
                .exitTime(session.getExitTime())
                .zoneEntryTime(session.getZoneEntryTime())
                .zoneExitTime(session.getZoneExitTime())
                .durationMinutes(session.getDurationMinutes())
                .totalFee(session.getTotalFee())
                .status(session.getStatus())
                .driverType(session.getDriverType())
                .paymentStatus(paymentStatus)
                .entryPlateImageUrl(session.getEntryPlateImageUrl())
                .entryFaceImageUrl(session.getEntryFaceImageUrl())
                .exitPlateImageUrl(session.getExitPlateImageUrl())
                .exitFaceImageUrl(session.getExitFaceImageUrl())
                .entryMainGateCode(session.getEntryMainGate() != null ? session.getEntryMainGate().getGateCode() : null)
                .entryMainGateName(session.getEntryMainGate() != null ? session.getEntryMainGate().getGateName() : null)
                .exitMainGateCode(session.getExitMainGate() != null ? session.getExitMainGate().getGateCode() : null)
                .exitMainGateName(session.getExitMainGate() != null ? session.getExitMainGate().getGateName() : null)
                .entryZoneGateCode(session.getEntryZoneGate() != null ? session.getEntryZoneGate().getGateCode() : null)
                .entryZoneGateName(session.getEntryZoneGate() != null ? session.getEntryZoneGate().getGateName() : null)
                .exitZoneGateCode(session.getExitZoneGate() != null ? session.getExitZoneGate().getGateCode() : null)
                .exitZoneGateName(session.getExitZoneGate() != null ? session.getExitZoneGate().getGateName() : null)
                .notes(session.getNotes());

        if (zone != null) {
            builder.zoneId(zone.getId())
                    .zoneCode(zone.getZoneCode())
                    .floorName(zone.getFloor() != null ? zone.getFloor().getFloorName() : null)
                    .zoneName(zone.getZoneName())
                    .guideMessage(String.format(
                            "Vui lòng đến Tầng %s - %s",
                            zone.getFloor() != null ? zone.getFloor().getFloorName() : "--",
                            zone.getZoneName()));
        }

        if (building != null) {
            builder.buildingId(building.getId())
                    .buildingName(building.getName())
                    .buildingAddress(building.getAddress());
        }

        String customerName = null;
        String passType = null;

        if (building != null) {
            List<ParkingPass> activePasses = parkingPassRepository.findByLicensePlateAndBuildingAndStatus(
                    session.getLicensePlate(),
                    building,
                    ParkingPass.PassStatus.ACTIVE);

            java.time.LocalDate today = java.time.LocalDate.now();

            for (ParkingPass pass : activePasses) {
                if (!today.isBefore(pass.getStartDate()) && !today.isAfter(pass.getEndDate())) {
                    if (pass.getUser() != null) {
                        customerName = pass.getUser().getFullName();
                    }

                    if (pass.getPassType() != null) {
                        passType = pass.getPassType().name();
                    }

                    break;
                }
            }
        }

        if (customerName == null) {
            String normalizedSessionPlate = LicensePlateUtil.normalize(session.getLicensePlate());

            List<Reservation> reservations = reservationRepository.findByLicensePlateAndStatusIn(
                    normalizedSessionPlate,
                    List.of(
                            Reservation.ReservationStatus.COMPLETED,
                            Reservation.ReservationStatus.CONFIRMED,
                            Reservation.ReservationStatus.PENDING));

            if (!reservations.isEmpty() && reservations.get(0).getUser() != null) {
                customerName = reservations.get(0).getUser().getFullName();
            }
        }

        if (customerName == null) {
            List<UserLicensePlate> plateUsers = userLicensePlateRepository
                    .findByLicensePlate(session.getLicensePlate());

            if (!plateUsers.isEmpty() && plateUsers.get(0).getUser() != null) {
                customerName = plateUsers.get(0).getUser().getFullName();
            }
        }

        builder.customerName(customerName)
                .passType(passType);

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

    /**
     * Lấy lịch sử gửi xe theo biển số — dùng cho Driver xem history.
     */
    public List<SessionResponse> getSessionHistory(String licensePlate) {
        String normalizedInput = LicensePlateUtil.normalize(licensePlate);

        List<ParkingSession> sessions = sessionRepository.findAll().stream()
                .filter(session -> LicensePlateUtil.normalize(session.getLicensePlate()).equals(normalizedInput))
                .sorted((a, b) -> {
                    LocalDateTime at = a.getEntryTime();
                    LocalDateTime bt = b.getEntryTime();

                    if (at == null && bt == null)
                        return 0;
                    if (at == null)
                        return 1;
                    if (bt == null)
                        return -1;

                    return bt.compareTo(at);
                })
                .toList();

        return sessions.stream().map(session -> {
            Zone zone = session.getZone();
            String paymentStatus = session.getStatus() == SessionStatus.COMPLETED ? "PAID" : "PENDING";
            return buildSessionResponse(session, zone, paymentStatus);
        }).toList();
    }

    /**
     * Lấy toàn bộ danh sách lịch sử tất cả các phiên gửi xe cho
     * Staff/Manager/Admin.
     */
    public List<SessionResponse> getAllSessions() {
        return sessionRepository
                .findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        "entryTime"))
                .stream()
                .map(session -> {
                    Zone zone = session.getZone();
                    String paymentStatus = session.getStatus() == SessionStatus.COMPLETED ? "PAID" : "PENDING";
                    SessionResponse response = buildSessionResponse(session, zone, paymentStatus);
                    if (session.getStatus() == ParkingSession.SessionStatus.ACTIVE) {
                        int currentMinutes = (int) java.time.temporal.ChronoUnit.MINUTES.between(session.getEntryTime(),
                                LocalDateTime.now());
                        response.setDurationMinutes(currentMinutes);
                        if (zone != null && zone.getFloor() != null && zone.getFloor().getBuilding() != null) {
                            try {
                                BigDecimal estimatedFee = pricingService.estimateFee(
                                        zone.getFloor().getBuilding().getId(),
                                        session.getVehicleType().getId(),
                                        currentMinutes);
                                response.setTotalFee(estimatedFee);
                            } catch (Exception e) {
                                log.warn("Failed to estimate fee for session {}: {}", session.getSessionCode(),
                                        e.getMessage());
                            }
                        }
                    }
                    return response;
                })
                .toList();
    }

    public Page<SessionResponse> searchSessions(String licensePlate, ParkingSession.SessionStatus status, int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "entryTime"));
        String plateParam = (licensePlate == null || licensePlate.trim().isEmpty()) ? null
                : "%" + licensePlate.trim().toUpperCase() + "%";
        Page<ParkingSession> sessions = sessionRepository.searchSessions(plateParam, status, pageable);
        return sessions.map(session -> {
            Zone zone = session.getZone();
            String paymentStatus = session.getStatus() == SessionStatus.COMPLETED ? "PAID" : "PENDING";
            SessionResponse response = buildSessionResponse(session, zone, paymentStatus);
            if (session.getStatus() == ParkingSession.SessionStatus.ACTIVE) {
                int currentMinutes = (int) java.time.temporal.ChronoUnit.MINUTES.between(session.getEntryTime(),
                        LocalDateTime.now());
                response.setDurationMinutes(currentMinutes);
                if (zone != null && zone.getFloor() != null && zone.getFloor().getBuilding() != null) {
                    try {
                        BigDecimal estimatedFee = pricingService.estimateFee(
                                zone.getFloor().getBuilding().getId(),
                                session.getVehicleType().getId(),
                                currentMinutes);
                        response.setTotalFee(estimatedFee);
                    } catch (Exception e) {
                        log.warn("Failed to estimate fee for session {}: {}", session.getSessionCode(), e.getMessage());
                    }
                }
            }
            return response;
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardStats() {
        LocalDateTime startOfDay = LocalDateTime.now().with(java.time.LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.now().with(java.time.LocalTime.MAX);

        BigDecimal todayRevenue = paymentRepository.sumCompletedPaymentsBetween(startOfDay, endOfDay);
        long activeSessions = sessionRepository.countActiveSessions();
        long completedSessionsToday = sessionRepository.countSessionsCompletedBetween(startOfDay, endOfDay);
        long securityIncidentsToday = exceptionLogRepository.countExceptionsBetween(startOfDay, endOfDay);
        boolean activeEmergency = emergencyEventRepository.existsActiveEmergency();
        long todayCheckIn = sessionRepository.countByEntryTimeBetween(startOfDay, endOfDay);

        List<Zone> zones = zoneRepository.findAll();
        int totalCapacity = 0;
        int totalOccupied = 0;
        int totalReserved = 0;
        for (Zone z : zones) {
            totalCapacity += z.getCapacity() != null ? z.getCapacity() : 0;
            totalOccupied += z.getCurrentCount() != null ? z.getCurrentCount() : 0;
            totalReserved += z.getReservedCount() != null ? z.getReservedCount() : 0;
        }
        int totalOccupiedAndReserved = totalOccupied + totalReserved;
        int totalAvailable = Math.max(0, totalCapacity - totalOccupiedAndReserved);
        double occupancyPercent = totalCapacity > 0 ? (double) totalOccupiedAndReserved / totalCapacity * 100 : 0.0;
        occupancyPercent = Math.round(occupancyPercent * 10.0) / 10.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("todayRevenue", todayRevenue != null ? todayRevenue : BigDecimal.ZERO);
        stats.put("activeSessions", activeSessions);
        stats.put("occupancyPercent", occupancyPercent);
        stats.put("completedSessionsToday", completedSessionsToday);
        stats.put("securityIncidentsToday", securityIncidentsToday);
        stats.put("activeEmergency", activeEmergency);
        stats.put("availableSlots", totalAvailable);
        stats.put("occupiedSlots", totalOccupied);
        stats.put("reservedSlots", totalReserved);
        stats.put("todayCheckIn", todayCheckIn);
        stats.put("todayCheckOut", completedSessionsToday);

        return stats;
    }

    /**
     * Lấy danh sách phân khu (Zone) khả dụng để thay đổi gợi ý đỗ xe.
     */
    public List<EligibleZoneResponse> getEligibleZones(UUID sessionId) {
        ParkingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên gửi xe"));

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new BusinessException("Phiên gửi xe không còn hoạt động");
        }

        if (session.getZoneEntryTime() != null) {
            throw new BusinessException("Xe đã đi vào khu đỗ, không thể đổi phân khu");
        }

        return buildEligibleZonesForVehicleType(session.getVehicleType().getId());
    }

    private List<EligibleZoneResponse> buildEligibleZonesForVehicleType(UUID vehicleTypeId) {
        // Lấy toàn bộ zone của loại phương tiện này
        List<Zone> allMatchedZones = zoneRepository.findAll().stream()
                .filter(z -> z.getVehicleType().getId().equals(vehicleTypeId))
                .filter(z -> z.getStatus() == Zone.ZoneStatus.ACTIVE || z.getStatus() == Zone.ZoneStatus.FULL)
                .sorted((z1, z2) -> {
                    // Ưu tiên các zone có thể chọn trước
                    boolean z1Selectable = isZoneSelectable(z1);
                    boolean z2Selectable = isZoneSelectable(z2);
                    if (z1Selectable != z2Selectable) {
                        return z1Selectable ? -1 : 1;
                    }

                    // Sắp xếp ưu tiên:
                    // 1. Số lượng chỗ trống giảm dần (tức occupied tăng dần)
                    int occ1 = (z1.getCurrentCount() != null ? z1.getCurrentCount() : 0)
                            + (z1.getReservedCount() != null ? z1.getReservedCount() : 0);
                    int occ2 = (z2.getCurrentCount() != null ? z2.getCurrentCount() : 0)
                            + (z2.getReservedCount() != null ? z2.getReservedCount() : 0);
                    if (occ1 != occ2) {
                        return Integer.compare(occ1, occ2);
                    }
                    // 2. Khoảng cách gần nhất
                    int dist1 = z1.getDistanceToGate() != null ? z1.getDistanceToGate() : Integer.MAX_VALUE;
                    int dist2 = z2.getDistanceToGate() != null ? z2.getDistanceToGate() : Integer.MAX_VALUE;
                    return Integer.compare(dist1, dist2);
                })
                .collect(Collectors.toList());

        List<EligibleZoneResponse> responseList = new java.util.ArrayList<>();
        for (int i = 0; i < allMatchedZones.size(); i++) {
            Zone z = allMatchedZones.get(i);
            int capacity = z.getCapacity() != null ? z.getCapacity() : 0;
            int current = z.getCurrentCount() != null ? z.getCurrentCount() : 0;
            int reserved = z.getReservedCount() != null ? z.getReservedCount() : 0;
            int occupied = current + reserved;

            boolean hasActiveGate = hasActiveEntryGate(z);
            boolean isMaintenance = z.getStatus() == Zone.ZoneStatus.MAINTENANCE
                    || z.getStatus() == Zone.ZoneStatus.LOCKED
                    || !hasActiveGate;
            boolean isSelectable = z.getStatus() == Zone.ZoneStatus.ACTIVE
                    && hasActiveGate
                    && (occupied < capacity);

            responseList.add(EligibleZoneResponse.builder()
                    .zoneId(z.getId())
                    .zoneCode(z.getZoneCode())
                    .zoneName(z.getZoneName())
                    .floorName(z.getFloor().getFloorName())
                    .capacity(capacity)
                    .currentCount(current)
                    .reservedCount(reserved)
                    .priority(i + 1)
                    .isMaintenance(isMaintenance)
                    .isSelectable(isSelectable)
                    .build());
        }

        return responseList;
    }

    private boolean isZoneSelectable(Zone zone) {
        int capacity = zone.getCapacity() != null ? zone.getCapacity() : 0;
        int current = zone.getCurrentCount() != null ? zone.getCurrentCount() : 0;
        int reserved = zone.getReservedCount() != null ? zone.getReservedCount() : 0;
        int occupied = current + reserved;

        return zone.getStatus() == Zone.ZoneStatus.ACTIVE
                && hasActiveEntryGate(zone)
                && (occupied < capacity);
    }

    private boolean hasActiveEntryGate(Zone zone) {
        if (zone.getFloor() == null || zone.getFloor().getBuilding() == null) {
            return false;
        }
        return gateRepository.findByBuildingId(zone.getFloor().getBuilding().getId()).stream()
                .anyMatch(g -> g.getZone() != null
                        && g.getZone().getId().equals(zone.getId())
                        && Boolean.TRUE.equals(g.getIsActive())
                        && (g.getGateType() == Gate.GateType.ZONE_ENTRY || g.getGateType() == Gate.GateType.ZONE_BOTH));
    }

    /**
     * Thay đổi phân khu đỗ xe chỉ định cho session (Cổng chính - Stage 1.5).
     */
    @Transactional
    public SessionResponse changeSessionZone(UUID sessionId, UUID targetZoneId) {
        ParkingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên gửi xe"));

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new BusinessException("Phiên gửi xe không còn hoạt động");
        }

        if (session.getZoneEntryTime() != null) {
            throw new BusinessException("Không thể đổi phân khu do xe đã đi vào khu đỗ");
        }

        Zone oldZone = session.getZone();
        Zone newZone = zoneRepository.findById(targetZoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân khu mới"));

        if (!newZone.getVehicleType().getId().equals(session.getVehicleType().getId())) {
            throw new BusinessException("Phân khu mới không phù hợp với loại phương tiện");
        }

        // Kiểm tra xem Zone mới có cổng vào hoạt động hay không
        validateZoneHasEntryGate(newZone);

        // Kiểm tra sức chứa của Zone mới
        int occupied = (newZone.getCurrentCount() != null ? newZone.getCurrentCount() : 0)
                + (newZone.getReservedCount() != null ? newZone.getReservedCount() : 0);
        if (newZone.getStatus() == Zone.ZoneStatus.FULL || occupied >= newZone.getCapacity()) {
            throw new BusinessException("Khu vực đỗ xe mới đã đầy chỗ");
        }

        // Cập nhật số đếm ở Zone cũ (trừ 1 ở currentCount)
        if (oldZone.getCurrentCount() != null && oldZone.getCurrentCount() > 0) {
            oldZone.setCurrentCount(oldZone.getCurrentCount() - 1);
        }
        int oldOccupied = (oldZone.getCurrentCount() != null ? oldZone.getCurrentCount() : 0)
                + (oldZone.getReservedCount() != null ? oldZone.getReservedCount() : 0);
        if (oldZone.getStatus() == Zone.ZoneStatus.FULL && oldOccupied < oldZone.getCapacity()) {
            oldZone.setStatus(Zone.ZoneStatus.ACTIVE);
        }
        zoneRepository.save(oldZone);

        // Cập nhật số đếm ở Zone mới (cộng 1 ở currentCount)
        newZone.setCurrentCount((newZone.getCurrentCount() != null ? newZone.getCurrentCount() : 0) + 1);
        int newOccupied = (newZone.getCurrentCount() != null ? newZone.getCurrentCount() : 0)
                + (newZone.getReservedCount() != null ? newZone.getReservedCount() : 0);
        if (newOccupied >= newZone.getCapacity()) {
            newZone.setStatus(Zone.ZoneStatus.FULL);
        }
        zoneRepository.save(newZone);

        // Cập nhật session sang Zone mới
        session.setZone(newZone);
        ParkingSession savedSession = sessionRepository.save(session);

        // Phát WebSocket cập nhật sức chứa cho cả 2 zone
        broadcastZoneChange(oldZone);
        broadcastZoneChange(newZone);

        log.info("CHANGE ZONE: plate={}, sessionCode={}, oldZone={}, newZone={}",
                session.getLicensePlate(), session.getSessionCode(), oldZone.getZoneCode(), newZone.getZoneCode());

        return buildSessionResponse(savedSession, newZone, null);
    }

    private void validateZoneHasEntryGate(Zone zone) {
        if (!hasActiveEntryGate(zone)) {
            throw new BusinessException("Khu vực đỗ xe " + zone.getZoneName()
                    + " hiện tại không thể tiếp nhận xe do chưa được cấu hình cổng vào hoạt động.");
        }
    }

    /**
     * Xe đạp không dùng biển số thật.
     * Hệ thống chỉ dùng mã 1 chữ và 3 số để định danh.
     */
    private boolean isBicycleVehicleType(VehicleType vehicleType) {
        if (vehicleType == null || vehicleType.getName() == null) {
            return false;
        }

        return normalizeVietnameseText(vehicleType.getName()).contains("XE DAP");
    }

    private String resolveBicycleIdentifier(String input) {
        String normalized = LicensePlateUtil.normalize(input);

        if (normalized.isBlank()) {
            return generateAvailableBicycleIdentifier();
        }

        if (!normalized.matches("^BC\\d{10}$")) {
            throw new BusinessException("Mã xe đạp phải có định dạng BCyymmddnnnn. Ví dụ: BC2607010001");
        }

        // Nếu khách tự cung cấp mã, chỉ cần đảm bảo mã đó KHÔNG CÓ XE NÀO
        // ĐANG ĐỖ (ACTIVE).
        boolean hasActiveSession = sessionRepository
                .findByLicensePlateAndStatus(normalized, ParkingSession.SessionStatus.ACTIVE)
                .isPresent();

        if (hasActiveSession) {
            throw new BusinessException("Mã xe đạp này đang có một chiếc xe đỗ trong bãi, vui lòng kiểm tra lại");
        }

        return normalized;
    }

    private String generateAvailableBicycleIdentifier() {
        return uniqueCodeGeneratorService.generateBicycleIdentifier();
    }

    // Hàm này chỉ dùng khi hệ thống TỰ ĐỘNG SINH mã mới, để đảm bảo mã sinh ra là
    // duy nhất 100%
    private boolean isBicycleIdentifierAvailable(String identifier) {
        boolean hasActiveReservation = !reservationRepository.findByLicensePlateAndStatusIn(
                identifier,
                List.of(Reservation.ReservationStatus.PENDING, Reservation.ReservationStatus.CONFIRMED)).isEmpty();

        boolean hasActiveSession = sessionRepository
                .findByLicensePlateAndStatus(identifier, ParkingSession.SessionStatus.ACTIVE)
                .isPresent();

        return !hasActiveReservation && !hasActiveSession;
    }

    private String normalizeVietnameseText(String value) {
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
        return normalized
                .replaceAll("\\p{M}", "")
                .replace("Đ", "D")
                .replace("đ", "d")
                .toUpperCase()
                .trim();
    }
}
