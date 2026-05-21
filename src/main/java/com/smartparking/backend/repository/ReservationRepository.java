package com.smartparking.backend.repository;

import com.smartparking.backend.entity.Reservation;
import com.smartparking.backend.entity.Reservation.ReservationStatus;
import com.smartparking.backend.entity.Slot;
import com.smartparking.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository thao tác với bảng reservations.
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /**
     * Kiểm tra slot đã có reservation còn hiệu lực hay chưa.
     *
     * Dùng để tránh 2 người cùng đặt 1 slot.
     */
    boolean existsBySlotAndStatusAndReservedToAfter(
            Slot slot,
            ReservationStatus status,
            LocalDateTime now
    );

    /**
     * Tìm reservation đang pending của user theo ID.
     */
    Optional<Reservation> findByIdAndUserAndStatus(
            UUID id,
            User user,
            ReservationStatus status
    );

    /**
     * Lấy danh sách reservation của user, mới nhất trước.
     */
    List<Reservation> findByUserOrderByCreatedAtDesc(User user);

    /**
     * Lấy các reservation đã quá hạn để auto-cancel.
     */
    List<Reservation> findByStatusAndReservedToBefore(
            ReservationStatus status,
            LocalDateTime now
    );
}