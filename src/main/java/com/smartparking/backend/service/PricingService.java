package com.smartparking.backend.service;

import com.smartparking.backend.repository.PricingRuleRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * PricingService — Tính phí gửi xe (Tùng phụ trách)
 *
 * TODO (Tùng): Implement:
 * - calculateFee(buildingId, vehicleTypeId, durationMinutes)
 *   + Tìm PricingRule theo buildingId + vehicleTypeId
 *   + Công thức: ceil((durationMinutes - freeMinutes) / 60) × pricePerUnit
 *   + Nếu duration <= freeMinutes → trả 0
 */
@Service
public class PricingService {

    // TODO: Inject PricingRuleRepository
    // TODO: Implement calculateFee()
}
