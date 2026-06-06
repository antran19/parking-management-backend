package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.ZoneInfoResponse;
import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.entity.ParkingSession.SessionStatus;
import com.smartparking.backend.repository.ParkingSessionRepository;
import com.smartparking.backend.repository.ZoneRepository;
import com.smartparking.backend.service.RedisZoneCounterService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ManagerReportController — cung cấp các endpoint dành cho Manager/Quản lý:
 * - /dashboard : tổng quan (số xe trong bãi, lượt vào/ra hôm nay, doanh thu, zone gần đầy)
 * - /reports/revenue : doanh thu theo khoảng thời gian
 *
 * Chú ý:
 *  - Dashboard sử dụng redisZoneCounterService.getCount để xác định các zone gần đầy (gần thời gian thực).
 *  - Các phép tính doanh thu/ lượt vào/ra lấy từ DB (parkingSessionRepository).
 */
@RestController
@RequestMapping("/api/v1/manager")
public class ManagerReportController {

    private final ParkingSessionRepository parkingSessionRepository;
    private final ZoneRepository zoneRepository;
    private final RedisZoneCounterService redisZoneCounterService;

    public ManagerReportController(ParkingSessionRepository parkingSessionRepository,
                                   ZoneRepository zoneRepository,
                                   RedisZoneCounterService redisZoneCounterService) {
        this.parkingSessionRepository = parkingSessionRepository;
        this.zoneRepository = zoneRepository;
        this.redisZoneCounterService = redisZoneCounterService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() {
        // Thời gian: hôm nay
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        // Lấy số liệu từ DB
        long totalInLot = parkingSessionRepository.countByStatus(SessionStatus.ACTIVE);
        long entriesToday = parkingSessionRepository.countByEntryTimeBetween(start, end);
        long exitsToday = parkingSessionRepository.countByExitTimeBetween(start, end);
        BigDecimal revenueToday = parkingSessionRepository.sumTotalFeeBetween(start, end);
        if (revenueToday == null) revenueToday = BigDecimal.ZERO;

        // Tìm zone gần đầy bằng cách đọc currentCount từ Redis (tức thời thực)
        List<ZoneInfoResponse> nearFull = zoneRepository.findAll().stream()
                .filter(z -> {
                    int cur = redisZoneCounterService.getCount(z.getId());
                    int remaining = z.getCapacity() - cur - z.getReservedCount();
                    // near full: còn <= max(1, 10% capacity)
                    return remaining <= Math.max(1, z.getCapacity() / 10);
                })
                .map(z -> ZoneInfoResponse.builder()
                        .id(z.getId())
                        .zoneCode(z.getZoneCode())
                        .zoneName(z.getZoneName())
                        .floorName(z.getFloor().getFloorName())
                        .capacity(z.getCapacity())
                        .currentCount(redisZoneCounterService.getCount(z.getId()))
                        .status(z.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        Map<String, Object> out = Map.of(
                "totalInLot", totalInLot,
                "entriesToday", entriesToday,
                "exitsToday", exitsToday,
                "revenueToday", revenueToday,
                "nearFullZones", nearFull
        );
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/reports/revenue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revenue(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        // Trả về tổng doanh thu trong khoảng [from, to]
        BigDecimal revenue = parkingSessionRepository.sumTotalFeeBetween(from, to);
        if (revenue == null) revenue = BigDecimal.ZERO;
        Map<String, Object> out = Map.of(
                "from", from,
                "to", to,
                "revenue", revenue
        );
        return ResponseEntity.ok(ApiResponse.success(out));
    }
}
