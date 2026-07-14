package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "parking_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_code", unique = true, nullable = false, length = 20)
    private String sessionCode; // Mã vé - PS20240513001

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "driver_type", nullable = false, length = 20)
    private DriverType driverType = DriverType.WALK_IN;

    // === 4 cổng theo flow v2 ===

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_main_gate_id")
    private Gate entryMainGate; // Cổng CHÍNH khi xe vào

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_zone_gate_id")
    private Gate entryZoneGate; // Cổng TẦNG khi xe vào khu

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_zone_gate_id")
    private Gate exitZoneGate; // Cổng TẦNG khi xe ra khu

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_main_gate_id")
    private Gate exitMainGate; // Cổng CHÍNH khi xe ra

    // === Thông tin xe ===

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "license_plate", nullable = false, length = 15)
    private String licensePlate;

    // === Nhân viên xử lý ===

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_entry_id")
    private User staffEntry; // Nhân viên làm thủ tục vào

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_exit_id")
    private User staffExit; // Nhân viên làm thủ tục ra

    // === Thời gian ===

    @Column(name = "entry_time", nullable = false)
    private LocalDateTime entryTime; // Thời gian qua cổng CHÍNH vào

    @Column(name = "exit_time")
    private LocalDateTime exitTime; // Thời gian qua cổng CHÍNH ra

    @Column(name = "zone_entry_time")
    private LocalDateTime zoneEntryTime; // Thời gian quét QR vào khu

    @Column(name = "zone_exit_time")
    private LocalDateTime zoneExitTime; // Thời gian quét QR ra khu

    // === Phí ===

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Builder.Default
    @Column(name = "base_fee", precision = 10, scale = 2)
    private BigDecimal baseFee = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_fee", precision = 10, scale = 2)
    private BigDecimal totalFee = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "entry_plate_image_url", length = 255)
    private String entryPlateImageUrl;

    @Column(name = "entry_face_image_url", length = 255)
    private String entryFaceImageUrl;

    @Column(name = "exit_plate_image_url", length = 255)
    private String exitPlateImageUrl;

    @Column(name = "exit_face_image_url", length = 255)
    private String exitFaceImageUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum DriverType {
        WALK_IN, PRE_BOOKED, SUBSCRIBER
    }

    public enum SessionStatus {
        ACTIVE, COMPLETED, CANCELLED
    }
}
