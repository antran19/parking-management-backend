package com.smartparking.backend.service;

import com.smartparking.backend.dto.response.*;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.entity.ExceptionLog.ExceptionType;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class ManagerService {

    private final PaymentRepository paymentRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ZoneRepository zoneRepository;
    private final ExceptionLogRepository exceptionLogRepository;
    private final BuildingRepository buildingRepository;  // thêm
    private final FloorRepository floorRepository;        // thêm
    private final ParkingSessionService parkingSessionService;

    // Cập nhật Constructor để Spring tự động tiêm (inject) các Repository vào
    public ManagerService(PaymentRepository paymentRepository,
                          ParkingSessionRepository parkingSessionRepository,
                          ZoneRepository zoneRepository,
                          ExceptionLogRepository exceptionLogRepository,
                          BuildingRepository buildingRepository,
                          FloorRepository floorRepository,
                          ParkingSessionService parkingSessionService) {
        this.paymentRepository = paymentRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.zoneRepository = zoneRepository;
        this.exceptionLogRepository = exceptionLogRepository;
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.parkingSessionService = parkingSessionService;
    }


    /*
    ===========================================================================================================
                                            DOANH THU
    ===========================================================================================================
    */
    /**
     * Lấy tổng doanh thu trong khoảng từ..đến (nếu null -> hôm nay)
     */

    public RevenueResponse getRevenueBetween(
            String type,
            LocalDate from,
            LocalDate to) {

        LocalDateTime start;
        LocalDateTime end;

        if ("today".equalsIgnoreCase(type)) {

            start = LocalDate.now().atStartOfDay();
            end = LocalDate.now().atTime(LocalTime.MAX);

        } else if ("month".equalsIgnoreCase(type)) {

            LocalDate firstDay = LocalDate.now().withDayOfMonth(1);

            start = firstDay.atStartOfDay();
            end = LocalDate.now().atTime(LocalTime.MAX);

        } else if ("year".equalsIgnoreCase(type)) {

            LocalDate firstDay = LocalDate.now().withDayOfYear(1);

            start = firstDay.atStartOfDay();
            end = LocalDate.now().atTime(LocalTime.MAX);

        } else {

            if (from == null || to == null) {
                throw new BusinessException("from and to are required");
            }

            start = from.atStartOfDay();
            end = to.atTime(LocalTime.MAX);
        }

        BigDecimal revenue =
                paymentRepository.sumAmountByStatusAndPaidAtBetween(
                        Payment.PaymentStatus.COMPLETED,
                        start,
                        end
                );

        long totalSessions =
                parkingSessionRepository.countByEntryTimeBetween(
                        start,
                        end
                );

        return RevenueResponse.builder()
                .from(start)
                .to(end)
                .totalRevenue(revenue == null ? BigDecimal.ZERO : revenue)
                .totalSessions(totalSessions)
                .currency("VND")
                .build();
    }



    /*
    ========================================================================================================
                                             LƯỢT GỬI XE
    ========================================================================================================
    */


    public RevenueResponse getVisits(String type, LocalDate from, LocalDate to) {

        LocalDateTime start;
        LocalDateTime end;

        if ("today".equalsIgnoreCase(type)) {
            start = LocalDate.now().atStartOfDay();
            end = LocalDate.now().atTime(LocalTime.MAX);

        } else if ("month".equalsIgnoreCase(type)) {
            start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            end = LocalDate.now().atTime(LocalTime.MAX);

        } else if ("year".equalsIgnoreCase(type)) {
            start = LocalDate.now().withDayOfYear(1).atStartOfDay();
            end = LocalDate.now().atTime(LocalTime.MAX);

        } else {
            if (from == null || to == null)
                throw new BusinessException("from and to are required");
            start = from.atStartOfDay();
            end = to.atTime(LocalTime.MAX);
        }

        long totalSessions = parkingSessionRepository.countByEntryTimeBetween(start, end);

        return RevenueResponse.builder()
                .from(start)
                .to(end)
                .totalSessions(totalSessions)
                .totalRevenue(BigDecimal.ZERO) // không dùng, set 0
                .currency("VND")
                .build();
    }

    /*
    ==============================================================================================================
                                                      CÔNG SUẤT
    ==============================================================================================================
    */


    // ManagerServiceImpl.java


    public OccupancyEntry getBuildingOccupancy(UUID id) {

        Building building = buildingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Building not found: " + id));

        List<Zone> zones = zoneRepository.findAllByBuildingId(id);

        int totalCapacity = zones.stream()
                .mapToInt(Zone::getCapacity)
                .sum();

        int totalOccupied = zones.stream()
                .mapToInt(z -> z.getCurrentCount() + z.getReservedCount())
                .sum();

        int available = Math.max(0, totalCapacity - totalOccupied);

        double percent = totalCapacity > 0
                ? Math.round((totalOccupied * 100.0 / totalCapacity) * 10.0) / 10.0
                : 0.0;

        return OccupancyEntry.builder()
                .id(id.toString())
                .name(building.getName())
                .level("building")
                .defaultCapacity(totalCapacity)
                .capacity(totalCapacity)
                .currentOccupancy(totalOccupied)
                .availableSlots(available)
                .percent(percent)
                .reportDate(LocalDate.now())
                .build();
    }


    public OccupancyEntry getFloorOccupancy(UUID id) {

        Floor floor = floorRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Floor not found: " + id));

        List<Zone> zones = zoneRepository.findAllByFloorId(id);

        int totalCapacity = zones.stream()
                .mapToInt(Zone::getCapacity)
                .sum();

        int totalOccupied = zones.stream()
                .mapToInt(z -> z.getCurrentCount() + z.getReservedCount())
                .sum();

        int available = Math.max(0, totalCapacity - totalOccupied);

        double percent = totalCapacity > 0
                ? Math.round((totalOccupied * 100.0 / totalCapacity) * 10.0) / 10.0
                : 0.0;

        return OccupancyEntry.builder()
                .id(id.toString())
                .name(floor.getFloorName())
                .level("floor")
                .defaultCapacity(totalCapacity)
                .capacity(totalCapacity)
                .currentOccupancy(totalOccupied)
                .availableSlots(available)
                .percent(percent)
                .reportDate(LocalDate.now())
                .build();
    }



    /*
    =============================================================================================================
                                                      THANH TOÁN
    =============================================================================================================
    */
    /**
     * Lấy chi tiết một giao dịch payment theo id
     */
    public PaymentDetailResponse getPaymentDetail(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(p -> PaymentDetailResponse.builder()
                        .id(p.getId())
                        .referenceType(p.getReferenceType())
                        .referenceId(p.getReferenceId())
                        .amount(p.getAmount())
                        .paymentMethod(p.getPaymentMethod())
                        .status(p.getStatus())
                        .transactionId(p.getTransactionId())
                        .paidAt(p.getPaidAt())
                        .createdAt(p.getCreatedAt())
                        .build())
                .orElse(null);
    }
     /*
    =============================================================================================================
                                                     SỰ CỐ AN NINH
    =============================================================================================================
    */
    /**
     * Tổng hợp sự cố: tổng, chưa giải quyết, phân loại theo type
     */
    public SecurityIncidentSummary getSecuritySummary(LocalDateTime from, LocalDateTime to) {
        List<ExceptionLog> logs;
        if (from != null && to != null) logs = exceptionLogRepository.findByCreatedAtBetween(from, to);
        else logs = exceptionLogRepository.findAll();

        long total = logs.size();
        long unresolved = logs.stream()
                .filter(log -> log.getResolvedAt() == null)
                .count();
        Map<String, Long> byType = classifySecurityIncidents(logs);
        return SecurityIncidentSummary.builder().totalIncidents(total).unresolvedIncidents(unresolved).byType(byType).build();
    }

    private Map<String, Long> classifySecurityIncidents(List<ExceptionLog> logs) {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (ExceptionType type : ExceptionType.values()) {
            byType.put(type.name(), 0L);
        }

        logs.stream()
                .map(ExceptionLog::getExceptionType)
                .filter(Objects::nonNull)
                .forEach(type -> byType.put(type.name(), byType.get(type.name()) + 1));

        return byType;
    }

    public Map<String, Object> getDashboardStats() {
        return parkingSessionService.getDashboardStats();
    }
}
