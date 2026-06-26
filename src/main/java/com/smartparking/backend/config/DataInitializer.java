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
import java.util.List;

/**
 * DataInitializer — Tự động bổ sung dữ liệu mẫu khi khởi động app.
 *
 * Idempotent: thiếu dữ liệu nào thì tạo dữ liệu đó, không bỏ qua toàn bộ seed
 * chỉ vì một bảng đã có dữ liệu từ lần chạy trước.
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
    private final UserLicensePlateRepository userLicensePlateRepository;
    private final SystemSettingsRepository systemSettingsRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("🚀 DataInitializer: Kiểm tra và bổ sung dữ liệu mẫu nếu thiếu...");

        VehicleType xeDap = getOrCreateVehicleType("Xe đạp", "Xe đạp thường, xe đạp điện");
        VehicleType xeMay = getOrCreateVehicleType("Xe máy", "Xe gắn máy 2 bánh các loại");
        VehicleType oTo = getOrCreateVehicleType("Ô tô", "Ô tô 4-5-7 chỗ các loại");
        VehicleType xeTai = getOrCreateVehicleType("Xe tải", "Xe tải nhẹ, xe van, xe bán tải");

        Building building = getOrCreateBuilding("SmartParking Tower", "123 Nguyễn Văn Linh, Quận 7, TP.HCM");

        // Cấu trúc hàm: getOrCreateZone(floor, zoneCode, zoneName, vehicleType, capacity (sức chứa), distanceToGate (khoảng cách tới cổng - mét))
        // Cấu trúc hàm: getOrCreateFloor(building, floorNumber (số tầng, âm là hầm), floorName (tên tầng), mainVehicleType, totalSlots (tổng sức chứa tầng))
        Floor b2 = getOrCreateFloor(building, -2, "B2", xeMay, 120 /* Tổng sức chứa */);
        Zone b2a = getOrCreateZone(b2, "A", "Khu A - Xe máy", xeMay, 50 /* Sức chứa */, 20 /* Khoảng cách (m) */);
        Zone b2b = getOrCreateZone(b2, "B", "Khu B - Xe máy", xeMay, 40 /* Sức chứa */, 40 /* Khoảng cách (m) */);
        Zone b2c = getOrCreateZone(b2, "C", "Khu C - Xe đạp", xeDap, 30 /* Sức chứa */, 15 /* Khoảng cách (m) */);

        Floor b1 = getOrCreateFloor(building, -1, "B1", xeMay, 140 /* Tổng sức chứa */);
        Zone b1a = getOrCreateZone(b1, "A", "Khu A - Xe máy", xeMay, 60 /* Sức chứa */, 15 /* Khoảng cách (m) */);
        Zone b1b = getOrCreateZone(b1, "B", "Khu B - Xe máy", xeMay, 50 /* Sức chứa */, 35 /* Khoảng cách (m) */);
        Zone b1c = getOrCreateZone(b1, "C", "Khu C - Xe đạp", xeDap, 30 /* Sức chứa */, 10 /* Khoảng cách (m) */);

        Floor t1 = getOrCreateFloor(building, 1, "T1", oTo, 80 /* Tổng sức chứa */);
        Zone t1a = getOrCreateZone(t1, "A", "Khu A - Ô tô", oTo, 40 /* Sức chứa */, 25 /* Khoảng cách (m) */);
        Zone t1b = getOrCreateZone(t1, "B", "Khu B - Ô tô", oTo, 40 /* Sức chứa */, 45 /* Khoảng cách (m) */);

        Floor t2 = getOrCreateFloor(building, 2, "T2", xeTai, 40 /* Tổng sức chứa */);
        Zone t2a = getOrCreateZone(t2, "A", "Khu A - Xe tải", xeTai, 20 /* Sức chứa */, 30 /* Khoảng cách (m) */);
        Zone t2b = getOrCreateZone(t2, "B", "Khu B - Xe tải", xeTai, 20 /* Sức chứa */, 50 /* Khoảng cách (m) */);

        // Cấu trúc hàm: getOrCreateGate(building, gateCode (mã cổng), gateName (tên cổng), gateType, zone (nếu có))
        getOrCreateGate(building, "MAIN-IN", "Cổng chính - Lối vào", Gate.GateType.MAIN_ENTRY);
        getOrCreateGate(building, "MAIN-OUT", "Cổng chính - Lối ra", Gate.GateType.MAIN_EXIT);
        getOrCreateGate(building, "ZONE-B1", "Cổng tầng B1", Gate.GateType.ZONE_BOTH, b1a);
        getOrCreateGate(building, "ZONE-B2", "Cổng tầng B2", Gate.GateType.ZONE_BOTH, b2a);
        getOrCreateGate(building, "ZONE-T1", "Cổng tầng T1", Gate.GateType.ZONE_BOTH, t1a);
        getOrCreateGate(building, "ZONE-T2", "Cổng tầng T2", Gate.GateType.ZONE_BOTH, t2a);

        // Tạo các cổng zone phụ liên kết trực tiếp với Zone
        getOrCreateZoneGate(building, "GATE-ZONE-B2-A", "Cổng Zone B2-A (Xe máy)", Gate.GateType.ZONE_ENTRY, b2a);
        getOrCreateZoneGate(building, "GATE-ZONE-B2-B", "Cổng Zone B2-B (Xe máy)", Gate.GateType.ZONE_ENTRY, b2b);
        getOrCreateZoneGate(building, "GATE-ZONE-B2-C", "Cổng Zone B2-C (Xe đạp)", Gate.GateType.ZONE_ENTRY, b2c);

        getOrCreateZoneGate(building, "GATE-ZONE-B1-A", "Cổng Zone B1-A (Xe máy)", Gate.GateType.ZONE_ENTRY, b1a);
        getOrCreateZoneGate(building, "GATE-ZONE-B1-B", "Cổng Zone B1-B (Xe máy)", Gate.GateType.ZONE_ENTRY, b1b);
        getOrCreateZoneGate(building, "GATE-ZONE-B1-C", "Cổng Zone B1-C (Xe đạp)", Gate.GateType.ZONE_ENTRY, b1c);

        getOrCreateZoneGate(building, "GATE-ZONE-T1-A", "Cổng Zone T1-A (Ô tô)", Gate.GateType.ZONE_ENTRY, t1a);
        getOrCreateZoneGate(building, "GATE-ZONE-T1-B", "Cổng Zone T1-B (Ô tô)", Gate.GateType.ZONE_ENTRY, t1b);

        getOrCreateZoneGate(building, "GATE-ZONE-T2-A", "Cổng Zone T2-A (Xe tải)", Gate.GateType.ZONE_ENTRY, t2a);
        getOrCreateZoneGate(building, "GATE-ZONE-T2-B", "Cổng Zone T2-B (Xe tải)", Gate.GateType.ZONE_ENTRY, t2b);

        // Cấu trúc hàm: seedPricing(building, vehicleType, hourlyPrice (phí theo giờ), dailyPrice (phí ngày), monthlyPrice (phí tháng), hourlyFreeMinutes (phút miễn phí))
        seedPricing(building, xeDap, "2000" /* Phí giờ */, "10000" /* Phí ngày */, "100000" /* Phí tháng */, 30 /* Phút miễn phí */);
        seedPricing(building, xeMay, "5000" /* Phí giờ */, "25000" /* Phí ngày */, "200000" /* Phí tháng */, 15 /* Phút miễn phí */);
        seedPricing(building, oTo, "15000" /* Phí giờ */, "80000" /* Phí ngày */, "1500000" /* Phí tháng */, 15 /* Phút miễn phí */);
        seedPricing(building, xeTai, "25000" /* Phí giờ */, "120000" /* Phí ngày */, "2500000" /* Phí tháng */, 15 /* Phút miễn phí */);

        // Cấu trúc hàm: getOrCreateUser(email, fullName, phone, role)
        User admin = getOrCreateUser("admin@parking.vn", "Admin Hệ Thống", "0901000001", User.Role.ADMIN);
        User manager = getOrCreateUser("manager@parking.vn", "Nguyễn Văn Quản Lý", "0901000002", User.Role.MANAGER);
        User staff = getOrCreateUser("staff@parking.vn", "Trần Thị Nhân Viên", "0901000003", User.Role.STAFF);
        User driver = getOrCreateUser("driver@parking.vn", "Lê Văn Tài Xế", "0901000004", User.Role.DRIVER);
        User security = getOrCreateUser("security@parking.vn", "Phạm Văn Bảo Vệ", "0901000005", User.Role.SECURITY);

        userLicensePlateRepository.findByUserAndLicensePlate(driver, "30A-999.88")
                .orElseGet(() -> userLicensePlateRepository.save(UserLicensePlate.builder()
                        .user(driver)
                        .licensePlate("30A-999.88")
                        .build()));

        systemSettingsRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> systemSettingsRepository.save(SystemSettings.builder()
                        .gracePeriodMinutes(10)
                        .currency("VND")
                        .vatPercentage(10)
                        .systemName("Bãi xe Thông minh SmartParking v2")
                        .sosEnabled(true)
                        .build()));

        log.info("✅ DataInitializer: Seed/repair hoàn tất. Users: {}, Buildings: {}, Floors: {}, Zones: {}, Gates: {}, PricingRules: {}",
                userRepository.count(), buildingRepository.count(), floorRepository.count(), zoneRepository.count(), gateRepository.count(), pricingRuleRepository.count());
        log.debug("Seed accounts ready: {}, {}, {}, {}, {} / password: 123456",
                admin.getEmail(), manager.getEmail(), staff.getEmail(), driver.getEmail(), security.getEmail());
    }

    private VehicleType getOrCreateVehicleType(String name, String description) {
        return vehicleTypeRepository.findByName(name)
                .orElseGet(() -> vehicleTypeRepository.save(VehicleType.builder()
                        .name(name)
                        .description(description)
                        .build()));
    }

    private Building getOrCreateBuilding(String name, String address) {
        return buildingRepository.findAll().stream()
                .filter(b -> name.equalsIgnoreCase(b.getName()))
                .findFirst()
                .orElseGet(() -> buildingRepository.save(Building.builder()
                        .name(name)
                        .address(address)
                        .operatingHoursStart(LocalTime.of(6, 0))
                        .operatingHoursEnd(LocalTime.of(22, 0))
                        .build()));
    }

    private Floor getOrCreateFloor(Building building, int floorNumber, String floorName, VehicleType vehicleType, int totalCapacity) {
        return floorRepository.findByBuildingId(building.getId()).stream()
                .filter(f -> floorNumber == f.getFloorNumber())
                .findFirst()
                .orElseGet(() -> floorRepository.save(Floor.builder()
                        .building(building)
                        .floorNumber(floorNumber)
                        .floorName(floorName)
                        .vehicleType(vehicleType)
                        .totalSlots(totalCapacity)
                        .build()));
    }

    private Zone getOrCreateZone(Floor floor, String zoneCode, String zoneName, VehicleType vehicleType, int capacity, int distanceToGate) {
        List<Zone> zones = zoneRepository.findAllByBuildingId(floor.getBuilding().getId());
        return zones.stream()
                .filter(z -> z.getFloor() != null && floor.getId().equals(z.getFloor().getId()) && zoneCode.equalsIgnoreCase(z.getZoneCode()))
                .findFirst()
                .orElseGet(() -> zoneRepository.save(Zone.builder()
                        .floor(floor)
                        .zoneCode(zoneCode)
                        .zoneName(zoneName)
                        .vehicleType(vehicleType)
                        .capacity(capacity)
                        .currentCount(0)
                        .reservedCount(0)
                        .status(Zone.ZoneStatus.ACTIVE)
                        .distanceToGate(distanceToGate)
                        .build()));
    }

    private Gate getOrCreateGate(Building building, String gateCode, String gateName, Gate.GateType gateType) {
        return getOrCreateGate(building, gateCode, gateName, gateType, null);
    }

    private Gate getOrCreateGate(Building building, String gateCode, String gateName, Gate.GateType gateType, Zone zone) {
        return gateRepository.findByBuildingId(building.getId()).stream()
                .filter(g -> gateCode.equalsIgnoreCase(g.getGateCode()))
                .findFirst()
                .map(g -> {
                    // Cập nhật zone nếu cổng đã tồn tại nhưng chưa gán zone
                    if (zone != null && g.getZone() == null) {
                        g.setZone(zone);
                        return gateRepository.save(g);
                    }
                    return g;
                })
                .orElseGet(() -> gateRepository.save(Gate.builder()
                        .building(building)
                        .gateCode(gateCode)
                        .gateName(gateName)
                        .gateType(gateType)
                        .zone(zone)
                        .isActive(true)
                        .build()));
    }

    private Gate getOrCreateZoneGate(Building building, String gateCode, String gateName, Gate.GateType gateType, Zone zone) {
        Gate gate = gateRepository.findByBuildingId(building.getId()).stream()
                .filter(g -> gateCode.equalsIgnoreCase(g.getGateCode()))
                .findFirst()
                .orElse(null);
        if (gate == null) {
            return gateRepository.save(Gate.builder()
                    .building(building)
                    .gateCode(gateCode)
                    .gateName(gateName)
                    .gateType(gateType)
                    .zone(zone)
                    .isActive(true)
                    .build());
        } else {
            boolean updated = false;
            if (gate.getZone() == null && zone != null) {
                gate.setZone(zone);
                updated = true;
            }
            if (gate.getGateType() != gateType) {
                gate.setGateType(gateType);
                updated = true;
            }
            if (updated) {
                return gateRepository.save(gate);
            }
        }
        return gate;
    }

    private void seedPricing(Building building, VehicleType vehicleType, String hourly, String daily, String monthly, int hourlyFreeMinutes) {
        getOrCreatePricingRule(building, vehicleType, PricingRule.PricingType.HOURLY, new BigDecimal(hourly), hourlyFreeMinutes);
        getOrCreatePricingRule(building, vehicleType, PricingRule.PricingType.DAILY, new BigDecimal(daily), 0);
        getOrCreatePricingRule(building, vehicleType, PricingRule.PricingType.MONTHLY, new BigDecimal(monthly), 0);
    }

    private PricingRule getOrCreatePricingRule(Building building, VehicleType vehicleType, PricingRule.PricingType pricingType, BigDecimal price, int freeMinutes) {
        return pricingRuleRepository.findByBuildingIdAndVehicleTypeIdAndPricingType(building.getId(), vehicleType.getId(), pricingType)
                .orElseGet(() -> pricingRuleRepository.save(PricingRule.builder()
                        .building(building)
                        .vehicleType(vehicleType)
                        .pricingType(pricingType)
                        .pricePerUnit(price)
                        .freeMinutes(freeMinutes)
                        .build()));
    }

    private User getOrCreateUser(String email, String fullName, String phone, User.Role role) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode("123456"))
                        .fullName(fullName)
                        .phone(phone)
                        .role(role)
                        .isActive(true)
                        .build()));
    }
}
