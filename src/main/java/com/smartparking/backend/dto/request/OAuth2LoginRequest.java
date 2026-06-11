package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO nhận dữ liệu đăng nhập OAuth2 từ Google / Facebook.
 * Frontend gửi thông tin user profile (email, tên, provider) sau khi xác thực OAuth thành công.
 */
@Data
public class OAuth2LoginRequest {

    @NotBlank(message = "Provider không được để trống")
    private String provider; // "google" hoặc "facebook"

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Tên không được để trống")
    private String fullName;

    private String avatarUrl; // URL ảnh đại diện từ Google/Facebook (optional)
}
