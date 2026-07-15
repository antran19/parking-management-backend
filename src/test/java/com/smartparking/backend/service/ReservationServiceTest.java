package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.ReservationRequest;
import com.smartparking.backend.dto.response.ReservationResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Flow #2: Reservation / Pre-booking
 *
 * Test ReservationService:
 * - Tạo đặt chỗ thành công → reservedCount tăng
 * - Zone đầy → từ chối
 * - Zone không tồn tại → lỗi
 * - Hủy đặt chỗ → reservedCount giảm
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ZoneRepository zoneRepository;
    @Mock private VehicleTypeRepository vehicleTypeRepository;
    @Mock private UserLicensePlateRepository userLicensePlateRepository;
    @Mock private BlacklistService blacklistService;
    @Mock private EmergencyService emergencyService;
    @Mock private ParkingSessionRepository parkingSessionRepository;
    @Mock private UniqueCodeGeneratorService uniqueCodeGeneratorService;

    @InjectMocks
    private ReservationService reservationService;

    private User driver;
    private Zone zone;
    private VehicleType motorbike;
    private UUID zoneId;
    private UUID vehicleTypeId;

    @BeforeEach
    void setUp() {
        zoneId = UUID.randomUUID();
        vehicleTypeId = UUID.randomUUID();

        driver = User.builder()
                .id(UUID.randomUUID())
                .email("driver@test.com")
                .fullName("Test Driver")
                .role(User.Role.DRIVER)
                .build();

        motorbike = VehicleType.builder()
                .id(vehicleTypeId)
                .name("Xe máy")
                .build();

        zone = Zone.builder()
                .id(zoneId)
                .zoneName("A1")
                .capacity(10)
                .currentCount(3)
                .reservedCount(2)
                .status(Zone.ZoneStatus.ACTIVE)
                .vehicleType(motorbike)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Zone không tồn tại → ResourceNotFoundException
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow2: Đặt chỗ zone không tồn tại → Lỗi ResourceNotFound")
    void createReservation_zoneNotFound_shouldThrow() {
        ReservationRequest request = new ReservationRequest();
        request.setZoneId(UUID.randomUUID());
        request.setVehicleTypeId(vehicleTypeId);
        request.setLicensePlate("30A12345");

        when(zoneRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reservationService.createReservation(driver, request));
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Zone đầy (currentCount + reservedCount >= capacity) → BusinessException
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow2: Zone đầy chỗ → Lỗi BusinessException")
    void createReservation_zoneFull_shouldThrow() {
        zone.setCapacity(5);
        zone.setCurrentCount(3);
        zone.setReservedCount(2); // 3 + 2 = 5 >= 5

        UserLicensePlate userPlate = UserLicensePlate.builder()
                .user(driver)
                .licensePlate("30A12345")
                .vehicleType(motorbike)
                .build();

        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(zone));
        when(vehicleTypeRepository.findById(vehicleTypeId)).thenReturn(Optional.of(motorbike));
        doNothing().when(emergencyService).ensureNormalOperation();
        doNothing().when(blacklistService).ensurePlateNotBlacklisted(anyString());
        when(userLicensePlateRepository.findByUser(driver)).thenReturn(List.of(userPlate));

        ReservationRequest request = new ReservationRequest();
        request.setZoneId(zoneId);
        request.setVehicleTypeId(vehicleTypeId);
        request.setLicensePlate("30A-123.45");

        assertThrows(BusinessException.class,
                () -> reservationService.createReservation(driver, request));
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Hủy reservation → status = CANCELLED, reservedCount giảm
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow2: Hủy đặt chỗ thành công → status CANCELLED, reservedCount giảm")
    void cancelReservation_success_shouldDecreaseReservedCount() {
        UUID reservationId = UUID.randomUUID();
        zone.setReservedCount(3);

        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .user(driver)
                .zone(zone)
                .status(Reservation.ReservationStatus.PENDING)
                .licensePlate("30A12345")
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(zoneRepository.save(any(Zone.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = reservationService.cancelReservation(driver, reservationId);

        assertEquals(Reservation.ReservationStatus.CANCELLED, response.getStatus());
        assertEquals(2, zone.getReservedCount()); // 3 → 2
    }

    // ═══════════════════════════════════════════════════════════════
    // CASE: Hủy reservation của người khác → BusinessException
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Flow2: Hủy đặt chỗ của người khác → Lỗi BusinessException")
    void cancelReservation_notOwner_shouldThrow() {
        UUID reservationId = UUID.randomUUID();
        User otherUser = User.builder().id(UUID.randomUUID()).build();

        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .user(otherUser) // thuộc user khác
                .status(Reservation.ReservationStatus.PENDING)
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        assertThrows(BusinessException.class,
                () -> reservationService.cancelReservation(driver, reservationId));
    }
}
