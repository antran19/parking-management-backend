package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.*;
import com.smartparking.backend.service.UniqueCodeGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * AdminManagementController — CRUD quản trị hệ thống (An phụ trách)
 *
 * Chức năng đã implement:
 * - CRUD /admin/users           → Quản lý tài khoản
 * - CRUD /admin/zones           → Quản lý khu đỗ xe
 * - CRUD /admin/gates + barrier → Quản lý cổng + điều khiển barrier
 * - CRUD /admin/pricing-rules   → Quản lý bảng giá
 * - CRUD /admin/parking-passes  → Quản lý vé định kỳ + gia hạn
 * - GET  /admin/payments        → Xem danh sách thanh toán
 * - GET/PUT /admin/settings     → Cài đặt hệ thống
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Management", description = "APIs for Administrator to manage users, zones, gates, pricing, and settings")
public class AdminManagementController {

    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final FloorRepository floorRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final GateRepository gateRepository;
    private final BuildingRepository buildingRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ParkingPassRepository parkingPassRepository;
    private final PaymentRepository paymentRepository;
    private final SystemSettingsRepository systemSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final UniqueCodeGeneratorService uniqueCodeGeneratorService;

    public AdminManagementController(UserRepository userRepository,
                                     ZoneRepository zoneRepository,
                                     FloorRepository floorRepository,
                                     VehicleTypeRepository vehicleTypeRepository,
                                     GateRepository gateRepository,
                                     BuildingRepository buildingRepository,
                                     PricingRuleRepository pricingRuleRepository,
                                     ParkingPassRepository parkingPassRepository,
                                     PaymentRepository paymentRepository,
                                     SystemSettingsRepository systemSettingsRepository,
                                     PasswordEncoder passwordEncoder,
                                     UniqueCodeGeneratorService uniqueCodeGeneratorService) {
        this.userRepository = userRepository;
        this.zoneRepository = zoneRepository;
        this.floorRepository = floorRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.gateRepository = gateRepository;
        this.buildingRepository = buildingRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.parkingPassRepository = parkingPassRepository;
        this.paymentRepository = paymentRepository;
        this.systemSettingsRepository = systemSettingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.uniqueCodeGeneratorService = uniqueCodeGeneratorService;
    }

    // ═══════════════════════════════════════════════════════════════════
    // USERS — Quản lý tài khoản
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all users")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUsers() {
        return ResponseEntity.ok(ApiResponse.success(
                userRepository.findAll().stream().map(this::userMap).toList()));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Create a new user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(@RequestBody Map<String, Object> body) {
        String email = text(body, "email");
        if (userRepository.existsByEmail(email))
            throw new IllegalArgumentException("Email đã tồn tại");

        String temporaryPassword = textOrDefault(body, "password", generateTemporaryPassword());
        User user = User.builder()
                .fullName(text(body, "name"))
                .email(email)
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .role(User.Role.valueOf(textOrDefault(body, "role", "DRIVER").toUpperCase()))
                .phone(textOrDefault(body, "phone", ""))
                .isActive(true)
                .build();

        Map<String, Object> response = userMap(userRepository.save(user));
        response.put("temporaryPassword", temporaryPassword);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã tạo tài khoản. Mật khẩu tạm thời chỉ hiển thị một lần", response));
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Reset user password")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetUserPassword(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));
        String temporaryPassword = textOrDefault(body, "password", generateTemporaryPassword());
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        Map<String, Object> response = userMap(userRepository.save(user));
        response.put("temporaryPassword", temporaryPassword);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã reset mật khẩu. Mật khẩu tạm thời chỉ hiển thị một lần", response));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Update user details")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUser(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));
        if (body.containsKey("name")) user.setFullName(text(body, "name"));
        if (body.containsKey("email")) user.setEmail(text(body, "email"));
        if (body.containsKey("phone")) user.setPhone(text(body, "phone"));
        if (body.containsKey("role")) user.setRole(User.Role.valueOf(text(body, "role").toUpperCase()));
        if (body.containsKey("status")) user.setIsActive(!"suspended".equalsIgnoreCase(text(body, "status")));
        return ResponseEntity.ok(ApiResponse.success(
                "Đã cập nhật tài khoản", userMap(userRepository.save(user))));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Delete user")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable UUID id) {
        userRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa tài khoản", id.toString()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // ZONES — Quản lý khu đỗ xe
    // ═══════════════════════════════════════════════════════════════════

    @PostMapping("/zones")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Create a new parking zone")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createZone(@RequestBody Map<String, Object> body) {
        Floor floor = floorRepository.findById(uuid(body, "floorId"))
                .orElseThrow(() -> new ResourceNotFoundException("Floor không tồn tại"));
        VehicleType vehicleType = vehicleTypeRepository.findById(uuid(body, "vehicleTypeId"))
                .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại"));
        Zone zone = Zone.builder()
                .floor(floor)
                .vehicleType(vehicleType)
                .zoneCode(text(body, "zoneCode"))
                .zoneName(text(body, "zoneName"))
                .capacity(number(body, "capacity", 0))
                .currentCount(0)
                .reservedCount(0)
                .status(Zone.ZoneStatus.ACTIVE)
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đã tạo zone", zoneMap(zoneRepository.save(zone))));
    }

    @PutMapping("/zones/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Update parking zone")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateZone(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone không tồn tại"));
        if (body.containsKey("zoneName")) zone.setZoneName(text(body, "zoneName"));
        if (body.containsKey("capacity")) zone.setCapacity(number(body, "capacity", zone.getCapacity()));
        if (body.containsKey("status")) zone.setStatus(Zone.ZoneStatus.valueOf(text(body, "status").toUpperCase()));
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật zone", zoneMap(zoneRepository.save(zone))));
    }

    @DeleteMapping("/zones/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Delete parking zone")
    public ResponseEntity<ApiResponse<String>> deleteZone(@PathVariable UUID id) {
        zoneRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa zone", id.toString()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // GATES — Quản lý cổng ra/vào
    // ═══════════════════════════════════════════════════════════════════

    @PostMapping("/gates")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Create a new gate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createGate(@RequestBody Map<String, Object> body) {
        Building building = buildingRepository.findById(uuid(body, "buildingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Building không tồn tại"));
        Zone zone = null;
        if (body.containsKey("zoneId") && body.get("zoneId") != null && !String.valueOf(body.get("zoneId")).isBlank()) {
            zone = zoneRepository.findById(uuid(body, "zoneId"))
                    .orElseThrow(() -> new ResourceNotFoundException("Zone không tồn tại"));
        }
        Gate gate = Gate.builder()
                .building(building)
                .zone(zone)
                .gateCode(text(body, "gateCode"))
                .gateName(text(body, "gateName"))
                .gateType(Gate.GateType.valueOf(textOrDefault(body, "gateType", "MAIN_BOTH").toUpperCase()))
                .isActive(true)
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đã tạo cổng", gateMap(gateRepository.save(gate))));
    }

    @PutMapping("/gates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Update gate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateGate(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Gate gate = gateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gate không tồn tại"));
        if (body.containsKey("gateName")) gate.setGateName(text(body, "gateName"));
        if (body.containsKey("gateType")) gate.setGateType(Gate.GateType.valueOf(text(body, "gateType").toUpperCase()));
        if (body.containsKey("isActive")) gate.setIsActive(Boolean.parseBoolean(String.valueOf(body.get("isActive"))));
        if (body.containsKey("zoneId")) {
            if (body.get("zoneId") == null || String.valueOf(body.get("zoneId")).isBlank()) {
                gate.setZone(null);
            } else {
                Zone zone = zoneRepository.findById(uuid(body, "zoneId"))
                        .orElseThrow(() -> new ResourceNotFoundException("Zone không tồn tại"));
                gate.setZone(zone);
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật cổng", gateMap(gateRepository.save(gate))));
    }

    @DeleteMapping("/gates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Delete gate")
    public ResponseEntity<ApiResponse<String>> deleteGate(@PathVariable UUID id) {
        gateRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa cổng", id.toString()));
    }

    @PutMapping("/gates/{id}/barrier")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Control gate barrier (OPEN/CLOSED)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> controlBarrier(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Gate gate = gateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gate không tồn tại"));
        String state = text(body, "state").toUpperCase();
        if (!state.equals("OPEN") && !state.equals("CLOSED"))
            throw new IllegalArgumentException("Trạng thái barrier không hợp lệ (OPEN hoặc CLOSED)");
        
        gate.setBarrierState(state);
        gateRepository.save(gate);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gateId", gate.getId());
        payload.put("gateName", gate.getGateName());
        payload.put("barrierState", state);
        payload.put("gateType", gate.getGateType().name());
        return ResponseEntity.ok(ApiResponse.success("Đã gửi lệnh " + state + " tới barrier " + gate.getGateName(), payload));
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRICING RULES — Quản lý bảng giá
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/pricing-rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Get all pricing rules")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPricingRules() {
        return ResponseEntity.ok(ApiResponse.success(
                pricingRuleRepository.findAll().stream().map(this::pricingMap).toList()));
    }

    @PostMapping("/pricing-rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Create a pricing rule")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPricingRule(@RequestBody Map<String, Object> body) {
        Building building = buildingRepository.findById(uuid(body, "buildingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Building không tồn tại"));
        VehicleType vehicleType = vehicleTypeRepository.findById(uuid(body, "vehicleTypeId"))
                .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại"));
        PricingRule rule = PricingRule.builder()
                .building(building)
                .vehicleType(vehicleType)
                .pricingType(PricingRule.PricingType.valueOf(textOrDefault(body, "pricingType", "HOURLY").toUpperCase()))
                .pricePerUnit(decimal(body, "pricePerUnit", BigDecimal.ZERO))
                .freeMinutes(number(body, "freeMinutes", 0))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đã tạo bảng giá", pricingMap(pricingRuleRepository.save(rule))));
    }

    @PutMapping("/pricing-rules/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Update pricing rule")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePricingRule(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bảng giá không tồn tại"));
        if (body.containsKey("pricingType")) rule.setPricingType(PricingRule.PricingType.valueOf(text(body, "pricingType").toUpperCase()));
        if (body.containsKey("pricePerUnit")) rule.setPricePerUnit(decimal(body, "pricePerUnit", rule.getPricePerUnit()));
        if (body.containsKey("freeMinutes")) rule.setFreeMinutes(number(body, "freeMinutes", rule.getFreeMinutes()));
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật bảng giá", pricingMap(pricingRuleRepository.save(rule))));
    }

    @DeleteMapping("/pricing-rules/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Delete pricing rule")
    public ResponseEntity<ApiResponse<String>> deletePricingRule(@PathVariable UUID id) {
        pricingRuleRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa bảng giá", id.toString()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // PARKING PASSES — Quản lý vé định kỳ (tháng / quý / năm)
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/parking-passes")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Get all parking passes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPasses() {
        return ResponseEntity.ok(ApiResponse.success(
                parkingPassRepository.findAll().stream().map(this::passMap).toList()));
    }

    @PostMapping("/parking-passes")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Create a new parking pass")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPass(@RequestBody Map<String, Object> body) {
        User user = userRepository.findById(uuid(body, "userId"))
                .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));
        Building building = buildingRepository.findById(uuid(body, "buildingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Building không tồn tại"));
        VehicleType vehicleType = vehicleTypeRepository.findById(uuid(body, "vehicleTypeId"))
                .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại"));
        ParkingPass pass = ParkingPass.builder()
                .user(user)
                .building(building)
                .vehicleType(vehicleType)
                .licensePlate(text(body, "licensePlate").toUpperCase().replaceAll("\\s+", ""))
                .parkingPassCode(uniqueCodeGeneratorService.generateParkingPassCode())
                .startDate(LocalDate.parse(text(body, "startDate")))
                .endDate(LocalDate.parse(text(body, "endDate")))
                .passType(ParkingPass.PassType.valueOf(textOrDefault(body, "passType", "MONTHLY").toUpperCase()))
                .fee(decimal(body, "fee", BigDecimal.ZERO))
                .status(ParkingPass.PassStatus.ACTIVE)
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đã phát hành vé định kỳ", passMap(parkingPassRepository.save(pass))));
    }

    @PutMapping("/parking-passes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Update parking pass")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePass(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        ParkingPass pass = parkingPassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vé định kỳ không tồn tại"));
        if (body.containsKey("userId")) pass.setUser(userRepository.findById(uuid(body, "userId"))
                .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại")));
        if (body.containsKey("buildingId")) pass.setBuilding(buildingRepository.findById(uuid(body, "buildingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Building không tồn tại")));
        if (body.containsKey("vehicleTypeId")) pass.setVehicleType(vehicleTypeRepository.findById(uuid(body, "vehicleTypeId"))
                .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại")));
        if (body.containsKey("licensePlate")) pass.setLicensePlate(text(body, "licensePlate").toUpperCase().replaceAll("\\s+", ""));
        if (body.containsKey("startDate")) pass.setStartDate(LocalDate.parse(text(body, "startDate")));
        if (body.containsKey("endDate")) pass.setEndDate(LocalDate.parse(text(body, "endDate")));
        if (body.containsKey("passType")) pass.setPassType(ParkingPass.PassType.valueOf(text(body, "passType").toUpperCase()));
        if (body.containsKey("fee")) pass.setFee(decimal(body, "fee", pass.getFee()));
        if (body.containsKey("status")) pass.setStatus(ParkingPass.PassStatus.valueOf(text(body, "status").toUpperCase()));
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật vé định kỳ", passMap(parkingPassRepository.save(pass))));
    }

    @DeleteMapping("/parking-passes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Delete parking pass")
    public ResponseEntity<ApiResponse<String>> deletePass(@PathVariable UUID id) {
        parkingPassRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa vé định kỳ", id.toString()));
    }

    @PostMapping("/parking-passes/{id}/renew")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Renew parking pass")
    public ResponseEntity<ApiResponse<Map<String, Object>>> renewPass(@PathVariable UUID id) {
        ParkingPass pass = parkingPassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vé định kỳ không tồn tại"));
        int days = switch (pass.getPassType()) {
            case YEARLY -> 365;
            case QUARTERLY -> 90;
            default -> 30;
        };
        pass.setEndDate(pass.getEndDate().plusDays(days));
        pass.setStatus(ParkingPass.PassStatus.ACTIVE);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã gia hạn vé " + days + " ngày", passMap(parkingPassRepository.save(pass))));
    }

    // ═══════════════════════════════════════════════════════════════════
    // PAYMENTS — Xem danh sách thanh toán
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Get all completed payments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPayments() {
        List<Map<String, Object>> result = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("referenceType", p.getReferenceType());
                    m.put("referenceId", p.getReferenceId());
                    m.put("amount", p.getAmount());
                    m.put("paymentMethod", p.getPaymentMethod().name());
                    m.put("transactionId", p.getTransactionId());
                    m.put("paidAt", p.getPaidAt());
                    m.put("createdAt", p.getCreatedAt());
                    return m;
                }).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ═══════════════════════════════════════════════════════════════════
    // SYSTEM SETTINGS — Cài đặt hệ thống
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get system settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsMap(settings())));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Update system settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSettings(@RequestBody Map<String, Object> body) {
        SystemSettings settings = settings();
        if (body.containsKey("gracePeriod")) settings.setGracePeriodMinutes(number(body, "gracePeriod", settings.getGracePeriodMinutes()));
        if (body.containsKey("currency")) settings.setCurrency(textOrDefault(body, "currency", settings.getCurrency()));
        if (body.containsKey("vat")) settings.setVatPercentage(number(body, "vat", settings.getVatPercentage()));
        if (body.containsKey("systemName")) settings.setSystemName(textOrDefault(body, "systemName", settings.getSystemName()));
        if (body.containsKey("sosEnabled")) settings.setSosEnabled(Boolean.parseBoolean(String.valueOf(body.get("sosEnabled"))));
        return ResponseEntity.ok(ApiResponse.success(
                "Đã cập nhật cài đặt hệ thống", settingsMap(systemSettingsRepository.save(settings))));
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    private Map<String, Object> userMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getFullName());
        map.put("email", user.getEmail());
        map.put("role", user.getRole().name().toLowerCase());
        map.put("status", Boolean.TRUE.equals(user.getIsActive()) ? "active" : "suspended");
        map.put("phone", user.getPhone());
        return map;
    }

    private Map<String, Object> zoneMap(Zone zone) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", zone.getId());
        map.put("zoneCode", zone.getZoneCode());
        map.put("zoneName", zone.getZoneName());
        map.put("capacity", zone.getCapacity());
        map.put("currentCount", zone.getCurrentCount());
        map.put("reservedCount", zone.getReservedCount());
        map.put("status", zone.getStatus().name());
        map.put("floorId", zone.getFloor().getId());
        map.put("floorName", zone.getFloor().getFloorName());
        map.put("vehicleTypeId", zone.getVehicleType().getId());
        map.put("vehicleTypeName", zone.getVehicleType().getName());
        return map;
    }

    private Map<String, Object> gateMap(Gate gate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", gate.getId());
        map.put("gateCode", gate.getGateCode());
        map.put("gateName", gate.getGateName());
        map.put("gateType", gate.getGateType().name());
        map.put("isActive", gate.getIsActive());
        map.put("barrierState", gate.getBarrierState());
        map.put("buildingId", gate.getBuilding().getId());
        map.put("buildingName", gate.getBuilding().getName());
        if (gate.getZone() != null) {
            map.put("zoneId", gate.getZone().getId());
            map.put("zoneCode", gate.getZone().getZoneCode());
            map.put("zoneName", gate.getZone().getZoneName());
        }
        return map;
    }

    private String generateTemporaryPassword() {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "Smart@" + token;
    }

    private String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank())
            throw new IllegalArgumentException(key + " không được để trống");
        return String.valueOf(value).trim();
    }

    private String textOrDefault(Map<String, Object> body, String key, String defaultValue) {
        Object value = body.get(key);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value).trim();
    }

    private UUID uuid(Map<String, Object> body, String key) {
        return UUID.fromString(text(body, key));
    }

    private int number(Map<String, Object> body, String key, int defaultValue) {
        Object value = body.get(key);
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal decimal(Map<String, Object> body, String key, BigDecimal defaultValue) {
        Object value = body.get(key);
        return value == null ? defaultValue : new BigDecimal(String.valueOf(value));
    }

    private Map<String, Object> pricingMap(PricingRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rule.getId());
        map.put("pricingType", rule.getPricingType().name());
        map.put("pricePerUnit", rule.getPricePerUnit());
        map.put("freeMinutes", rule.getFreeMinutes());
        map.put("buildingId", rule.getBuilding().getId());
        map.put("buildingName", rule.getBuilding().getName());
        map.put("vehicleTypeId", rule.getVehicleType().getId());
        map.put("vehicleTypeName", rule.getVehicleType().getName());
        return map;
    }

    private Map<String, Object> passMap(ParkingPass pass) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", pass.getId());
        map.put("userId", pass.getUser().getId());
        map.put("userName", pass.getUser().getFullName());
        map.put("buildingId", pass.getBuilding().getId());
        map.put("buildingName", pass.getBuilding().getName());
        map.put("vehicleTypeId", pass.getVehicleType().getId());
        map.put("vehicleTypeName", pass.getVehicleType().getName());
        map.put("licensePlate", pass.getLicensePlate());
        map.put("parkingPassCode", pass.getParkingPassCode());
        map.put("startDate", pass.getStartDate());
        map.put("endDate", pass.getEndDate());
        map.put("passType", pass.getPassType().name());
        map.put("fee", pass.getFee());
        map.put("status", pass.getStatus().name());
        return map;
    }

    private SystemSettings settings() {
        return systemSettingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> systemSettingsRepository.save(SystemSettings.builder().build()));
    }

    private Map<String, Object> settingsMap(SystemSettings settings) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", settings.getId());
        map.put("gracePeriod", settings.getGracePeriodMinutes());
        map.put("currency", settings.getCurrency());
        map.put("vat", settings.getVatPercentage());
        map.put("systemName", settings.getSystemName());
        map.put("sosEnabled", settings.getSosEnabled() != null ? settings.getSosEnabled() : true);
        return map;
    }
}
