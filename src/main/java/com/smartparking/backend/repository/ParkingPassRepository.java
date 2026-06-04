package com.smartparking.backend.repository;

import com.smartparking.backend.entity.ParkingPass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParkingPassRepository extends JpaRepository<ParkingPass, UUID> {

    /**
     * Tìm kiếm vé đang hoạt động của biển số xe tại một tòa nhà cho một loại phương tiện cụ thể
     * vào một thời điểm (date) xác định.
     */
    @Query("SELECT p FROM ParkingPass p WHERE p.licensePlate = :licensePlate " +
           "AND p.building.id = :buildingId " +
           "AND p.vehicleType.id = :vehicleTypeId " +
           "AND p.status = 'ACTIVE' " +
           "AND p.startDate <= :date AND p.endDate >= :date")
    Optional<ParkingPass> findActivePass(@Param("licensePlate") String licensePlate,
                                         @Param("buildingId") UUID buildingId,
                                         @Param("vehicleTypeId") UUID vehicleTypeId,
                                         @Param("date") LocalDate date);

    /**
     * Tìm tất cả vé gửi xe của một Driver dựa theo Email.
     */
    List<ParkingPass> findByUserEmailOrderByCreatedAtDesc(String email);

    /**
     * Kiểm tra xem có vé đang hoạt động nào bị trùng lặp thời gian hay không.
     */
    @Query("SELECT COUNT(p) > 0 FROM ParkingPass p WHERE p.licensePlate = :licensePlate " +
           "AND p.building.id = :buildingId " +
           "AND p.vehicleType.id = :vehicleTypeId " +
           "AND p.status = 'ACTIVE' " +
           "AND p.startDate <= :endDate AND p.endDate >= :startDate")
    boolean existsOverlappingActivePass(@Param("licensePlate") String licensePlate,
                                        @Param("buildingId") UUID buildingId,
                                        @Param("vehicleTypeId") UUID vehicleTypeId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    /**
     * Tìm tất cả vé gửi xe có trạng thái ACTIVE của một Driver dựa theo Email.
     */
    @Query("SELECT p FROM ParkingPass p WHERE p.user.email = :email AND p.status = 'ACTIVE'")
    List<ParkingPass> findActivePassesByUserEmail(@Param("email") String email);
}
