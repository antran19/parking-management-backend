package com.smartparking.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import com.smartparking.backend.entity.Payment;
import com.smartparking.backend.entity.Reservation;
import com.smartparking.backend.repository.ReservationRepository;
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
import com.smartparking.backend.repository.PaymentRepository;
import com.smartparking.backend.repository.PricingRuleRepository;
import com.smartparking.backend.repository.UserLicensePlateRepository;
import com.smartparking.backend.repository.UserRepository;
import com.smartparking.backend.repository.VehicleTypeRepository;
import com.smartparking.backend.service.VnPayService;
import com.smartparking.backend.util.LicensePlateUtil;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DriverController — API cho Driver.
 *
 * Quảng phụ trách:
 * - GET /api/v1/driver/plates -- lấy danh sách biển số của driver
 * - POST /api/v1/driver/plates -- thêm biển số mới cho driver
 * - DELETE /api/v1/driver/plates?plate={plate} -- xóa biển số của driver
 * - GET /api/v1/driver/pricing-plans -- lấy bảng giá gửi xe theo loại xe
 * - GET /api/v1/driver/parking-passes -- lấy danh sách vé tháng/quý/năm của
 * driver
 * - POST /api/v1/driver/parking-passes -- đăng ký vé tháng/quý/năm mới
 * - DELETE /api/v1/driver/parking-passes/{passId}/cancel -- hủy đơn vé đang chờ
 * thanh toán
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
public class DriverController {

    private final UserRepository userRepository;
    private final UserLicensePlateRepository userLicensePlateRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ReservationRepository reservationRepository;
    /**
     * NOTE:
     * 3 repository dưới đây dùng cho chức năng vé tháng / quý / năm của Driver.
     */
    private final ParkingPassRepository parkingPassRepository;
    private final BuildingRepository buildingRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final PaymentRepository paymentRepository;
    private final VnPayService vnPayService;

    public DriverController(
            UserRepository userRepository,
            UserLicensePlateRepository userLicensePlateRepository,
            PricingRuleRepository pricingRuleRepository,
            ReservationRepository reservationRepository,
            ParkingPassRepository parkingPassRepository,
            BuildingRepository buildingRepository,
            VehicleTypeRepository vehicleTypeRepository,
            PaymentRepository paymentRepository,
            VnPayService vnPayService) {
        this.userRepository = userRepository;
        this.userLicensePlateRepository = userLicensePlateRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.reservationRepository = reservationRepository;
        this.parkingPassRepository = parkingPassRepository;
        this.buildingRepository = buildingRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.paymentRepository = paymentRepository;
        this.vnPayService = vnPayService;
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
                        java.util.LinkedHashMap::new))
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
            @Valid @RequestBody DriverPlateRequest request) {
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
            throw new BusinessException("Biển số đã tồn tại trong tài khoản của bạn");
        }
        boolean plateBelongsToAnotherUser = userLicensePlateRepository.findAll()
                .stream()
                .filter(item -> item.getUser() != null)
                .filter(item -> !item.getUser().getId().equals(currentUser.getId()))
                .anyMatch(item -> LicensePlateUtil.normalize(item.getLicensePlate()).equals(licensePlate));

        if (plateBelongsToAnotherUser) {
            throw new BusinessException("Biển số này đã được đăng ký bởi tài khoản khác");
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
            @RequestParam String plate) {
        User currentUser = getCurrentUser(authentication);
        String licensePlate = normalizeAndValidatePlate(plate);

        ensurePlateHasNoActiveReservation(currentUser, licensePlate);
        ensurePlateHasNoActiveOrPendingPass(currentUser, licensePlate);

        UserLicensePlate userLicensePlate = userLicensePlateRepository.findByUser(currentUser)
                .stream()
                .filter(item -> LicensePlateUtil.normalize(item.getLicensePlate()).equals(licensePlate))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biển số cần xóa"));

        userLicensePlateRepository.delete(userLicensePlate);

        return ApiResponse.success("Xóa biển số thành công", licensePlate);
    }

    /**
     * Không cho Driver xóa biển số nếu biển số đó còn đặt chỗ chưa hoàn tất.
     *
     * Trạng thái bị chặn:
     * - PENDING
     * - CONFIRMED
     */
    private void ensurePlateHasNoActiveReservation(User user, String licensePlate) {
        boolean existed = reservationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .filter(reservation -> reservation.getStatus() == Reservation.ReservationStatus.PENDING
                        || reservation.getStatus() == Reservation.ReservationStatus.CONFIRMED)
                .anyMatch(
                        reservation -> LicensePlateUtil.normalize(reservation.getLicensePlate()).equals(licensePlate));

        if (existed) {
            throw new BusinessException("Không thể xóa biển số đang có đặt chỗ chưa hoàn tất");
        }
    }

    /**
     * Không cho Driver xóa biển số nếu biển số đó còn vé gửi xe đang hoạt động
     * hoặc đang chờ thanh toán.
     *
     * Trạng thái bị chặn:
     * - ACTIVE
     * - PENDING_PAYMENT
     */
    private void ensurePlateHasNoActiveOrPendingPass(User user, String licensePlate) {
        boolean existed = parkingPassRepository.findByUser(user)
                .stream()
                .filter(pass -> LicensePlateUtil.normalize(pass.getLicensePlate()).equals(licensePlate))
                .anyMatch(pass -> pass.getStatus() == ParkingPass.PassStatus.ACTIVE
                        || pass.getStatus() == ParkingPass.PassStatus.PENDING_PAYMENT);

        if (existed) {
            throw new BusinessException("Không thể xóa biển số đang có vé gửi xe hoạt động hoặc chờ thanh toán");
        }
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
     * "buildingId": "...",
     * "vehicleTypeId": "...",
     * "licensePlate": "30A-999.88",
     * "passType": "MONTHLY"
     * }
     *
     * NOTE:
     * Vé mới tạo sẽ ở trạng thái PENDING_PAYMENT.
     * Backend tạo Payment PENDING và trả paymentUrl VNPay sandbox để FE redirect.
     */
    @PostMapping("/driver/parking-passes")
    @Transactional
    public ApiResponse<Map<String, Object>> registerParkingPass(
            Authentication authentication,
            @Valid @RequestBody RegisterParkingPassRequest request,
            HttpServletRequest httpRequest) {
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

        Map<String, Object> data = createOrReusePassPayment(savedPass, httpRequest);

        return ApiResponse.success("Tạo vé gửi xe thành công, chờ thanh toán VNPay", data);
    }

    /**
     * Tiếp tục thanh toán vé đã đăng ký nhưng chưa thanh toán.
     *
     * NOTE Quảng - Driver scope:
     * - Driver chỉ validate vé của chính mình và trạng thái PENDING_PAYMENT.
     * - Không tạo fake URL.
     * - Gọi VnPayService để tạo URL thanh toán sandbox và chờ callback kích hoạt vé.
     */
    @PostMapping("/driver/parking-passes/{passId}/pay")
    public ApiResponse<Map<String, Object>> continueParkingPassPayment(
            Authentication authentication,
            @PathVariable UUID passId,
            HttpServletRequest httpRequest) {
        User currentUser = getCurrentUser(authentication);

        ParkingPass parkingPass = parkingPassRepository.findById(passId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé gửi xe"));

        if (!parkingPass.getUser().getId().equals(currentUser.getId())) {
            throw new BusinessException("Bạn không có quyền thanh toán vé này");
        }

        if (parkingPass.getStatus() == ParkingPass.PassStatus.ACTIVE) {
            throw new BusinessException("Vé này đã được kích hoạt");
        }

        if (parkingPass.getStatus() == ParkingPass.PassStatus.CANCELLED) {
            throw new BusinessException("Vé này đã bị hủy");
        }

        if (parkingPass.getStatus() == ParkingPass.PassStatus.EXPIRED) {
            throw new BusinessException("Vé này đã hết hạn");
        }

        if (parkingPass.getStatus() != ParkingPass.PassStatus.PENDING_PAYMENT) {
            throw new BusinessException("Chỉ có thể thanh toán vé đang chờ thanh toán");
        }

        Map<String, Object> data = createOrReusePassPayment(parkingPass, httpRequest);

        return ApiResponse.success("Vé hợp lệ, đã tạo liên kết thanh toán VNPay", data);
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
            @PathVariable UUID passId) {
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
     * Tạo hoặc tái sử dụng đơn thanh toán VNPay cho parking pass.
     *
     * Flow này ghép phần Driver với module Payment/VNPay đã merge ở nhánh develop:
     * - Driver tạo parking pass ở trạng thái PENDING_PAYMENT.
     * - Backend tạo Payment PENDING referenceType = PASS.
     * - Backend trả paymentUrl để FE redirect sang VNPay sandbox.
     * - Khi VNPay callback /driver/payments/vnpay-return hoặc /vnpay-ipn,
     *   PaymentController sẽ kích hoạt pass nếu giao dịch thành công.
     */
    private Map<String, Object> createOrReusePassPayment(ParkingPass pass, HttpServletRequest request) {
        BigDecimal amount = pass.getFee() == null ? BigDecimal.ZERO : pass.getFee();

        Payment payment = paymentRepository.findByReferenceTypeAndReferenceId("PASS", pass.getId())
                .stream()
                .filter(item -> item.getPaymentMethod() == Payment.PaymentMethod.ONLINE)
                .filter(item -> item.getStatus() != Payment.PaymentStatus.COMPLETED)
                .findFirst()
                .orElseGet(() -> Payment.builder()
                        .referenceType("PASS")
                        .referenceId(pass.getId())
                        .amount(amount)
                        .paymentMethod(Payment.PaymentMethod.ONLINE)
                        .status(Payment.PaymentStatus.PENDING)
                        .transactionId(generatePassOrderCode(pass.getId()))
                        .build());

        payment.setAmount(amount);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
            payment.setTransactionId(generatePassOrderCode(pass.getId()));
        }
        payment = paymentRepository.save(payment);

        String orderInfo = "Thanh toan ve gui xe " + pass.getPassType() + " bien so " + pass.getLicensePlate();
        String paymentUrl = vnPayService.createPaymentUrl(payment.getTransactionId(), amount, orderInfo, request);

        Map<String, Object> data = toParkingPassResponse(pass);
        data.put("paymentStatus", payment.getStatus());
        data.put("paymentId", payment.getId());
        data.put("orderCode", payment.getTransactionId());
        data.put("paymentUrl", paymentUrl);
        data.put("note", "Chuyển tới VNPay sandbox để thanh toán và kích hoạt vé.");

        return data;
    }

    private String generatePassOrderCode(UUID passId) {
        return "PASS-" + passId.toString().replace("-", "").substring(0, 16).toUpperCase();
    }

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
     * MONTHLY = 1 tháng
     * QUARTERLY = 3 tháng
     * YEARLY = 12 tháng, giảm 10% demo
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
     * Kiểm tra biển số dùng để mua gói có thuộc tài khoản Driver hay không.
     *
     * Không tự động thêm biển số ở đây, vì Driver đã có API riêng:
     * POST /api/v1/driver/plates
     */
    private void ensurePlateBelongsToUser(User user, String licensePlate) {
        boolean exists = userLicensePlateRepository.findByUser(user)
                .stream()
                .anyMatch(item -> LicensePlateUtil.normalize(item.getLicensePlate()).equals(licensePlate));

        if (!exists) {
            throw new BusinessException("Biển số chưa được đăng ký bởi driver này");
        }
    }

    /**
     * Kiểm tra driver không tạo trùng vé cho cùng một biển số.
     *
     * Quy tắc:
     * - Một biển số chỉ nên có 1 vé ACTIVE hoặc PENDING_PAYMENT tại một thời điểm.
     * - Không phân biệt MONTHLY/QUARTERLY/YEARLY vì mua chồng gói sẽ sai nghiệp vụ.
     */
    private void ensureNoActiveOrPendingPass(
            User user,
            String licensePlate,
            ParkingPass.PassType passType) {
        boolean existed = parkingPassRepository.findByUser(user)
                .stream()
                .filter(pass -> LicensePlateUtil.normalize(pass.getLicensePlate()).equals(licensePlate))
                .anyMatch(pass -> pass.getStatus() == ParkingPass.PassStatus.ACTIVE
                        || pass.getStatus() == ParkingPass.PassStatus.PENDING_PAYMENT);

        if (existed) {
            throw new BusinessException("Biển số này đã có vé đang hoạt động hoặc đang chờ thanh toán");
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
     * - "51f 12345" -> "51F12345"
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