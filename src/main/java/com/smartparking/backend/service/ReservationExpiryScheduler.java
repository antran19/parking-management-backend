package com.smartparking.backend.service;

import com.smartparking.backend.entity.Reservation;
import com.smartparking.backend.entity.Reservation.ReservationStatus;
import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.repository.ReservationRepository;
import com.smartparking.backend.repository.ZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ReservationExpiryScheduler — Background Job tự động hủy reservation hết hạn (Quảng phụ trách).
 *
 * Nhiệm vụ:
 * - Chạy mỗi 30 giây.
 * - Tìm reservation PENDING/CONFIRMED đã quá reservedTo.
 * - Chuyển status sang EXPIRED.
 * - Giảm zone.reservedCount.
 * - Nếu zone đang FULL và còn chỗ thì chuyển về ACTIVE.
 
 * TODO (Quảng): Implement method:
 * - expireOverdueReservations() → Chạy mỗi 30 giây
 *   + Tìm reservation PENDING/CONFIRMED có reservedTo < now
 *   + Chuyển status → EXPIRED
 *   + Giảm zone.reservedCount -= 1
 *   + Nếu zone đang FULL → chuyển về ACTIVE
 */

@Component
public class ReservationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

    private final ReservationRepository reservationRepository;
    private final ZoneRepository zoneRepository;

    public ReservationExpiryScheduler(
            ReservationRepository reservationRepository,
            ZoneRepository zoneRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.zoneRepository = zoneRepository;
    }

    /**
     * Chạy mỗi 30 giây để expire reservation quá hạn.
     *
     * Vì đang làm demo/team project, dùng findAll() rồi filter trong service
     * để tránh sửa Repository chung nếu chưa có approval từ leader.
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void expireOverdueReservations() {
        LocalDateTime now = LocalDateTime.now();

        List<Reservation> overdueReservations = reservationRepository.findAll()
                .stream()
                .filter(this::isActiveReservation)
                .filter(reservation -> reservation.getReservedTo() != null)
                .filter(reservation -> reservation.getReservedTo().isBefore(now))
                .toList();

        if (overdueReservations.isEmpty()) {
            return;
        }

        for (Reservation reservation : overdueReservations) {
            expireReservation(reservation);
        }

        log.info("Expired {} overdue reservation(s)", overdueReservations.size());
    }

    /**
     * Reservation còn giữ chỗ là PENDING hoặc CONFIRMED.
     */
    private boolean isActiveReservation(Reservation reservation) {
        return reservation.getStatus() == ReservationStatus.PENDING
                || reservation.getStatus() == ReservationStatus.CONFIRMED;
    }

    /**
     * Chuyển reservation sang EXPIRED và trả reserved slot về zone.
     */
    private void expireReservation(Reservation reservation) {
        Zone zone = reservation.getZone();

        reservation.setStatus(ReservationStatus.EXPIRED);

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

        reservationRepository.save(reservation);

        log.info(
                "Reservation {} expired. Plate: {}",
                reservation.getReservationCode(),
                reservation.getLicensePlate()
        );
    }
}