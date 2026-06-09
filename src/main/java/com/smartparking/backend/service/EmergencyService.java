package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.*;
import com.smartparking.backend.dto.response.EmergencyStatusResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.repository.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * EmergencyService — SOS khẩn cấp (Thiên phụ trách)
 *
 * TODO (Thiên): Implement:
 * - activateEmergency(request)   → Kích hoạt SOS → Lưu EmergencyEvent → WebSocket broadcast
 * - deactivateEmergency(request) → Hủy SOS → Cập nhật event → WebSocket broadcast
 * - getCurrentStatus()           → Trạng thái SOS hiện tại (ACTIVE/INACTIVE)
 * - getHistory()                 → Lịch sử SOS events
 * - getSettings() / updateSettings() → Cấu hình SOS (auto-deactivate timeout...)
 *
 * Lưu ý: Broadcast WebSocket /topic/emergency khi thay đổi trạng thái
 */
@Service
public class EmergencyService {

    // TODO: Inject EmergencyEventRepository, SystemSettingsRepository, SimpMessagingTemplate
    // TODO: Implement methods
}
