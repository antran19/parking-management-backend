package com.smartparking.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "blacklist_plates", indexes = {
        @Index(name = "idx_blacklist_plate_normalized", columnList = "normalized_plate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistPlate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "license_plate", nullable = false, length = 50)
    private String licensePlate;

    @Column(name = "normalized_plate", nullable = false, length = 20)
    private String normalizedPlate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BlacklistReason reason;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "blacklist_plate_images", joinColumns = @JoinColumn(name = "blacklist_plate_id"))
    @Column(name = "image_url")
    private List<String> imageUrls;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by_user_id", nullable = false)
    private User addedBy;

    @CreationTimestamp
    @Column(name = "added_at", updatable = false)
    private LocalDateTime addedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removed_by_user_id")
    private User removedBy;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    public enum BlacklistReason {
        STOLEN,
        DISTURBANCE,
        UNPAID_FEE,
        SECURITY_RISK,
        OTHER
    }
}
