package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.*;
import com.smartparking.backend.service.UniqueCodeGeneratorService;
import com.smartparking.backend.service.ZonePlanningService;
import com.smartparking.backend.util.LicensePlateUtil;
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
 * - CRUD /admin/users → Quản lý tài khoản
 * - CRUD /admin/zones → Quản lý khu đỗ xe
 * - CRUD /admin/gates + barrier → Quản lý cổng + điều khiển barrier
 * - CRUD /admin/pricing-rules → Quản lý bảng giá
 * - CRUD /admin/parking-passes → Quản lý vé định kỳ + gia hạn
 * - GET /admin/payments → Xem danh sách thanh toán
 * - GET/PUT /admin/settings → Cài đặt hệ thống
 *
 * MỚI (18/07/2026 — theo góp ý giảng viên):
 * - CRUD /admin/buildings → Quản lý tòa nhà
 * - CRUD /admin/floors → Quản lý tầng (có thông số vật lý)
 * - GET/PUT /admin/vehicle-types → Quản lý loại phương tiện (thông số slot)
 * - POST /admin/calculate-slots → Tính slot tự động: maxSlots = floor(zoneArea
 * / slotAreaSqm)
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
    private final ParkingSessionRepository parkingSessionRepository;
    private final ReservationRepository reservationRepository;
    private final PasswordEncoder passwordEncoder;
    private final UniqueCodeGeneratorService uniqueCodeGeneratorService;
    private final ZonePlanningService zonePlanningService;

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
            ParkingSessionRepository parkingSessionRepository,
            ReservationRepository reservationRepository,
            PasswordEncoder passwordEncoder,
            UniqueCodeGeneratorService uniqueCodeGeneratorService,
            ZonePlanningService zonePlanningService) {
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
        this.parkingSessionRepository = parkingSessionRepository;
        this.reservationRepository = reservationRepository;
        this.passwordEncoder = passwordEncoder;
        this.uniqueCodeGeneratorService = uniqueCodeGeneratorService;
        this.zonePlanningService = zonePlanningService;
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
        validateEmail(email);
        if (userRepository.existsByEmail(email))
            throw new IllegalArgumentException("Email đã tồn tại");
        String phone = textOrDefault(body, "phone", "");
        if (!phone.isBlank())
            validatePhone(phone);

        String temporaryPassword = textOrDefault(body, "password", generateTemporaryPassword());
        User user = User.builder()
                .fullName(text(body, "name"))
                .email(email)
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .role(User.Role.valueOf(textOrDefault(body, "role", "DRIVER").toUpperCase()))
                .phone(phone)
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
        if (body.containsKey("name"))
            user.setFullName(text(body, "name"));
        if (body.containsKey("email")) {
            String email = text(body, "email");
            validateEmail(email);
            user.setEmail(email);
        }
        if (body.containsKey("phone")) {
            String phone = text(body, "phone");
            validatePhone(phone);
            user.setPhone(phone);
        }
        if (body.containsKey("role"))
            user.setRole(User.Role.valueOf(text(body, "role").toUpperCase()));
        if (body.containsKey("status"))
            user.setIsActive(!"suspended".equalsIgnoreCase(text(body, "status")));
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateZone(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone không tồn tại"));
        if (body.containsKey("zoneName"))
            zone.setZoneName(text(body, "zoneName"));
        if (body.containsKey("status"))
            zone.setStatus(Zone.ZoneStatus.valueOf(text(body, "status").toUpperCase()));
        Zone saved = zoneRepository.save(zone);
        // Capacity đi qua ZonePlanningService để tính lại zoneArea + validate diện tích
        // tầng còn
        // trống, tránh capacity lệch khỏi diện tích thật mà tab Cấu hình hạ tầng đang
        // theo dõi.
        if (body.containsKey("capacity")) {
            saved = zonePlanningService.resizeZoneCapacity(id, number(body, "capacity", saved.getCapacity()));
        }
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật zone", zoneMap(saved)));
    }

    @DeleteMapping("/zones/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Delete parking zone")
    public ResponseEntity<ApiResponse<String>> deleteZone(@PathVariable UUID id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone không tồn tại"));
        assertZoneDeletable(zone);
        Floor floor = zone.getFloor();
        deleteZoneCascade(zone);
        syncFloorTotalSlots(floor);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa zone", id.toString()));
    }

    /**
     * Chặn xóa zone nếu còn dữ liệu nghiệp vụ thật (xe đang đỗ / lịch sử gửi xe
     * thực / đặt chỗ đang active).
     */
    private void assertZoneDeletable(Zone zone) {
        // 1. Xe đang đỗ trong zone
        if (zone.getCurrentCount() != null && zone.getCurrentCount() > 0) {
            throw new BusinessException(
                    "Zone " + zone.getZoneName() + " đang có " + zone.getCurrentCount() + " xe đỗ, không thể xóa");
        }
        // 2. Có lịch sử gửi xe thực (COMPLETED) → bảo toàn dữ liệu báo cáo doanh thu
        if (parkingSessionRepository.existsByZoneIdAndStatus(zone.getId(), ParkingSession.SessionStatus.COMPLETED)) {
            throw new BusinessException(
                    "Zone " + zone.getZoneName() + " đã có lịch sử gửi xe, không thể xóa để bảo toàn dữ liệu báo cáo — "
                            + "hãy chuyển trạng thái zone sang \"Bảo trì\" thay vì xóa");
        }
        // 3. Có đặt chỗ đang active (PENDING hoặc CONFIRMED) → không thể xóa
        if (reservationRepository.existsByZoneIdAndStatusIn(zone.getId(),
                List.of(Reservation.ReservationStatus.PENDING, Reservation.ReservationStatus.CONFIRMED))) {
            throw new BusinessException(
                    "Zone " + zone.getZoneName() + " đang có đặt chỗ chưa hoàn tất, không thể xóa — "
                            + "hãy hủy các đặt chỗ trước hoặc chuyển sang \"Bảo trì\"");
        }
        // Lưu ý: Đặt chỗ đã HỦY (CANCELLED/EXPIRED) không chặn xóa zone
    }

    /**
     * Xóa zone + các gate + reservation/session đã hủy phụ thuộc.
     * Chỉ gọi sau khi assertZoneDeletable() đã xác nhận không còn dữ liệu active.
     * Không đồng bộ lại Floor.totalSlots — caller tự lo việc đó.
     */
    private void deleteZoneCascade(Zone zone) {
        UUID zoneId = zone.getId();

        // 1. Xóa các reservation đã hủy (CANCELLED/EXPIRED) — FK constraint cần xóa
        // trước zone
        List<Reservation> cancelledReservations = reservationRepository.findByZoneId(zoneId).stream()
                .filter(r -> r.getStatus() == Reservation.ReservationStatus.CANCELLED
                        || r.getStatus() == Reservation.ReservationStatus.EXPIRED)
                .toList();
        if (!cancelledReservations.isEmpty()) {
            reservationRepository.deleteAll(cancelledReservations);
        }

        // 2. Xóa các parking session đã hủy (CANCELLED) nếu có FK đến zone
        // (COMPLETED sessions đã bị chặn bởi assertZoneDeletable, không thể vào đây)
        List<ParkingSession> cancelledSessions = parkingSessionRepository.findAll().stream()
                .filter(s -> s.getZone() != null && s.getZone().getId().equals(zoneId))
                .filter(s -> s.getStatus() == ParkingSession.SessionStatus.CANCELLED)
                .toList();
        if (!cancelledSessions.isEmpty()) {
            parkingSessionRepository.deleteAll(cancelledSessions);
        }

        // 3. Xóa gates của zone
        gateRepository.deleteAll(gateRepository.findByZoneId(zoneId));

        // 4. Xóa zone
        zoneRepository.deleteById(zoneId);
    }

    /**
     * Đồng bộ lại Floor.totalSlots = tổng capacity các zone còn lại, tránh số liệu
     * ảo sau khi xóa/sửa zone.
     */
    private void syncFloorTotalSlots(Floor floor) {
        if (floor == null)
            return;
        int total = zoneRepository.findAllByFloorId(floor.getId()).stream()
                .mapToInt(z -> z.getCapacity() != null ? z.getCapacity() : 0).sum();
        floor.setTotalSlots(total);
        floorRepository.save(floor);
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateGate(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        Gate gate = gateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gate không tồn tại"));
        if (body.containsKey("gateName"))
            gate.setGateName(text(body, "gateName"));
        if (body.containsKey("gateType"))
            gate.setGateType(Gate.GateType.valueOf(text(body, "gateType").toUpperCase()));
        if (body.containsKey("isActive"))
            gate.setIsActive(Boolean.parseBoolean(String.valueOf(body.get("isActive"))));
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> controlBarrier(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        Gate gate = gateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gate không tồn tại"));
        String state = text(body, "state").toUpperCase();
        if (!state.equals("OPEN") && !state.equals("CLOSED"))
            throw new IllegalArgumentException("Trạng thái barrier không hợp lệ (OPEN hoặc CLOSED)");

        gate.setBarrierState(state);
        // Cưỡng chế đóng barrier phải thật sự chặn được check-in/check-out qua cổng
        // này, không chỉ đổi
        // màu hiển thị — tận dụng field isActive đã được ParkingSessionService kiểm tra
        // sẵn ở mọi luồng
        // check-in/check-out (cổng chính vào/ra, cổng zone vào/ra), tránh phải thêm
        // field mới trùng ý nghĩa.
        gate.setIsActive(state.equals("OPEN"));
        gateRepository.save(gate);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gateId", gate.getId());
        payload.put("gateName", gate.getGateName());
        payload.put("barrierState", state);
        payload.put("gateType", gate.getGateType().name());
        return ResponseEntity
                .ok(ApiResponse.success("Đã gửi lệnh " + state + " tới barrier " + gate.getGateName(), payload));
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
                .pricingType(
                        PricingRule.PricingType.valueOf(textOrDefault(body, "pricingType", "HOURLY").toUpperCase()))
                .pricePerUnit(decimal(body, "pricePerUnit", BigDecimal.ZERO))
                .freeMinutes(number(body, "freeMinutes", 0))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đã tạo bảng giá", pricingMap(pricingRuleRepository.save(rule))));
    }

    @PutMapping("/pricing-rules/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Update pricing rule")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePricingRule(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bảng giá không tồn tại"));
        if (body.containsKey("pricingType"))
            rule.setPricingType(PricingRule.PricingType.valueOf(text(body, "pricingType").toUpperCase()));
        if (body.containsKey("pricePerUnit"))
            rule.setPricePerUnit(decimal(body, "pricePerUnit", rule.getPricePerUnit()));
        if (body.containsKey("freeMinutes"))
            rule.setFreeMinutes(number(body, "freeMinutes", rule.getFreeMinutes()));
        return ResponseEntity
                .ok(ApiResponse.success("Đã cập nhật bảng giá", pricingMap(pricingRuleRepository.save(rule))));
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
        LocalDate startDate = LocalDate.parse(text(body, "startDate"));
        if (startDate.isBefore(LocalDate.now())) {
            throw new BusinessException("Ngày bắt đầu không được ở quá khứ");
        }
        String licensePlate = resolvePassLicensePlate(body, vehicleType, null);
        ParkingPass pass = ParkingPass.builder()
                .user(user)
                .building(building)
                .vehicleType(vehicleType)
                .licensePlate(licensePlate)
                .parkingPassCode(uniqueCodeGeneratorService.generateParkingPassCode())
                .startDate(startDate)
                .endDate(LocalDate.parse(text(body, "endDate")))
                .passType(ParkingPass.PassType.valueOf(textOrDefault(body, "passType", "MONTHLY").toUpperCase()))
                .fee(decimal(body, "fee", BigDecimal.ZERO))
                .status(ParkingPass.PassStatus.ACTIVE)
                .build();
        return ResponseEntity
                .ok(ApiResponse.success("Đã phát hành vé định kỳ", passMap(parkingPassRepository.save(pass))));
    }

    @PutMapping("/parking-passes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    @Operation(summary = "Update parking pass")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePass(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        ParkingPass pass = parkingPassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vé định kỳ không tồn tại"));
        if (body.containsKey("userId"))
            pass.setUser(userRepository.findById(uuid(body, "userId"))
                    .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại")));
        if (body.containsKey("buildingId"))
            pass.setBuilding(buildingRepository.findById(uuid(body, "buildingId"))
                    .orElseThrow(() -> new ResourceNotFoundException("Building không tồn tại")));
        if (body.containsKey("vehicleTypeId"))
            pass.setVehicleType(vehicleTypeRepository.findById(uuid(body, "vehicleTypeId"))
                    .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại")));
        if (body.containsKey("licensePlate") || body.containsKey("vehicleTypeId")) {
            pass.setLicensePlate(resolvePassLicensePlate(body, pass.getVehicleType(), pass.getLicensePlate()));
        }
        if (body.containsKey("startDate")) {
            LocalDate newStartDate = LocalDate.parse(text(body, "startDate"));
            // Chỉ chặn khi admin THỰC SỰ đổi sang 1 ngày quá khứ mới — không chặn việc sửa
            // các
            // trường khác (fee, status...) của vé đã có startDate quá khứ hợp lệ từ trước.
            if (!newStartDate.equals(pass.getStartDate()) && newStartDate.isBefore(LocalDate.now())) {
                throw new BusinessException("Ngày bắt đầu không được ở quá khứ");
            }
            pass.setStartDate(newStartDate);
        }
        if (body.containsKey("endDate"))
            pass.setEndDate(LocalDate.parse(text(body, "endDate")));
        if (body.containsKey("passType"))
            pass.setPassType(ParkingPass.PassType.valueOf(text(body, "passType").toUpperCase()));
        if (body.containsKey("fee"))
            pass.setFee(decimal(body, "fee", pass.getFee()));
        if (body.containsKey("status"))
            pass.setStatus(ParkingPass.PassStatus.valueOf(text(body, "status").toUpperCase()));
        return ResponseEntity
                .ok(ApiResponse.success("Đã cập nhật vé định kỳ", passMap(parkingPassRepository.save(pass))));
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
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        List<Payment> payments;
        try {
            payments = paymentRepository.findAll();
        } catch (Exception e) {
            System.err.println("[ERROR] Cannot load payments from DB: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        for (Payment p : payments) {
            try {
                if (p.getStatus() != Payment.PaymentStatus.COMPLETED)
                    continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.getId());
                m.put("referenceType", p.getReferenceType());
                m.put("referenceId", p.getReferenceId());
                m.put("amount", p.getAmount());
                m.put("paymentMethod", p.getPaymentMethod() != null ? p.getPaymentMethod().name() : null);
                m.put("transactionId", p.getTransactionId());
                m.put("paidAt", p.getPaidAt());
                m.put("createdAt", p.getCreatedAt());
                result.add(m);
            } catch (Exception ex) {
                System.err.println("[WARN] Skipping payment due to mapping error: " + ex.getMessage());
            }
        }
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
        if (body.containsKey("gracePeriod"))
            settings.setGracePeriodMinutes(number(body, "gracePeriod", settings.getGracePeriodMinutes()));
        if (body.containsKey("currency"))
            settings.setCurrency(textOrDefault(body, "currency", settings.getCurrency()));
        if (body.containsKey("vat"))
            settings.setVatPercentage(number(body, "vat", settings.getVatPercentage()));
        if (body.containsKey("sosEnabled"))
            settings.setSosEnabled(Boolean.parseBoolean(String.valueOf(body.get("sosEnabled"))));
        if (body.containsKey("incidentResolverRoles"))
            settings.setIncidentResolverRoles(sanitizeRoles(body.get("incidentResolverRoles")));
        if (body.containsKey("blacklistManagerRoles"))
            settings.setBlacklistManagerRoles(sanitizeRoles(body.get("blacklistManagerRoles")));
        return ResponseEntity.ok(ApiResponse.success(
                "Đã cập nhật cài đặt hệ thống", settingsMap(systemSettingsRepository.save(settings))));
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDINGS — Quản lý tòa nhà (MỚI — theo góp ý giảng viên 18/07)
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/buildings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy danh sách tòa nhà")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBuildings() {
        return ResponseEntity.ok(ApiResponse.success(
                buildingRepository.findAll().stream().map(this::buildingMap).toList()));
    }

    @PostMapping("/buildings")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Tạo tòa nhà mới")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBuilding(@RequestBody Map<String, Object> body) {
        Building building = Building.builder()
                .name(text(body, "name"))
                .address(textOrDefault(body, "address", ""))
                .totalFloors(number(body, "totalFloors", 0))
                .description(textOrDefault(body, "description", ""))
                .build();
        if (body.containsKey("operatingHoursStart"))
            building.setOperatingHoursStart(java.time.LocalTime.parse(text(body, "operatingHoursStart")));
        if (body.containsKey("operatingHoursEnd"))
            building.setOperatingHoursEnd(java.time.LocalTime.parse(text(body, "operatingHoursEnd")));
        return ResponseEntity.ok(ApiResponse.success("Đã tạo tòa nhà", buildingMap(buildingRepository.save(building))));
    }

    @PutMapping("/buildings/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Cập nhật tòa nhà")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateBuilding(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        Building building = buildingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tòa nhà không tồn tại"));
        if (body.containsKey("name"))
            building.setName(text(body, "name"));
        if (body.containsKey("address"))
            building.setAddress(text(body, "address"));
        if (body.containsKey("totalFloors"))
            building.setTotalFloors(number(body, "totalFloors", building.getTotalFloors()));
        if (body.containsKey("description"))
            building.setDescription(textOrDefault(body, "description", ""));
        if (body.containsKey("operatingHoursStart"))
            building.setOperatingHoursStart(java.time.LocalTime.parse(text(body, "operatingHoursStart")));
        if (body.containsKey("operatingHoursEnd"))
            building.setOperatingHoursEnd(java.time.LocalTime.parse(text(body, "operatingHoursEnd")));
        return ResponseEntity
                .ok(ApiResponse.success("Đã cập nhật tòa nhà", buildingMap(buildingRepository.save(building))));
    }

    @DeleteMapping("/buildings/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Xóa tòa nhà")
    public ResponseEntity<ApiResponse<String>> deleteBuilding(@PathVariable UUID id) {
        buildingRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa tòa nhà", id.toString()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // FLOORS — Quản lý tầng (MỚI — theo góp ý giảng viên 18/07)
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/floors")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy danh sách tầng (có thể filter theo buildingId)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFloors(
            @RequestParam(required = false) UUID buildingId) {
        List<Floor> floors = buildingId != null
                ? floorRepository.findByBuildingId(buildingId)
                : floorRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(
                floors.stream().map(this::floorMap).toList()));
    }

    @PostMapping("/floors")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Tạo tầng mới")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createFloor(@RequestBody Map<String, Object> body) {
        Building building = buildingRepository.findById(uuid(body, "buildingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Tòa nhà không tồn tại"));
        VehicleType vehicleType = vehicleTypeRepository.findById(uuid(body, "vehicleTypeId"))
                .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại"));
        String floorName = text(body, "floorName");
        int floorNumber = number(body, "floorNumber", 0);
        validateFloorNumberMatchesName(floorName, floorNumber);
        double floorArea = doubleVal(body, "floorArea", 0.0);
        if (floorArea <= 0) {
            throw new BusinessException("Diện tích tầng phải lớn hơn 0");
        }
        Floor floor = Floor.builder()
                .building(building)
                .floorNumber(floorNumber)
                .floorName(floorName)
                .vehicleType(vehicleType)
                .totalSlots(number(body, "totalSlots", 0))
                .floorArea(floorArea)
                .maxZones(number(body, "maxZones", 4))
                .description(textOrDefault(body, "description", ""))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đã tạo tầng", floorMap(floorRepository.save(floor))));
    }

    @PutMapping("/floors/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Cập nhật tầng")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateFloor(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        Floor floor = floorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tầng không tồn tại"));
        if (body.containsKey("floorName")) {
            String newFloorName = text(body, "floorName");
            validateFloorNumberMatchesName(newFloorName, floor.getFloorNumber());
            floor.setFloorName(newFloorName);
        }
        if (body.containsKey("totalSlots"))
            floor.setTotalSlots(number(body, "totalSlots", floor.getTotalSlots()));
        if (body.containsKey("floorArea")) {
            double newFloorArea = doubleVal(body, "floorArea", floor.getFloorArea());
            if (newFloorArea <= 0) {
                throw new BusinessException("Diện tích tầng phải lớn hơn 0");
            }
            double allocatedByZones = zoneRepository.findAllByFloorId(id).stream()
                    .mapToDouble(z -> z.getZoneArea() != null ? z.getZoneArea() : 0.0)
                    .sum();
            if (newFloorArea < allocatedByZones - 0.01) {
                throw new BusinessException(String.format(
                        "Diện tích mới (%.1fm²) nhỏ hơn tổng diện tích các zone đã quy hoạch trên tầng (%.1fm²)",
                        newFloorArea, allocatedByZones));
            }
            floor.setFloorArea(newFloorArea);
        }
        if (body.containsKey("maxZones"))
            floor.setMaxZones(number(body, "maxZones", floor.getMaxZones()));
        if (body.containsKey("description"))
            floor.setDescription(textOrDefault(body, "description", ""));
        if (body.containsKey("vehicleTypeId"))
            floor.setVehicleType(vehicleTypeRepository.findById(uuid(body, "vehicleTypeId"))
                    .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại")));
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật tầng", floorMap(floorRepository.save(floor))));
    }

    @DeleteMapping("/floors/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Xóa tầng")
    public ResponseEntity<ApiResponse<String>> deleteFloor(@PathVariable UUID id) {
        Floor floor = floorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tầng không tồn tại"));
        List<Zone> zones = zoneRepository.findAllByFloorId(id);
        // Kiểm tra hết trước khi xóa cái nào, để báo lỗi rõ ràng thay vì xóa dở dang
        zones.forEach(this::assertZoneDeletable);
        zones.forEach(this::deleteZoneCascade);
        floorRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa tầng", id.toString()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUY HOẠCH ZONE THEO DIỆN TÍCH — thay cho tạo zone thủ công
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/floors/{id}/capacity-summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xem diện tích khả dụng/đã dùng/còn lại của 1 tầng để quy hoạch zone")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFloorCapacitySummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(zonePlanningService.getFloorCapacitySummary(id)));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/floors/{id}/zones/preview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tính thử số zone/slot sẽ được tạo (chưa lưu DB)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewFloorZones(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) body.getOrDefault("allocations", List.of());
        return ResponseEntity.ok(ApiResponse.success(zonePlanningService.previewGeneratedZones(id, allocations)));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/floors/{id}/zones/generate")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Tự sinh zone theo diện tích tầng còn trống (thay thế tạo zone thủ công)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateFloorZones(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) body.getOrDefault("allocations", List.of());
        List<Zone> created = zonePlanningService.generateZones(id, allocations);
        Floor floor = floorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tầng không tồn tại"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zones", created.stream().map(this::zoneMap).toList());
        result.put("floor", floorMap(floor));
        return ResponseEntity.ok(ApiResponse.success(
                "Đã tạo " + created.size() + " zone mới cho tầng " + floor.getFloorName(), result));
    }

    // ═══════════════════════════════════════════════════════════════════
    // VEHICLE TYPES — Quản lý loại phương tiện
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/vehicle-types")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy danh sách loại phương tiện")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVehicleTypes() {
        return ResponseEntity.ok(ApiResponse.success(
                vehicleTypeRepository.findAll().stream().map(this::vehicleTypeMap).toList()));
    }

    @PostMapping("/vehicle-types")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Tạo loại phương tiện mới")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createVehicleType(@RequestBody Map<String, Object> body) {
        String name = text(body, "name");
        if (vehicleTypeRepository.findByName(name).isPresent()) {
            throw new BusinessException("Loại xe \"" + name + "\" đã tồn tại");
        }
        VehicleType vt = VehicleType.builder()
                .name(name)
                .description(textOrDefault(body, "description", ""))
                .slotAreaSqm(doubleVal(body, "slotAreaSqm", 1.0))
                .maxWeight(doubleVal(body, "maxWeight", 0.0))
                .mixable(boolVal(body, "mixable", false))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Đã tạo loại xe", vehicleTypeMap(vehicleTypeRepository.save(vt))));
    }

    @PutMapping("/vehicle-types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Cập nhật thông số loại phương tiện")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateVehicleType(@PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        VehicleType vt = vehicleTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại"));
        if (body.containsKey("name"))
            vt.setName(text(body, "name"));
        if (body.containsKey("description"))
            vt.setDescription(textOrDefault(body, "description", ""));
        if (body.containsKey("slotAreaSqm"))
            vt.setSlotAreaSqm(doubleVal(body, "slotAreaSqm", vt.getSlotAreaSqm()));
        if (body.containsKey("maxWeight"))
            vt.setMaxWeight(doubleVal(body, "maxWeight", vt.getMaxWeight()));
        if (body.containsKey("mixable"))
            vt.setMixable(boolVal(body, "mixable", vt.getMixable()));
        return ResponseEntity
                .ok(ApiResponse.success("Đã cập nhật loại xe", vehicleTypeMap(vehicleTypeRepository.save(vt))));
    }

    @DeleteMapping("/vehicle-types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Xóa loại phương tiện")
    public ResponseEntity<ApiResponse<String>> deleteVehicleType(@PathVariable UUID id) {
        VehicleType vt = vehicleTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại"));
        if (zoneRepository.existsByVehicleTypeId(id)) {
            throw new BusinessException(
                    "Loại xe \"" + vt.getName() + "\" đang được dùng ở ít nhất 1 khu vực đỗ, không thể xóa");
        }
        try {
            vehicleTypeRepository.deleteById(id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new BusinessException("Loại xe \"" + vt.getName()
                    + "\" đang được dùng ở nơi khác (bảng giá, vé tháng, biển số xe, lịch sử...), không thể xóa");
        }
        return ResponseEntity.ok(ApiResponse.success("Đã xóa loại xe", id.toString()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // AUTO-CALCULATE SLOTS — Công thức tính slot tự động
    // Công thức: maxSlots = floor(zoneArea / slotAreaSqm)
    // ═══════════════════════════════════════════════════════════════════

    @PostMapping("/calculate-slots")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tính số slot tối đa từ diện tích zone và loại xe — Công thức: maxSlots = floor(zoneArea / slotAreaSqm)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateSlots(@RequestBody Map<String, Object> body) {
        Double zoneArea = doubleVal(body, "zoneArea", 0.0);
        UUID vehicleTypeId = uuid(body, "vehicleTypeId");

        VehicleType vt = vehicleTypeRepository.findById(vehicleTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vehicleTypeName", vt.getName());
        result.put("slotAreaSqm", vt.getSlotAreaSqm());
        result.put("zoneArea", zoneArea);

        // Công thức chính: maxSlots = zoneArea / slotAreaSqm
        int maxSlots = 0;
        if (vt.getSlotAreaSqm() != null && vt.getSlotAreaSqm() > 0) {
            maxSlots = (int) Math.floor(zoneArea / vt.getSlotAreaSqm());
        }
        result.put("maxSlots", maxSlots);
        result.put("formula", zoneArea + "m² ÷ " + vt.getSlotAreaSqm() + "m²/slot = " + maxSlots + " slot tối đa");

        return ResponseEntity.ok(ApiResponse.success(result));
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
        map.put("zoneArea", zone.getZoneArea());
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

    private static final java.util.regex.Pattern EMAIL_PATTERN = java.util.regex.Pattern
            .compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");
    private static final java.util.regex.Pattern PHONE_PATTERN = java.util.regex.Pattern.compile("^(0|\\+84)\\d{9}$");

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException("Email không hợp lệ (thiếu tên miền, vd: ...@gmail.com)");
        }
    }

    private void validatePhone(String phone) {
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException("Số điện thoại không hợp lệ (định dạng: 0xxxxxxxxx hoặc +84xxxxxxxxx)");
        }
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

    /**
     * Xác định licensePlate cuối cùng cho 1 vé định kỳ: xe đạp luôn dùng mã BC...
     * tự sinh (bỏ qua
     * input tay của admin, giữ nguyên nếu đã có mã hợp lệ để tránh sinh lại vô cớ
     * mỗi lần sửa),
     * các loại xe khác phải qua LicensePlateUtil validate đúng định dạng + khớp
     * loại xe — cùng
     * 1 hàm validate mà DriverController đang dùng để chặn tài xế tự đăng ký sai
     * định dạng.
     */
    private String resolvePassLicensePlate(Map<String, Object> body, VehicleType vehicleType, String existingPlate) {
        boolean isBicycle = "BICYCLE".equals(LicensePlateUtil.getVehicleTypeKey(vehicleType.getName()));
        if (isBicycle) {
            if (existingPlate != null && existingPlate.matches("^BC\\d{10}$")) {
                return existingPlate;
            }
            return uniqueCodeGeneratorService.generateBicycleIdentifier();
        }
        String rawPlate = body.containsKey("licensePlate") ? text(body, "licensePlate") : existingPlate;
        String plate = rawPlate == null ? "" : rawPlate.toUpperCase().replaceAll("\\s+", "");
        String error = LicensePlateUtil.getLicensePlateValidationError(plate, vehicleType.getName());
        if (error != null) {
            throw new BusinessException(error);
        }
        return plate;
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
        map.put("sosEnabled", settings.getSosEnabled() != null ? settings.getSosEnabled() : true);
        map.put("incidentResolverRoles", toRoleList(settings.getIncidentResolverRoles()));
        map.put("blacklistManagerRoles", toRoleList(settings.getBlacklistManagerRoles()));
        return map;
    }

    /**
     * Các role admin được phép bật/tắt (ADMIN luôn có quyền nên không nằm ở đây).
     * STAFF không có màn UI cho giải quyết sự cố / blacklist nên không đưa vào.
     */
    private static final java.util.Set<String> TOGGLEABLE_ROLES = java.util.Set.of("SECURITY", "MANAGER");

    private java.util.List<String> toRoleList(String csv) {
        if (csv == null || csv.isBlank())
            return java.util.List.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Nhận List hoặc CSV, giữ lại chỉ role hợp lệ (STAFF/SECURITY/MANAGER), trả về
     * CSV.
     */
    private String sanitizeRoles(Object value) {
        java.util.stream.Stream<String> stream;
        if (value instanceof java.util.List<?> list) {
            stream = list.stream().map(String::valueOf);
        } else {
            stream = java.util.Arrays.stream(String.valueOf(value).split(","));
        }
        return stream.map(s -> s.trim().toUpperCase())
                .filter(TOGGLEABLE_ROLES::contains)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private Map<String, Object> buildingMap(Building building) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", building.getId());
        map.put("name", building.getName());
        map.put("address", building.getAddress());
        map.put("totalFloors", building.getTotalFloors());
        map.put("description", building.getDescription());
        map.put("operatingHoursStart", building.getOperatingHoursStart());
        map.put("operatingHoursEnd", building.getOperatingHoursEnd());
        map.put("createdAt", building.getCreatedAt());
        return map;
    }

    /**
     * Đảm bảo Thứ tự tầng khớp quy ước tên: "B<n>" (hầm) phải là số âm -n,
     * "T<n>" (tầng nổi) phải là số dương n. Tên không theo mẫu B/T thì bỏ qua,
     * không ép buộc — tránh 1 lỗi âm thầm như "B5" nhưng gõ floorNumber dương/sai.
     */
    private void validateFloorNumberMatchesName(String floorName, int floorNumber) {
        if (floorName == null)
            return;
        String name = floorName.trim();
        java.util.regex.Matcher basement = java.util.regex.Pattern.compile("^[Bb](\\d+)$").matcher(name);
        if (basement.matches()) {
            int expected = -Integer.parseInt(basement.group(1));
            if (floorNumber != expected) {
                throw new BusinessException("Tên tầng \"" + floorName + "\" (hầm) yêu cầu Thứ tự tầng = "
                        + expected + ", nhưng bạn nhập " + floorNumber);
            }
            return;
        }
        java.util.regex.Matcher aboveGround = java.util.regex.Pattern.compile("^[Tt](\\d+)$").matcher(name);
        if (aboveGround.matches()) {
            int expected = Integer.parseInt(aboveGround.group(1));
            if (floorNumber != expected) {
                throw new BusinessException("Tên tầng \"" + floorName + "\" (tầng nổi) yêu cầu Thứ tự tầng = "
                        + expected + ", nhưng bạn nhập " + floorNumber);
            }
        }
    }

    private Map<String, Object> floorMap(Floor floor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", floor.getId());
        map.put("floorNumber", floor.getFloorNumber());
        map.put("floorName", floor.getFloorName());
        map.put("totalSlots", floor.getTotalSlots());
        map.put("floorArea", floor.getFloorArea());
        map.put("maxZones", floor.getMaxZones());
        map.put("description", floor.getDescription());
        map.put("buildingId", floor.getBuilding().getId());
        map.put("buildingName", floor.getBuilding().getName());
        map.put("vehicleTypeId", floor.getVehicleType().getId());
        map.put("vehicleTypeName", floor.getVehicleType().getName());
        // Thêm danh sách zone thuộc tầng này
        List<Map<String, Object>> zones = zoneRepository.findAllByFloorId(floor.getId())
                .stream().map(this::zoneMap).toList();
        map.put("zones", zones);
        map.put("zoneCount", zones.size());
        return map;
    }

    private Map<String, Object> vehicleTypeMap(VehicleType vt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", vt.getId());
        map.put("name", vt.getName());
        map.put("description", vt.getDescription());
        map.put("slotAreaSqm", vt.getSlotAreaSqm());
        map.put("maxWeight", vt.getMaxWeight());
        map.put("mixable", vt.getMixable());
        return map;
    }

    private Boolean boolVal(Map<String, Object> body, String key, Boolean defaultValue) {
        Object value = body.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private Double doubleVal(Map<String, Object> body, String key, Double defaultValue) {
        Object value = body.get(key);
        return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
    }
}
