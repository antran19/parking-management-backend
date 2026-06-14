package com.smartparking.backend.repository;

import com.smartparking.backend.entity.Reservation;
import com.smartparking.backend.entity.Reservation.ReservationStatus;
import com.smartparking.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    List<Reservation> findByUserOrderByCreatedAtDesc(User user);

    Optional<Reservation> findByReservationCode(String reservationCode);

    boolean existsByUserAndLicensePlateAndStatusIn(User user, String licensePlate, List<ReservationStatus> statuses);

    List<Reservation> findByLicensePlateAndStatusIn(String licensePlate, List<ReservationStatus> statuses);
}
