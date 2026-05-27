package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.UpdateProfileRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.DriverProfileResponse;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/driver")
@PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')") // Cấu hình bảo vệ: Chỉ tài khoản DRIVER hoặc ADMIN mới gọi được nhóm API này
public class DriverController {

    private final UserRepository userRepository;

    public DriverController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * GET /api/v1/driver/profile
     * Lấy thông tin cá nhân Driver
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<DriverProfileResponse>> getProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người dùng"));

        DriverProfileResponse response = DriverProfileResponse.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin cá nhân thành công", response));
    }

    /**
     * PUT /api/v1/driver/profile
     * Cập nhật thông tin cá nhân của Driver
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<DriverProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người dùng"));

        // Cập nhật thông tin mới
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        userRepository.save(user);

        DriverProfileResponse response = DriverProfileResponse.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin cá nhân thành công", response));
    }
}
