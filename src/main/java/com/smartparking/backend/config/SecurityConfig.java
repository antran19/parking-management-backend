package com.smartparking.backend.config;

import com.smartparking.backend.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig — Cấu hình bảo mật trung tâm cho SmartParking V2.
 *
 * Kiến trúc phân quyền 2 lớp (Two-Layer Authorization):
 *   Layer 1: URL Path-based (SecurityFilterChain) — lọc theo nhóm đường dẫn
 *   Layer 2: Method-level (@PreAuthorize)          — kiểm soát từng API cụ thể
 *
 * Mô hình Role Hierarchy (cao → thấp):
 *   ADMIN > MANAGER > STAFF > DRIVER
 *   - ADMIN:    Toàn quyền hệ thống, quản lý user, cấu hình tổng
 *   - MANAGER:  Quản lý vận hành, báo cáo, cấu hình zone/giá
 *   - STAFF:    Nhân viên soát vé, check-in/check-out xe
 *   - DRIVER:   Tài xế, đặt chỗ, xem lịch sử, thanh toán
 *   - SECURITY: Bảo vệ, ghi log ngoại lệ an ninh (nhánh riêng)
 *
 * JWT Token Flow:
 *   1. Client gửi request kèm header: Authorization: Bearer <jwt_token>
 *   2. JwtAuthFilter validate token → extract role → set SecurityContext
 *   3. SecurityFilterChain kiểm tra path prefix → cho phép hoặc từ chối
 *   4. @PreAuthorize trên method kiểm tra chi tiết hơn (nếu có)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Bật @PreAuthorize trên method level
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, UserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENDPOINT KHÔNG CẦN XÁC THỰC (Public — permitAll)
    // ═══════════════════════════════════════════════════════════════════
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**",           // Đăng nhập, đăng ký, refresh token
            "/api/v1/public/**",         // Thông tin bãi xe công khai (nếu cần)
            "/api/v1/emergency/status",  // Trạng thái SOS cho mọi dashboard
            "/api/v1/driver/payments/vnpay-ipn", // VNPay server-to-server callback
            "/ws/**",                    // WebSocket STOMP endpoint
            "/actuator/health",          // Health check cho DevOps
            "/swagger-ui.html",          // Swagger redirect
            "/swagger-ui/**",            // Swagger UI static files
            "/v3/api-docs/**",           // OpenAPI specification
            "/swagger-resources/**",     // Swagger resources
            "/webjars/**"                // Swagger webjars
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                // ── Public: không cần JWT ──
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                // ── Parking: Dữ liệu dùng chung cho TẤT CẢ user đã đăng nhập ──
                // Zone config, vehicle types, gates — Driver/Staff/Manager đều cần
                .requestMatchers("/api/v1/parking/**").authenticated()

                // ── Driver: Tài xế + các role cao hơn ──
                // Active session, history, bookings, payments
                .requestMatchers("/api/v1/driver/**").hasAnyRole("DRIVER", "STAFF", "MANAGER", "ADMIN")

                // ── Staff: Nhân viên soát vé + Quản lý + Admin ──
                // Check-in, check-out, quản lý session
                .requestMatchers("/api/v1/staff/**").hasAnyRole("STAFF", "MANAGER", "ADMIN")

                // ── Security: Bảo vệ + Quản lý + Admin ──
                // Log ngoại lệ an ninh, giám sát cổng
                .requestMatchers("/api/v1/security/exceptions", "/api/v1/security/exceptions/**").hasAnyRole("SECURITY", "STAFF", "MANAGER", "ADMIN")
                .requestMatchers("/api/v1/security/**").hasAnyRole("SECURITY", "MANAGER", "ADMIN")

                // ── Manager: Quản lý vận hành + Admin ──
                // Báo cáo, cấu hình zone/giá, dashboard tổng quan
                .requestMatchers("/api/v1/manager/**").hasAnyRole("MANAGER", "ADMIN")

                // ── Admin namespace: ADMIN + MANAGER được qua URL layer.
                // Method-level @PreAuthorize sẽ giữ user/settings chỉ ADMIN,
                // còn zone/gate/pricing/pass là vận hành cho MANAGER + ADMIN.
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "MANAGER")

                // ── Mọi request khác: phải đăng nhập ──
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CORS — Cho phép Frontend (Vite dev server) truy cập API
    // ═══════════════════════════════════════════════════════════════════
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOrigins(java.util.List.of(
                "http://localhost:5173",
                "http://localhost:5174",
                "https://localhost:5173",
                "https://localhost:5174"
        ));
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Pre-flight cache 1 hour

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source =
                new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ═══════════════════════════════════════════════════════════════════
    // AUTHENTICATION PROVIDER — DaoAuthenticationProvider + BCrypt
    // ═══════════════════════════════════════════════════════════════════
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
