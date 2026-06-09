package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.*;
import com.smartparking.backend.dto.response.SessionResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.repository.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ParkingSessionService — Core logic check-in/out (Tùng phụ trách)
 *
 * TODO (Tùng): Implement các method sau:
 * 1. checkIn(CheckInRequest)      → Validate biển số → Gợi ý zone → Tạo session → WebSocket broadcast
 * 2. checkOut(CheckOutRequest)    → Tìm session → Tính phí → Tạo payment → Giải phóng zone → WebSocket
 * 3. findActiveSession(request)   → Tìm session ACTIVE theo biển số/sessionCode/sessionId
 *
 * Lưu ý:
 * - Dùng ZoneSuggestionService.suggestZone() để gợi ý zone tối ưu
 * - Dùng PricingService.calculateFee() để tính phí
 * - Sau mỗi thay đổi zone, gọi broadcastZoneChange() để push WebSocket
 * - Kiểm tra blacklist khi check-in
 */
@Service
public class ParkingSessionService {

    // TODO: Inject repositories + services cần thiết
    // private final ParkingSessionRepository sessionRepository;
    // private final ZoneSuggestionService zoneSuggestionService;
    // private final PricingService pricingService;
    // private final SimpMessagingTemplate messagingTemplate;
    // ... thêm các repository khác

    // TODO: Constructor injection

    // TODO: Implement checkIn()
    // TODO: Implement checkOut()
    // TODO: Implement findActiveSession()
    // TODO: Implement broadcastZoneChange() — gửi WebSocket /topic/zones/{buildingId}
}
