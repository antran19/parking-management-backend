package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.CheckInRequest;
import com.smartparking.backend.dto.request.CheckOutRequest;
import com.smartparking.backend.dto.request.CheckInZoneRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.SessionResponse;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.entity.UserLicensePlate;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.UserLicensePlateRepository;
import com.smartparking.backend.repository.UserRepository;
import com.smartparking.backend.service.ParkingSessionService;
import com.smartparking.backend.util.LicensePlateUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import com.smartparking.backend.entity.Reservation;
import com.smartparking.backend.entity.ParkingPass;
import com.smartparking.backend.repository.ReservationRepository;
import com.smartparking.backend.repository.ParkingPassRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.smartparking.backend.dto.response.EligibleZoneResponse;

/**
 * SessionController — API quản lý phiên gửi xe (Parking Session).
 * Được thiết kế theo chuẩn RESTful chuyên nghiệp, phân tách rõ nhóm Staff và
 * Driver.
 *
 * Endpoints:
 * - POST /api/v1/staff/sessions/checkin → Check-in xe vào bãi (Chỉ STAFF trở
 * lên)
 * - POST /api/v1/staff/sessions/checkout → Check-out xe ra bãi (Chỉ STAFF trở
 * lên)
 * - GET /api/v1/driver/sessions/active → Xem session đang mở của tài xế (Tài xế
 * + Staff)
 * - GET /api/v1/driver/sessions/history → Lịch sử gửi xe theo biển số (Tài xế +
 * Staff)
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private final ParkingSessionService parkingSessionService;
    private final UserRepository userRepository;
    private final UserLicensePlateRepository userLicensePlateRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingPassRepository parkingPassRepository;

    public SessionController(
            ParkingSessionService parkingSessionService,
            UserRepository userRepository,
            UserLicensePlateRepository userLicensePlateRepository,
            ReservationRepository reservationRepository,
            ParkingPassRepository parkingPassRepository) {
        this.parkingSessionService = parkingSessionService;
        this.userRepository = userRepository;
        this.userLicensePlateRepository = userLicensePlateRepository;
        this.reservationRepository = reservationRepository;
        this.parkingPassRepository = parkingPassRepository;
    }

    /**
     * Lấy dữ liệu thống kê tổng quan cho Staff Dashboard.
     */
    @GetMapping("/staff/dashboard")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStaffDashboard() {
        return ResponseEntity.ok(ApiResponse.success(parkingSessionService.getDashboardStats()));
    }

    /**
     * Check-in xe vào bãi — chỉ STAFF trở lên.
     */
    @PostMapping("/staff/sessions/checkin")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> checkIn(
            @Valid @RequestBody CheckInRequest request) {
        SessionResponse response = parkingSessionService.checkIn(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Check-in thành công. " + response.getGuideMessage(), response));
    }

    /**
     * Check-in xe vào Zone (Check-in lần 2) — chỉ STAFF trở lên.
     */
    @PostMapping("/staff/sessions/zone-entry")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> checkInZone(
            @Valid @RequestBody CheckInZoneRequest request) {
        SessionResponse response = parkingSessionService.checkInZone(request);
        return ResponseEntity.ok(ApiResponse.success(
                response.getGuideMessage(), response));
    }

    /**
     * Lấy danh sách phân khu khả dụng để thay đổi gợi ý đỗ xe.
     */
    @GetMapping("/staff/sessions/{sessionId}/eligible-zones")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<EligibleZoneResponse>>> getEligibleZones(
            @PathVariable UUID sessionId) {
        List<EligibleZoneResponse> response = parkingSessionService.getEligibleZones(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phân khu thành công", response));
    }

    /**
     * Thay đổi phân khu đỗ xe chỉ định cho session.
     */
    @PutMapping("/staff/sessions/{sessionId}/change-zone")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> changeSessionZone(
            @PathVariable UUID sessionId,
            @RequestParam UUID zoneId) {
        SessionResponse response = parkingSessionService.changeSessionZone(sessionId, zoneId);
        return ResponseEntity.ok(ApiResponse.success("Thay đổi phân khu đỗ xe thành công", response));
    }

    /**
     * Check-out xe ra bãi — chỉ STAFF trở lên.
     */
    @PostMapping("/staff/sessions/checkout")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> checkOut(
            @Valid @RequestBody CheckOutRequest request) {
        SessionResponse response = parkingSessionService.checkOut(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Check-out thành công. Tổng phí: " + response.getTotalFee() + "đ", response));
    }

    /**
     * /**
     * Cập nhật URL ảnh lên phiên đỗ xe (sau khi Check-in/Check-out thành công).
     */
    @PutMapping("/staff/sessions/{sessionId}/images")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateSessionImages(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, Object> payload) {
        String plateUrl = (String) payload.get("plateUrl");
        String faceUrl = (String) payload.get("faceUrl");
        Boolean isEntry = (Boolean) payload.get("isEntry");
        parkingSessionService.updateSessionImages(sessionId, plateUrl, faceUrl, Boolean.TRUE.equals(isEntry));
        return ResponseEntity.ok(ApiResponse.success("Cập nhật ảnh thành công", null));
    }

    // /**
    // * Lấy toàn bộ danh sách phiên gửi xe cho Staff/Manager/Admin xem (hỗ trợ phân
    // * trang và tìm kiếm).
    // */
    // * Lấy toàn bộ danh sách phiên gửi xe cho Staff/Manager/Admin xem (hỗ trợ phân
    // * trang và tìm kiếm).
    // */
    @GetMapping("/staff/sessions/history")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<?>> getSessionsHistory(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "licensePlate", required = false) String licensePlate,
            @RequestParam(value = "status", required = false) String status) {

        com.smartparking.backend.entity.ParkingSession.SessionStatus sessionStatus = null;
        if (status != null && !status.trim().isEmpty() && !"all".equalsIgnoreCase(status)) {
            try {
                if ("parked".equalsIgnoreCase(status)) {
                    sessionStatus = com.smartparking.backend.entity.ParkingSession.SessionStatus.ACTIVE;
                } else if ("checked_out".equalsIgnoreCase(status)) {
                    sessionStatus = com.smartparking.backend.entity.ParkingSession.SessionStatus.COMPLETED;
                } else {
                    sessionStatus = com.smartparking.backend.entity.ParkingSession.SessionStatus
                            .valueOf(status.toUpperCase());
                }
            } catch (IllegalArgumentException e) {
                // Ignore invalid status enum string
            }
        }

        if (page == null || size == null) {
            java.util.List<SessionResponse> history = parkingSessionService.getAllSessions();
            return ResponseEntity.ok(ApiResponse.success(history));
        }

        Page<SessionResponse> history = parkingSessionService.searchSessions(licensePlate, sessionStatus, page, size);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    /**
     * Driver khởi tạo thanh toán VNPay sandbox để check-out phiên gửi xe đang hoạt
     * động.
     */
    @PostMapping("/driver/sessions/checkout/vnpay")
    @PreAuthorize("hasAnyRole('DRIVER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateVnPayCheckout(
            Authentication authentication,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String readablePlate = ensureDriverCanReadPlate(authentication, body.get("licensePlate"));
        body.put("licensePlate", readablePlate);
        Map<String, Object> response = parkingSessionService.initiateDriverVnPayCheckout(body, request);
        return ResponseEntity.ok(ApiResponse.success("Đã tạo liên kết thanh toán VNPay cho phiên gửi xe", response));
    }

    /**
     * Xem session đang hoạt động của Driver — Mọi authenticated user (Driver/Staff)
     * có quyền truy cập.
     */
    @GetMapping("/driver/sessions/active")
    @PreAuthorize("hasAnyRole('DRIVER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> getActiveSession(
            Authentication authentication,
            @RequestParam("plate") String licensePlate) {
        String readablePlate = ensureDriverCanReadPlate(authentication, licensePlate);
        SessionResponse response = parkingSessionService.getActiveSession(readablePlate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Lịch sử gửi xe theo biển số — Mọi authenticated user (Driver/Staff) có quyền
     * truy cập.
     */
    @GetMapping("/driver/sessions/history")
    @PreAuthorize("hasAnyRole('DRIVER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<SessionResponse>>> getSessionHistory(
            Authentication authentication,
            @RequestParam("plate") String licensePlate) {
        String readablePlate = ensureDriverCanReadPlate(authentication, licensePlate);
        java.util.List<SessionResponse> history = parkingSessionService.getSessionHistory(readablePlate);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    /**
     * Driver chỉ được xem active session/history của biển số thuộc tài khoản mình.
     * Staff/Manager/Admin vẫn được tra cứu toàn bộ để phục vụ nghiệp vụ soát vé.
     */
    private String ensureDriverCanReadPlate(Authentication authentication, String rawPlate) {
        String normalizedPlate = LicensePlateUtil.normalize(rawPlate);
        if (normalizedPlate.isBlank()) {
            throw new BusinessException("Biển số hoặc mã xe không được để trống");
        }

        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException("Bạn cần đăng nhập để xem phiên gửi xe");
        }

        boolean isDriver = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_DRIVER".equals(authority.getAuthority()));

        boolean hasElevatedRole = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_STAFF".equals(authority.getAuthority())
                        || "ROLE_MANAGER".equals(authority.getAuthority())
                        || "ROLE_ADMIN".equals(authority.getAuthority()));

        if (isDriver && !hasElevatedRole) {
            User currentUser = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản driver"));

            boolean plateBelongsToUser = userLicensePlateRepository.findByUser(currentUser)
                    .stream()
                    .map(UserLicensePlate::getLicensePlate)
                    .map(LicensePlateUtil::normalize)
                    .anyMatch(normalizedPlate::equals);

            boolean identifierBelongsToReservation = reservationRepository.findByUserOrderByCreatedAtDesc(currentUser)
                    .stream()
                    .map(Reservation::getLicensePlate)
                    .map(LicensePlateUtil::normalize)
                    .anyMatch(normalizedPlate::equals);

            boolean identifierBelongsToPass = parkingPassRepository.findByUser(currentUser)
                    .stream()
                    .map(ParkingPass::getLicensePlate)
                    .map(LicensePlateUtil::normalize)
                    .anyMatch(normalizedPlate::equals);

            if (!plateBelongsToUser && !identifierBelongsToReservation && !identifierBelongsToPass) {
                throw new BusinessException("Bạn không có quyền xem phiên gửi xe của mã xe này");
            }
        }

        return normalizedPlate;
    }

}
