package com.smartparking.backend.service;

import com.smartparking.backend.entity.Reservation;
import com.smartparking.backend.entity.Reservation.ReservationStatus;
import com.smartparking.backend.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ReservationExpiryScheduler — Background Job tự động hủy reservation hết hạn (Quảng phụ trách)
 *
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

    // TODO: Inject ReservationRepository, ZoneRepository
    // TODO: Constructor injection

    @Scheduled(fixedRate = 30000) // chạy mỗi 30 giây
    @Transactional
    public void expireOverdueReservations() {
        // TODO: Implement
    }
}
