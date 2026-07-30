package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vehicle_types")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VehicleType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String name; // Xe máy, Ô tô, Xe điện

    @Column(length = 200)
    private String description;

    @Column(name = "slot_area_sqm")
    private Double slotAreaSqm; // Diện tích 1 slot (m²) — Xe máy: 2.5, Ô tô: 12.5, Xe tải: 37.5

    @Column(name = "max_weight")
    private Double maxWeight; // Trọng lượng tối đa xe (tấn) — Xe tải: 3.5

    @Builder.Default
    @Column(name = "mixable", nullable = false)
    private Boolean mixable = false; // Có được gộp chung tầng "tổng hợp" với các loại mixable khác không (Xe máy/Xe đạp: true)

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
