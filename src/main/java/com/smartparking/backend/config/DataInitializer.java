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
 *
 * ===========================================================================================
 * THAY ĐỔI NGÀY 18/07/2026 — THEO GÓP Ý GIẢNG VIÊN
 * ===========================================================================================
 *
 * 1. THÊM THÔNG SỐ VẬT LÝ VÀO ENTITY:
 *    - Building: totalFloors (tổng số tầng), description (mô tả)
 *    - Floor:    floorArea (diện tích m²), ceilingHeight (chiều cao trần m),
 *                maxZones (số zone tối đa), description (mô tả)
 *    - VehicleType: slotAreaSqm (diện tích 1 slot m²), minCeilingHeight (chiều cao trần
 *                   tối thiểu m), maxWeight (trọng lượng tối đa xe tấn)
 *    → Mục đích: để hệ thống TỰ ĐỘNG validate và gợi ý khi Admin cấu hình tầng/zone.
 *      Ví dụ: xe tải cần trần ≥ 3.5m → không gán được vào tầng trần 2.8m.
 *      Ví dụ: diện tích zone 500m² ÷ slotAreaSqm 12.5 = tối đa 40 ô tô.
 *
 * 2. SỬA LẠI CẤU TRÚC PHÂN TẦNG (logic vật lý thực tế):
 *    TRƯỚC (sai logic):           SAU (đúng logic):
 *    B2: Xe máy + Xe đạp         B2: Ô tô (hầm sâu, nền bê tông chịu lực)
 *    B1: Xe máy + Xe đạp         B1: Xe tải (gần mặt đất nhất, trần cao 4.5m, chịu tải 8 tấn)
 *    T1: Ô tô                    T1: Tổng hợp — Xe máy + Xe đạp (tầng trệt, lối vào thuận tiện)
 *    T2: Xe tải ⚠️ SAI           T2: Tổng hợp — Xe máy + Xe đạp (tầng trên, xe nhẹ)
 *
 *    Nguyên tắc phân tầng:
 *    - Xe nặng (ô tô, xe tải) → tầng HẦM (sàn bê tông dày, chịu tải, gần lối ra)
 *    - Xe nhẹ (xe máy, xe đạp) → tầng NỔI (dễ lên dốc, không cần sàn chịu tải cao)
 *    - Xe tải đặc biệt cần trần CAO (≥ 4.5m) → tầng B1 (gần mặt đất, xe tải không leo dốc cao)
 *
 * 3. THÔNG SỐ DIỆN TÍCH SLOT THEO LOẠI XE (dùng để auto-calculate):
 *    - Xe đạp:  1.5 m²/slot  (trần tối thiểu 2.0m, trọng lượng 0.02 tấn)
 *    - Xe máy:  2.5 m²/slot  (trần tối thiểu 2.2m, trọng lượng 0.15 tấn)
 *    - Ô tô:  12.5 m²/slot  (trần tối thiểu 2.5m, trọng lượng 2.5 tấn)
 *    - Xe tải: 37.5 m²/slot  (trần tối thiểu 3.5m, trọng lượng 5.0 tấn)
 * ===========================================================================================
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
    private final ReservationRepository reservationRepository;
    private final ParkingSessionRepository parkingSessionRepository;

    @Override
    public void run(String... args) {
        log.info("🚀 DataInitializer: Kiểm tra và bổ sung dữ liệu mẫu nếu thiếu...");

        // Tự động đồng bộ và sửa reservedCount + currentCount cho tất cả các Zone dựa theo dữ liệu thực tế trong DB
        try {
            log.info("🔄 DataInitializer: Đang đồng bộ lại reservedCount và currentCount cho các Zone từ dữ liệu thực tế...");
            List<Reservation> activeReservations = reservationRepository.findAll().stream()
                    .filter(r -> r.getStatus() == Reservation.ReservationStatus.PENDING 
                              || r.getStatus() == Reservation.ReservationStatus.CONFIRMED)
                    .toList();

            List<ParkingSession> activeSessions = parkingSessionRepository.findAll().stream()
                    .filter(s -> s.getStatus() == ParkingSession.SessionStatus.ACTIVE)
                    .toList();

            List<Zone> allZones = zoneRepository.findAll();
            for (Zone z : allZones) {
                long activeResCount = activeReservations.stream()
                        .filter(r -> r.getZone() != null && r.getZone().getId().equals(z.getId()))
                        .count();

                long activeSessCount = activeSessions.stream()
                        .filter(s -> s.getZone() != null && s.getZone().getId().equals(z.getId()))
                        .count();

                z.setReservedCount((int) activeResCount);
                z.setCurrentCount((int) activeSessCount);
                
                int capacity = z.getCapacity() == null ? 0 : z.getCapacity();
                if (capacity > 0 && (int) activeSessCount + (int) activeResCount >= capacity) {
                    z.setStatus(Zone.ZoneStatus.FULL);
                } else if (z.getStatus() == Zone.ZoneStatus.FULL) {
                    z.setStatus(Zone.ZoneStatus.ACTIVE);
                }
                zoneRepository.save(z);
            }
            log.info("✅ DataInitializer: Đã đồng bộ xong trạng thái và số lượng các Zone!");
        } catch (Exception e) {
            log.error("❌ DataInitializer: Lỗi khi đồng bộ các Zone: {}", e.getMessage(), e);
        }

        // === LOẠI PHƯƠNG TIỆN (có thông số vật lý để auto-calculate slot) ===
        // mixable=true: được phép gộp chung 1 tầng "tổng hợp" với các loại mixable khác
        VehicleType xeDap = getOrCreateVehicleType("Xe đạp", "Xe đạp thường, xe đạp điện", 1.5, 0.02, true);
        VehicleType xeMay = getOrCreateVehicleType("Xe máy", "Xe gắn máy 2 bánh các loại", 2.5, 0.15, true);
        VehicleType oTo = getOrCreateVehicleType("Ô tô", "Ô tô 4-5-7 chỗ các loại", 12.5, 2.5, false);
        VehicleType xeTai = getOrCreateVehicleType("Xe tải", "Xe tải nhẹ, xe van, xe bán tải", 37.5, 5.0, false);

        Building building = getOrCreateBuilding("SmartParking Tower", "123 Nguyễn Văn Linh, Quận 7, TP.HCM",
                4 /* tổng tầng */, "Tòa nhà bãi đỗ xe thông minh chính — 2 tầng hầm + 2 tầng nổi");

        // === PHÂN TẦNG (theo yêu cầu giảng viên) ===
        // B2: Ô tô — hầm sâu nhất, xe nặng ở dưới cùng
        // B1: Xe tải — gần mặt đất, trần cao, sàn chịu tải lớn
        // T1: Tổng hợp (Xe máy + Xe đạp) — tầng trệt, lối vào thuận tiện
        // T2: Tổng hợp (Xe máy + Xe đạp) — tầng trên, xe nhẹ
        //
        // getOrCreateFloor(building, floorNumber, floorName, mainVehicleType, totalSlots,
        //                  floorArea(m²), maxZones, description)
        // getOrCreateZone(floor, code, name, vehicleType, capacity, distanceToGate, zoneArea(m²))
        // zoneArea backfill = capacity * vehicleType.slotAreaSqm (Xe đạp 1.5 · Xe máy 2.5 · Ô tô 12.5 · Xe tải 37.5)

        Floor b2 = getOrCreateFloor(building, -2, "B2", oTo, 80,
                2000.0, 3, "Hầm B2 — Ô tô chuyên dụng, hầm sâu nhất, chịu tải tốt");
        Zone b2a = getOrCreateZone(b2, "A", "Khu A - Ô tô", oTo, 30, 20, 375.0);
        Zone b2b = getOrCreateZone(b2, "B", "Khu B - Ô tô", oTo, 30, 40, 375.0);
        Zone b2c = getOrCreateZone(b2, "C", "Khu C - Ô tô", oTo, 20, 55, 250.0);

        Floor b1 = getOrCreateFloor(building, -1, "B1", xeTai, 34,
                2000.0, 3, "Hầm B1 — Xe tải chuyên dụng, gần mặt đất");
        Zone b1a = getOrCreateZone(b1, "A", "Khu A - Xe tải", xeTai, 12, 15, 450.0);
        Zone b1b = getOrCreateZone(b1, "B", "Khu B - Xe tải", xeTai, 12, 35, 450.0);
        Zone b1c = getOrCreateZone(b1, "C", "Khu C - Xe tải", xeTai, 10, 50, 375.0);

        Floor t1 = getOrCreateFloor(building, 1, "T1", xeMay, 150,
                2000.0, 4, "Tầng T1 — Tổng hợp: Xe máy + Xe đạp, lối vào thuận tiện");
        Zone t1a = getOrCreateZone(t1, "A", "Khu A - Xe máy", xeMay, 50, 15, 125.0);
        Zone t1b = getOrCreateZone(t1, "B", "Khu B - Xe máy", xeMay, 40, 30, 100.0);
        Zone t1c = getOrCreateZone(t1, "C", "Khu C - Xe máy", xeMay, 30, 45, 75.0);
        Zone t1d = getOrCreateZone(t1, "D", "Khu D - Xe đạp", xeDap, 30, 10, 45.0);

        Floor t2 = getOrCreateFloor(building, 2, "T2", xeMay, 160,
                2000.0, 4, "Tầng T2 — Tổng hợp: Xe máy + Xe đạp, tầng trên nhẹ tải");
        Zone t2a = getOrCreateZone(t2, "A", "Khu A - Xe máy", xeMay, 50, 20, 125.0);
        Zone t2b = getOrCreateZone(t2, "B", "Khu B - Xe máy", xeMay, 40, 35, 100.0);
        Zone t2c = getOrCreateZone(t2, "C", "Khu C - Xe máy", xeMay, 40, 50, 100.0);
        Zone t2d = getOrCreateZone(t2, "D", "Khu D - Xe đạp", xeDap, 30, 12, 45.0);

        // === CỔNG VÀO/RA ===
        getOrCreateGate(building, "MAIN-IN", "Cổng chính - Lối vào", Gate.GateType.MAIN_ENTRY);
        getOrCreateGate(building, "MAIN-OUT", "Cổng chính - Lối ra", Gate.GateType.MAIN_EXIT);

        // Cổng vào zone (ZONE_ENTRY)
        // Floor B2 — Ô tô
        getOrCreateGate(building, "GATE-ZONE-B2-A", "Cổng vào Zone B2-A (Ô tô)", Gate.GateType.ZONE_ENTRY, b2a);
        getOrCreateGate(building, "GATE-ZONE-B2-B", "Cổng vào Zone B2-B (Ô tô)", Gate.GateType.ZONE_ENTRY, b2b);
        getOrCreateGate(building, "GATE-ZONE-B2-C", "Cổng vào Zone B2-C (Ô tô)", Gate.GateType.ZONE_ENTRY, b2c);
        // Floor B1 — Xe tải
        getOrCreateGate(building, "GATE-ZONE-B1-A", "Cổng vào Zone B1-A (Xe tải)", Gate.GateType.ZONE_ENTRY, b1a);
        getOrCreateGate(building, "GATE-ZONE-B1-B", "Cổng vào Zone B1-B (Xe tải)", Gate.GateType.ZONE_ENTRY, b1b);
        getOrCreateGate(building, "GATE-ZONE-B1-C", "Cổng vào Zone B1-C (Xe tải)", Gate.GateType.ZONE_ENTRY, b1c);
        // Floor T1 — Tổng hợp
        getOrCreateGate(building, "GATE-ZONE-T1-A", "Cổng vào Zone T1-A (Xe máy)", Gate.GateType.ZONE_ENTRY, t1a);
        getOrCreateGate(building, "GATE-ZONE-T1-B", "Cổng vào Zone T1-B (Xe máy)", Gate.GateType.ZONE_ENTRY, t1b);
        getOrCreateGate(building, "GATE-ZONE-T1-C", "Cổng vào Zone T1-C (Xe máy)", Gate.GateType.ZONE_ENTRY, t1c);
        getOrCreateGate(building, "GATE-ZONE-T1-D", "Cổng vào Zone T1-D (Xe đạp)", Gate.GateType.ZONE_ENTRY, t1d);
        // Floor T2 — Tổng hợp
        getOrCreateGate(building, "GATE-ZONE-T2-A", "Cổng vào Zone T2-A (Xe máy)", Gate.GateType.ZONE_ENTRY, t2a);
        getOrCreateGate(building, "GATE-ZONE-T2-B", "Cổng vào Zone T2-B (Xe máy)", Gate.GateType.ZONE_ENTRY, t2b);
        getOrCreateGate(building, "GATE-ZONE-T2-C", "Cổng vào Zone T2-C (Xe máy)", Gate.GateType.ZONE_ENTRY, t2c);
        getOrCreateGate(building, "GATE-ZONE-T2-D", "Cổng vào Zone T2-D (Xe đạp)", Gate.GateType.ZONE_ENTRY, t2d);

        // Cổng ra zone (ZONE_EXIT)
        // Floor B2
        getOrCreateGate(building, "GATE-ZONE-B2-A-OUT", "Cổng ra Zone B2-A (Ô tô)", Gate.GateType.ZONE_EXIT, b2a);
        getOrCreateGate(building, "GATE-ZONE-B2-B-OUT", "Cổng ra Zone B2-B (Ô tô)", Gate.GateType.ZONE_EXIT, b2b);
        getOrCreateGate(building, "GATE-ZONE-B2-C-OUT", "Cổng ra Zone B2-C (Ô tô)", Gate.GateType.ZONE_EXIT, b2c);
        // Floor B1
        getOrCreateGate(building, "GATE-ZONE-B1-A-OUT", "Cổng ra Zone B1-A (Xe tải)", Gate.GateType.ZONE_EXIT, b1a);
        getOrCreateGate(building, "GATE-ZONE-B1-B-OUT", "Cổng ra Zone B1-B (Xe tải)", Gate.GateType.ZONE_EXIT, b1b);
        getOrCreateGate(building, "GATE-ZONE-B1-C-OUT", "Cổng ra Zone B1-C (Xe tải)", Gate.GateType.ZONE_EXIT, b1c);
        // Floor T1
        getOrCreateGate(building, "GATE-ZONE-T1-A-OUT", "Cổng ra Zone T1-A (Xe máy)", Gate.GateType.ZONE_EXIT, t1a);
        getOrCreateGate(building, "GATE-ZONE-T1-B-OUT", "Cổng ra Zone T1-B (Xe máy)", Gate.GateType.ZONE_EXIT, t1b);
        getOrCreateGate(building, "GATE-ZONE-T1-C-OUT", "Cổng ra Zone T1-C (Xe máy)", Gate.GateType.ZONE_EXIT, t1c);
        getOrCreateGate(building, "GATE-ZONE-T1-D-OUT", "Cổng ra Zone T1-D (Xe đạp)", Gate.GateType.ZONE_EXIT, t1d);
        // Floor T2
        getOrCreateGate(building, "GATE-ZONE-T2-A-OUT", "Cổng ra Zone T2-A (Xe máy)", Gate.GateType.ZONE_EXIT, t2a);
        getOrCreateGate(building, "GATE-ZONE-T2-B-OUT", "Cổng ra Zone T2-B (Xe máy)", Gate.GateType.ZONE_EXIT, t2b);
        getOrCreateGate(building, "GATE-ZONE-T2-C-OUT", "Cổng ra Zone T2-C (Xe máy)", Gate.GateType.ZONE_EXIT, t2c);
        getOrCreateGate(building, "GATE-ZONE-T2-D-OUT", "Cổng ra Zone T2-D (Xe đạp)", Gate.GateType.ZONE_EXIT, t2d);



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

        SystemSettings sysSettings = systemSettingsRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> systemSettingsRepository.save(SystemSettings.builder()
                        .gracePeriodMinutes(10)
                        .currency("VND")
                        .vatPercentage(10)
                        .sosEnabled(true)
                        .build()));
        // Backfill cột phân quyền mới nếu row cũ có giá trị NULL (ddl-auto thêm cột NULL).
        // Chỉ backfill khi NULL — KHÔNG đụng chuỗi rỗng "" (admin cố ý bỏ hết role → chỉ ADMIN).
        boolean settingsChanged = false;
        if (sysSettings.getIncidentResolverRoles() == null) {
            sysSettings.setIncidentResolverRoles("SECURITY,MANAGER");
            settingsChanged = true;
        }
        if (sysSettings.getBlacklistManagerRoles() == null) {
            sysSettings.setBlacklistManagerRoles("SECURITY,MANAGER");
            settingsChanged = true;
        }
        if (settingsChanged) systemSettingsRepository.save(sysSettings);

        log.info("✅ DataInitializer: Seed/repair hoàn tất. Users: {}, Buildings: {}, Floors: {}, Zones: {}, Gates: {}, PricingRules: {}",
                userRepository.count(), buildingRepository.count(), floorRepository.count(), zoneRepository.count(), gateRepository.count(), pricingRuleRepository.count());
        log.debug("Seed accounts ready: {}, {}, {}, {}, {} / password: 123456",
                admin.getEmail(), manager.getEmail(), staff.getEmail(), driver.getEmail(), security.getEmail());
    }

    private VehicleType getOrCreateVehicleType(String name, String description,
                                                 Double slotAreaSqm, Double maxWeight, boolean mixable) {
        return vehicleTypeRepository.findByName(name)
                .map(vt -> {
                    // Cập nhật thông số nếu chưa có
                    if (vt.getSlotAreaSqm() == null) vt.setSlotAreaSqm(slotAreaSqm);
                    if (vt.getMaxWeight() == null) vt.setMaxWeight(maxWeight);
                    if (vt.getMixable() == null) vt.setMixable(mixable);
                    return vehicleTypeRepository.save(vt);
                })
                .orElseGet(() -> vehicleTypeRepository.save(VehicleType.builder()
                        .name(name)
                        .description(description)
                        .slotAreaSqm(slotAreaSqm)
                        .maxWeight(maxWeight)
                        .mixable(mixable)
                        .build()));
    }

    private Building getOrCreateBuilding(String name, String address, int totalFloors, String description) {
        return buildingRepository.findAll().stream()
                .filter(b -> name.equalsIgnoreCase(b.getName()))
                .findFirst()
                .map(b -> {
                    if (b.getTotalFloors() == null) b.setTotalFloors(totalFloors);
                    if (b.getDescription() == null) b.setDescription(description);
                    return buildingRepository.save(b);
                })
                .orElseGet(() -> buildingRepository.save(Building.builder()
                        .name(name)
                        .address(address)
                        .operatingHoursStart(LocalTime.of(6, 0))
                        .operatingHoursEnd(LocalTime.of(22, 0))
                        .totalFloors(totalFloors)
                        .description(description)
                        .build()));
    }

    private Floor getOrCreateFloor(Building building, int floorNumber, String floorName,
                                    VehicleType vehicleType, int totalCapacity,
                                    Double floorArea, Integer maxZones, String description) {
        return floorRepository.findByBuildingId(building.getId()).stream()
                .filter(f -> floorNumber == f.getFloorNumber())
                .findFirst()
                .map(f -> {
                    // Cập nhật thông số vật lý (luôn đồng bộ khi boot)
                    f.setFloorArea(floorArea);
                    f.setMaxZones(maxZones);
                    f.setDescription(description);
                    return floorRepository.save(f);
                })
                .orElseGet(() -> floorRepository.save(Floor.builder()
                        .building(building)
                        .floorNumber(floorNumber)
                        .floorName(floorName)
                        .vehicleType(vehicleType)
                        .totalSlots(totalCapacity)
                        .floorArea(floorArea)
                        .maxZones(maxZones)
                        .description(description)
                        .build()));
    }

    private Zone getOrCreateZone(Floor floor, String zoneCode, String zoneName, VehicleType vehicleType, int capacity, int distanceToGate, double zoneArea) {
        List<Zone> zones = zoneRepository.findAllByBuildingId(floor.getBuilding().getId());
        return zones.stream()
                .filter(z -> z.getFloor() != null && floor.getId().equals(z.getFloor().getId()) && zoneCode.equalsIgnoreCase(z.getZoneCode()))
                .findFirst()
                .map(z -> {
                    if (z.getZoneArea() == null) {
                        z.setZoneArea(zoneArea);
                        return zoneRepository.save(z);
                    }
                    return z;
                })
                .orElseGet(() -> zoneRepository.save(Zone.builder()
                        .floor(floor)
                        .zoneCode(zoneCode)
                        .zoneName(zoneName)
                        .vehicleType(vehicleType)
                        .capacity(capacity)
                        .zoneArea(zoneArea)
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
