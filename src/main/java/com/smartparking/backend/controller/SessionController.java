package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.CheckInRequest;
import com.smartparking.backend.dto.request.CheckOutRequest;
import com.smartparking.backend.dto.request.CheckInZoneRequest;
import com.smartparking.backend.dto.request.CheckOutZoneRequest;
import com.smartparking.backend.dto.request.UpdateImagesRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.SessionResponse;
import com.smartparking.backend.dto.response.ReservationResponse;
import com.smartparking.backend.service.ReservationService;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Parking Session", description = "APIs for Staff check-in/check-out and Driver session management")
public class SessionController {

    private final ParkingSessionService parkingSessionService;
    private final UserRepository userRepository;
    private final UserLicensePlateRepository userLicensePlateRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingPassRepository parkingPassRepository;
    private final ReservationService reservationService;

    public SessionController(
            ParkingSessionService parkingSessionService,
            UserRepository userRepository,
            UserLicensePlateRepository userLicensePlateRepository,
            ReservationRepository reservationRepository,
            ParkingPassRepository parkingPassRepository,
            ReservationService reservationService) {
        this.parkingSessionService = parkingSessionService;
        this.userRepository = userRepository;
        this.userLicensePlateRepository = userLicensePlateRepository;
        this.reservationRepository = reservationRepository;
        this.parkingPassRepository = parkingPassRepository;
        this.reservationService = reservationService;
    }

    /**
     * Lấy danh sách đặt chỗ của bãi xe dành cho Staff/Manager/Admin xem.
     */
    @Operation(summary = "Lấy danh sách đặt chỗ", description = "Staff/Manager/Admin xem reservation theo zone hoặc status")
    @GetMapping("/staff/reservations")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getAllReservations(
            @RequestParam(value = "zoneId", required = false) UUID zoneId,
            @RequestParam(value = "status", required = false) String status) {
        List<ReservationResponse> responses = reservationService.getAllReservations(zoneId, status);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * Lấy dữ liệu thống kê tổng quan cho Staff Dashboard.
     */
    @Operation(summary = "Thống kê tổng quan Staff", description = "Dữ liệu dashboard: số xe, doanh thu, công suất")
    @GetMapping("/staff/dashboard")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStaffDashboard() {
        return ResponseEntity.ok(ApiResponse.success(parkingSessionService.getDashboardStats()));
    }

    /**
     * Check-in xe vào bãi — chỉ STAFF trở lên.
     */
    @Operation(summary = "Check-in xe vào bãi", description = "Tạo phiên gửi xe mới — chỉ STAFF trở lên")
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
    @Operation(summary = "Check-in xe vào Zone", description = "Xác nhận xe đã vào zone được chỉ định")
    @PostMapping("/staff/sessions/zone-entry")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> checkInZone(
            @Valid @RequestBody CheckInZoneRequest request) {
        SessionResponse response = parkingSessionService.checkInZone(request);
        return ResponseEntity.ok(ApiResponse.success(
                response.getGuideMessage(), response));
    }

    /**
     * Check-out xe ra khỏi Zone (Check-out lần 2) — chỉ STAFF trở lên.
     */
    @Operation(summary = "Check-out xe ra khỏi Zone", description = "Xác nhận xe đã rời zone")
    @PostMapping("/staff/sessions/zone-exit")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> checkOutZone(
            @Valid @RequestBody CheckOutZoneRequest request) {
        SessionResponse response = parkingSessionService.checkOutZone(request);
        return ResponseEntity.ok(ApiResponse.success(
                response.getGuideMessage(), response));
    }

    /**
     * Lấy danh sách phân khu khả dụng để thay đổi gợi ý đỗ xe.
     */
    @Operation(summary = "Lấy danh sách zone khả dụng", description = "Trả về các zone còn chỗ phù hợp với loại xe")
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
    @Operation(summary = "Thay đổi zone đỗ xe", description = "Chuyển session sang zone khác")
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
    @Operation(summary = "Check-out xe ra bãi", description = "Kết thúc phiên gửi xe và tính phí")
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
    @Operation(summary = "Cập nhật ảnh phiên gửi xe", description = "Upload URL ảnh biển số/khuôn mặt từ Cloudinary")
    @PutMapping("/staff/sessions/{sessionId}/images")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateSessionImages(
            @PathVariable UUID sessionId,
            @Valid @RequestBody UpdateImagesRequest request) {
        parkingSessionService.updateSessionImages(
                sessionId,
                request.getPlateUrl(),
                request.getFaceUrl(),
                Boolean.TRUE.equals(request.getIsEntry()));
        return ResponseEntity.ok(ApiResponse.success("Cập nhật ảnh thành công", null));
    }

    // /**
    // * Lấy toàn bộ danh sách phiên gửi xe cho Staff/Manager/Admin xem (hỗ trợ phân
    // * trang và tìm kiếm).
    // */
    // * Lấy toàn bộ danh sách phiên gửi xe cho Staff/Manager/Admin xem (hỗ trợ phân
    // * trang và tìm kiếm).
    // */
    @Operation(summary = "Lịch sử phiên gửi xe (Staff)", description = "Hỗ trợ phân trang và lọc theo biển số/status")
    @GetMapping("/staff/sessions/history")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN', 'SECURITY')")
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
    @Operation(summary = "Thanh toán VNPay check-out", description = "Driver khởi tạo thanh toán VNPay sandbox cho phiên gửi xe")
    @PostMapping("/driver/sessions/checkout/vnpay")
    @PreAuthorize("hasAnyRole('DRIVER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateVnPayCheckout(
            Authentication authentication,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String readablePlate = ensureDriverCanReadPlate(authentication, body.get("licensePlate"), null);
        body.put("licensePlate", readablePlate);
        Map<String, Object> response = parkingSessionService.initiateDriverVnPayCheckout(body, request);
        return ResponseEntity.ok(ApiResponse.success("Đã tạo liên kết thanh toán VNPay cho phiên gửi xe", response));
    }

    /**
     * Xem session đang hoạt động của Driver — Mọi authenticated user (Driver/Staff)
     * có quyền truy cập.
     */
    @Operation(summary = "Xem phiên gửi xe đang hoạt động (Driver)", description = "Driver xem session đang mở của mình theo biển số")
    @GetMapping("/driver/sessions/active")
    @PreAuthorize("hasAnyRole('DRIVER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> getActiveSession(
            Authentication authentication,
            @RequestParam(value = "plate", required = false) String licensePlate,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "vehicleTypeId", required = false) UUID vehicleTypeId) {
        String readablePlate = ensureDriverCanReadPlate(authentication, licensePlate, code);
        SessionResponse response = parkingSessionService.getActiveSession(readablePlate, code, vehicleTypeId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Xem session đang hoạt động dành cho Staff tra cứu (không bị giới hạn biển số xe như Driver)
     */
    @Operation(summary = "Xem phiên gửi xe đang hoạt động (Staff)", description = "Staff tra cứu session theo biển số bất kỳ")
    @GetMapping("/staff/sessions/active")
    @PreAuthorize("hasAnyRole('STAFF', 'SECURITY', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> getStaffActiveSession(
            @RequestParam("plate") String licensePlate) {
        String normalizedPlate = LicensePlateUtil.normalize(licensePlate);
        if (normalizedPlate.isBlank()) {
            throw new BusinessException("Biển số không được để trống");
        }
        SessionResponse response = parkingSessionService.getActiveSession(normalizedPlate, null, null);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Lịch sử gửi xe theo biển số — Mọi authenticated user (Driver/Staff) có quyền
     * truy cập.
     */
    @Operation(summary = "Lịch sử gửi xe theo biển số (Driver)", description = "Driver xem lịch sử các phiên gửi xe của mình")
    @GetMapping("/driver/sessions/history")
    @PreAuthorize("hasAnyRole('DRIVER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<SessionResponse>>> getSessionHistory(
            Authentication authentication,
            @RequestParam("plate") String licensePlate) {
        String readablePlate = ensureDriverCanReadPlate(authentication, licensePlate, null);
        java.util.List<SessionResponse> history = parkingSessionService.getSessionHistory(readablePlate);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    /**
     * Driver chỉ được xem active session/history của biển số thuộc tài khoản mình.
     * Staff/Manager/Admin vẫn được tra cứu toàn bộ để phục vụ nghiệp vụ soát vé.
     */
    private String ensureDriverCanReadPlate(Authentication authentication, String rawPlate, String code) {
        String normalizedPlate = rawPlate != null ? LicensePlateUtil.normalize(rawPlate) : "";

        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException("Bạn cần đăng nhập để xem phiên gửi xe");
        }

        boolean hasElevatedRole = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_STAFF".equals(authority.getAuthority())
                        || "ROLE_MANAGER".equals(authority.getAuthority())
                        || "ROLE_ADMIN".equals(authority.getAuthority()));

        if (hasElevatedRole) {
            return normalizedPlate;
        }

        boolean isDriver = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_DRIVER".equals(authority.getAuthority()));

        if (isDriver) {
            String plateToCheck = normalizedPlate;
            if (plateToCheck.isBlank() && code != null && !code.isBlank()) {
                plateToCheck = parkingSessionService.findPlateByCodeForDriver(code);
            }

            if (plateToCheck.isBlank()) {
                throw new BusinessException("Biển số hoặc mã xe không được để trống");
            }

            User currentUser = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản driver"));

            final String finalPlateToCheck = plateToCheck;
            boolean plateBelongsToUser = userLicensePlateRepository.findByUser(currentUser)
                    .stream()
                    .map(UserLicensePlate::getLicensePlate)
                    .map(LicensePlateUtil::normalize)
                    .anyMatch(finalPlateToCheck::equals);

            boolean identifierBelongsToReservation = reservationRepository.findByUserOrderByCreatedAtDesc(currentUser)
                    .stream()
                    .map(Reservation::getLicensePlate)
                    .map(LicensePlateUtil::normalize)
                    .anyMatch(finalPlateToCheck::equals);

            boolean identifierBelongsToPass = parkingPassRepository.findByUser(currentUser)
                    .stream()
                    .map(ParkingPass::getLicensePlate)
                    .map(LicensePlateUtil::normalize)
                    .anyMatch(finalPlateToCheck::equals);

            if (!plateBelongsToUser && !identifierBelongsToReservation && !identifierBelongsToPass) {
                throw new BusinessException("Bạn không có quyền xem phiên gửi xe của mã xe này");
            }
            return finalPlateToCheck;
        }

        return normalizedPlate;
    }

}
