package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Zone — Khu vực trong một tầng, phân theo loại phương tiện.
 * Ví dụ: Tầng B1 có Zone A (xe máy), Zone B (ô tô).
 * Hierarchy: Building → Floor → Zone → Slot
 */
@Entity
@Table(name = "zones",
       uniqueConstraints = @UniqueConstraint(columnNames = {"floor_id", "zone_code"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @Column(name = "zone_code", nullable = false, length = 10)
    private String zoneCode; // "A", "B", "C"

    @Column(name = "zone_name", nullable = false, length = 50)
    private String zoneName; // "Khu A - Xe máy", "Khu B - Ô tô"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private VehicleType vehicleType; // Loại xe được phép đỗ trong zone này

    @Column(name = "total_slots")
    private Integer totalSlots = 0;

    @Column(name = "distance_to_elevator")
    private Integer distanceToElevator; // Khoảng cách tới thang máy (m) — dùng cho AI scoring

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
