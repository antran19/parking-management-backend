package com.smartparking.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Zone — Khu vực trong một tầng, phân theo loại phương tiện.
 * V2 quản lý sức chứa theo zone thay vì từng slot cố định.
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
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Floor floor;

    @Column(name = "zone_code", nullable = false, length = 10)
    private String zoneCode; // "A", "B", "C"

    @Column(name = "zone_name", nullable = false, length = 50)
    private String zoneName; // "Khu A - Xe máy", "Khu B - Ô tô"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private VehicleType vehicleType; // Loại xe được phép đỗ trong zone này

    @Column(nullable = false)
    private Integer capacity = 0;

    @Column(name = "zone_area")
    private Double zoneArea; // Diện tích thực tế cấp cho zone (m²) — dùng để tính lại slot, null với zone tạo trước khi có tính năng này

    @Column(name = "current_count", nullable = false)
    private Integer currentCount = 0;

    @Column(name = "reserved_count", nullable = false)
    private Integer reservedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ZoneStatus status = ZoneStatus.ACTIVE;

    public enum ZoneStatus {
        ACTIVE, FULL, MAINTENANCE, LOCKED
    }

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
