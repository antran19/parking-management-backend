package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.ParkingPassRequest;
import com.smartparking.backend.dto.response.ParkingPassResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParkingPassService {

    private static final Logger log = LoggerFactory.getLogger(ParkingPassService.class);

    private final ParkingPassRepository parkingPassRepository;
    private final UserRepository userRepository;
    private final BuildingRepository buildingRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Mua vé gửi xe mới (MONTHLY/QUARTERLY/YEARLY) cho Driver.
     */
    @Transactional
    public ParkingPassResponse buyPass(ParkingPassRequest request, String email) {
        log.info("Driver {} is purchasing a {} pass for license plate {}", email, request.getPassType(), request.getLicensePlate());

        // 1. Tìm thông tin tham chiếu
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người dùng"));
        Building building = buildingRepository.findById(request.getBuildingId())
                .orElseThrow(() -> new ResourceNotFoundException("Tòa nhà không tồn tại"));
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Loại phương tiện không tồn tại"));

        // 2. Tính toán ngày kết thúc
        LocalDate startDate = request.getStartDate();
        LocalDate endDate;
        switch (request.getPassType()) {
            case MONTHLY:
                endDate = startDate.plusMonths(1).minusDays(1);
                break;
            case QUARTERLY:
                endDate = startDate.plusMonths(3).minusDays(1);
                break;
            case YEARLY:
                endDate = startDate.plusMonths(12).minusDays(1);
                break;
            default:
                throw new BusinessException("Loại gói đăng ký không hợp lệ");
        }

        // 3. Kiểm tra vé hoạt động bị trùng lặp thời gian
        boolean isOverlapping = parkingPassRepository.existsOverlappingActivePass(
                request.getLicensePlate(), building.getId(), vehicleType.getId(), startDate, endDate);
        if (isOverlapping) {
            throw new BusinessException("Biển số xe " + request.getLicensePlate() + 
                    " đã có gói đăng ký hoạt động trùng với khoảng thời gian đăng ký mới (" + 
                    startDate + " đến " + endDate + ").");
        }

        // 4. Tính phí đăng ký (được cấu hình trong PricingRule dạng MONTHLY hoặc sử dụng mặc định)
        BigDecimal monthlyPrice = pricingRuleRepository
                .findByBuildingIdAndVehicleTypeIdAndPricingType(building.getId(), vehicleType.getId(), PricingRule.PricingType.MONTHLY)
                .map(PricingRule::getPricePerUnit)
                .orElseGet(() -> {
                    String typeName = vehicleType.getName().toLowerCase();
                    if (typeName.contains("xe máy")) {
                        return new BigDecimal("120000"); // 120k / tháng
                    } else if (typeName.contains("điện")) {
                        return new BigDecimal("150000"); // 150k / tháng
                    } else if (typeName.contains("ô tô") || typeName.contains("o to")) {
                        return new BigDecimal("1200000"); // 1.2M / tháng
                    } else {
                        return new BigDecimal("200000"); // 200k / tháng mặc định
                    }
                });

        BigDecimal totalFee = BigDecimal.ZERO;
        switch (request.getPassType()) {
            case MONTHLY:
                totalFee = monthlyPrice;
                break;
            case QUARTERLY:
                // Chiết khấu 10% cho gói Quý
                totalFee = monthlyPrice.multiply(BigDecimal.valueOf(3)).multiply(BigDecimal.valueOf(0.90));
                break;
            case YEARLY:
                // Chiết khấu 20% cho gói Năm
                totalFee = monthlyPrice.multiply(BigDecimal.valueOf(12)).multiply(BigDecimal.valueOf(0.80));
                break;
        }
        totalFee = totalFee.setScale(0, RoundingMode.HALF_UP);

        // 5. Khởi tạo đối tượng đăng ký vé gửi xe
        String qrCode = generateQrCode();
        ParkingPass pass = ParkingPass.builder()
                .user(user)
                .building(building)
                .vehicleType(vehicleType)
                .licensePlate(request.getLicensePlate())
                .qrCode(qrCode)
                .startDate(startDate)
                .endDate(endDate)
                .passType(request.getPassType())
                .fee(totalFee)
                .status(ParkingPass.PassStatus.ACTIVE)
                .build();

        pass = parkingPassRepository.save(pass);

        // 6. Tạo Payment record giả lập thanh toán thành công
        Payment.PaymentMethod paymentMethod;
        try {
            paymentMethod = Payment.PaymentMethod.valueOf(request.getPaymentMethod().toUpperCase());
        } catch (Exception e) {
            paymentMethod = Payment.PaymentMethod.ONLINE;
        }

        Payment payment = Payment.builder()
                .referenceType("MONTHLY_PASS")
                .referenceId(pass.getId())
                .amount(totalFee)
                .paymentMethod(paymentMethod)
                .status(Payment.PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now())
                .build();
        paymentRepository.save(payment);

        log.info("Successfully purchased pass {} with fee {}", pass.getId(), totalFee);
        return mapToResponse(pass);
    }

    /**
     * Xem danh sách tất cả vé của Driver đang đăng nhập.
     */
    @Transactional(readOnly = true)
    public List<ParkingPassResponse> getMyPasses(String email) {
        return parkingPassRepository.findByUserEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Xem danh sách vé đang ACTIVE của Driver đang đăng nhập.
     */
    @Transactional(readOnly = true)
    public List<ParkingPassResponse> getMyActivePasses(String email) {
        return parkingPassRepository.findActivePassesByUserEmail(email)
                .stream()
                // Lọc thêm điều kiện ngày để đảm bảo vé đang trong hạn sử dụng
                .filter(pass -> !LocalDate.now().isBefore(pass.getStartDate()) && !LocalDate.now().isAfter(pass.getEndDate()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Helper map sang Response DTO
    private ParkingPassResponse mapToResponse(ParkingPass pass) {
        return ParkingPassResponse.builder()
                .id(pass.getId())
                .userEmail(pass.getUser().getEmail())
                .userName(pass.getUser().getFullName())
                .buildingName(pass.getBuilding().getName())
                .vehicleTypeName(pass.getVehicleType().getName())
                .licensePlate(pass.getLicensePlate())
                .qrCode(pass.getQrCode())
                .startDate(pass.getStartDate())
                .endDate(pass.getEndDate())
                .passType(pass.getPassType().name())
                .fee(pass.getFee())
                .status(pass.getStatus().name())
                .createdAt(pass.getCreatedAt())
                .build();
    }

    // Helper tạo mã QR ngẫu nhiên
    private String generateQrCode() {
        String dateStr = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomStr = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        return "PASS-" + dateStr + "-" + randomStr;
    }
}
