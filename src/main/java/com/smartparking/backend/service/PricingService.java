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
 * Service tính phí gửi xe dựa trên các quy tắc cấu hình bảng giá (PricingRule).
 *
 * Công thức tính phí theo block giờ:
 * - Nếu thời gian gửi <= số phút miễn phí cấu hình (freeMinutes): Phí = 0 VNĐ.
 * - Phần thời gian còn lại được chia cho 60 và làm tròn lên (ceil) thành các block giờ gửi xe.
 * - Tổng phí = số block giờ * giá mỗi block (pricePerUnit).
 */
@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    private final PricingRuleRepository pricingRuleRepository;

    public PricingService(PricingRuleRepository pricingRuleRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
    }

    /**
     * Tính phí thực tế dựa trên loại xe, tòa nhà, và thời gian gửi (phút).
     */
    public BigDecimal calculateFee(UUID buildingId, UUID vehicleTypeId, int durationMinutes) {
        // Tìm bảng giá theo giờ (HOURLY) cho tòa nhà + loại xe tương ứng
        PricingRule rule = pricingRuleRepository
                .findByBuildingIdAndVehicleTypeIdAndPricingType(buildingId, vehicleTypeId, PricingType.HOURLY)
                .orElse(null);

        if (rule == null) {
            log.warn("No pricing rule found for building={}, vehicleType={}. Using default 5000đ/h",
                    buildingId, vehicleTypeId);
            // Giá mặc định nếu chưa được admin cấu hình bảng giá trong DB
            return calculateDefault(durationMinutes);
        }

        // Trừ số phút miễn phí (ví dụ 10 phút đầu vào bãi không tính tiền)
        int freeMinutes = rule.getFreeMinutes() != null ? rule.getFreeMinutes() : 0;
        int chargeableMinutes = Math.max(0, durationMinutes - freeMinutes);

        if (chargeableMinutes == 0) {
            return BigDecimal.ZERO;
        }

        // Tính theo block giờ (làm tròn lên: ví dụ đỗ 61 phút tính là 2 giờ)
        int hours = (int) Math.ceil((double) chargeableMinutes / 60.0);

        BigDecimal totalFee = rule.getPricePerUnit().multiply(BigDecimal.valueOf(hours));

        log.info("Fee calculated: {}đ (duration={}min, free={}min, blocks={}h, rate={}đ/h)",
                totalFee, durationMinutes, freeMinutes, hours, rule.getPricePerUnit());

        return totalFee.setScale(0, RoundingMode.CEILING);
    }

    /**
     * Ước lượng phí tạm tính cho phiên gửi xe đang chạy.
     */
    public BigDecimal estimateFee(UUID buildingId, UUID vehicleTypeId, int estimatedMinutes) {
        return calculateFee(buildingId, vehicleTypeId, estimatedMinutes);
    }

    /**
     * Phương thức dự phòng khi chưa cấu hình bảng giá trong DB: Tính mặc định 5.000đ / block giờ.
     */
    private BigDecimal calculateDefault(int durationMinutes) {
        int hours = (int) Math.ceil((double) durationMinutes / 60.0);
        return BigDecimal.valueOf(5000L * hours);
    }
}

