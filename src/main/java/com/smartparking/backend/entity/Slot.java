package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "slots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @Column(name = "slot_code", nullable = false, length = 20)
    private String slotCode; // T1-A01, B1-C15

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SlotStatus status = SlotStatus.AVAILABLE;

    @Column(name = "distance_to_gate")
    private Integer distanceToGate; // Khoảng cách tới cổng (m) - dùng cho AI scoring

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum SlotStatus {
        AVAILABLE, OCCUPIED, RESERVED, MAINTENANCE, LOCKED
    }
}
