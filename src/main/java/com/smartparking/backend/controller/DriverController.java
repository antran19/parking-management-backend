package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.repository.*;
import com.smartparking.backend.service.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    // TODO: Inject các repository/service cần thiết
    // private final UserLicensePlateRepository userLicensePlateRepository;
    // private final ParkingPassRepository parkingPassRepository;
    // private final PricingRuleRepository pricingRuleRepository;
    // private final UserRepository userRepository;
    // private final VnPayService vnPayService;

    // TODO: Constructor injection

    // TODO: Implement endpoints
}
