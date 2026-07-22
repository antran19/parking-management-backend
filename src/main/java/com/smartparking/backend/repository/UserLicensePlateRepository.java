package com.smartparking.backend.repository;

import com.smartparking.backend.entity.User;
import com.smartparking.backend.entity.UserLicensePlate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserLicensePlateRepository extends JpaRepository<UserLicensePlate, UUID> {
    List<UserLicensePlate> findByUser(User user);
    Optional<UserLicensePlate> findByUserAndLicensePlate(User user, String licensePlate);
    void deleteByUserAndLicensePlate(User user, String licensePlate);
    List<UserLicensePlate> findByLicensePlate(String licensePlate);
    List<UserLicensePlate> findByLicensePlateIn(List<String> licensePlates);
}
