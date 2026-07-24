package com.smartparking.backend.repository;

import com.smartparking.backend.entity.Building;
import com.smartparking.backend.entity.ParkingPass;
import com.smartparking.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

@Repository
public interface ParkingPassRepository extends JpaRepository<ParkingPass, UUID> {
    List<ParkingPass> findByUser(User user);
    List<ParkingPass> findByUserAndStatus(User user, ParkingPass.PassStatus status);
    List<ParkingPass> findByLicensePlateAndBuildingAndStatus(String licensePlate, Building building, ParkingPass.PassStatus status);
    List<ParkingPass> findByLicensePlateInAndStatus(List<String> licensePlates, ParkingPass.PassStatus status);
    List<ParkingPass> findByLicensePlateAndStatus(String licensePlate, ParkingPass.PassStatus status);
    List<ParkingPass> findByStatusAndCreatedAtBefore(ParkingPass.PassStatus status, java.time.LocalDateTime createdAt);

    java.util.Optional<ParkingPass> findByParkingPassCode(String parkingPassCode);

    @Query("SELECT MAX(p.parkingPassCode) FROM ParkingPass p WHERE p.parkingPassCode LIKE :prefix%")
    String findMaxParkingPassCodeByPrefix(@Param("prefix") String prefix);

    @Query("SELECT MAX(p.licensePlate) FROM ParkingPass p WHERE p.licensePlate LIKE :prefix%")
    String findMaxLicensePlateByPrefix(@Param("prefix") String prefix);
}
