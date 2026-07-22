package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "buildings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(name = "operating_hours_start")
    private LocalTime operatingHoursStart;

    @Column(name = "operating_hours_end")
    private LocalTime operatingHoursEnd;

    @Column(name = "total_floors")
    private Integer totalFloors; // Tổng số tầng (bao gồm hầm)

    @Column(length = 500)
    private String description; // Mô tả tòa nhà

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
