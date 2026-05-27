package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.ChangePasswordRequest;
import com.smartparking.backend.dto.request.LoginRequest;
import com.smartparking.backend.dto.request.RegisterRequest;
import com.smartparking.backend.dto.response.LoginResponse;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.UserRepository;
import com.smartparking.backend.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService,
                       UserRepository userRepository,
                       JwtUtil jwtUtil,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Đăng ký tài khoản Driver mới.
     */
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email này đã được sử dụng trong hệ thống");
        }

        User newUser = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(User.Role.DRIVER)
                .isActive(true)
                .build();

        userRepository.save(newUser);
        log.info("Người dùng mới đăng ký: {}", request.getEmail());
    }

    /**
     * Đăng nhập — xác thực email/password, trả về JWT tokens.
     */
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new BusinessException("Email hoặc mật khẩu không đúng");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));

        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        log.info("User logged in: {} (role={})", user.getEmail(), user.getRole());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L) // 1 giờ
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole().name())
                        .build())
                .build();
    }

    /**
     * Refresh access token bằng refresh token.
     */
    public LoginResponse refreshToken(String refreshToken) {
        String email;
        String tokenType;
        
        // --- THAY ĐỔI: Bảo vệ quá trình giải mã Token, tránh quăng lỗi 500 hệ thống khi token hết hạn ---
        try {
            email = jwtUtil.extractUsername(refreshToken);
            tokenType = jwtUtil.extractClaim(refreshToken, claims -> claims.get("type", String.class));
        } catch (Exception e) {
            throw new BusinessException("Refresh token không hợp lệ hoặc đã hết hạn");
        }
        // --- THAY ĐỔI: Bắt buộc Token sử dụng tại đây phải là Refresh Token ---
        if (!"refresh".equals(tokenType)) {
            throw new BusinessException("Token này không phải là Refresh Token hợp lệ");
        }
        // --------------------------------------------------------------------
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!jwtUtil.isTokenValid(refreshToken, userDetails)) {
            throw new BusinessException("Refresh token không hợp lệ hoặc đã hết hạn");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));
        String newAccessToken = jwtUtil.generateAccessToken(userDetails);
        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole().name())
                        .build())
                .build();
    }
    /**
     * --- THAY ĐỔI: Triển khai API đổi mật khẩu cho người dùng ---
     */
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản người dùng"));
        // Kiểm tra mật khẩu cũ gửi lên có khớp với password_hash trong DB không
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu cũ không chính xác");
        }
        // Mã hóa mật khẩu mới và lưu lại
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Người dùng {} đã đổi mật khẩu thành công.", email);
    }
}
