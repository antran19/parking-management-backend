package com.smartparking.backend.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Response khi đăng nhập thành công.
 */
@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;      // seconds
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private String id;
        private String email;
        private String fullName;
        private String role;     // ADMIN / MANAGER / STAFF / DRIVER
    }
}
