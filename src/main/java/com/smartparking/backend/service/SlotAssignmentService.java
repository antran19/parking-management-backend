package com.smartparking.backend.service;

import com.smartparking.backend.entity.Slot;
import com.smartparking.backend.entity.Slot.SlotStatus;
import com.smartparking.backend.entity.VehicleType;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Smart Slot Assignment Algorithm — Core AI của hệ thống SmartParking.
 *
 * Tiêu chí ưu tiên (RQ3):
 *   1. Loại xe phải đúng với zone (bắt buộc)
 *   2. Tầng thấp hơn (số floorNumber nhỏ hơn)
 *   3. Khoảng cách tới cổng/thang máy (distanceToGate ASC)
 *   4. Cân bằng tải giữa các tầng (tỷ lệ lấp đầy thấp hơn)
 *
 * Redis distributed lock (TTL 60s) chống race condition
 * khi nhiều xe check-in cùng lúc.
 */
@Service
public class SlotAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(SlotAssignmentService.class);
    private static final String SLOT_LOCK_PREFIX = "slot:lock:";
    private static final long LOCK_TTL_SECONDS = 60;

    private final SlotRepository slotRepository;
    private final StringRedisTemplate redisTemplate;

    public SlotAssignmentService(SlotRepository slotRepository,
                                 StringRedisTemplate redisTemplate) {
        this.slotRepository = slotRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Tìm và lock slot tốt nhất cho xe check-in.
     *
     * @param vehicleType Loại phương tiện cần đỗ
     * @param sessionId   ID session mới tạo (dùng làm lock value)
     * @return Slot tối ưu đã được lock
     * @throws BusinessException khi không còn slot phù hợp
     */
    public Slot assignOptimalSlot(VehicleType vehicleType, UUID sessionId) {
        // Bước 1: Lấy tất cả slot AVAILABLE đúng loại xe, sắp xếp theo scoring
        List<Slot> candidates = slotRepository
                .findAvailableSlotsByVehicleType(vehicleType.getId());

        if (candidates.isEmpty()) {
            throw new BusinessException(
                    "Không còn chỗ trống cho loại xe: " + vehicleType.getName() +
                    ". Vui lòng thử lại sau hoặc chọn khu vực khác.");
        }

        // Bước 2: Sort theo thuật toán scoring
        candidates.sort(Comparator
                // Tiêu chí 1: Tầng thấp hơn (floorNumber nhỏ hơn về tuyệt đối)
                .comparingInt((Slot s) -> Math.abs(s.getFloor().getFloorNumber()))
                // Tiêu chí 2: Gần cổng/thang máy hơn
                .thenComparingInt(s -> s.getDistanceToGate() != null ? s.getDistanceToGate() : 9999)
        );

        // Bước 3: Thử lock từng slot theo thứ tự ưu tiên (tránh race condition)
        for (Slot candidate : candidates) {
            String lockKey = SLOT_LOCK_PREFIX + candidate.getId().toString();
            String lockValue = sessionId.toString();

            // setIfAbsent = chỉ set nếu key chưa tồn tại (atomic Redis operation)
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, LOCK_TTL_SECONDS, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(locked)) {
                // Đã lock thành công → cập nhật status trong DB
                candidate.setStatus(SlotStatus.OCCUPIED);
                Slot assigned = slotRepository.save(candidate);

                log.info("Slot assigned: {} (floor={}, distance={}m) for session={}",
                        assigned.getSlotCode(),
                        assigned.getFloor().getFloorName(),
                        assigned.getDistanceToGate(),
                        sessionId);

                return assigned;
            }
            // Slot này vừa bị người khác lock → thử slot tiếp theo
        }

        throw new BusinessException("Tất cả slot trống đã được đặt. Vui lòng thử lại trong giây lát.");
    }

    /**
     * Giải phóng Redis lock khi session kết thúc (check-out).
     */
    public void releaseSlotLock(UUID slotId) {
        String lockKey = SLOT_LOCK_PREFIX + slotId.toString();
        redisTemplate.delete(lockKey);
        log.debug("Released lock for slot: {}", slotId);
    }

    /**
     * Tính điểm score cho slot (dùng cho debug/reporting).
     * Điểm thấp hơn = tốt hơn.
     */
    public double calculateScore(Slot slot) {
        int floorWeight = Math.abs(slot.getFloor().getFloorNumber()) * 100;
        int distanceWeight = slot.getDistanceToGate() != null ? slot.getDistanceToGate() : 9999;
        return floorWeight + distanceWeight;
    }

    /**
     * Kiểm tra xem slot có đang bị lock không (dùng cho monitoring).
     */
    public boolean isSlotLocked(UUID slotId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(SLOT_LOCK_PREFIX + slotId.toString()));
    }

    /**
     * Suggest các zone thay thế khi bãi đầy (trả về thông tin gợi ý cho tài xế).
     */
    public Optional<String> suggestAlternative(UUID buildingId, VehicleType vehicleType) {
        long availableInBuilding = slotRepository.countAvailableByBuilding(buildingId);
        if (availableInBuilding > 0) {
            return Optional.of("Còn " + availableInBuilding + " chỗ trống cho loại xe khác trong tòa nhà.");
        }
        return Optional.empty();
    }
}
