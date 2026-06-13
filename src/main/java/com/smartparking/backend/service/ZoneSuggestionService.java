package com.smartparking.backend.service;

import com.smartparking.backend.entity.*;
import com.smartparking.backend.repository.ZoneRepository;
import org.springframework.stereotype.Service;

/**
 * ZoneSuggestionService — Gợi ý zone tối ưu (Tùng phụ trách)
 *
 * TODO (Tùng): Implement:
 * - suggestZone(vehicleType) → Tìm zone ACTIVE cùng loại xe, ít xe nhất, gần cổng nhất
 * - enterZone(zone)          → currentCount + 1, nếu đầy → status = FULL
 * - exitZone(zone)           → currentCount - 1, nếu trước đó FULL → status = ACTIVE
 */
@Service
public class ZoneSuggestionService {

    // TODO: Inject ZoneRepository
    // TODO: Implement methods
}
