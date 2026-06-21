package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Building building;

    @Column(name = "gate_code", nullable = false, length = 20)
    private String gateCode; // GATE-A, GATE-B

    @Column(name = "gate_name", length = 50)
    private String gateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gate_type", nullable = false, length = 10)
    private GateType gateType;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum GateType {
        MAIN_ENTRY, // Cổng chính - vào
        MAIN_EXIT, // Cổng chính - ra
        MAIN_BOTH, // Cổng chính - vào + ra
        ZONE_ENTRY, // Cổng tầng - vào khu
        ZONE_EXIT, // Cổng tầng - ra khu
        ZONE_BOTH // Cổng tầng - vào + ra khu
    }
}
