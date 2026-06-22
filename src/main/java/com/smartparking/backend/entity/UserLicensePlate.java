package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thực thể liên kết lưu trữ biển số xe của User (Tài xế) dưới database.
 * Giải quyết dứt điểm vấn đề mất biển số xe đã đăng ký khi F5 hoặc logout.
 */
@Entity
@Table(name = "user_license_plates", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "license_plate"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLicensePlate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "license_plate", nullable = false, length = 15)
    private String licensePlate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_type_id")
    private VehicleType vehicleType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
