package com.smartparking.backend.service;

import com.smartparking.backend.entity.UserLicensePlate;
import com.smartparking.backend.dto.request.ReservationRequest;
import com.smartparking.backend.dto.response.ReservationResponse;
import com.smartparking.backend.entity.Reservation;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.entity.VehicleType;
import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.ReservationRepository;
import com.smartparking.backend.repository.UserLicensePlateRepository;
import com.smartparking.backend.repository.VehicleTypeRepository;
import com.smartparking.backend.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartparking.backend.util.LicensePlateUtil;
import com.smartparking.backend.entity.ParkingPass;
import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.repository.ParkingPassRepository;
import com.smartparking.backend.repository.ParkingSessionRepository;

import java.util.concurrent.ThreadLocalRandom;
import java.sql.Driver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * ReservationService — Đặt giữ chỗ zone (Quảng phụ trách)
 *
 * Nhiệm vụ:
 * - Tạo reservation
 * - Lấy danh sách reservation của driver
 * - Hủy reservation
 *
 * Quy ước theo docs:
 * - Không sửa Entity
 * - Không sửa Repository
 * - Không sửa DTO
 * - Dùng constructor injection
 * - Validate business logic trong Service
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ZoneRepository zoneRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final UserLicensePlateRepository userLicensePlateRepository;
    private final BlacklistService blacklistService;
    private final EmergencyService emergencyService;
    private final ParkingSessionRepository parkingSessionRepository;
    private final UniqueCodeGeneratorService uniqueCodeGeneratorService;
    private final ParkingPassRepository parkingPassRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            ParkingSessionRepository parkingSessionRepository,
            ZoneRepository zoneRepository,
            VehicleTypeRepository vehicleTypeRepository,
            UserLicensePlateRepository userLicensePlateRepository,
            BlacklistService blacklistService,
            EmergencyService emergencyService,
            UniqueCodeGeneratorService uniqueCodeGeneratorService,
            ParkingPassRepository parkingPassRepository) {
        this.reservationRepository = reservationRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.zoneRepository = zoneRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.userLicensePlateRepository = userLicensePlateRepository;
        this.blacklistService = blacklistService;
        this.emergencyService = emergencyService;
        this.uniqueCodeGeneratorService = uniqueCodeGeneratorService;
        this.parkingPassRepository = parkingPassRepository;
    }

    /**
     * Tạo đặt chỗ mới cho driver.
     *
     * Luồng xử lý:
     * 1. Kiểm tra zone tồn tại
     * 2. Kiểm tra loại xe tồn tại
     * 3. Kiểm tra biển số thuộc driver
     * 4. Kiểm tra loại xe khớp với zone
     * 5. Kiểm tra zone còn chỗ
     * 6. Kiểm tra biển số chưa có reservation PENDING/CONFIRMED
     * 7. Tăng reservedCount của zone
     * 8. Tạo reservationCode
     * 9. Lưu reservation
     */
    @Transactional
    public ReservationResponse createReservation(User user, ReservationRequest request) {
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy zone"));

        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại xe"));
        // Nếu không phải xe đạp:
        // - Bắt nhập biển số.
        // - Check biển số thuộc Driver.
        // - Check loại xe của biển số.

        // Nếu là xe đạp:
        // - Không bắt nhập biển số.
        // - Không check trong hồ sơ user_license_plates.
        // - Tự sinh mã dạng BCyymmdd-nnnn, ví dụ BC260702-0001.
        boolean bicycleReservation = isBicycleVehicleType(vehicleType);

        String requestedIdentifier = LicensePlateUtil.normalize(request.getLicensePlate());

        String licensePlate;

        if (bicycleReservation && !requestedIdentifier.isBlank()) {
            /*
             * Driver đã chọn mã xe đạp từ gói hội viên.
             * Phải xác minh mã thuộc đúng Driver và gói vẫn còn hiệu lực.
             */
            validateActiveBicyclePass(
                    user,
                    requestedIdentifier,
                    vehicleType,
                    zone);

            licensePlate = requestedIdentifier;
        } else if (bicycleReservation) {
            /*
             * Giữ tương thích với luồng cũ:
             * Driver chưa có gói xe đạp vẫn có thể đặt chỗ và backend tự sinh mã.
             */
            licensePlate = uniqueCodeGeneratorService.generateBicycleIdentifier();
        } else {
            /*
             * Ô tô, xe máy và xe tải vẫn xử lý như trước.
             */
            licensePlate = normalizePlate(request.getLicensePlate());
        }

        /*
         * Quảng - Driver scope:
         * Chỉ gọi service của role khác, không sửa service của role khác.
         */
        emergencyService.ensureNormalOperation();
        blacklistService.ensurePlateNotBlacklisted(licensePlate);

        if (!bicycleReservation) {
            UserLicensePlate userPlate = validatePlateBelongsToUser(user, licensePlate);
            validatePlateVehicleTypeMatches(userPlate, vehicleType);
        }

        validateZoneMatchesVehicleType(zone, vehicleType);
        validateZoneHasAvailableSlot(zone);
        validatePlateHasNoActiveReservation(user, licensePlate);

        validatePlateHasNoActiveSession(licensePlate);

        LocalDateTime reservedFrom = request.getReservedFrom() != null
                ? request.getReservedFrom()
                : LocalDateTime.now();

        LocalDateTime reservedTo = request.getReservedTo() != null
                ? request.getReservedTo()
                : reservedFrom.plusHours(1);

        if (!reservedTo.isAfter(reservedFrom)) {
            throw new BusinessException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        validateReservationWindow(reservedFrom, reservedTo);

        int currentCount = zone.getCurrentCount() == null ? 0 : zone.getCurrentCount();
        int capacity = zone.getCapacity() == null ? 0 : zone.getCapacity();
        int newReservedCount = (zone.getReservedCount() == null ? 0 : zone.getReservedCount()) + 1;

        zone.setReservedCount(newReservedCount);

        if (capacity > 0 && currentCount + newReservedCount >= capacity) {
            zone.setStatus(Zone.ZoneStatus.FULL);
        }

        zoneRepository.save(zone);

        Reservation reservation = Reservation.builder()
                .user(user)
                .zone(zone)
                .reservationCode(generateReservationCode())
                .vehicleType(vehicleType)
                .licensePlate(licensePlate)
                .reservedFrom(reservedFrom)
                .reservedTo(reservedTo)
                .status(Reservation.ReservationStatus.PENDING)
                .build();

        Reservation savedReservation = reservationRepository.save(reservation);

        return toResponse(savedReservation);
    }

    /**
     * Lấy danh sách đặt chỗ của driver đang đăng nhập.
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getUserReservations(User user) {
        return reservationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Hủy đặt chỗ của driver.
     *
     * Nếu reservation đang PENDING hoặc CONFIRMED:
     * - Chuyển status thành CANCELLED
     * - Giảm reservedCount của zone
     */
    @Transactional
    public ReservationResponse cancelReservation(User user, UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đặt chỗ"));

        if (!reservation.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền hủy đặt chỗ này");
        }

        if (reservation.getStatus() == Reservation.ReservationStatus.CANCELLED) {
            throw new BusinessException("Đặt chỗ này đã bị hủy trước đó");
        }

        if (reservation.getStatus() == Reservation.ReservationStatus.COMPLETED) {
            throw new BusinessException("Đặt chỗ đã hoàn tất, không thể hủy");
        }

        if (reservation.getStatus() == Reservation.ReservationStatus.EXPIRED) {
            throw new BusinessException("Đặt chỗ đã hết hạn, không thể hủy");
        }

        Zone zone = reservation.getZone();
        /*
         * NOTE Quảng - Driver reservation:
         * Khi hủy đặt chỗ, giảm reservedCount an toàn và không cho âm.
         * Nếu zone đang FULL nhưng sau khi hủy còn chỗ thì trả về ACTIVE.
         */
        if (zone != null) {
            int reservedCount = zone.getReservedCount() == null ? 0 : zone.getReservedCount();
            int currentCount = zone.getCurrentCount() == null ? 0 : zone.getCurrentCount();
            int capacity = zone.getCapacity() == null ? 0 : zone.getCapacity();

            zone.setReservedCount(Math.max(0, reservedCount - 1));

            if (zone.getStatus() == Zone.ZoneStatus.FULL
                    && currentCount + zone.getReservedCount() < capacity) {
                zone.setStatus(Zone.ZoneStatus.ACTIVE);
            }

            zoneRepository.save(zone);
        }
        reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
        Reservation savedReservation = reservationRepository.save(reservation);

        return toResponse(savedReservation);
    }

    /**
     * Kiểm tra biển số có thuộc driver hiện tại không.
     *
     * NOTE:
     * Không dùng findByUserAndLicensePlate trực tiếp vì DB cũ có thể đang lưu lẫn:
     * - 51F-123.45
     * - 51F12345
     * Stream + normalize giúp test Postman không bị lệch format.
     */
    private UserLicensePlate validatePlateBelongsToUser(User user, String licensePlate) {
        return userLicensePlateRepository.findByUser(user)
                .stream()
                .filter(item -> LicensePlateUtil.normalize(item.getLicensePlate()).equals(licensePlate))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Biển số chưa được đăng ký bởi driver này"));
    }

    private void validatePlateVehicleTypeMatches(UserLicensePlate userPlate, VehicleType vehicleType) {
        if (userPlate.getVehicleType() == null) {
            throw new BusinessException(
                    "Biển số chưa được gắn loại xe. Vui lòng cập nhật loại xe trong hồ sơ trước khi đặt chỗ");
        }

        if (!userPlate.getVehicleType().getId().equals(vehicleType.getId())) {
            throw new BusinessException("Biển số này thuộc loại xe "
                    + userPlate.getVehicleType().getName()
                    + ", không phù hợp với khu "
                    + vehicleType.getName());
        }
    }

    /**
     * Kiểm tra loại xe có khớp với zone không.
     *
     * Ví dụ:
     * - Zone xe máy thì không được đặt bằng vehicleType ô tô.
     */
    private void validateZoneMatchesVehicleType(Zone zone, VehicleType vehicleType) {
        if (!zone.getVehicleType().getId().equals(vehicleType.getId())) {
            throw new BusinessException("Loại xe không phù hợp với zone đã chọn");
        }
    }

    /**
     * Kiểm tra zone còn chỗ không.
     *
     * Công thức theo docs:
     * currentCount + reservedCount < capacity
     */
    private void validateZoneHasAvailableSlot(Zone zone) {
        int currentCount = zone.getCurrentCount() == null ? 0 : zone.getCurrentCount();
        int reservedCount = zone.getReservedCount() == null ? 0 : zone.getReservedCount();
        int capacity = zone.getCapacity() == null ? 0 : zone.getCapacity();

        if (zone.getStatus() != Zone.ZoneStatus.ACTIVE) {
            throw new BusinessException("Zone hiện không hoạt động");
        }

        if (currentCount + reservedCount >= capacity) {
            throw new BusinessException("Zone đã hết chỗ trống");
        }
    }

    private void validatePlateHasNoActiveSession(String licensePlate) {
        parkingSessionRepository
                .findByLicensePlateAndStatus(licensePlate, ParkingSession.SessionStatus.ACTIVE)
                .ifPresent(session -> {
                    throw new BusinessException(
                            "Biển số " + licensePlate
                                    + " đang có phiên gửi xe chưa kết thúc, không thể tạo đặt chỗ mới");
                });
    }

    /**
     * Kiểm tra biển số có đang có đặt chỗ chưa hoàn tất không.
     *
     * NOTE Quảng - Driver scope:
     * Không dùng existsByUserAndLicensePlateAndStatusIn trực tiếp vì dữ liệu cũ có
     * thể lệch format biển số.
     */
    private void validatePlateHasNoActiveReservation(User user, String licensePlate) {
        boolean existed = reservationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .filter(reservation -> reservation.getStatus() == Reservation.ReservationStatus.PENDING
                        || reservation.getStatus() == Reservation.ReservationStatus.CONFIRMED)
                .anyMatch(
                        reservation -> LicensePlateUtil.normalize(reservation.getLicensePlate()).equals(licensePlate));

        if (existed) {
            throw new BusinessException("Biển số đang có đặt chỗ chưa hoàn tất");
        }
    }

    /**
     * Validate khung thời gian đặt chỗ của Driver.
     *
     * Quy tắc:
     * - Không cho đặt trong quá khứ.
     * - Không cho giữ chỗ quá 1 giờ để tránh khóa zone quá lâu.
     */
    private void validateReservationWindow(LocalDateTime reservedFrom, LocalDateTime reservedTo) {
        LocalDateTime now = LocalDateTime.now();

        if (reservedFrom.isBefore(now.minusMinutes(1))) {
            throw new BusinessException("Không thể đặt chỗ trong quá khứ");
        }

        if (reservedFrom.isAfter(now.plusHours(1))) {
            throw new BusinessException("Chỉ có thể giữ chỗ trong vòng 1 giờ tới");
        }

        if (reservedTo.isAfter(reservedFrom.plusHours(1))) {
            throw new BusinessException("Thời gian giữ chỗ tối đa là 1 giờ");
        }
    }

    /**
     * Xác minh mã xe đạp được Driver chọn khi đặt chỗ.
     *
     * Không thêm mã vào user_license_plates.
     * Mã vẫn thuộc ParkingPass nên Staff check-in tiếp tục kiểm tra như cũ.
     */
    private void validateActiveBicyclePass(
            User user,
            String bicycleIdentifier,
            VehicleType requestedVehicleType,
            Zone zone) {

        ParkingPass parkingPass = parkingPassRepository
                .findByUserAndStatus(
                        user,
                        ParkingPass.PassStatus.ACTIVE)
                .stream()
                .filter(pass -> LicensePlateUtil.normalize(
                        pass.getLicensePlate())
                        .equals(bicycleIdentifier))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Mã xe đạp không thuộc gói đang hoạt động của tài khoản này"));

        if (parkingPass.getVehicleType() == null
                || !isBicycleVehicleType(parkingPass.getVehicleType())) {

            throw new BusinessException(
                    "Mã đã chọn không thuộc gói xe đạp");
        }

        if (requestedVehicleType == null
                || !parkingPass.getVehicleType().getId()
                        .equals(requestedVehicleType.getId())) {

            throw new BusinessException(
                    "Mã xe đạp không phù hợp với loại xe của zone");
        }

        LocalDate today = LocalDate.now();

        if (parkingPass.getStartDate() == null
                || parkingPass.getEndDate() == null
                || today.isBefore(parkingPass.getStartDate())
                || today.isAfter(parkingPass.getEndDate())) {

            throw new BusinessException(
                    "Gói xe đạp của mã này đã hết hạn hoặc chưa có hiệu lực");
        }

        UUID passBuildingId = parkingPass.getBuilding() != null
                ? parkingPass.getBuilding().getId()
                : null;

        UUID zoneBuildingId = zone.getFloor() != null
                && zone.getFloor().getBuilding() != null
                        ? zone.getFloor().getBuilding().getId()
                        : null;

        if (passBuildingId == null
                || zoneBuildingId == null
                || !passBuildingId.equals(zoneBuildingId)) {

            throw new BusinessException(
                    "Mã xe đạp không thuộc bãi đỗ của zone đã chọn");
        }
    }

    /**
     * Xe đạp không dùng biển số thật.
     * Hệ thống tự sinh mã định danh dạng BCyymmdd-nnnn.
     * Ví dụ: BC260702-0001.
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

        if (!isBicycleIdentifierAvailable(normalized)) {
            throw new BusinessException("Mã xe đạp đang được sử dụng, vui lòng tạo lại đặt chỗ");
        }

        return normalized;
    }

    private String generateAvailableBicycleIdentifier() {
        return uniqueCodeGeneratorService.generateBicycleIdentifier();
    }

    // Xe đạp: tự sinh mã dạng BCyymmddnnnn, ví dụ BC2607020001.
    private boolean isBicycleIdentifierAvailable(String identifier) {
        boolean hasActiveReservation = !reservationRepository.findByLicensePlateAndStatusIn(
                identifier,
                List.of(Reservation.ReservationStatus.PENDING, Reservation.ReservationStatus.CONFIRMED)).isEmpty();

        boolean hasActiveSession = parkingSessionRepository
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

    /**
     * Chuyển Entity Reservation sang DTO ReservationResponse.
     */
    private ReservationResponse toResponse(Reservation reservation) {
        Zone zone = reservation.getZone();

        return ReservationResponse.builder()
                .reservationId(reservation.getId())
                .reservationCode(reservation.getReservationCode())
                .zoneId(zone != null ? zone.getId() : null)
                .zoneCode(zone != null ? zone.getZoneCode() : null)
                .zoneName(zone != null ? zone.getZoneName() : null)
                .floorName(zone != null && zone.getFloor() != null ? zone.getFloor().getFloorName() : null)
                .vehicleTypeId(reservation.getVehicleType() != null ? reservation.getVehicleType().getId() : null)
                .vehicleTypeName(reservation.getVehicleType() != null ? reservation.getVehicleType().getName() : null)
                .licensePlate(reservation.getLicensePlate())
                .reservedFrom(reservation.getReservedFrom())
                .reservedTo(reservation.getReservedTo())
                .status(reservation.getStatus())
                .createdAt(reservation.getCreatedAt())
                .customerName(reservation.getUser() != null ? reservation.getUser().getFullName() : null)
                .build();
    }

    public List<ReservationResponse> getAllReservations(UUID zoneId, String status) {
        List<Reservation> list;
        if (zoneId != null) {
            list = reservationRepository.findByZoneId(zoneId);
        } else {
            list = reservationRepository.findAll();
        }

        if (status != null && !status.trim().isEmpty() && !"all".equalsIgnoreCase(status)) {
            try {
                Reservation.ReservationStatus rStatus = Reservation.ReservationStatus.valueOf(status.toUpperCase());
                list = list.stream().filter(r -> r.getStatus() == rStatus).toList();
            } catch (IllegalArgumentException e) {
                // ignore invalid status
            }
        }

        return list.stream().map(this::toResponse).toList();
    }

    /**
     * NOTE:
     * Dùng chung chuẩn biển số với DriverController.
     * Nhờ vậy:
     * - Thêm biển số: 51F-123.45
     * - Đặt chỗ: 51F12345 hoặc 51F-123.45
     * đều hiểu là cùng một xe.
     */
    private String normalizePlate(String plate) {
        String normalizedPlate = LicensePlateUtil.normalize(plate);

        if (normalizedPlate.isBlank()) {
            throw new BusinessException("Biển số không được để trống");
        }

        return normalizedPlate;
    }

    /**
     * Sinh mã đặt chỗ theo format:
     * RS + yyyyMMdd + "-" + random 4 ký tự
     *
     * Ví dụ:
     * RS20260613-A7F2
     */
    private String generateReservationCode() {
        return uniqueCodeGeneratorService.generateReservationCode();
    }
}