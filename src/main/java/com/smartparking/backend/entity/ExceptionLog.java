package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "exception_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExceptionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 30)
    private ExceptionType exceptionType;

    @Column(name = "license_plate", length = 20)
    private String licensePlate;

    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy; // Nhân viên xử lý

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private ExceptionStatus status = ExceptionStatus.PENDING; // Thêm trạng thái xử lý

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exception_log_images", joinColumns = @JoinColumn(name = "exception_log_id"))
    @Column(name = "image_url")
    private List<String> imageUrls; // Thêm danh sách URL ảnh minh chứng

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum ExceptionType {
        LOST_TICKET, WRONG_PLATE, OVERTIME, WRONG_ZONE, UNPAID, SUSPICIOUS_BEHAVIOR, OTHER
    }

    public enum ExceptionStatus {
        PENDING, RESOLVED
    }
}