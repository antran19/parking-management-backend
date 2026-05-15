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
    private String sessionCode;   // Mã vé: PS20240513001

    // Thông tin slot được phân bổ
    private String slotCode;      // B2-A03
    private String floorName;     // "B2"
    private String zoneName;      // "Khu A - Xe máy"

    // Thông tin xe
    private String licensePlate;
    private String vehicleType;

    // Thời gian
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private Integer durationMinutes;

    // Phí
    private BigDecimal totalFee;

    // Trạng thái session
    private ParkingSession.SessionStatus status;

    // Trạng thái thanh toán (dùng khi check-out)
    private String paymentStatus;

    // Message hướng dẫn cho tài xế (hiển thị tại cổng vào)
    private String guideMessage; // "Vui lòng đến Tầng B2 - Khu A - Ô số B2-A03"
}
