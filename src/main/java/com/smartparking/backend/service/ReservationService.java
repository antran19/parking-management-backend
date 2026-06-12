package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.ReservationRequest;
import com.smartparking.backend.dto.response.ReservationResponse;
import com.smartparking.backend.entity.Reservation;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.entity.VehicleType;
import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.ReservationRepository;
import com.smartparking.backend.repository.UserRepository;
import com.smartparking.backend.repository.VehicleTypeRepository;
import com.smartparking.backend.repository.ZoneRepository;
import com.smartparking.backend.util.LicensePlateUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
/**
 * ReservationService — Đặt giữ chỗ zone (Quảng phụ trách)
 *
 * TODO (Quảng): Implement các method sau:
 * 1. createReservation(user, request)  → Validate zone còn chỗ → Tăng reservedCount → Tạo reservation
 * 2. getUserReservations(user)         → Lấy danh sách reservation theo user
 * 3. cancelReservation(user, id)       → Hủy reservation → Giảm reservedCount
 *
 * Lưu ý:
 * - Kiểm tra biển số đã có reservation PENDING/CONFIRMED chưa
 * - Kiểm tra loại xe có khớp zone không
 * - Kiểm tra zone còn chỗ: currentCount + reservedCount < capacity
 * - Sinh mã reservation: "RS" + yyyyMMdd + "-" + random 4 ký tự
 */
@Service
public class ReservationService {
    // TODO: Inject repositories
    // TODO: Constructor injection
    // TODO: Implement methods
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final VehicleTypeRepository vehicleTypeRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              UserRepository userRepository,
                              ZoneRepository zoneRepository,
                              VehicleTypeRepository vehicleTypeRepository) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.zoneRepository = zoneRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
    }

    @Transactional
    public ReservationResponse createReservation(String email, ReservationRequest request) {
        User user = getUserByEmail(email);

        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy khu đỗ xe"));

        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy loại xe"));

        String licensePlate = LicensePlateUtil.normalize(request.getLicensePlate());

        if (licensePlate.isBlank()) {
            throw new BusinessException("Biển số xe không được để trống");
        }

        if (!zone.getVehicleType().getId().equals(vehicleType.getId())) {
            throw new BusinessException("Loại xe không phù hợp với khu đỗ đã chọn");
        }

        int currentCount = zone.getCurrentCount() == null ? 0 : zone.getCurrentCount();
        int reservedCount = zone.getReservedCount() == null ? 0 : zone.getReservedCount();
        int capacity = zone.getCapacity() == null ? 0 : zone.getCapacity();

        if (currentCount + reservedCount >= capacity) {
            throw new BusinessException("Khu đỗ này đã hết chỗ");
        }

        boolean duplicatedReservation = reservationRepository.existsByUserAndLicensePlateAndStatusIn(
                user,
                licensePlate,
                List.of(Reservation.ReservationStatus.PENDING, Reservation.ReservationStatus.CONFIRMED)
        );

        if (duplicatedReservation) {
            throw new BusinessException("Biển số này đang có đặt chỗ chưa hoàn tất");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reservedFrom = request.getReservedFrom() != null ? request.getReservedFrom() : now;
        LocalDateTime reservedTo = request.getReservedTo() != null ? request.getReservedTo() : now.plusMinutes(30);

        Reservation reservation = Reservation.builder()
                .user(user)
                .zone(zone)
                .vehicleType(vehicleType)
                .licensePlate(licensePlate)
                .reservationCode(generateReservationCode())
                .reservedFrom(reservedFrom)
                .reservedTo(reservedTo)
                .status(Reservation.ReservationStatus.PENDING)
                .build();

        zone.setReservedCount(reservedCount + 1);
        zoneRepository.save(zone);

        Reservation saved = reservationRepository.save(reservation);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getUserReservations(String email) {
        User user = getUserByEmail(email);

        return reservationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<ReservationResponse> cancelReservation(String email, UUID reservationId) {
        User user = getUserByEmail(email);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đặt chỗ"));

        if (!reservation.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền hủy đặt chỗ này");
        }

        if (reservation.getStatus() == Reservation.ReservationStatus.CANCELLED ||
                reservation.getStatus() == Reservation.ReservationStatus.COMPLETED ||
                reservation.getStatus() == Reservation.ReservationStatus.EXPIRED) {
            throw new BusinessException("Đặt chỗ này không thể hủy");
        }

        reservation.setStatus(Reservation.ReservationStatus.CANCELLED);

        Zone zone = reservation.getZone();
        int reservedCount = zone.getReservedCount() == null ? 0 : zone.getReservedCount();
        zone.setReservedCount(Math.max(0, reservedCount - 1));

        zoneRepository.save(zone);
        reservationRepository.save(reservation);

        return getUserReservations(email);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản hiện tại"));
    }

    private String generateReservationCode() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "RS" + datePart + "-" + randomPart;
    }

    private ReservationResponse toResponse(Reservation reservation) {
        Zone zone = reservation.getZone();

        return ReservationResponse.builder()
                .reservationId(reservation.getId())
                .reservationCode(reservation.getReservationCode())
                .zoneId(zone.getId())
                .zoneCode(zone.getZoneCode())
                .zoneName(zone.getZoneName())
                .floorName(zone.getFloor().getFloorName())
                .vehicleTypeId(reservation.getVehicleType().getId())
                .vehicleTypeName(reservation.getVehicleType().getName())
                .licensePlate(reservation.getLicensePlate())
                .reservedFrom(reservation.getReservedFrom())
                .reservedTo(reservation.getReservedTo())
                .status(reservation.getStatus())
                .createdAt(reservation.getCreatedAt())
                .build();
    }
}