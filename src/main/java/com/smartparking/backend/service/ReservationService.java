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
import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.repository.ParkingSessionRepository;

import java.util.concurrent.ThreadLocalRandom;
import java.sql.Driver;
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

    public ReservationService(
            ReservationRepository reservationRepository,
            ParkingSessionRepository parkingSessionRepository,
            ZoneRepository zoneRepository,
            VehicleTypeRepository vehicleTypeRepository,
            UserLicensePlateRepository userLicensePlateRepository,
            BlacklistService blacklistService,
            EmergencyService emergencyService,
            UniqueCodeGeneratorService uniqueCodeGeneratorService) {
        this.reservationRepository = reservationRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.zoneRepository = zoneRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.userLicensePlateRepository = userLicensePlateRepository;
        this.blacklistService = blacklistService;
        this.emergencyService = emergencyService;
        this.uniqueCodeGeneratorService = uniqueCodeGeneratorService;
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
        // - Tự sinh mã 4 số.
        boolean bicycleReservation = isBicycleVehicleType(vehicleType);
        String licensePlate = bicycleReservation
                ? resolveBicycleIdentifier(request.getLicensePlate())
                : normalizePlate(request.getLicensePlate());

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

        LocalDateTime reservedFrom = request.getReservedFrom() != null
                ? request.getReservedFrom()
                : LocalDateTime.now();

        LocalDateTime reservedTo = request.getReservedTo() != null
                ? request.getReservedTo()
                : reservedFrom.plusMinutes(30);

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
     * - Không cho giữ chỗ quá 2 giờ để tránh khóa zone quá lâu.
     */
    private void validateReservationWindow(LocalDateTime reservedFrom, LocalDateTime reservedTo) {
        LocalDateTime now = LocalDateTime.now();

        if (reservedFrom.isBefore(now.minusMinutes(1))) {
            throw new BusinessException("Không thể đặt chỗ trong quá khứ");
        }

        if (reservedFrom.isAfter(now.plusHours(2))) {
            throw new BusinessException("Chỉ có thể giữ chỗ trong vòng 2 giờ tới");
        }

        if (reservedTo.isAfter(reservedFrom.plusHours(2))) {
            throw new BusinessException("Thời gian giữ chỗ tối đa là 2 giờ");
        }
    }

    /**
     * Xe đạp không dùng biển số thật.
     * Hệ thống chỉ dùng mã 4 số để định danh reservation/check-in.
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

        if (!normalized.matches("^BC\\d{6}-\\d{4}$")) {
            throw new BusinessException("Mã xe đạp phải có định dạng BCyymmdd-nnnn. Ví dụ: BC260701-0001");
        }

        if (!isBicycleIdentifierAvailable(normalized)) {
            throw new BusinessException("Mã xe đạp đang được sử dụng, vui lòng tạo lại đặt chỗ");
        }

        return normalized;
    }

    private String generateAvailableBicycleIdentifier() {
        return uniqueCodeGeneratorService.generateBicycleIdentifier();
    }
    // Xe đạp: tự sinh mã dạng 1 chữ cái + 3 số, ví dụ B482.
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
                .build();
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