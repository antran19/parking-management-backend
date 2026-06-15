package com.smartparking.backend.controller;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.entity.Building;
import com.smartparking.backend.entity.ParkingPass;
import com.smartparking.backend.entity.PricingRule;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.entity.UserLicensePlate;
import com.smartparking.backend.entity.VehicleType;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.BuildingRepository;
import com.smartparking.backend.repository.ParkingPassRepository;
import com.smartparking.backend.repository.PricingRuleRepository;
import com.smartparking.backend.repository.UserLicensePlateRepository;
import com.smartparking.backend.repository.UserRepository;
import com.smartparking.backend.repository.VehicleTypeRepository;
import com.smartparking.backend.util.LicensePlateUtil;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


/**
 * DriverController — API cho Driver.
 *
 * Quảng phụ trách:
 * - GET    /api/v1/driver/plates  -- lấy danh sách biển số của driver
 * - POST   /api/v1/driver/plates  -- thêm biển số mới cho driver
 * - DELETE /api/v1/driver/plates?plate={plate} -- xóa biển số của driver
 * - GET    /api/v1/driver/pricing-plans -- lấy bảng giá gửi xe theo loại xe
 * - GET    /api/v1/driver/parking-passes -- lấy danh sách vé tháng/quý/năm của driver
 * - POST   /api/v1/driver/parking-passes -- đăng ký vé tháng/quý/năm mới
 * - POST   /api/v1/driver/parking-passes/{passId}/pay -- tạo lại link thanh toán cho vé đã đăng ký
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
public class DriverController {

    private final UserRepository userRepository;
    private final UserLicensePlateRepository userLicensePlateRepository;
    private final PricingRuleRepository pricingRuleRepository;

    /**
     * NOTE:
     * 3 repository dưới đây dùng cho chức năng vé tháng / quý / năm của Driver.
     */
    private final ParkingPassRepository parkingPassRepository;
    private final BuildingRepository buildingRepository;
    private final VehicleTypeRepository vehicleTypeRepository;

    public DriverController(
            UserRepository userRepository,
            UserLicensePlateRepository userLicensePlateRepository,
            PricingRuleRepository pricingRuleRepository,
            ParkingPassRepository parkingPassRepository,
            BuildingRepository buildingRepository,
            VehicleTypeRepository vehicleTypeRepository
    ) {
        this.userRepository = userRepository;
        this.userLicensePlateRepository = userLicensePlateRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.parkingPassRepository = parkingPassRepository;
        this.buildingRepository = buildingRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
    
    }

    /**
     * Lấy danh sách biển số của driver đang đăng nhập.
     *
     * Method: GET
     * Endpoint: /api/v1/driver/plates
     */
    @GetMapping("/driver/plates")
    public ApiResponse<List<String>> getMyPlates(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);

        /*
        * NOTE Quảng - Driver scope:
        * DB có thể đã tồn tại dữ liệu cũ dạng:
        * - 51H12345
        * - 51H-123.45
        *
        * Hai chuỗi này là cùng một biển số sau khi normalize.
        * Vì không được sửa Entity/Repository/shared DB constraint,
        * controller Driver tự lọc trùng theo LicensePlateUtil.normalize().
        */
        List<String> plates = userLicensePlateRepository.findByUser(currentUser)
                .stream()
                .map(UserLicensePlate::getLicensePlate)
                .collect(java.util.stream.Collectors.toMap(
                        LicensePlateUtil::normalize,
                        LicensePlateUtil::normalize,
                        (oldValue, newValue) -> oldValue,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();

        return ApiResponse.success("Lấy danh sách biển số thành công", plates);
    }

    /**
     * Lấy danh sách bảng giá/gói gửi xe.
     *
     * Method: GET
     * Endpoint: /api/v1/driver/pricing-plans
     *
     * NOTE:
     * FE dùng dữ liệu này để hiển thị giá gửi xe theo loại xe.
     */
    @GetMapping("/driver/pricing-plans")
    public ApiResponse<List<PricingRule>> getPricingPlans() {
        List<PricingRule> pricingPlans = pricingRuleRepository.findAll();

        return ApiResponse.success("Lấy bảng giá thành công", pricingPlans);
    }

    /**
     * Thêm biển số mới cho driver đang đăng nhập.
     *
     * Method: POST
     * Endpoint: /api/v1/driver/plates
     *
     * NOTE:
     * Đây là chức năng đúng role DRIVER.
     * FE sử dụng ở:
     * - ProfileTab.jsx: thêm biển số trong hồ sơ
     * - DriverMapping.jsx: tự thêm biển số trước khi đặt chỗ nếu user nhập biển mới
     */
    @PostMapping("/driver/plates")
    public ApiResponse<String> addPlate(
            Authentication authentication,
            @Valid @RequestBody DriverPlateRequest request
    ) {
        User currentUser = getCurrentUser(authentication);
        String licensePlate = normalizeAndValidatePlate(request.getLicensePlate());

        /*
        * NOTE Quảng - Driver scope:
        * Không so sánh raw string vì:
        * - 51H12345
        * - 51H-123.45
        * - 51H 123.45
        * đều là cùng một biển số.
        */
        boolean plateExists = userLicensePlateRepository.findByUser(currentUser)
                .stream()
                .anyMatch(item -> LicensePlateUtil.normalize(item.getLicensePlate()).equals(licensePlate));

        if (plateExists) {
            throw new BusinessException("Biển số đã tồn tại");
        }

        UserLicensePlate userLicensePlate = UserLicensePlate.builder()
                .user(currentUser)
                .licensePlate(licensePlate)
                .build();

        userLicensePlateRepository.save(userLicensePlate);

        return ApiResponse.success("Thêm biển số thành công", licensePlate);
    }
    

    /**
     * Xóa biển số của driver đang đăng nhập.
     *
     * Method: DELETE
     * Endpoint: /api/v1/driver/plates?plate={plate}
     *
     * NOTE:
     * Chỉ xóa biển số thuộc tài khoản hiện tại.
     * Không cho driver xóa biển số của người khác.
     */

    @DeleteMapping("/driver/plates")
    public ApiResponse<String> deletePlate(
            Authentication authentication,
            @RequestParam String plate
    ) {
        User currentUser = getCurrentUser(authentication);
        String licensePlate = normalizeAndValidatePlate(plate);

        UserLicensePlate userLicensePlate = userLicensePlateRepository.findByUser(currentUser)
                .stream()
                .filter(item -> LicensePlateUtil.normalize(item.getLicensePlate()).equals(licensePlate))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biển số cần xóa"));

        userLicensePlateRepository.delete(userLicensePlate);

        return ApiResponse.success("Xóa biển số thành công", licensePlate);
    }


    // =========================================================
    // DRIVER PARKING PASS APIs
    // Phần này phục vụ FE Driver ProfileTab:
    // - Xem vé tháng / quý / năm
    // - Đăng ký vé
    // - Tiếp tục thanh toán vé
    // =========================================================

    /**
     * Lấy danh sách vé gửi xe của driver đang đăng nhập.
     *
     * Method: GET
     * Endpoint: /api/v1/driver/parking-passes
     *
     * FE gọi hàm này để hiển thị tab vé tháng/quý/năm trong ProfileTab.
     */
    @GetMapping("/driver/parking-passes")
    public ApiResponse<List<Map<String, Object>>> getMyParkingPasses(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);

        List<Map<String, Object>> passes = parkingPassRepository.findByUser(currentUser)
                .stream()
                .map(this::toParkingPassResponse)
                .toList();

        return ApiResponse.success("Lấy danh sách vé gửi xe thành công", passes);
    }

    /**
     * Đăng ký vé gửi xe tháng / quý / năm.
     *
     * Method: POST
     * Endpoint: /api/v1/driver/parking-passes
     *
     * Body FE gửi lên thường có:
     * {
     *   "buildingId": "...",
     *   "vehicleTypeId": "...",
     *   "licensePlate": "30A-999.88",
     *   "passType": "MONTHLY"
     * }
     *
     * NOTE:
     * Vé mới tạo sẽ ở trạng thái PENDING_PAYMENT.
     * paymentUrl bên dưới là URL demo để FE chuyển sang trang payment-return.
     * Chưa phải VNPay thật.
     */
    @PostMapping("/driver/parking-passes")
    @Transactional
    public ApiResponse<Map<String, Object>> registerParkingPass(
            Authentication authentication,
            @Valid @RequestBody RegisterParkingPassRequest request
    ) {
        User currentUser = getCurrentUser(authentication);
        String licensePlate = normalizeAndValidatePlate(request.getLicensePlate());

        ensurePlateBelongsToUser(currentUser, licensePlate);
        ensureNoActiveOrPendingPass(currentUser, licensePlate, request.getPassType());

        Building building = buildingRepository.findById(request.getBuildingId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bãi xe"));

        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại xe"));

        BigDecimal monthlyPrice = findMonthlyPrice(building, vehicleType);
        BigDecimal fee = calculatePassFee(monthlyPrice, request.getPassType());

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = calculateEndDate(startDate, request.getPassType());

        ParkingPass parkingPass = ParkingPass.builder()
                .user(currentUser)
                .building(building)
                .vehicleType(vehicleType)
                .licensePlate(licensePlate)
                .qrCode("PASS-" + UUID.randomUUID())
                .startDate(startDate)
                .endDate(endDate)
                .passType(request.getPassType())
                .fee(fee)
                .status(ParkingPass.PassStatus.PENDING_PAYMENT)
                .build();

        ParkingPass savedPass = parkingPassRepository.save(parkingPass);

        Map<String, Object> data = toParkingPassResponse(savedPass);
        data.put("paymentUrl", buildFakeVnPayReturnUrl(savedPass));

        return ApiResponse.success("Tạo vé gửi xe thành công, chờ thanh toán", data);
    }

    /**
     * Tạo lại link thanh toán cho vé đã đăng ký nhưng chưa thanh toán.
     *
     * Method: POST
     * Endpoint: /api/v1/driver/parking-passes/{passId}/pay
     *
     * NOTE:
     * API này giúp FE có nút "Thanh toán tiếp".
     * Hiện tại trả về paymentUrl demo.
     */
    @PostMapping("/driver/parking-passes/{passId}/pay")
    public ApiResponse<Map<String, Object>> continueParkingPassPayment(
            Authentication authentication,
            @PathVariable UUID passId
    ) {
        User currentUser = getCurrentUser(authentication);

        ParkingPass parkingPass = parkingPassRepository.findById(passId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé gửi xe"));

        if (!parkingPass.getUser().getId().equals(currentUser.getId())) {
            throw new BusinessException("Bạn không có quyền thanh toán vé này");
        }

        if (parkingPass.getStatus() == ParkingPass.PassStatus.ACTIVE) {
            throw new BusinessException("Vé này đã được kích hoạt");
        }

        Map<String, Object> data = toParkingPassResponse(parkingPass);
        data.put("paymentUrl", buildFakeVnPayReturnUrl(parkingPass));

        return ApiResponse.success("Tạo link thanh toán thành công", data);
    }

    /**
 * Nhận kết quả thanh toán VNPay demo cho Parking Pass của Driver.
 *
 * Method: GET
 * Endpoints:
 * - /api/v1/driver/parking-passes/vnpay-return
 * - /api/v1/driver/payments/vnpay-return
 *
 * NOTE Quảng - Driver scope:
 * - Chỉ xử lý thanh toán vé tháng/quý/năm của Driver.
 * - Không xử lý thanh toán phiên gửi xe/check-out vì phần đó thuộc Staff/Payment.
 * - Khi Toàn merge PaymentController/VnPayService thật thì endpoint /driver/payments/vnpay-return
 *   có thể chuyển về module Payment chung.
 */
    @GetMapping({
        "/driver/parking-passes/vnpay-return",
        "/driver/payments/vnpay-return"
    })
    @Transactional
    public ApiResponse<Map<String, Object>> handleParkingPassVnPayReturn(
            Authentication authentication,
            @RequestParam Map<String, String> params
    ) {
        User currentUser = getCurrentUser(authentication);

        String responseCode = params.getOrDefault("vnp_ResponseCode", "");
        String txnRef = params.getOrDefault("vnp_TxnRef", "");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", false);
        data.put("paymentType", "PARKING_PASS");
        data.put("txnRef", txnRef);
        data.put("responseCode", responseCode);

        if (!"00".equals(responseCode)) {
            data.put("message", "Thanh toán thất bại hoặc bị hủy");
            return ApiResponse.success("Thanh toán thất bại", data);
        }

        if (txnRef == null || !txnRef.startsWith("PASS-")) {
            data.put("message", "Mã giao dịch không thuộc parking pass");
            return ApiResponse.success("Không xác định được loại giao dịch", data);
        }

        UUID passId;

        try {
            passId = UUID.fromString(txnRef.replace("PASS-", ""));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Mã giao dịch parking pass không hợp lệ");
        }

        ParkingPass parkingPass = parkingPassRepository.findById(passId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé gửi xe"));

        if (!parkingPass.getUser().getId().equals(currentUser.getId())) {
            throw new BusinessException("Bạn không có quyền xác nhận thanh toán vé này");
        }

        /*
        * NOTE Quảng - Driver scope:
        * Idempotent callback: nếu user refresh trang return hoặc callback bị gọi lại,
        * vé đã ACTIVE thì vẫn trả success, không báo lỗi.
        */
        if (parkingPass.getStatus() != ParkingPass.PassStatus.ACTIVE) {
            parkingPass.setStatus(ParkingPass.PassStatus.ACTIVE);
            parkingPass = parkingPassRepository.save(parkingPass);
        }

        data.put("success", true);
        data.put("message", "Thanh toán thành công, vé đã được kích hoạt");
        data.put("pass", toParkingPassResponse(parkingPass));

        return ApiResponse.success("Xác nhận thanh toán parking pass thành công", data);
    }

    /**
 * Nhận IPN VNPay demo cho Parking Pass.
 *
 * Method: GET
 * Endpoint: /api/v1/driver/payments/vnpay-ipn
 *
 * NOTE Quảng - Driver scope:
 * SecurityConfig hiện đã mở public endpoint này.
 * Bản demo chỉ xử lý PASS-* để không đụng checkout session của role khác.
 */
@GetMapping("/driver/payments/vnpay-ipn")
@PreAuthorize("permitAll()")
@Transactional
public ApiResponse<Map<String, Object>> handleParkingPassVnPayIpn(
        @RequestParam Map<String, String> params
) {
    String responseCode = params.getOrDefault("vnp_ResponseCode", "");
    String txnRef = params.getOrDefault("vnp_TxnRef", "");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("success", false);
    data.put("paymentType", "PARKING_PASS");
    data.put("txnRef", txnRef);
    data.put("responseCode", responseCode);

    if (!"00".equals(responseCode)) {
        data.put("message", "Thanh toán thất bại hoặc bị hủy");
        return ApiResponse.success("Thanh toán thất bại", data);
    }

    if (txnRef == null || !txnRef.startsWith("PASS-")) {
        data.put("message", "IPN này không thuộc parking pass của Driver");
        return ApiResponse.success("Bỏ qua IPN không thuộc parking pass", data);
    }

    UUID passId;

    try {
        passId = UUID.fromString(txnRef.replace("PASS-", ""));
    } catch (IllegalArgumentException ex) {
        throw new BusinessException("Mã giao dịch parking pass không hợp lệ");
    }

    ParkingPass parkingPass = parkingPassRepository.findById(passId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé gửi xe"));

    if (parkingPass.getStatus() != ParkingPass.PassStatus.ACTIVE) {
        parkingPass.setStatus(ParkingPass.PassStatus.ACTIVE);
        parkingPass = parkingPassRepository.save(parkingPass);
    }

    data.put("success", true);
    data.put("message", "IPN thành công, vé đã được kích hoạt");
    data.put("pass", toParkingPassResponse(parkingPass));

    return ApiResponse.success("Xác nhận IPN parking pass thành công", data);
}


    /**
 * Hủy đơn vé tháng/quý/năm đang chờ thanh toán.
 *
 * Method: DELETE
 * Endpoint: /api/v1/driver/parking-passes/{passId}/cancel
 *
 * NOTE Quảng - Driver scope:
 * Driver chỉ được hủy đơn của chính mình khi status là PENDING_PAYMENT.
 */
@DeleteMapping("/driver/parking-passes/{passId}/cancel")
@Transactional
public ApiResponse<String> cancelPendingParkingPass(
        Authentication authentication,
        @PathVariable UUID passId
) {
    User currentUser = getCurrentUser(authentication);

    ParkingPass pass = parkingPassRepository.findById(passId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé định kỳ"));

    if (!pass.getUser().getId().equals(currentUser.getId())) {
        throw new BusinessException("Bạn không có quyền hủy đơn thanh toán này");
    }

    if (pass.getStatus() != ParkingPass.PassStatus.PENDING_PAYMENT) {
        throw new BusinessException("Chỉ có thể hủy đơn đang chờ thanh toán");
    }

    pass.setStatus(ParkingPass.PassStatus.CANCELLED);
    parkingPassRepository.save(pass);

    return ApiResponse.success("Hủy đơn thanh toán thành công", pass.getId().toString());
}
    // =========================================================
    // PRIVATE HELPER METHODS
    // Các hàm phụ trợ bên dưới giúp controller gọn hơn.
    // =========================================================

     /**
     * Chuyển entity ParkingPass thành Map để FE dễ đọc.
     *
     * NOTE:
     * Trả cả id và passId vì FE có thể dùng một trong hai tên.
     */
    private Map<String, Object> toParkingPassResponse(ParkingPass pass) {
        Map<String, Object> item = new LinkedHashMap<>();

        item.put("id", pass.getId());
        item.put("passId", pass.getId());

        item.put("buildingId", pass.getBuilding() != null ? pass.getBuilding().getId() : null);
        item.put("buildingName", pass.getBuilding() != null ? pass.getBuilding().getName() : null);

        item.put("vehicleTypeId", pass.getVehicleType() != null ? pass.getVehicleType().getId() : null);
        item.put("vehicleTypeName", pass.getVehicleType() != null ? pass.getVehicleType().getName() : null);

        item.put("licensePlate", pass.getLicensePlate());
        item.put("qrCode", pass.getQrCode());
        item.put("startDate", pass.getStartDate());
        item.put("endDate", pass.getEndDate());
        item.put("passType", pass.getPassType());
        item.put("fee", pass.getFee());
        item.put("status", pass.getStatus());
        item.put("createdAt", pass.getCreatedAt());

        return item;
    }

    /**
 * Tìm giá MONTHLY theo building + vehicleType.
 *
 * NOTE Quảng - Driver scope:
 * Không thêm method custom vào PricingRuleRepository vì đây là file chung.
 * Dùng findAll rồi lọc trong controller để tránh conflict khi merge.
 */
private BigDecimal findMonthlyPrice(Building building, VehicleType vehicleType) {
    return pricingRuleRepository.findAll()
            .stream()
            .filter(rule -> rule.getBuilding() != null)
            .filter(rule -> rule.getVehicleType() != null)
            .filter(rule -> rule.getBuilding().getId().equals(building.getId()))
            .filter(rule -> rule.getVehicleType().getId().equals(vehicleType.getId()))
            .filter(rule -> rule.getPricingType() == PricingRule.PricingType.MONTHLY)
            .map(PricingRule::getPricePerUnit)
            .findFirst()
            .orElseThrow(() -> new BusinessException("Chưa cấu hình giá vé tháng cho loại xe này"));
}

    /**
     * Tính tiền vé dựa theo loại vé.
     *
     * MONTHLY   = 1 tháng
     * QUARTERLY = 3 tháng
     * YEARLY    = 12 tháng, giảm 10% demo
     */
    private BigDecimal calculatePassFee(BigDecimal monthlyPrice, ParkingPass.PassType passType) {
        if (monthlyPrice == null) {
            monthlyPrice = BigDecimal.ZERO;
        }

        if (passType == ParkingPass.PassType.MONTHLY) {
            return monthlyPrice;
        }

        if (passType == ParkingPass.PassType.QUARTERLY) {
            return monthlyPrice.multiply(BigDecimal.valueOf(3));
        }

        if (passType == ParkingPass.PassType.YEARLY) {
            return monthlyPrice.multiply(BigDecimal.valueOf(12)).multiply(BigDecimal.valueOf(0.9));
        }

        return monthlyPrice;
    }

    /**
     * Tính ngày hết hạn vé.
     */
    private LocalDate calculateEndDate(LocalDate startDate, ParkingPass.PassType passType) {
        if (passType == ParkingPass.PassType.MONTHLY) {
            return startDate.plusMonths(1);
        }

        if (passType == ParkingPass.PassType.QUARTERLY) {
            return startDate.plusMonths(3);
        }

        if (passType == ParkingPass.PassType.YEARLY) {
            return startDate.plusYears(1);
        }

        return startDate.plusMonths(1);
    }

    /**
     * Tạo URL thanh toán demo.
     *
     * NOTE:
     * Đây không phải VNPay thật.
     * Mục tiêu là để FE không bị gãy luồng khi bấm thanh toán vé.
     */
    private String buildFakeVnPayReturnUrl(ParkingPass pass) {
        long amount = pass.getFee() == null
                ? 0L
                : pass.getFee().multiply(BigDecimal.valueOf(100)).longValue();

        return "/driver/payment-return"
                + "?vnp_ResponseCode=00"
                + "&vnp_TxnRef=PASS-" + pass.getId()
                + "&vnp_Amount=" + amount
                + "&vnp_BankCode=DEMO"
                + "&vnp_TransactionNo=" + System.currentTimeMillis()
                + "&vnp_PayDate=" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    /**
     * Đảm bảo biển số thuộc tài khoản Driver.
     *
     * NOTE:
     * Khi đăng ký parking pass, nếu driver nhập biển số mới,
     * hệ thống tự thêm biển số đó vào hồ sơ driver.
     *
     * Việc này vẫn đúng role Driver vì driver đang quản lý phương tiện của chính mình.
     */
    private void ensurePlateBelongsToUser(User user, String licensePlate) {
        boolean exists = userLicensePlateRepository.findByUser(user)
                .stream()
                .anyMatch(item -> LicensePlateUtil.normalize(item.getLicensePlate()).equals(licensePlate));

        if (!exists) {
            UserLicensePlate userLicensePlate = UserLicensePlate.builder()
                    .user(user)
                    .licensePlate(licensePlate)
                    .build();

            userLicensePlateRepository.save(userLicensePlate);
        }
    }

    /**
 * Kiểm tra driver không tạo trùng vé tháng/quý/năm cho cùng một biển số.
 *
 * NOTE Quảng - Driver scope:
 * Không sửa ParkingPassRepository vì đây là file shared.
 * Lọc bằng findByUser(user) để tránh conflict khi merge với team.
 */
private void ensureNoActiveOrPendingPass(
        User user,
        String licensePlate,
        ParkingPass.PassType passType
) {
    boolean existed = parkingPassRepository.findByUser(user)
            .stream()
            .filter(pass -> LicensePlateUtil.normalize(pass.getLicensePlate()).equals(licensePlate))
            .filter(pass -> pass.getPassType() == passType)
            .anyMatch(pass ->
                    pass.getStatus() == ParkingPass.PassStatus.ACTIVE
                            || pass.getStatus() == ParkingPass.PassStatus.PENDING_PAYMENT
            );

    if (existed) {
        throw new BusinessException("Biển số này đã có vé cùng loại đang hoạt động hoặc đang chờ thanh toán");
    }
}


    /**
     * Lấy user đang đăng nhập từ JWT.
     *
     * authentication.getName() là email được lưu trong token.
     */
    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException("Bạn cần đăng nhập để sử dụng chức năng này");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản driver"));
    }

    /**
     * Chuẩn hóa biển số xe Driver.
     *
     * Ví dụ:
     * - "51F-123.45" -> "51F12345"
     * - "51f 12345"  -> "51F12345"
     *
     * NOTE:
     * Dùng chung chuẩn với ReservationService để:
     * - thêm biển số
     * - đặt chỗ
     * - đăng ký parking pass
     * đều hiểu cùng một format.
     */
    private String normalizeAndValidatePlate(String plate) {
        String normalizedPlate = LicensePlateUtil.normalize(plate);

        if (normalizedPlate.isBlank()) {
            throw new BusinessException("Biển số không được để trống");
        }

        return normalizedPlate;
    }

    /**
     * DTO nội bộ cho request thêm biển số.
     *
     * Đặt trong controller để không sửa thư mục dto/request chung của team.
     */
    @Data
    private static class DriverPlateRequest {
        @NotBlank(message = "Biển số không được để trống")
        private String licensePlate;
    }

    /**
     * DTO nội bộ cho request đăng ký vé tháng/quý/năm.
     *
     * FE gửi:
     * - buildingId: bãi xe
     * - vehicleTypeId: loại xe
     * - licensePlate: biển số
     * - passType: MONTHLY / QUARTERLY / YEARLY
     */
    @Data
    private static class RegisterParkingPassRequest {
        @NotNull(message = "buildingId không được để trống")
        private UUID buildingId;

        @NotNull(message = "vehicleTypeId không được để trống")
        private UUID vehicleTypeId;

        @NotBlank(message = "Biển số không được để trống")
        private String licensePlate;

        @NotNull(message = "passType không được để trống")
        private ParkingPass.PassType passType;
    }
}