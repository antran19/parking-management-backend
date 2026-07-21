package com.smartparking.backend.repository;

import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.entity.Zone.ZoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, UUID> {

       @Query("SELECT z FROM Zone z " +
                     "JOIN FETCH z.floor f " +
                     "JOIN FETCH f.building b " +
                     "WHERE z.vehicleType.id = :vehicleTypeId " +
                     "AND z.status = :status " +
                     "AND z.currentCount + z.reservedCount < z.capacity " +
                     "ORDER BY z.distanceToGate ASC")
       List<Zone> findAvailableZonesByVehicleType(@Param("vehicleTypeId") UUID vehicleTypeId,
                     @Param("status") ZoneStatus status);

       @Query("SELECT z FROM Zone z " +
                     "JOIN FETCH z.floor f " +
                     "JOIN FETCH f.building b " +
                     "WHERE b.id = :buildingId " +
                     "ORDER BY f.floorNumber ASC, z.zoneCode ASC")
       List<Zone> findAllByBuildingId(@Param("buildingId") UUID buildingId);

       List<Zone> findAllByFloorId(UUID floorId);

       @Query("SELECT z FROM Zone z " +
              "JOIN FETCH z.floor f " +
              "JOIN FETCH f.building b " +
              "WHERE z.vehicleType.id = :vehicleTypeId " +
              "AND z.status IN :statuses")
       List<Zone> findByVehicleTypeIdAndStatusIn(@Param("vehicleTypeId") UUID vehicleTypeId,
                     @Param("statuses") List<ZoneStatus> statuses);
}
