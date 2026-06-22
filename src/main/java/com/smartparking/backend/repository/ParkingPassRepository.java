package com.smartparking.backend.repository;

import com.smartparking.backend.entity.Building;
import com.smartparking.backend.entity.ParkingPass;
import com.smartparking.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ParkingPassRepository extends JpaRepository<ParkingPass, UUID> {
    List<ParkingPass> findByUser(User user);
    List<ParkingPass> findByUserAndStatus(User user, ParkingPass.PassStatus status);
    List<ParkingPass> findByLicensePlateAndBuildingAndStatus(String licensePlate, Building building, ParkingPass.PassStatus status);
}
