package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.BlacklistPlateRequest;
import com.smartparking.backend.dto.response.BlacklistPlateResponse;
import com.smartparking.backend.entity.BlacklistPlate;
import com.smartparking.backend.repository.BlacklistPlateRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * BlacklistService — Quản lý biển số đen (Thiên phụ trách)
 *
 * TODO (Thiên): Implement:
 * - getAllBlacklist()               → Lấy danh sách biển số đen
 * - addToBlacklist(request)         → Thêm biển số vào blacklist
 * - removeFromBlacklist(id)         → Gỡ biển số khỏi blacklist
 * - isBlacklisted(licensePlate)     → Kiểm tra biển số có trong danh sách đen không
 *
 * Lưu ý: Khi thêm/xóa blacklist, broadcast WebSocket /topic/blacklist
 */
@Service
public class BlacklistService {

    // TODO: Inject BlacklistPlateRepository, SimpMessagingTemplate
    // TODO: Implement methods
}
