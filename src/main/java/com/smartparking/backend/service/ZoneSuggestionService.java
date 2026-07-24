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
     * Gợi ý khu vực đỗ xe (Zone) còn trống tốt nhất cho loại xe chỉ định.
     * Tiêu chí ưu tiên:
     * 1. Số lượng xe hiện tại + số lượng chỗ đã đặt trước là ít nhất (tránh quá tải).
     * 2. Khoảng cách từ zone tới cổng (Gate) là ngắn nhất.
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
                .min(Comparator
                        .comparingInt((Zone zone) -> zone.getCurrentCount() + zone.getReservedCount())
                        .thenComparing(zone -> zone.getDistanceToGate() != null ? zone.getDistanceToGate() : Integer.MAX_VALUE))
                .orElseThrow(() -> new BusinessException("Không còn zone phù hợp hoặc tất cả các zone đều đang bảo trì cổng cho loại xe: " + vehicleType.getName()));
    }

    /**
     * Cập nhật tăng số lượng xe đang đỗ trong Zone khi xe đi vào.
     * Nếu số lượng xe đạt tới sức chứa tối đa (capacity), cập nhật trạng thái Zone sang FULL.
     */
    @Transactional
    public Zone enterZone(Zone zone) {
        int occupied = zone.getCurrentCount() + zone.getReservedCount();
        if (zone.getStatus() != ZoneStatus.ACTIVE || occupied >= zone.getCapacity()) {
            throw new BusinessException("Zone " + zone.getZoneName() + " đã đầy hoặc không hoạt động");
        }
        zone.setCurrentCount(zone.getCurrentCount() + 1);
        if (zone.getCurrentCount() + zone.getReservedCount() >= zone.getCapacity()) {
            zone.setStatus(ZoneStatus.FULL);
        }
        return zoneRepository.save(zone);
    }

    /**
     * Cập nhật giảm số lượng xe trong Zone khi xe đi ra.
     * Nếu Zone đang FULL và số lượng xe giảm xuống dưới sức chứa, chuyển trạng thái về ACTIVE.
     */
    @Transactional
    public Zone exitZone(Zone zone) {
        if (zone.getCurrentCount() > 0) {
            zone.setCurrentCount(zone.getCurrentCount() - 1);
        }
        if (zone.getStatus() == ZoneStatus.FULL && zone.getCurrentCount() + zone.getReservedCount() < zone.getCapacity()) {
            zone.setStatus(ZoneStatus.ACTIVE);
        }
        return zoneRepository.save(zone);
    }
}

