package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.LoginRequest;
import com.smartparking.backend.dto.request.OAuth2LoginRequest;
import com.smartparking.backend.dto.request.RegisterRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.LoginResponse;
import com.smartparking.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "APIs for user authentication: login, register, OAuth2, token refresh and logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/v1/auth/login
     * Body: { "email": "staff@parking.vn", "password": "123456" }
     */
    @Operation(summary = "Đăng nhập bằng email/password", description = "Trả về JWT access token và refresh token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    /**
     * API Đăng ký tài khoản Driver.
     * Route: POST /api/v1/auth/register
     * Access: Public
     */
    @Operation(summary = "Đăng ký tài khoản Driver mới", description = "Tạo tài khoản role DRIVER với email, password, fullName")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng ký tài khoản thành công", null));
    }

    /**
     * Đăng nhập bằng OAuth2 (Google / Facebook).
     * Route: POST /api/v1/auth/oauth2
     * Body: { "provider": "google", "email": "user@gmail.com", "fullName": "Nguyen Van A" }
     */
    @Operation(summary = "Đăng nhập bằng OAuth2 (Google/Facebook)", description = "Tự động tạo tài khoản nếu chưa tồn tại")
    @PostMapping("/oauth2")
    public ResponseEntity<ApiResponse<LoginResponse>> oauth2Login(
            @Valid @RequestBody OAuth2LoginRequest request) {
        LoginResponse response = authService.oauth2Login(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Đăng nhập " + request.getProvider() + " thành công", response));
    }

    /**
     * POST /api/v1/auth/refresh
     * Header: Authorization: Bearer <refresh_token>
     */
    @Operation(summary = "Làm mới access token", description = "Dùng refresh token để lấy access token mới")
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
    @Operation(summary = "Đăng xuất", description = "Client xoá token phía FE — stateless")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }
}
