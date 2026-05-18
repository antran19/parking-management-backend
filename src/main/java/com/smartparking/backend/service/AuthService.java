package com.smartparking.backend.service;

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
    // Inject PasswordEncoder để mã hóa mật khẩu trước khi lưu vào DB
    private final PasswordEncoder passwordEncoder; // Thêm PasswordEncoder

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
     * Logic đăng ký tài khoản mới cho Driver.
     * Sử dụng @Transactional để đảm bảo nếu lưu DB lỗi thì toàn bộ quá trình sẽ bị hủy bỏ (Rollback).
     */
    @Transactional
    public void register(RegisterRequest request) {
        // Bước 1: Kiểm tra xem Email đã có người sử dụng chưa
        if (userRepository.existsByEmail(request.getEmail())) {
            // Ném BusinessException để GlobalExceptionHandler tự bắt và trả về lỗi 400 cho FE
            throw new BusinessException("Email này đã được sử dụng trong hệ thống");
        }

        // Bước 2: Khởi tạo đối tượng User từ Request
        // Mật khẩu được băm (hash) bằng BCrypt để đảm bảo an toàn dữ liệu
        User newUser = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(User.Role.DRIVER) // Phân quyền mặc định là DRIVER (Tài xế)
                .isActive(true)        // Kích hoạt tài khoản ngay sau khi đăng ký
                .build();

        // Bước 3: Lưu xuống PostgreSQL
        userRepository.save(newUser);

        // Log lại để Staff/Manager có thể theo dõi qua log server nếu cần
        log.info("Hệ thống: Người dùng mới đăng ký thành công - Email: {}", request.getEmail());
    }

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
        String email = jwtUtil.extractUsername(refreshToken);
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
}
