package com.smartparking.backend.dto.response;

import com.smartparking.backend.entity.ParkingSession;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response trả về sau check-in hoặc check-out.
 * Dùng để FE hiển thị thông tin phiên gửi xe.
 */
@Data
@Builder
public class SessionResponse {

    private UUID sessionId;
    private String sessionCode;

    // Thông tin zone
    private UUID zoneId;
    private String zoneCode;
    private String floorName;
    private String zoneName;

    // Thông tin bãi xe
    private UUID buildingId;
    private String buildingName;
    private String buildingAddress;

    // Thông tin xe
    private UUID vehicleTypeId;
    private String licensePlate;
    private String vehicleType;

    // Thời gian
    private LocalDateTime sessionCreatedAt;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private LocalDateTime zoneEntryTime;
    private LocalDateTime zoneExitTime;
    private Integer durationMinutes;

    // Phí
    private BigDecimal totalFee;

    // Trạng thái
    private ParkingSession.SessionStatus status;
    private ParkingSession.DriverType driverType;
    private String paymentStatus;

    // Hướng dẫn
    private String guideMessage;

    // Ảnh
    private String entryPlateImageUrl;
    private String entryFaceImageUrl;
    private String exitPlateImageUrl;
    private String exitFaceImageUrl;

    // Cổng ra vào
    private String entryMainGateCode;
    private String entryMainGateName;
    private String exitMainGateCode;
    private String exitMainGateName;
    private String entryZoneGateCode;
    private String entryZoneGateName;
    private String exitZoneGateCode;
    private String exitZoneGateName;

    // Thông tin khách / vé
    private String customerName;
    private String passType;
    private String reservationCode;

    // Sai zone
    private Integer wrongZoneCount;
    private Boolean wrongZoneDetected;

    private String notes;

    // Danh sách phân khu khả dụng (gửi kèm khi ở chế độ preview/tìm kiếm)
    private java.util.List<EligibleZoneResponse> eligibleZones;
}