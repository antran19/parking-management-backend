package com.smartparking.backend.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration; // milliseconds

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    // ─── Key ────────────────────────────────────────────────────────────────────

    /**
     * Tạo SecretKey từ chuỗi secret trong application.yml (${jwt.secret}).
     * Dùng thuật toán HMAC-SHA256 — đây là khóa bí mật để ký và xác minh token.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ─── Generate ────────────────────────────────────────────────────────────────

    /**
     * Tạo Access Token — token ngắn hạn, dùng để xác thực mỗi request.
     * Payload chứa: type="access" + email user + thời gian hết hạn.
     */
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "access");
        return buildToken(claims, userDetails.getUsername(), expiration);
    }

    /**
     * Tạo Refresh Token — token dài hạn, chỉ dùng để xin cấp Access Token mới khi hết hạn.
     * Payload chứa: type="refresh" + email user + thời gian hết hạn dài hơn.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return buildToken(claims, userDetails.getUsername(), refreshExpiration);
    }

    /**
     * Hàm nội bộ tạo JWT — thư viện jjwt tự động ghép 3 phần:
     *
     * HEADER (tự động): { "alg": "HS256", "typ": "JWT" }
     *   → Được tạo tự động khi gọi .signWith() bên dưới.
     *
     * PAYLOAD (do chúng ta định nghĩa):
     *   .claims(extraClaims) → type: "access" hoặc "refresh"
     *   .subject(subject)    → email của user (username)
     *   .issuedAt(...)       → thời điểm tạo token
     *   .expiration(...)     → thời điểm hết hạn
     *
     * SIGNATURE:
     *   .signWith(getSigningKey()) → ký bằng HMAC-SHA256 với secret key
     *
     * .compact() → ghép Header.Payload.Signature thành chuỗi JWT hoàn chỉnh.
     */
    private String buildToken(Map<String, Object> extraClaims, String subject, long expirationMs) {
        return Jwts.builder()
                .claims(extraClaims)       // PAYLOAD: custom claims (type)
                .subject(subject)           // PAYLOAD: email user
                .issuedAt(new Date())       // PAYLOAD: thời điểm tạo
                .expiration(new Date(System.currentTimeMillis() + expirationMs)) // PAYLOAD: hết hạn
                .signWith(getSigningKey())  // SIGNATURE + tự tạo HEADER alg=HS256
                .compact();                 // ghép thành chuỗi Header.Payload.Signature
    }

    // ─── Extract ─────────────────────────────────────────────────────────────────

    /** Lấy email (subject) từ payload của token. */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Lấy bất kỳ thông tin nào từ payload của token theo hàm claimsResolver truyền vào. */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Giải mã toàn bộ payload của token.
     * .verifyWith(getSigningKey()) → xác minh chữ ký SIGNATURE trước
     *   → Nếu token bị sửa hoặc sai secret → ném JwtException ngay.
     * .getPayload() → trả về phần Payload đã giải mã.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey()) // kiểm tra SIGNATURE — nếu sai → ném exception
                .build()
                .parseSignedClaims(token)
                .getPayload();              // trả về PAYLOAD đã giải mã
    }

    // ─── Validate ────────────────────────────────────────────────────────────────

    /**
     * Kiểm tra token hợp lệ — cần đủ 2 điều kiện:
     * 1. username trong token phải khớp với user đang đăng nhập
     * 2. token chưa hết hạn
     * Nếu token bị giả mạo hoặc sai secret → extractUsername ném JwtException → return false.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) // điều kiện 1: đúng user
                    && !isTokenExpired(token);                // điều kiện 2: chưa hết hạn
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false; // token giả mạo hoặc bị sửa → từ chối
        }
    }

    /** Kiểm tra token đã hết hạn chưa — so sánh expiration trong payload với thời điểm hiện tại. */
    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}
