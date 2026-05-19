package com.smartparking.backend.repository;
import com.smartparking.backend.entity.Floor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FloorRepository extends JpaRepository<Floor, UUID> {
}
