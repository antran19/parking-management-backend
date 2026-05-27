package com.smartparking.backend.security;

import com.smartparking.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — chạy 1 lần mỗi request.
 * Đọc Bearer token từ Authorization header, validate và set SecurityContext.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Nếu không có Bearer token → bỏ qua, để Spring Security tự xử lý
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String userEmail = jwtUtil.extractUsername(jwt);

            // --- THAY ĐỔI: Trích xuất loại token (type claim) để validate ---
            final String tokenType = jwtUtil.extractClaim(jwt, claims -> claims.get("type", String.class));

            // Chỉ xử lý nếu token hợp lệ, chưa có auth trong Security Context và PHẢI LÀ
            // access token
            if (userEmail != null && "access".equals(tokenType)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (jwtUtil.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } else if (userEmail != null && !"access".equals(tokenType)) {
                // Ghi nhận cảnh báo nếu client cố tình dùng refresh token để gọi API thường
                log.warn("Cảnh báo bảo mật: Phát hiện token loại '{}' cố gắng truy cập API bảo mật!", tokenType);
            }
            // ----------------------------------------------------------------
        } catch (Exception e) {
            log.warn("JWT processing failed for request [{}]: {}", request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
