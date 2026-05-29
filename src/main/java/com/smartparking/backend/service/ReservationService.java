package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.ReservationRequest;
import com.smartparking.backend.dto.response.ReservationResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.entity.Reservation.ReservationStatus;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ZoneRepository zoneRepository;
    private final UserRepository userRepository;
    private final VehicleTypeRepository vehicleTypeRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              ZoneRepository zoneRepository,
                              UserRepository userRepository,
                              VehicleTypeRepository vehicleTypeRepository) {
        this.reservationRepository = reservationRepository;
        this.zoneRepository = zoneRepository;
        this.userRepository = userRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
    }

    /**
     * Tài xế đặt chỗ trước theo Zone
     */
    @Transactional
    public ReservationResponse createReservation(ReservationRequest request, String email) {
        // 1. Tìm thông tin tài xế đặt chỗ
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người dùng"));

        // 2. Kiểm tra thời gian đỗ xe hợp lệ
        LocalDateTime now = LocalDateTime.now();
        if (request.getReservedFrom().isBefore(now)) {
            throw new BusinessException("Thời gian bắt đầu đặt chỗ phải ở tương lai");
        }
        if (request.getReservedTo().isBefore(request.getReservedFrom())) {
            throw new BusinessException("Thời gian kết thúc đặt chỗ phải sau thời gian bắt đầu");
        }

        // NGHIỆP VỤ: Chỉ cho phép đặt trước tối đa 2 tiếng để tránh lãng phí giữ chỗ
        if (request.getReservedFrom().isAfter(now.plusHours(2))) {
            throw new BusinessException("Hệ thống chỉ hỗ trợ đặt chỗ trước tối đa 2 giờ so với giờ vào dự kiến");
        }

        // 3. Truy xuất Zone và VehicleType
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Khu vực đỗ xe (Zone) không tồn tại"));
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Loại phương tiện không tồn tại"));

        // 4. Validate loại xe gửi lên có khớp với thiết kế của Zone không
        if (!zone.getVehicleType().getId().equals(vehicleType.getId())) {
            throw new BusinessException("Khu vực " + zone.getZoneName() + " không hỗ trợ đỗ loại xe: " + vehicleType.getName());
        }

        // 5. Kiểm tra sức chứa khả dụng của Zone: (Xe thực tế + Xe đã đặt chỗ trước)
        int currentOccupied = zone.getCurrentCount() + zone.getReservedCount();
        if (zone.getStatus() != Zone.ZoneStatus.ACTIVE || currentOccupied >= zone.getCapacity()) {
            throw new BusinessException("Khu vực " + zone.getZoneName() + " đã hết chỗ đỗ trống khả dụng");
        }

        // 6. Sinh mã đặt chỗ duy nhất dạng: RES + yyyyMMdd + 4 ký tự ngẫu nhiên
        String reservationCode = generateReservationCode();

        // 7. Khởi tạo đối tượng Đặt chỗ
        Reservation reservation = Reservation.builder()
                .user(user)
                .zone(zone)
                .reservationCode(reservationCode)
                .vehicleType(vehicleType)
                .licensePlate(request.getLicensePlate())
                .reservedFrom(request.getReservedFrom())
                .reservedTo(request.getReservedTo())
                .status(ReservationStatus.CONFIRMED) // Khởi tạo với trạng thái CONFIRMED (tạm thời không cần cọc tiền thật)
                .build();

        // 8. Tăng số lượng đặt chỗ (reservedCount) của Zone lên 1 để khóa chỗ ngay
        zone.setReservedCount(zone.getReservedCount() + 1);
        if (zone.getCurrentCount() + zone.getReservedCount() >= zone.getCapacity()) {
            zone.setStatus(Zone.ZoneStatus.FULL);
        }
        zoneRepository.save(zone);

        reservation = reservationRepository.save(reservation);
        return mapToResponse(reservation);
    }

    /**
     * Lấy danh sách lịch sử đặt chỗ của tài xế đang đăng nhập
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người dùng"));

        return reservationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Hủy đặt chỗ trước
     */
    @Transactional
    public void cancelReservation(UUID id, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người dùng"));

        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin đặt chỗ"));

        // Xác thực quyền sở hữu (chỉ chính tài xế hoặc ADMIN mới được hủy)
        if (!reservation.getUser().getId().equals(user.getId()) && !user.getRole().name().equals("ADMIN")) {
            throw new BusinessException("Bạn không có quyền thực hiện thao tác hủy đặt chỗ này");
        }

        // Chỉ được hủy khi lịch đặt chỗ còn hiệu lực CONFIRMED
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException("Chỉ được phép hủy đặt chỗ khi trạng thái là CONFIRMED");
        }

        // Cập nhật trạng thái lịch đặt chỗ
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        // Giảm reservedCount của Zone để giải phóng slot trống cho bãi
        Zone zone = reservation.getZone();
        if (zone.getReservedCount() > 0) {
            zone.setReservedCount(zone.getReservedCount() - 1);
        }

        // Mở lại trạng thái Zone nếu trước đó bị FULL
        int occupied = zone.getCurrentCount() + zone.getReservedCount();
        if (zone.getStatus() == Zone.ZoneStatus.FULL && occupied < zone.getCapacity()) {
            zone.setStatus(Zone.ZoneStatus.ACTIVE);
        }
        zoneRepository.save(zone);
    }

    private String generateReservationCode() {
        String date = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "RES" + date + "-" + random;
    }

    private ReservationResponse mapToResponse(Reservation r) {
        return ReservationResponse.builder()
                .id(r.getId())
                .reservationCode(r.getReservationCode())
                .zoneId(r.getZone().getId())
                .zoneCode(r.getZone().getZoneCode())
                .zoneName(r.getZone().getZoneName())
                .floorName(r.getZone().getFloor().getFloorName())
                .vehicleTypeName(r.getVehicleType().getName())
                .licensePlate(r.getLicensePlate())
                .reservedFrom(r.getReservedFrom())
                .reservedTo(r.getReservedTo())
                .status(r.getStatus().name())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
