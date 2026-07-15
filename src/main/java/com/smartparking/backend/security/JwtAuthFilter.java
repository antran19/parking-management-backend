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
 * =====================================================================
 * JWT Authentication Filter
 * =====================================================================
 * Kế thừa OncePerRequestFilter → Spring đảm bảo filter này chỉ chạy
 * ĐÚNG 1 LẦN cho mỗi HTTP request (tránh bị gọi trùng lặp).
 *
 * Nhiệm vụ: Đọc JWT token từ header "Authorization", xác thực token,
 * và nếu hợp lệ thì gắn thông tin người dùng vào SecurityContext để
 * Spring Security nhận ra request này thuộc về ai và có role gì.
 *
 * Luồng xử lý mỗi request:
 *   Request đến → Filter này chạy → (nếu hợp lệ) → Vào Controller
 *   Request đến → Filter này chạy → (nếu không hợp lệ) → 401/403, DỪNG
 * =====================================================================
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    // JwtUtil: công cụ để giải mã JWT, kiểm tra chữ ký, lấy thông tin từ token
    private final JwtUtil jwtUtil;

    // UserDetailsService: dùng để load thông tin user từ Database theo email
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Phương thức chính — chạy tự động cho MỌI HTTP request gửi đến server.
     *
     * @param request    HTTP request từ client (FE gửi lên)
     * @param response   HTTP response sẽ trả về client
     * @param filterChain chuỗi filter tiếp theo; gọi doFilter() để chuyển request sang bước kế
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // ── BƯỚC 1: Đọc header "Authorization" từ HTTP request ──────────────
        // axiosClient.js FE tự động gắn: "Authorization: Bearer <jwt_token>"
        // Nếu không có header này → request là public (vd: /auth/login, /auth/register)
        final String authHeader = request.getHeader("Authorization");

        // Nếu không có header hoặc không bắt đầu bằng "Bearer " → bỏ qua filter này,
        // chuyển sang filter tiếp theo trong chuỗi. Spring Security sẽ tự xử lý
        // (public endpoint thì cho qua, protected endpoint thì trả 401).
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── BƯỚC 2: Tách JWT token ra khỏi chuỗi "Bearer " ──────────────────
        // "Bearer eyJhbGci..."  →  substring(7)  →  "eyJhbGci..."
        final String jwt = authHeader.substring(7);

        try {
            // ── BƯỚC 3: Giải mã JWT → lấy email người dùng ─────────────────
            // JWT được ký bằng secret key (application.yml: jwt.secret).
            // jwtUtil.extractUsername() giải mã phần "sub" (subject) trong payload JWT.
            // Nếu token bị giả mạo hoặc sai chữ ký → exception, nhảy xuống catch.
            final String userEmail = jwtUtil.extractUsername(jwt);

            // ── BƯỚC 4: Kiểm tra xem request đã được xác thực chưa ──────────
            // SecurityContextHolder lưu thông tin xác thực của request hiện tại.
            // Nếu đã có authentication (vd: filter khác đã xử lý rồi) → bỏ qua,
            // tránh xử lý trùng lặp. Đây là lý do class kế thừa OncePerRequestFilter.
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // ── BƯỚC 5: Load thông tin user từ Database ─────────────────
                // SELECT * FROM users WHERE email = ? (thực hiện mỗi request!)
                // Lấy được: email, passwordHash, role (ADMIN/STAFF/DRIVER...), isActive
                // → Nếu user bị khóa (isActive=false) thì userDetails sẽ phản ánh điều đó.
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                // ── BƯỚC 6: Kiểm tra token có hợp lệ không ──────────────────
                // jwtUtil.isTokenValid() thực hiện 2 kiểm tra:
                //   1. Email trong token có khớp với email trong DB không?
                //   2. Token có còn hạn sử dụng không? (expiration < now)
                // Nếu cả 2 đều đúng → token hợp lệ → được phép tiếp tục.
                if (jwtUtil.isTokenValid(jwt, userDetails)) {

                    // ── BƯỚC 7: Tạo đối tượng Authentication ────────────────
                    // UsernamePasswordAuthenticationToken là cách Spring Security
                    // biểu diễn "người dùng đã đăng nhập thành công".
                    // Tham số: (principal=userDetails, credentials=null, authorities=roles)
                    // credentials=null vì không cần password nữa (đã xác thực bằng JWT).
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                    // Gắn thêm thông tin request (IP, session ID...) vào authentication
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // ── BƯỚC 8: Ghi vào SecurityContext ─────────────────────
                    // Sau bước này, Spring Security "biết" request này thuộc về ai.
                    // @PreAuthorize("hasRole('ADMIN')") sẽ đọc từ SecurityContext này
                    // để quyết định có cho vào Controller hay trả 403 Forbidden.
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
                // Nếu token không hợp lệ → không set SecurityContext
                // → Spring Security sẽ trả 401 Unauthorized tự động.
            }
        } catch (Exception e) {
            // Token bị giả mạo, hết hạn, sai format → log cảnh báo, không crash server.
            // Request sẽ tiếp tục nhưng không có authentication → bị chặn bởi Spring Security.
            log.warn("JWT processing failed for request [{}]: {}", request.getRequestURI(), e.getMessage());
        }

        // ── BƯỚC 9: Chuyển sang filter/controller tiếp theo ─────────────────
        // Dù token hợp lệ hay không, đều gọi doFilter() để request tiếp tục.
        // Spring Security sẽ là người cuối cùng quyết định cho qua hay chặn lại
        // dựa trên SecurityContext đã được (hoặc chưa được) set ở bước 8.
        filterChain.doFilter(request, response);
    }
}
