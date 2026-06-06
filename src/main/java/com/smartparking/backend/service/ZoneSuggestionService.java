package com.smartparking.backend.service;

import com.smartparking.backend.entity.VehicleType;
import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.entity.Zone.ZoneStatus;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

/**
 * ZoneSuggestionService —
 * - Suggest zone considering up-to-date Redis counters.
 * - Use Redis atomic INCR/DECR for occupancy and persist DB to keep canonical state.
 * - Acquire a short-lived distributed lock when zone is near full to avoid races
 *   crossing the capacity boundary.
 *
 * Flow (check-in):
 * 1. suggestZone(): query candidate zones from DB, but read current occupancy from Redis
 *    (redis.getCount) to make decision based on latest counters.
 * 2. enterZone(): (a) check capacity using redis count; (b) when near-full, tryLock(zone); (c)
 *    perform redis.increment(); (d) if increment result > capacity -> rollback (redis.decrement)
 *    and throw; (e) persist updated currentCount into DB and update status if needed; (f) unlock.
 *
 * Flow (check-out):
 * 1. exitZone(): perform redis.decrement() atomically; 2. persist new currentCount to DB and
 *    clear FULL status if space freed.
 */
@Service
public class ZoneSuggestionService {

    private final ZoneRepository zoneRepository;
    private final RedisZoneCounterService redisZoneCounterService;

    public ZoneSuggestionService(ZoneRepository zoneRepository, RedisZoneCounterService redisZoneCounterService) {
        this.zoneRepository = zoneRepository;
        this.redisZoneCounterService = redisZoneCounterService;
    }

    @Transactional(readOnly = true)
    public Zone suggestZone(VehicleType vehicleType) {
        // Use Redis counts for decision so suggestions reflect real-time occupancy
        return zoneRepository.findAvailableZonesByVehicleType(vehicleType.getId(), ZoneStatus.ACTIVE).stream()
                .min(Comparator
                        .comparingInt((Zone zone) -> {
                            int cur = redisZoneCounterService.getCount(zone.getId());
                            return cur + zone.getReservedCount();
                        })
                        .thenComparing(zone -> zone.getDistanceToGate() != null ? zone.getDistanceToGate() : Integer.MAX_VALUE))
                .orElseThrow(() -> new BusinessException("Không còn zone phù hợp cho loại xe: " + vehicleType.getName()));
    }

    @Transactional
    public Zone enterZone(Zone zone) {
        // Read latest occupancy from Redis
        int cur = redisZoneCounterService.getCount(zone.getId());
        int occupied = cur + zone.getReservedCount();
        if (zone.getStatus() != ZoneStatus.ACTIVE || occupied >= zone.getCapacity()) {
            throw new BusinessException("Zone " + zone.getZoneName() + " đã đầy hoặc không hoạt động");
        }

        // If zone is "near full" (remaining <= 1), acquire short lock to avoid race when
        // multiple check-ins try to occupy the last slot.
        boolean locked = false;
        String lockSession = UUID.randomUUID().toString();
        int remaining = zone.getCapacity() - occupied;
        if (remaining <= 1) {
            locked = redisZoneCounterService.tryLock(zone.getId(), lockSession, 30);
            if (!locked) {
                // Someone else is crossing the boundary — ask caller to retry
                throw new BusinessException("Zone đang được cập nhật. Vui lòng thử lại trong giây lát.");
            }
            // Re-read current after acquiring lock because it may have changed
            cur = redisZoneCounterService.getCount(zone.getId());
            occupied = cur + zone.getReservedCount();
            if (occupied >= zone.getCapacity()) {
                // no space
                redisZoneCounterService.unlock(zone.getId(), lockSession);
                throw new BusinessException("Zone " + zone.getZoneName() + " đã đầy");
            }
        }

        // Atomically increment Redis counter. If overflow ( > capacity ), rollback and fail.
        long newVal = redisZoneCounterService.increment(zone.getId());
        if (newVal > zone.getCapacity()) {
            // overflow — rollback Redis and release lock
            redisZoneCounterService.decrement(zone.getId());
            if (locked) redisZoneCounterService.unlock(zone.getId(), lockSession);
            throw new BusinessException("Zone " + zone.getZoneName() + " đã đầy (race)");
        }

        // Persist new currentCount to DB so API consumers reading DB can see a near-correct snapshot.
        zone.setCurrentCount((int) newVal);
        if (zone.getCurrentCount() + zone.getReservedCount() >= zone.getCapacity()) {
            zone.setStatus(ZoneStatus.FULL);
        }
        Zone saved = zoneRepository.save(zone);

        if (locked) {
            // release lock after DB persisted
            redisZoneCounterService.unlock(zone.getId(), lockSession);
        }
        return saved;
    }

    @Transactional
    public Zone exitZone(Zone zone) {
        // Atomically decrement Redis counter first
        long newVal = redisZoneCounterService.decrement(zone.getId());
        if (newVal < 0) newVal = 0; // safety

        // Persist to DB
        zone.setCurrentCount((int) newVal);
        if (zone.getStatus() == ZoneStatus.FULL && zone.getCurrentCount() + zone.getReservedCount() < zone.getCapacity()) {
            zone.setStatus(ZoneStatus.ACTIVE);
        }
        return zoneRepository.save(zone);
    }
}
