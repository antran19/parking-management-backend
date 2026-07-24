package com.smartparking.backend.service;

import com.smartparking.backend.entity.PricingRule;
import com.smartparking.backend.repository.PricingRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Flow #1: Parking Session Management — Tính phí gửi xe
 *
 * Test PricingService — lõi tính toán phí gửi xe:
 * - Miễn phí nếu nằm trong freeMinutes
 * - Tính theo block giờ (làm tròn lên)
 * - Giá mặc định khi chưa cấu hình bảng giá
 */
@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @InjectMocks
    private PricingService pricingService;

    private UUID buildingId;
    private UUID vehicleTypeId;

    @BeforeEach
    void setUp() {
        buildingId = UUID.randomUUID();
        vehicleTypeId = UUID.randomUUID();
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Gửi xe trong thời gian miễn phí → Phí = 0đ
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow1: Gửi xe 5 phút (free 10 phút) → Phí = 0đ")
    void calculateFee_withinFreeMinutes_shouldReturnZero() {
        PricingRule rule = PricingRule.builder()
                .pricePerUnit(BigDecimal.valueOf(5000))
                .freeMinutes(10)
                .pricingType(PricingRule.PricingType.HOURLY)
                .build();

        when(pricingRuleRepository.findByBuildingIdAndVehicleTypeIdAndPricingType(
                eq(buildingId), eq(vehicleTypeId), eq(PricingRule.PricingType.HOURLY)))
                .thenReturn(Optional.of(rule));

        BigDecimal fee = pricingService.calculateFee(buildingId, vehicleTypeId, 5);

        assertEquals(BigDecimal.ZERO, fee);
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Gửi 90 phút → 2 block giờ → 10.000đ
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow1: Gửi xe 90 phút (free 10 phút) → 2 block × 5.000đ = 10.000đ")
    void calculateFee_90minutes_shouldReturn10000() {
        PricingRule rule = PricingRule.builder()
                .pricePerUnit(BigDecimal.valueOf(5000))
                .freeMinutes(10)
                .pricingType(PricingRule.PricingType.HOURLY)
                .build();

        when(pricingRuleRepository.findByBuildingIdAndVehicleTypeIdAndPricingType(
                eq(buildingId), eq(vehicleTypeId), eq(PricingRule.PricingType.HOURLY)))
                .thenReturn(Optional.of(rule));

        // 90 phút - 10 phút free = 80 phút → ceil(80/60) = 2 block → 2 × 5000 = 10000
        BigDecimal fee = pricingService.calculateFee(buildingId, vehicleTypeId, 90);

        assertEquals(new BigDecimal("10000"), fee);
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Gửi đúng 1 giờ → 1 block → 5.000đ
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow1: Gửi xe 60 phút (free 0) → 1 block × 5.000đ = 5.000đ")
    void calculateFee_exactlyOneHour_shouldReturn5000() {
        PricingRule rule = PricingRule.builder()
                .pricePerUnit(BigDecimal.valueOf(5000))
                .freeMinutes(0)
                .pricingType(PricingRule.PricingType.HOURLY)
                .build();

        when(pricingRuleRepository.findByBuildingIdAndVehicleTypeIdAndPricingType(
                eq(buildingId), eq(vehicleTypeId), eq(PricingRule.PricingType.HOURLY)))
                .thenReturn(Optional.of(rule));

        BigDecimal fee = pricingService.calculateFee(buildingId, vehicleTypeId, 60);

        assertEquals(new BigDecimal("5000"), fee);
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Chưa cấu hình bảng giá → Dùng giá mặc định 5.000đ/h
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow1: Chưa cấu hình bảng giá → Giá mặc định 5.000đ/h")
    void calculateFee_noPricingRule_shouldUseDefault() {
        when(pricingRuleRepository.findByBuildingIdAndVehicleTypeIdAndPricingType(
                any(), any(), any()))
                .thenReturn(Optional.empty());

        // 120 phút → 2 block → 2 × 5000 = 10.000đ (giá mặc định)
        BigDecimal fee = pricingService.calculateFee(buildingId, vehicleTypeId, 120);

        assertEquals(BigDecimal.valueOf(10000), fee);
    }
}
