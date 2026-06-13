package com.smartparking.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pricing_rules",
       uniqueConstraints = @UniqueConstraint(columnNames = {"building_id", "vehicle_type_id", "pricing_type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "building_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Building building;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_type", nullable = false, length = 10)
    private PricingType pricingType;

    @Column(name = "price_per_unit", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerUnit; // Giá mỗi giờ/ngày/tháng

    @Column(name = "free_minutes")
    private Integer freeMinutes = 0; // Số phút miễn phí đầu tiên

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum PricingType {
        HOURLY, DAILY, MONTHLY, QUARTERLY, YEARLY
    }
}
