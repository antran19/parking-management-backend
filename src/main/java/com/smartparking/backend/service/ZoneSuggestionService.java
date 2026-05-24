package com.smartparking.backend.service;

import com.smartparking.backend.entity.VehicleType;
import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.entity.Zone.ZoneStatus;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

@Service
public class ZoneSuggestionService {

    private final ZoneRepository zoneRepository;

    public ZoneSuggestionService(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    @Transactional(readOnly = true)
    public Zone suggestZone(VehicleType vehicleType) {
        return zoneRepository.findAvailableZonesByVehicleType(vehicleType.getId(), ZoneStatus.ACTIVE).stream()
                .min(Comparator
                        .comparingInt((Zone zone) -> zone.getCurrentCount() + zone.getReservedCount())
                        .thenComparing(zone -> zone.getDistanceToGate() != null ? zone.getDistanceToGate() : Integer.MAX_VALUE))
                .orElseThrow(() -> new BusinessException("Không còn zone phù hợp cho loại xe: " + vehicleType.getName()));
    }

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
