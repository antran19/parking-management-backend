package com.smartparking.backend.service;

import com.smartparking.backend.entity.EmergencyEvent;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Flow #4 + Flow #1: Emergency / SOS
 *
 * Test EmergencyService:
 * - Hệ thống bình thường → ensureNormalOperation() không throw
 * - SOS đang kích hoạt → ensureNormalOperation() throw BusinessException
 * - isEmergencyActive() trả đúng trạng thái
 * - getCurrentStatus() khi không có SOS → active = false
 */
@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {

    @Mock private EmergencyEventRepository emergencyEventRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private UserRepository userRepository;
    @Mock private GateRepository gateRepository;
    @Mock private SystemSettingsRepository systemSettingsRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private EmergencyService emergencyService;

    // ═══════════════════════════════════════════════════════════════
    // CASE: Hệ thống bình thường → ensureNormalOperation() OK
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow1+4: Hệ thống bình thường → ensureNormalOperation() không lỗi")
    void ensureNormalOperation_noActiveSOS_shouldNotThrow() {
        when(emergencyEventRepository.findFirstByStatusOrderByActivatedAtDesc(
                EmergencyEvent.EmergencyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> emergencyService.ensureNormalOperation());
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: SOS đang kích hoạt → chặn mọi thao tác
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow1+4: SOS đang kích hoạt → ensureNormalOperation() throw lỗi")
    void ensureNormalOperation_activeSOS_shouldThrow() {
        EmergencyEvent activeEvent = EmergencyEvent.builder()
                .status(EmergencyEvent.EmergencyStatus.ACTIVE)
                .reason("FIRE")
                .build();

        when(emergencyEventRepository.findFirstByStatusOrderByActivatedAtDesc(
                EmergencyEvent.EmergencyStatus.ACTIVE))
                .thenReturn(Optional.of(activeEvent));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> emergencyService.ensureNormalOperation());

        assertTrue(exception.getMessage().contains("KHẨN CẤP"));
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: isEmergencyActive() khi không có SOS → false
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow4: Không có SOS → isEmergencyActive() = false")
    void isEmergencyActive_noSOS_shouldReturnFalse() {
        when(emergencyEventRepository.findFirstByStatusOrderByActivatedAtDesc(
                EmergencyEvent.EmergencyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertFalse(emergencyService.isEmergencyActive());
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: isEmergencyActive() khi có SOS → true
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow4: SOS đang hoạt động → isEmergencyActive() = true")
    void isEmergencyActive_activeSOS_shouldReturnTrue() {
        EmergencyEvent activeEvent = EmergencyEvent.builder()
                .status(EmergencyEvent.EmergencyStatus.ACTIVE)
                .build();

        when(emergencyEventRepository.findFirstByStatusOrderByActivatedAtDesc(
                EmergencyEvent.EmergencyStatus.ACTIVE))
                .thenReturn(Optional.of(activeEvent));

        assertTrue(emergencyService.isEmergencyActive());
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: getCurrentStatus() khi không có SOS → active = false
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow4: Không có SOS → getCurrentStatus() active = false")
    void getCurrentStatus_noSOS_shouldReturnInactive() {
        when(emergencyEventRepository.findFirstByStatusOrderByActivatedAtDesc(
                EmergencyEvent.EmergencyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        var status = emergencyService.getCurrentStatus();

        assertFalse(status.isActive());
        assertTrue(status.getMessage().contains("bình thường"));
    }
}
