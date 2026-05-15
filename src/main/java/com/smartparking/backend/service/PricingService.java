package com.smartparking.backend.service;

import com.smartparking.backend.entity.PricingRule;
import com.smartparking.backend.entity.PricingRule.PricingType;
import com.smartparking.backend.repository.PricingRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Tính phí gửi xe dựa trên bảng giá (PricingRule).
 *
 * Công thức:
 *   - Nếu thời gian <= freeMinutes → miễn phí
 *   - Phí = ceil((durationMinutes - freeMinutes) / 60) × pricePerUnit
 *   - Block giờ: tính tròn lên (ví dụ: 1h01 → tính 2 giờ)
 */
@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    private final PricingRuleRepository pricingRuleRepository;

    public PricingService(PricingRuleRepository pricingRuleRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
    }

    /**
     * Tính phí dựa trên loại xe, tòa nhà, và thời gian gửi.
     *
     * @param buildingId     ID tòa nhà
     * @param vehicleTypeId  ID loại xe
     * @param durationMinutes Thời gian gửi (phút)
     * @return Tổng phí (VNĐ)
     */
    public BigDecimal calculateFee(UUID buildingId, UUID vehicleTypeId, int durationMinutes) {
        // Tìm bảng giá theo giờ (HOURLY) cho tòa nhà + loại xe
        PricingRule rule = pricingRuleRepository
                .findByBuildingIdAndVehicleTypeIdAndPricingType(buildingId, vehicleTypeId, PricingType.HOURLY)
                .orElse(null);

        if (rule == null) {
            log.warn("No pricing rule found for building={}, vehicleType={}. Using default 5000đ/h",
                    buildingId, vehicleTypeId);
            // Giá mặc định nếu chưa cấu hình bảng giá
            return calculateDefault(durationMinutes);
        }

        // Trừ số phút miễn phí
        int freeMinutes = rule.getFreeMinutes() != null ? rule.getFreeMinutes() : 0;
        int chargeableMinutes = Math.max(0, durationMinutes - freeMinutes);

        if (chargeableMinutes == 0) {
            return BigDecimal.ZERO;
        }

        // Tính theo block giờ (làm tròn lên)
        int hours = (int) Math.ceil((double) chargeableMinutes / 60.0);

        BigDecimal totalFee = rule.getPricePerUnit().multiply(BigDecimal.valueOf(hours));

        log.info("Fee calculated: {}đ (duration={}min, free={}min, blocks={}h, rate={}đ/h)",
                totalFee, durationMinutes, freeMinutes, hours, rule.getPricePerUnit());

        return totalFee.setScale(0, RoundingMode.CEILING);
    }

    /**
     * Tính phí tạm tính (preview) — dùng cho Driver xem phí ước lượng.
     */
    public BigDecimal estimateFee(UUID buildingId, UUID vehicleTypeId, int estimatedMinutes) {
        return calculateFee(buildingId, vehicleTypeId, estimatedMinutes);
    }

    /**
     * Giá mặc định khi chưa có bảng giá: 5.000đ/giờ.
     */
    private BigDecimal calculateDefault(int durationMinutes) {
        int hours = (int) Math.ceil((double) durationMinutes / 60.0);
        return BigDecimal.valueOf(5000L * hours);
    }
}
