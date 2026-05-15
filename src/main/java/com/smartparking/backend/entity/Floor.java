package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "floors",
       uniqueConstraints = @UniqueConstraint(columnNames = {"building_id", "floor_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Floor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber; // -2=B2, -1=B1, 1=T1...

    @Column(name = "floor_name", nullable = false, length = 10)
    private String floorName; // "B1", "T1", "T2"...

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id")
    private VehicleType vehicleType; // Loại xe cho phép đỗ tầng này

    @Column(name = "total_slots")
    private Integer totalSlots = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
