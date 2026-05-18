package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.LoginRequest;
import com.smartparking.backend.dto.request.RegisterRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.LoginResponse;
import com.smartparking.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/v1/auth/login
     * Body: { "email": "staff@parking.vn", "password": "123456" }
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }
    /**
     * API Đăng ký tài khoản Driver.
     * Route: POST /api/v1/auth/register
     * Access: Public (Được cấu hình trong SecurityConfig)
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        // Gọi xuống tầng Service để xử lý nghiệp vụ
        authService.register(request);

        // Trả về ApiResponse chuẩn của dự án với thông báo thành công
        return ResponseEntity.ok(ApiResponse.success("Đăng ký tài khoản thành công", null));
    }
    /**
     * POST /api/v1/auth/refresh
     * Header: Authorization: Bearer <refresh_token>
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @RequestHeader("Authorization") String authHeader) {
        String refreshToken = authHeader.substring(7);
        LoginResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Token đã được làm mới", response));
    }

    /**
     * POST /api/v1/auth/logout
     * (Client xoá token phía FE — stateless nên BE không cần làm gì thêm)
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }
}
