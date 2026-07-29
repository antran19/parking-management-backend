package com.smartparking.backend.service;

import com.smartparking.backend.entity.VehicleType;
import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.entity.Zone.ZoneStatus;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.ZoneRepository;
import com.smartparking.backend.repository.GateRepository;
import com.smartparking.backend.entity.Gate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

/**
 * Service gợi ý Zone (khu vực đỗ xe) phù hợp cho phương tiện.
 */
@Service
public class ZoneSuggestionService {

    private final ZoneRepository zoneRepository;
    private final GateRepository gateRepository;

    public ZoneSuggestionService(ZoneRepository zoneRepository, GateRepository gateRepository) {
        this.zoneRepository = zoneRepository;
        this.gateRepository = gateRepository;
    }

    /**
     * Comparator xếp hạng ưu tiên Zone theo 3 tiêu chí:
     * 1. % sức chứa lấp đầy (currentCount + reservedCount) / capacity là ít nhất (cân bằng tải).
     * 2. Tầng gần mặt đất nhất (ưu tiên T1 làm chuẩn, tầng nổi T2, T3... trước tầng hầm B1, B2...).
     * 3. Sắp xếp theo mã zoneCode (A -> Z).
     */
    public static Comparator<Zone> getZonePreferenceComparator() {
        return Comparator
                .comparingDouble((Zone zone) -> (zone.getCapacity() != null && zone.getCapacity() > 0)
                        ? (double) ((zone.getCurrentCount() != null ? zone.getCurrentCount() : 0)
                                + (zone.getReservedCount() != null ? zone.getReservedCount() : 0)) / zone.getCapacity()
                        : 1.0)
                .thenComparingInt(zone -> (zone.getFloor() != null && zone.getFloor().getFloorNumber() != null)
                        ? Math.abs(zone.getFloor().getFloorNumber() - 1) : Integer.MAX_VALUE)
                .thenComparingInt(zone -> (zone.getFloor() != null && zone.getFloor().getFloorNumber() != null
                        && zone.getFloor().getFloorNumber() < 0) ? 1 : 0)
                .thenComparing(Zone::getZoneCode, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Gợi ý khu vực đỗ xe (Zone) còn trống tốt nhất cho loại xe chỉ định.
     * Tiêu chí ưu tiên:
     * 1. Tỷ lệ % sức chứa đã sử dụng (hiện tại + đã đặt trước) / capacity là ít nhất (cân bằng tải).
     * 2. Tầng gần mặt đất nhất (ưu tiên T1 làm chuẩn, tầng nổi T2, T3... trước tầng hầm B1, B2...).
     * 3. Sắp xếp theo mã zoneCode (A -> Z) khi các tiêu chí trên bằng nhau.
     */
    @Transactional(readOnly = true)
    public Zone suggestZone(VehicleType vehicleType) {
        return zoneRepository.findAvailableZonesByVehicleType(vehicleType.getId(), ZoneStatus.ACTIVE).stream()
                .filter(zone -> {
                    // Tự động loại bỏ các Zone đang bị lỗi/không có cổng vào hoạt động
                    return gateRepository.findByBuildingId(zone.getFloor().getBuilding().getId()).stream()
                            .anyMatch(g -> g.getZone() != null
                                    && g.getZone().getId().equals(zone.getId())
                                    && Boolean.TRUE.equals(g.getIsActive())
                                    && (g.getGateType() == Gate.GateType.ZONE_ENTRY || g.getGateType() == Gate.GateType.ZONE_BOTH));
                })
                .min(getZonePreferenceComparator())
                .orElseThrow(() -> new BusinessException("Không còn zone phù hợp hoặc tất cả các zone đều đang bảo trì cổng cho loại xe: " + vehicleType.getName()));
    }

    /**
     * Cập nhật tăng số lượng xe đang đỗ trong Zone khi xe đi vào.
     * Nếu số lượng xe đạt tới sức chứa tối đa (capacity), cập nhật trạng thái Zone sang FULL.
     */
    @Transactional
    public Zone enterZone(Zone zone) {
        Zone lockedZone = zoneRepository.findByIdForUpdate(zone.getId())
                .orElseThrow(() -> new com.smartparking.backend.exception.ResourceNotFoundException("Phân khu không tồn tại"));
        int occupied = lockedZone.getCurrentCount() + lockedZone.getReservedCount();
        if (lockedZone.getStatus() != ZoneStatus.LOCKED && lockedZone.getStatus() != ZoneStatus.ACTIVE && lockedZone.getStatus() != ZoneStatus.FULL) {
            // Zone đang bảo trì
            throw new BusinessException("Zone " + lockedZone.getZoneName() + " đang không hoạt động (Trạng thái: " + lockedZone.getStatus() + ")");
        }
        if (occupied >= lockedZone.getCapacity()) {
            throw new BusinessException("Zone " + lockedZone.getZoneName() + " đã đầy");
        }
        lockedZone.setCurrentCount(lockedZone.getCurrentCount() + 1);
        if (lockedZone.getCurrentCount() + lockedZone.getReservedCount() >= lockedZone.getCapacity()) {
            lockedZone.setStatus(ZoneStatus.FULL);
        }
        return zoneRepository.save(lockedZone);
    }

    /**
     * Cập nhật giảm số lượng xe trong Zone khi xe đi ra.
     * Nếu Zone đang FULL và số lượng xe giảm xuống dưới sức chứa, chuyển trạng thái về ACTIVE.
     */
    @Transactional
    public Zone exitZone(Zone zone) {
        Zone lockedZone = zoneRepository.findByIdForUpdate(zone.getId())
                .orElseThrow(() -> new com.smartparking.backend.exception.ResourceNotFoundException("Phân khu không tồn tại"));
        if (lockedZone.getCurrentCount() > 0) {
            lockedZone.setCurrentCount(lockedZone.getCurrentCount() - 1);
        }
        if (lockedZone.getStatus() == ZoneStatus.FULL && lockedZone.getCurrentCount() + lockedZone.getReservedCount() < lockedZone.getCapacity()) {
            lockedZone.setStatus(ZoneStatus.ACTIVE);
        }
        return zoneRepository.save(lockedZone);
    }
}

