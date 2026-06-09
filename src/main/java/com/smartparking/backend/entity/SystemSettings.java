package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "grace_period_minutes")
    @Builder.Default
    private Integer gracePeriodMinutes = 10;

    @Column(length = 10)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "vat_percentage")
    @Builder.Default
    private Integer vatPercentage = 10;

    @Column(name = "system_name", length = 100)
    @Builder.Default
    private String systemName = "Bãi xe Thông minh SmartParking v2";

    @Column(name = "sos_enabled")
    @Builder.Default
    private Boolean sosEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
