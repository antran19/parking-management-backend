package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "parking_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParkingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_code", unique = true, nullable = false, length = 20)
    private String sessionCode; // Mã vé - PS20240513001

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private Slot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_entry_id", nullable = false)
    private Gate gateEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_exit_id")
    private Gate gateExit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "license_plate", nullable = false, length = 15)
    private String licensePlate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_entry_id")
    private User staffEntry; // Nhân viên làm thủ tục vào

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_exit_id")
    private User staffExit; // Nhân viên làm thủ tục ra

    @Column(name = "entry_time", nullable = false)
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "base_fee", precision = 10, scale = 2)
    private BigDecimal baseFee = BigDecimal.ZERO;

    @Column(name = "total_fee", precision = 10, scale = 2)
    private BigDecimal totalFee = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum SessionStatus {
        ACTIVE, COMPLETED, CANCELLED
    }
}
