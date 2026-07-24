package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.SecurityExceptionRequest;
import com.smartparking.backend.dto.response.ExceptionLogResponse;
import com.smartparking.backend.entity.ExceptionLog;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.entity.ParkingPass;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.ExceptionLogRepository;
import com.smartparking.backend.repository.UserRepository;
import com.smartparking.backend.repository.ParkingSessionRepository;
import com.smartparking.backend.repository.ParkingPassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Flow #4: Security Monitoring
 *
 * Test SecurityExceptionService:
 * - Ghi nhận sự cố mới → status PENDING
 * - Giải quyết sự cố → status RESOLVED, có resolvedAt
 * - Giải quyết sự cố không tồn tại → lỗi
 * - Lấy danh sách sự cố
 */
@ExtendWith(MockitoExtension.class)
class SecurityExceptionServiceTest {

    @Mock
    private ExceptionLogRepository exceptionLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    @Mock
    private ParkingPassRepository parkingPassRepository;

    @InjectMocks
    private SecurityExceptionService securityExceptionService;

    private User securityStaff;

    @BeforeEach
    void setUp() {
        securityStaff = User.builder()
                .id(UUID.randomUUID())
                .email("security@parking.vn")
                .fullName("Nguyen Van Bao Ve")
                .role(User.Role.SECURITY)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Ghi nhận sự cố mới → status = PENDING
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow4: Báo cáo sự cố mới → Trạng thái PENDING")
    void logException_shouldCreateWithPendingStatus() {
        SecurityExceptionRequest request = new SecurityExceptionRequest();
        request.setExceptionType(ExceptionLog.ExceptionType.LOST_TICKET);
        request.setDescription("Xe không vé đang đỗ tại A3");
        request.setLicensePlate("51B12345");

        ExceptionLog savedLog = ExceptionLog.builder()
                .id(UUID.randomUUID())
                .exceptionType(ExceptionLog.ExceptionType.LOST_TICKET)
                .description("Xe không vé đang đỗ tại A3")
                .licensePlate("51B12345")
                .status(ExceptionLog.ExceptionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(exceptionLogRepository.save(any(ExceptionLog.class))).thenReturn(savedLog);

        ExceptionLogResponse response = securityExceptionService.logException(request);

        assertNotNull(response);
        assertEquals("PENDING", response.getStatus());
        assertEquals("51B12345", response.getLicensePlate());
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Giải quyết sự cố → status = RESOLVED, resolvedAt != null
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow4: Giải quyết sự cố → status RESOLVED, resolvedAt có giá trị")
    void resolveException_shouldMarkResolved() {
        UUID exceptionId = UUID.randomUUID();

        ExceptionLog existing = ExceptionLog.builder()
                .id(exceptionId)
                .exceptionType(ExceptionLog.ExceptionType.LOST_TICKET)
                .description("Xe không vé")
                .status(ExceptionLog.ExceptionStatus.PENDING)
                .build();

        when(exceptionLogRepository.findById(exceptionId)).thenReturn(Optional.of(existing));
        when(userRepository.findById(securityStaff.getId())).thenReturn(Optional.of(securityStaff));
        when(exceptionLogRepository.save(any(ExceptionLog.class))).thenAnswer(inv -> inv.getArgument(0));

        ExceptionLogResponse response = securityExceptionService.resolveException(
                exceptionId, securityStaff.getId(), "Đã xử lý, xe đã rời bãi", null);

        assertEquals("RESOLVED", response.getStatus());
        assertNotNull(response.getResolvedAt());
        assertEquals("Nguyen Van Bao Ve", response.getHandledBy());
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Giải quyết sự cố không tồn tại → BusinessException
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow4: Giải quyết sự cố không tồn tại → Lỗi BusinessException")
    void resolveException_notFound_shouldThrow() {
        UUID fakeId = UUID.randomUUID();
        when(exceptionLogRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> securityExceptionService.resolveException(
                        fakeId, securityStaff.getId(), null, null));
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Lấy danh sách sự cố → Trả về đúng số lượng
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow4: Lấy danh sách sự cố → Trả về đúng 2 sự cố")
    void getAllExceptions_shouldReturnAll() {
        ExceptionLog log1 = ExceptionLog.builder()
                .id(UUID.randomUUID())
                .status(ExceptionLog.ExceptionStatus.PENDING)
                .build();

        ExceptionLog log2 = ExceptionLog.builder()
                .id(UUID.randomUUID())
                .status(ExceptionLog.ExceptionStatus.RESOLVED)
                .resolvedAt(LocalDateTime.now())
                .build();

        when(exceptionLogRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(log1, log2));

        List<ExceptionLogResponse> result = securityExceptionService.getAllExceptions();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Flow4: Kiểm tra biển số xe báo sự cố — có session hoạt động")
    void checkPlateForException_hasActiveSession() {
        String plate = "51B12345";
        ParkingSession mockSession = new ParkingSession();
        mockSession.setLicensePlate(plate);
        mockSession.setStatus(ParkingSession.SessionStatus.ACTIVE);

        when(parkingSessionRepository.findByLicensePlateAndStatus("51B12345", ParkingSession.SessionStatus.ACTIVE))
                .thenReturn(Optional.of(mockSession));

        Map<String, Object> result = securityExceptionService.checkPlateForException(plate);

        assertEquals("51B12345", result.get("licensePlate"));
        assertTrue((Boolean) result.get("hasActiveSession"));
        assertFalse((Boolean) result.get("hasActivePass"));
    }

    @Test
    @DisplayName("Flow4: Kiểm tra biển số xe báo sự cố — có gói cước còn hạn")
    void checkPlateForException_hasActivePass() {
        String plate = "51B12345";

        when(parkingSessionRepository.findByLicensePlateAndStatus("51B12345", ParkingSession.SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ParkingPass mockPass = ParkingPass.builder()
                .licensePlate(plate)
                .status(ParkingPass.PassStatus.ACTIVE)
                .startDate(java.time.LocalDate.now().minusDays(1))
                .endDate(java.time.LocalDate.now().plusDays(10))
                .build();

        when(parkingPassRepository.findAll())
                .thenReturn(List.of(mockPass));

        Map<String, Object> result = securityExceptionService.checkPlateForException(plate);

        assertEquals("51B12345", result.get("licensePlate"));
        assertFalse((Boolean) result.get("hasActiveSession"));
        assertTrue((Boolean) result.get("hasActivePass"));
    }
}
