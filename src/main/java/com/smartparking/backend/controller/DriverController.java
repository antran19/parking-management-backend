package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.entity.UserLicensePlate;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.UserLicensePlateRepository;
import com.smartparking.backend.repository.UserRepository;
import com.smartparking.backend.util.LicensePlateUtil;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.smartparking.backend.entity.ParkingPass;
import com.smartparking.backend.entity.Payment;
import com.smartparking.backend.entity.PricingRule;
import com.smartparking.backend.entity.VehicleType;
import com.smartparking.backend.entity.Building;
import com.smartparking.backend.repository.ParkingPassRepository;
import com.smartparking.backend.repository.PaymentRepository;
import com.smartparking.backend.repository.PricingRuleRepository;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DriverController — API cho Driver (Quảng phụ trách)
 *
 * Phase 1: Implement quản lý biển số
 * - GET  /driver/plates
 * - POST /driver/plates
 * - DELETE /driver/plates?plate=
 */
/**
 * DriverController — API cho Driver (Quảng phụ trách)
 *
 * TODO (Quảng): Implement các endpoint sau:
 * - GET  /driver/plates              → Lấy danh sách biển số đã đăng ký
 * - POST /driver/plates              → Thêm biển số mới
 * - DELETE /driver/plates?plate=     → Xóa biển số
 * - GET  /driver/pricing-plans       → Xem gói dịch vụ (vé tháng/quý/năm)
 * - POST /driver/parking-passes      → Đăng ký parking pass + thanh toán VNPAY
 * - GET  /driver/parking-passes      → Xem parking pass đã mua
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
public class DriverController {

    private final UserLicensePlateRepository userLicensePlateRepository;
    private final UserRepository userRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ParkingPassRepository parkingPassRepository;
    private final PaymentRepository paymentRepository;

    public DriverController(UserLicensePlateRepository userLicensePlateRepository,
                            UserRepository userRepository,
                            PricingRuleRepository pricingRuleRepository,
                            ParkingPassRepository parkingPassRepository,
                            PaymentRepository paymentRepository) {
        this.userLicensePlateRepository = userLicensePlateRepository;
        this.userRepository = userRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.parkingPassRepository = parkingPassRepository;
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/driver/plates")
    public ResponseEntity<ApiResponse<List<String>>> getDriverPlates(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);

        List<String> plates = userLicensePlateRepository.findByUser(currentUser)
                .stream()
                .map(UserLicensePlate::getLicensePlate)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách biển số thành công", plates));
    }

    @PostMapping("/driver/plates")
    @Transactional
    public ResponseEntity<ApiResponse<List<String>>> addDriverPlate(
            Authentication authentication,
            @RequestBody Map<String, String> request
    ) {
        User currentUser = getCurrentUser(authentication);

        String rawPlate = request.get("licensePlate");
        String normalizedPlate = LicensePlateUtil.normalize(rawPlate);

        if (normalizedPlate.isBlank()) {
            throw new BusinessException("Biển số xe không được để trống");
        }

        if (normalizedPlate.length() > 15) {
            throw new BusinessException("Biển số xe không hợp lệ");
        }

        boolean exists = userLicensePlateRepository
                .findByUserAndLicensePlate(currentUser, normalizedPlate)
                .isPresent();

        if (exists) {
            throw new BusinessException("Biển số xe này đã tồn tại");
        }

        UserLicensePlate plate = UserLicensePlate.builder()
                .user(currentUser)
                .licensePlate(normalizedPlate)
                .build();

        userLicensePlateRepository.save(plate);

        List<String> plates = userLicensePlateRepository.findByUser(currentUser)
                .stream()
                .map(UserLicensePlate::getLicensePlate)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Thêm biển số thành công", plates));
    }

    @DeleteMapping("/driver/plates")
    @Transactional
    public ResponseEntity<ApiResponse<List<String>>> deleteDriverPlate(
            Authentication authentication,
            @RequestParam("plate") String plate
    ) {
        User currentUser = getCurrentUser(authentication);

        String normalizedPlate = LicensePlateUtil.normalize(plate);

        UserLicensePlate existing = userLicensePlateRepository
                .findByUserAndLicensePlate(currentUser, normalizedPlate)
                .orElseThrow(() -> new BusinessException("Không tìm thấy biển số để xóa"));

        userLicensePlateRepository.delete(existing);

        List<String> plates = userLicensePlateRepository.findByUser(currentUser)
                .stream()
                .map(UserLicensePlate::getLicensePlate)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Xóa biển số thành công", plates));
    }

    @GetMapping("/driver/pricing-plans")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDriverPricingPlans() {
        List<Map<String, Object>> plans = pricingRuleRepository.findAll()
                .stream()
                .filter(rule -> rule.getPricingType() == PricingRule.PricingType.MONTHLY)
                .flatMap(rule -> List.of(
                        buildPricingPlanResponse(rule, ParkingPass.PassType.MONTHLY),
                        buildPricingPlanResponse(rule, ParkingPass.PassType.QUARTERLY),
                        buildPricingPlanResponse(rule, ParkingPass.PassType.YEARLY)
                ).stream())
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách gói vé thành công", plans));
    }

    private Map<String, Object> buildPricingPlanResponse(PricingRule rule, ParkingPass.PassType passType) {
        BigDecimal price = calculatePassFee(rule.getPricePerUnit(), passType);

        String passLabel = switch (passType) {
            case MONTHLY -> "Vé tháng";
            case QUARTERLY -> "Vé quý";
            case YEARLY -> "Vé năm";
        };

        Map<String, Object> plan = new LinkedHashMap<>();

        plan.put("id", buildPricingPlanId(rule.getId(), passType));
        plan.put("pricingPlanId", buildPricingPlanId(rule.getId(), passType));
        plan.put("pricingRuleId", rule.getId());
        plan.put("name", passLabel + " - " + rule.getVehicleType().getName());
        plan.put("planName", passLabel + " - " + rule.getVehicleType().getName());
        plan.put("passType", passType.name());
        plan.put("vehicleTypeId", rule.getVehicleType().getId());
        plan.put("vehicleTypeName", rule.getVehicleType().getName());
        plan.put("buildingId", rule.getBuilding().getId());
        plan.put("buildingName", rule.getBuilding().getName());
        plan.put("pricingType", rule.getPricingType().name());
        plan.put("price", price);
        plan.put("durationDays", getDurationDays(passType));

        return plan;
    }

    @GetMapping("/driver/parking-passes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDriverParkingPasses(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);

        List<Map<String, Object>> passes = parkingPassRepository.findByUser(currentUser)
                .stream()
                .map(pass -> {
                    List<Payment> payments = findPassPayments(pass.getId());
                    Payment latestPayment = payments.isEmpty() ? null : payments.get(payments.size() - 1);

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", pass.getId());
                    item.put("passId", pass.getId());
                    item.put("licensePlate", pass.getLicensePlate());
                    item.put("planName", getPassLabel(pass.getPassType()) + " - " + pass.getVehicleType().getName());
                    item.put("passType", pass.getPassType().name());
                    item.put("vehicleTypeName", pass.getVehicleType().getName());
                    item.put("buildingName", pass.getBuilding().getName());
                    item.put("validFrom", pass.getStartDate());
                    item.put("validTo", pass.getEndDate());
                    item.put("fee", pass.getFee());
                    item.put("price", pass.getFee());
                    item.put("status", pass.getStatus().name());
                    item.put("paymentStatus", latestPayment != null ? latestPayment.getStatus().name() : "UNKNOWN");
                    item.put("paymentId", latestPayment != null ? latestPayment.getId() : null);
                    item.put("qrCode", pass.getQrCode());

                    return item;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách vé dài hạn thành công", passes));
    }

    @PostMapping("/driver/parking-passes")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerDriverParkingPass(
            Authentication authentication,
            @RequestBody Map<String, String> request
    ) {
        User currentUser = getCurrentUser(authentication);

        String licensePlate = LicensePlateUtil.normalize(request.get("licensePlate"));

        if (licensePlate.isBlank()) {
            throw new BusinessException("Biển số không được để trống");
        }

        boolean plateBelongsToDriver = userLicensePlateRepository
                .findByUser(currentUser)
                .stream()
                .anyMatch(item -> LicensePlateUtil.normalize(item.getLicensePlate()).equals(licensePlate));

        if (!plateBelongsToDriver) {
            throw new BusinessException("Biển số này chưa thuộc tài khoản Driver");
        }

        String savedLicensePlate = userLicensePlateRepository
                .findByUser(currentUser)
                .stream()
                .map(UserLicensePlate::getLicensePlate)
                .filter(item -> LicensePlateUtil.normalize(item).equals(licensePlate))
                .findFirst()
                .orElse(licensePlate);

        ParsedPricingPlan parsedPlan = parsePricingPlanFromRequest(request);

        PricingRule pricingRule = pricingRuleRepository.findById(parsedPlan.pricingRuleId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy gói giá"));

        if (pricingRule.getPricingType() != PricingRule.PricingType.MONTHLY) {
            throw new BusinessException("Gói vé dài hạn phải được tạo từ giá MONTHLY");
        }

        VehicleType vehicleType = pricingRule.getVehicleType();
        Building building = pricingRule.getBuilding();
        ParkingPass.PassType passType = parsedPlan.passType();

        boolean hasActiveOrPendingPass = parkingPassRepository.findByUser(currentUser)
                .stream()
                .anyMatch(pass ->
                        LicensePlateUtil.normalize(pass.getLicensePlate()).equals(licensePlate)
                                && pass.getVehicleType().getId().equals(vehicleType.getId())
                                && (
                                        pass.getStatus() == ParkingPass.PassStatus.ACTIVE
                                                || pass.getStatus() == ParkingPass.PassStatus.PENDING_PAYMENT
                                )
                );

        if (hasActiveOrPendingPass) {
            throw new BusinessException("Biển số này đã có vé đang hoạt động hoặc đang chờ thanh toán");
        }

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(getDurationDays(passType));
        BigDecimal fee = calculatePassFee(pricingRule.getPricePerUnit(), passType);

        ParkingPass parkingPass = ParkingPass.builder()
                .user(currentUser)
                .building(building)
                .vehicleType(vehicleType)
                .licensePlate(savedLicensePlate)
                .qrCode("PASS-" + UUID.randomUUID())
                .startDate(startDate)
                .endDate(endDate)
                .passType(passType)
                .fee(fee)
                .status(ParkingPass.PassStatus.PENDING_PAYMENT)
                .build();

        ParkingPass savedPass = parkingPassRepository.save(parkingPass);

        Payment payment = Payment.builder()
                .referenceType("MONTHLY_PASS")
                .referenceId(savedPass.getId())
                .amount(fee)
                .paymentMethod(Payment.PaymentMethod.ONLINE)
                .status(Payment.PaymentStatus.PENDING)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        Map<String, Object> response = buildPassPaymentResponse(savedPass, savedPayment);
        response.put("message", "Đăng ký vé thành công. Vé đang chờ thanh toán.");
        response.put("paymentUrl", "");

        return ResponseEntity.ok(ApiResponse.success(
                "Đăng ký vé thành công. Vé đang chờ thanh toán.",
                response
        ));
    }

    @PostMapping("/driver/parking-passes/{passId}/pay")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> continueDriverPassPayment(
            Authentication authentication,
            @PathVariable UUID passId
    ) {
        User currentUser = getCurrentUser(authentication);

        ParkingPass parkingPass = parkingPassRepository.findById(passId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy vé dài hạn"));

        if (!parkingPass.getUser().getId().equals(currentUser.getId())) {
            throw new BusinessException("Bạn không có quyền thanh toán vé này");
        }

        if (parkingPass.getStatus() == ParkingPass.PassStatus.ACTIVE) {
            throw new BusinessException("Vé này đã được kích hoạt");
        }

        List<Payment> payments = findPassPayments(parkingPass.getId());

        Payment payment = payments.stream()
                .filter(item -> item.getStatus() == Payment.PaymentStatus.PENDING)
                .findFirst()
                .orElseGet(() -> paymentRepository.save(
                        Payment.builder()
                                .referenceType("MONTHLY_PASS")
                                .referenceId(parkingPass.getId())
                                .amount(parkingPass.getFee())
                                .paymentMethod(Payment.PaymentMethod.ONLINE)
                                .status(Payment.PaymentStatus.PENDING)
                                .build()
                ));

        Map<String, Object> response = buildPassPaymentResponse(parkingPass, payment);
        response.put("message", "Payment đã được tạo. Chờ module VNPay tạo URL thanh toán.");
        response.put("paymentUrl", "");

        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin thanh toán vé thành công", response));
    }

    private Map<String, Object> buildPassPaymentResponse(ParkingPass pass, Payment payment) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("id", pass.getId());
        response.put("passId", pass.getId());
        response.put("licensePlate", pass.getLicensePlate());
        response.put("planName", getPassLabel(pass.getPassType()) + " - " + pass.getVehicleType().getName());
        response.put("passType", pass.getPassType().name());
        response.put("vehicleTypeName", pass.getVehicleType().getName());
        response.put("buildingName", pass.getBuilding().getName());
        response.put("validFrom", pass.getStartDate());
        response.put("validTo", pass.getEndDate());
        response.put("fee", pass.getFee());
        response.put("price", pass.getFee());
        response.put("status", pass.getStatus().name());
        response.put("paymentId", payment.getId());
        response.put("paymentStatus", payment.getStatus().name());
        response.put("requiresPayment", payment.getStatus() == Payment.PaymentStatus.PENDING);

        return response;
    }

    private List<Payment> findPassPayments(UUID passId) {
        java.util.ArrayList<Payment> payments = new java.util.ArrayList<>();

        payments.addAll(paymentRepository.findByReferenceTypeAndReferenceId("MONTHLY_PASS", passId));

        // Giữ lại để không vỡ dữ liệu cũ nếu trước đó bạn đã tạo Payment bằng PARKING_PASS
        payments.addAll(paymentRepository.findByReferenceTypeAndReferenceId("PARKING_PASS", passId));

        return payments;
    }

    private ParsedPricingPlan parsePricingPlanFromRequest(Map<String, String> request) {
        String pricingPlanId = request.get("pricingPlanId");

        if (pricingPlanId != null && !pricingPlanId.isBlank()) {
            return parsePricingPlanId(pricingPlanId);
        }

        String pricingRuleId = request.get("pricingRuleId");
        String passTypeValue = request.get("passType");

        if (pricingRuleId != null && !pricingRuleId.isBlank()) {
            try {
                return new ParsedPricingPlan(
                        UUID.fromString(pricingRuleId),
                        parsePassType(passTypeValue)
                );
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("pricingRuleId không hợp lệ");
            }
        }

        String buildingIdValue = request.get("buildingId");
        String vehicleTypeIdValue = request.get("vehicleTypeId");

        if (buildingIdValue != null && !buildingIdValue.isBlank()
                && vehicleTypeIdValue != null && !vehicleTypeIdValue.isBlank()) {
            try {
                UUID buildingId = UUID.fromString(buildingIdValue);
                UUID vehicleTypeId = UUID.fromString(vehicleTypeIdValue);
                ParkingPass.PassType passType = parsePassType(passTypeValue);

                PricingRule pricingRule = pricingRuleRepository
                        .findByBuildingIdAndVehicleTypeIdAndPricingType(
                                buildingId,
                                vehicleTypeId,
                                PricingRule.PricingType.MONTHLY
                        )
                        .orElseThrow(() -> new BusinessException("Không tìm thấy gói giá theo tòa nhà và loại xe"));

                return new ParsedPricingPlan(pricingRule.getId(), passType);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("buildingId hoặc vehicleTypeId không hợp lệ");
            }
        }

        throw new BusinessException("Vui lòng chọn gói vé");
    }

    private ParkingPass.PassType parsePassType(String rawPassType) {
        if (rawPassType == null || rawPassType.isBlank()) {
            return ParkingPass.PassType.MONTHLY;
        }

        try {
            return ParkingPass.PassType.valueOf(rawPassType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Loại vé không hợp lệ. Chỉ hỗ trợ MONTHLY, QUARTERLY, YEARLY");
        }
    }

    private int getDurationDays(ParkingPass.PassType passType) {
        return switch (passType) {
            case MONTHLY -> 30;
            case QUARTERLY -> 90;
            case YEARLY -> 365;
        };
    }

    private BigDecimal calculatePassFee(BigDecimal monthlyPrice, ParkingPass.PassType passType) {
        if (monthlyPrice == null) {
            throw new BusinessException("Gói giá chưa có giá tiền");
        }

        return switch (passType) {
            case MONTHLY -> monthlyPrice;
            case QUARTERLY -> monthlyPrice.multiply(BigDecimal.valueOf(3));
            case YEARLY -> monthlyPrice.multiply(BigDecimal.valueOf(12));
        };
    }

    private String buildPricingPlanId(UUID pricingRuleId, ParkingPass.PassType passType) {
        return pricingRuleId + ":" + passType.name();
    }

    private ParsedPricingPlan parsePricingPlanId(String pricingPlanId) {
        if (pricingPlanId == null || pricingPlanId.isBlank()) {
            throw new BusinessException("Vui lòng chọn gói vé");
        }

        String[] parts = pricingPlanId.trim().split(":");

        try {
            UUID pricingRuleId = UUID.fromString(parts[0]);
            ParkingPass.PassType passType = parts.length > 1
                    ? parsePassType(parts[1])
                    : ParkingPass.PassType.MONTHLY;

            return new ParsedPricingPlan(pricingRuleId, passType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Gói vé không hợp lệ");
        }
    }

    private String getPassLabel(ParkingPass.PassType passType) {
        return switch (passType) {
            case MONTHLY -> "Vé tháng";
            case QUARTERLY -> "Vé quý";
            case YEARLY -> "Vé năm";
        };
    }

    private record ParsedPricingPlan(UUID pricingRuleId, ParkingPass.PassType passType) {
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException("Không xác định được người dùng hiện tại");
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản hiện tại"));
    }
}