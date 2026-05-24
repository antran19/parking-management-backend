package com.smartparking.backend.config;

import com.smartparking.backend.entity.*;
import com.smartparking.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * DataInitializer — Tự động thêm dữ liệu mẫu khi khởi động app.
 *
 * Chạy 1 lần duy nhất: nếu DB đã có data → bỏ qua.
 * Thứ tự INSERT: VehicleType → Building → Floor → Zone → Gate → PricingRule → User
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final VehicleTypeRepository vehicleTypeRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ZoneRepository zoneRepository;
    private final GateRepository gateRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Nếu DB đã có data → không insert lại
        if (buildingRepository.count() > 0) {
            log.info("📌 DataInitializer: DB đã có data, bỏ qua seed.");
            return;
        }

        log.info("🚀 DataInitializer: Bắt đầu seed dữ liệu mẫu...");

        // ═══════════════════════════════════════════
        // 1. LOẠI XE (VehicleType)
        // ═══════════════════════════════════════════
        VehicleType xeMay = vehicleTypeRepository.save(VehicleType.builder()
                .name("Xe máy")
                .description("Xe gắn máy 2 bánh các loại")
                .build());

        VehicleType oTo = vehicleTypeRepository.save(VehicleType.builder()
                .name("Ô tô")
                .description("Ô tô 4-7 chỗ các loại")
                .build());

        VehicleType xeDien = vehicleTypeRepository.save(VehicleType.builder()
                .name("Xe điện")
                .description("Xe máy điện, ô tô điện")
                .build());

        log.info("✅ Đã tạo 3 loại xe: Xe máy, Ô tô, Xe điện");

        // ═══════════════════════════════════════════
        // 2. TÒA NHÀ (Building)
        // ═══════════════════════════════════════════
        Building building = buildingRepository.save(Building.builder()
                .name("SmartParking Tower")
                .address("123 Nguyễn Văn Linh, Quận 7, TP.HCM")
                .operatingHoursStart(LocalTime.of(6, 0))
                .operatingHoursEnd(LocalTime.of(22, 0))
                .build());

        log.info("✅ Đã tạo tòa nhà: {}", building.getName());

        // ═══════════════════════════════════════════
        // 3. TẦNG + ZONE (Floor → Zone)
        // ═══════════════════════════════════════════
        Floor b2 = createFloor(building, -2, "B2", xeMay, 100);
        Floor b1 = createFloor(building, -1, "B1", xeMay, 120);
        Floor t1 = createFloor(building, 1, "T1", oTo, 60);

        createZone(b2, "A", "Khu A - Xe máy", xeMay, 50, 20);
        createZone(b2, "B", "Khu B - Xe máy", xeMay, 50, 40);
        createZone(b1, "A", "Khu A - Xe máy", xeMay, 60, 15);
        createZone(b1, "B", "Khu B - Xe máy điện", xeDien, 60, 35);
        createZone(t1, "A", "Khu A - Ô tô", oTo, 30, 25);
        createZone(t1, "B", "Khu B - Ô tô", oTo, 30, 45);

        log.info("✅ Đã tạo 3 tầng và 6 zone quản lý theo sức chứa");

        // ═══════════════════════════════════════════
        // 4. CỔNG (Gate)
        // ═══════════════════════════════════════════
        // --- Cổng CHÍNH (Main Gate) ---
        Gate mainGateIn = gateRepository.save(Gate.builder()
                .building(building)
                .gateCode("MAIN-IN")
                .gateName("Cổng chính - Lối vào")
                .gateType(Gate.GateType.MAIN_ENTRY)
                .isActive(true)
                .build());

        Gate mainGateOut = gateRepository.save(Gate.builder()
                .building(building)
                .gateCode("MAIN-OUT")
                .gateName("Cổng chính - Lối ra")
                .gateType(Gate.GateType.MAIN_EXIT)
                .isActive(true)
                .build());

        // --- Cổng TẦNG (Zone Gate) ---
        Gate zoneGateB1 = gateRepository.save(Gate.builder()
                .building(building)
                .gateCode("ZONE-B1")
                .gateName("Cổng tầng B1")
                .gateType(Gate.GateType.ZONE_BOTH)
                .isActive(true)
                .build());

        Gate zoneGateB2 = gateRepository.save(Gate.builder()
                .building(building)
                .gateCode("ZONE-B2")
                .gateName("Cổng tầng B2")
                .gateType(Gate.GateType.ZONE_BOTH)
                .isActive(true)
                .build());

        Gate zoneGateT1 = gateRepository.save(Gate.builder()
                .building(building)
                .gateCode("ZONE-T1")
                .gateName("Cổng tầng T1")
                .gateType(Gate.GateType.ZONE_BOTH)
                .isActive(true)
                .build());

        log.info("✅ Đã tạo 5 cổng: 2 cổng chính (MAIN-IN, MAIN-OUT) + 3 cổng tầng (B1, B2, T1)");

        // ═══════════════════════════════════════════
        // IN UUID ĐỂ TEST POSTMAN
        // ═══════════════════════════════════════════
        log.info("========== UUID DE TEST POSTMAN ==========");
        log.info("vehicleTypeId (Xe may)    : {}", xeMay.getId());
        log.info("vehicleTypeId (O to)      : {}", oTo.getId());
        log.info("mainGateIn    (MAIN-IN)   : {}", mainGateIn.getId());
        log.info("mainGateOut   (MAIN-OUT)  : {}", mainGateOut.getId());
        log.info("zoneGateB1    (ZONE-B1)   : {}", zoneGateB1.getId());
        log.info("zoneGateB2    (ZONE-B2)   : {}", zoneGateB2.getId());
        log.info("zoneGateT1    (ZONE-T1)   : {}", zoneGateT1.getId());
        log.info("===========================================");

        // ═══════════════════════════════════════════
        // 5. BẢNG GIÁ (PricingRule)
        // ═══════════════════════════════════════════
        pricingRuleRepository.save(PricingRule.builder()
                .building(building)
                .vehicleType(xeMay)
                .pricingType(PricingRule.PricingType.HOURLY)
                .pricePerUnit(new BigDecimal("5000"))    // 5.000đ/h
                .freeMinutes(15)                          // Miễn phí 15 phút đầu
                .build());

        pricingRuleRepository.save(PricingRule.builder()
                .building(building)
                .vehicleType(oTo)
                .pricingType(PricingRule.PricingType.HOURLY)
                .pricePerUnit(new BigDecimal("15000"))   // 15.000đ/h
                .freeMinutes(15)
                .build());

        log.info("✅ Đã tạo bảng giá: Xe máy 5.000đ/h, Ô tô 15.000đ/h (free 15 phút)");

        // ═══════════════════════════════════════════
        // 6. TÀI KHOẢN (User) — password đều là "123456"
        // ═══════════════════════════════════════════
        String hashedPassword = passwordEncoder.encode("123456");

        userRepository.save(User.builder()
                .email("admin@parking.vn")
                .passwordHash(hashedPassword)
                .fullName("Admin Hệ Thống")
                .phone("0901000001")
                .role(User.Role.ADMIN)
                .isActive(true)
                .build());

        userRepository.save(User.builder()
                .email("manager@parking.vn")
                .passwordHash(hashedPassword)
                .fullName("Nguyễn Văn Quản Lý")
                .phone("0901000002")
                .role(User.Role.MANAGER)
                .isActive(true)
                .build());

        userRepository.save(User.builder()
                .email("staff@parking.vn")
                .passwordHash(hashedPassword)
                .fullName("Trần Thị Nhân Viên")
                .phone("0901000003")
                .role(User.Role.STAFF)
                .isActive(true)
                .build());

        userRepository.save(User.builder()
                .email("driver@parking.vn")
                .passwordHash(hashedPassword)
                .fullName("Lê Văn Tài Xế")
                .phone("0901000004")
                .role(User.Role.DRIVER)
                .isActive(true)
                .build());

        userRepository.save(User.builder()
                .email("security@parking.vn")
                .passwordHash(hashedPassword)
                .fullName("Phạm Văn Bảo Vệ")
                .phone("0901000005")
                .role(User.Role.SECURITY)
                .isActive(true)
                .build());

        log.info("✅ Đã tạo 5 user: admin, manager, staff, driver, security (password: 123456)");
        log.info("🎉 DataInitializer: Seed hoàn tất!");
    }

    private Floor createFloor(Building building, int floorNumber,
                              String floorName, VehicleType vehicleType,
                              int totalCapacity) {
        return floorRepository.save(Floor.builder()
                .building(building)
                .floorNumber(floorNumber)
                .floorName(floorName)
                .vehicleType(vehicleType)
                .totalSlots(totalCapacity)
                .build());
    }

    private Zone createZone(Floor floor, String zoneCode, String zoneName,
                            VehicleType vehicleType, int capacity, int distanceToGate) {
        return zoneRepository.save(Zone.builder()
                .floor(floor)
                .zoneCode(zoneCode)
                .zoneName(zoneName)
                .vehicleType(vehicleType)
                .capacity(capacity)
                .currentCount(0)
                .reservedCount(0)
                .status(Zone.ZoneStatus.ACTIVE)
                .distanceToGate(distanceToGate)
                .build());
    }
}
