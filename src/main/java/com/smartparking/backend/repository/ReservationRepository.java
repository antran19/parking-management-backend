package com.smartparking.backend.repository;

import com.smartparking.backend.entity.Reservation;
import com.smartparking.backend.entity.Reservation.ReservationStatus;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.entity.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    
    // Tìm danh sách đặt chỗ của một Driver, sắp xếp theo thời gian tạo mới nhất
    List<Reservation> findByUserOrderByCreatedAtDesc(User user);

    // Tìm đặt chỗ CONFIRMED khớp với biển số xe và loại phương tiện khi xe vào cổng check-in
    Optional<Reservation> findFirstByLicensePlateAndVehicleTypeAndStatusOrderByReservedFromAsc(
            String licensePlate, 
            VehicleType vehicleType, 
            ReservationStatus status
    );
}
