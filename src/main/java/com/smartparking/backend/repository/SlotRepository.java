package com.smartparking.backend.repository;

import com.smartparking.backend.entity.Slot;
import com.smartparking.backend.entity.Slot.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SlotRepository extends JpaRepository<Slot, UUID> {

    List<Slot> findByFloorIdAndStatus(UUID floorId, SlotStatus status);

    // Lấy slot trống theo loại xe - dùng cho AI scoring
    @Query("SELECT s FROM Slot s WHERE s.vehicleType.id = :vehicleTypeId AND s.status = 'AVAILABLE' ORDER BY s.distanceToGate ASC")
    List<Slot> findAvailableSlotsByVehicleType(@Param("vehicleTypeId") UUID vehicleTypeId);

    // Đếm slot trống theo tầng - dùng cho báo cáo
    @Query("SELECT COUNT(s) FROM Slot s WHERE s.floor.id = :floorId AND s.status = 'AVAILABLE'")
    long countAvailableByFloor(@Param("floorId") UUID floorId);

    // Đếm slot trống theo toàn bộ tòa nhà - dùng khi suggest alternative
    @Query("SELECT COUNT(s) FROM Slot s WHERE s.floor.building.id = :buildingId AND s.status = 'AVAILABLE'")
    long countAvailableByBuilding(@Param("buildingId") UUID buildingId);

    // Lấy tất cả slot theo tòa nhà - dùng cho Slot Map
    @Query("SELECT s FROM Slot s " +
           "JOIN FETCH s.floor f " +
           "JOIN FETCH f.building b " +
           "WHERE b.id = :buildingId " +
           "ORDER BY f.floorNumber ASC, s.slotCode ASC")
    List<Slot> findAllByBuildingId(@Param("buildingId") UUID buildingId);
}
