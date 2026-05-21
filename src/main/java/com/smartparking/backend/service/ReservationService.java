package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.CreateReservationRequest;
import com.smartparking.backend.dto.response.ReservationResponse;
import com.smartparking.backend.entity.Reservation;
import com.smartparking.backend.entity.Reservation.ReservationStatus;
import com.smartparking.backend.entity.Slot;
import com.smartparking.backend.entity.Slot.SlotStatus;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.entity.VehicleType;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.ReservationRepository;
import com.smartparking.backend.repository.SlotRepository;
import com.smartparking.backend.repository.UserRepository;
import com.smartparking.backend.repository.VehicleTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ReservationService — Business logic cho chức năng Đặt chỗ trước.
 *
 * Luồng chính:
 * 1. Driver chọn slot trống.
 * 2. Backend kiểm tra slot AVAILABLE.
 * 3. Backend đổi slot thành RESERVED.
 * 4. Tạo reservation giữ chỗ 30 phút.
 * 5. Nếu quá 30 phút chưa dùng thì scheduler tự hủy và trả slot về AVAILABLE.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    /**
     * Thời gian giữ chỗ mặc định: 30 phút.
     */
    private static final int RESERVATION_HOLD_MINUTES = 30;

    private final ReservationRepository reservationRepository;
    private final SlotRepository slotRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ReservationService(
            ReservationRepository reservationRepository,
            SlotRepository slotRepository,
            VehicleTypeRepository vehicleTypeRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.reservationRepository = reservationRepository;
        this.slotRepository = slotRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Tạo đặt chỗ trước cho Driver đang đăng nhập.
     */
    @Transactional
    public ReservationResponse createReservation(
            CreateReservationRequest request,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);

        Slot slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot không tồn tại"));

        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Loại phương tiện không tồn tại"));

        // Kiểm tra slot có đúng loại xe FE gửi lên không.
        if (!slot.getVehicleType().getId().equals(vehicleType.getId())) {
            throw new BusinessException("Slot này không phù hợp với loại phương tiện đã chọn");
        }

        // Chỉ cho phép đặt slot đang AVAILABLE.
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new BusinessException("Slot " + slot.getSlotCode() + " hiện không còn trống để đặt");
        }

        LocalDateTime now = LocalDateTime.now();

        // Check thêm reservation pending còn hạn để tránh race condition logic.
        boolean hasActiveReservation = reservationRepository
                .existsBySlotAndStatusAndReservedToAfter(slot, ReservationStatus.PENDING, now);

        if (hasActiveReservation) {
            throw new BusinessException("Slot " + slot.getSlotCode() + " đã có người đặt trước");
        }

        // Đổi trạng thái slot sang RESERVED để bản đồ bãi xe hiển thị màu vàng.
        slot.setStatus(SlotStatus.RESERVED);
        slotRepository.save(slot);

        Reservation reservation = Reservation.builder()
                .user(currentUser)
                .slot(slot)
                .vehicleType(vehicleType)
                .licensePlate(request.getLicensePlate().trim().toUpperCase())
                .reservedFrom(now)
                .reservedTo(now.plusMinutes(RESERVATION_HOLD_MINUTES))
                .status(ReservationStatus.PENDING)
                .build();

        reservation = reservationRepository.save(reservation);

        // Gửi WebSocket để FE cập nhật slot realtime.
        broadcastSlotChange(slot);

        log.info(
                "CREATE RESERVATION: user={}, plate={}, slot={}, expiredAt={}",
                currentUser.getEmail(),
                reservation.getLicensePlate(),
                slot.getSlotCode(),
                reservation.getReservedTo()
        );

        return buildResponse(
                reservation,
                "Đặt chỗ thành công. Slot được giữ trong " + RESERVATION_HOLD_MINUTES + " phút."
        );
    }

    /**
     * Driver xem danh sách đặt chỗ của chính mình.
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);

        return reservationRepository.findByUserOrderByCreatedAtDesc(currentUser)
                .stream()
                .map(reservation -> buildResponse(reservation, null))
                .toList();
    }

    /**
     * Driver tự hủy reservation đang PENDING.
     */
    @Transactional
    public ReservationResponse cancelReservation(UUID reservationId, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);

        Reservation reservation = reservationRepository
                .findByIdAndUserAndStatus(reservationId, currentUser, ReservationStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đặt chỗ đang chờ của bạn"
                ));

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation = reservationRepository.save(reservation);

        Slot slot = reservation.getSlot();

        // Chỉ trả slot về AVAILABLE nếu slot vẫn đang RESERVED.
        if (slot.getStatus() == SlotStatus.RESERVED) {
            slot.setStatus(SlotStatus.AVAILABLE);
            slotRepository.save(slot);
            broadcastSlotChange(slot);
        }

        log.info(
                "CANCEL RESERVATION: user={}, reservation={}, slot={}",
                currentUser.getEmail(),
                reservation.getId(),
                slot.getSlotCode()
        );

        return buildResponse(reservation, "Đã hủy đặt chỗ thành công");
    }

    /**
     * Tự động hủy reservation quá hạn.
     *
     * fixedRate = 60000 nghĩa là mỗi 60 giây chạy 1 lần.
     * Nếu reservation PENDING quá reservedTo thì chuyển CANCELLED
     * và trả slot về AVAILABLE.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cancelExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();

        List<Reservation> expiredReservations = reservationRepository
                .findByStatusAndReservedToBefore(ReservationStatus.PENDING, now);

        for (Reservation reservation : expiredReservations) {
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(reservation);

            Slot slot = reservation.getSlot();

            if (slot.getStatus() == SlotStatus.RESERVED) {
                slot.setStatus(SlotStatus.AVAILABLE);
                slotRepository.save(slot);
                broadcastSlotChange(slot);
            }

            log.info(
                    "AUTO CANCEL EXPIRED RESERVATION: reservation={}, slot={}, expiredAt={}",
                    reservation.getId(),
                    slot.getSlotCode(),
                    reservation.getReservedTo()
            );
        }
    }

    /**
     * Lấy user hiện tại từ JWT Authentication.
     *
     * Trong hệ thống hiện tại thường username chính là email.
     */
    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException("Bạn cần đăng nhập để thực hiện chức năng này");
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản đang đăng nhập"));
    }

    /**
     * Build DTO response cho FE.
     */
    private ReservationResponse buildResponse(Reservation reservation, String message) {
        Slot slot = reservation.getSlot();

        return ReservationResponse.builder()
                .reservationId(reservation.getId())
                .reservationCode(reservation.getId().toString())
                .slotId(slot.getId())
                .slotCode(slot.getSlotCode())
                .floorName(slot.getFloor().getFloorName())
                .vehicleTypeId(reservation.getVehicleType().getId())
                .vehicleTypeName(reservation.getVehicleType().getName())
                .licensePlate(reservation.getLicensePlate())
                .reservedFrom(reservation.getReservedFrom())
                .reservedTo(reservation.getReservedTo())
                .status(reservation.getStatus())
                .message(message)
                .build();
    }

    /**
     * Broadcast trạng thái slot qua WebSocket.
     *
     * FE đang subscribe:
     * /topic/slots/{buildingId}
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
            log.warn("Không gửi được WebSocket slot update: {}", e.getMessage());
        }
    }
}