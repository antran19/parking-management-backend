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

    public DriverController(UserLicensePlateRepository userLicensePlateRepository,
                            UserRepository userRepository) {
        this.userLicensePlateRepository = userLicensePlateRepository;
        this.userRepository = userRepository;
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

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException("Không xác định được người dùng hiện tại");
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản hiện tại"));
    }
}