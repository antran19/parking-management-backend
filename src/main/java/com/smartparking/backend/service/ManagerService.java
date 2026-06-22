package com.smartparking.backend.service;

import com.smartparking.backend.dto.response.*;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.entity.ExceptionLog.ExceptionType;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ManagerService {

    private final PaymentRepository paymentRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ZoneRepository zoneRepository;
    private final ExceptionLogRepository exceptionLogRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final GateRepository gateRepository;
    private final ParkingSessionService parkingSessionService;

    // Constructor
    public ManagerService(PaymentRepository paymentRepository,
                          ParkingSessionRepository parkingSessionRepository,
                          ZoneRepository zoneRepository,
                          ExceptionLogRepository exceptionLogRepository,
                          BuildingRepository buildingRepository,
                          FloorRepository floorRepository,
                          VehicleTypeRepository vehicleTypeRepository,
                          PricingRuleRepository pricingRuleRepository,
                          GateRepository gateRepository,
                          ParkingSessionService parkingSessionService) {
        this.paymentRepository = paymentRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.zoneRepository = zoneRepository;
        this.exceptionLogRepository = exceptionLogRepository;
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.gateRepository = gateRepository;
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
    public BuildingOccupancyResponse getBuildingOccupancy(UUID id) {

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

        List<Floor> floors = floorRepository.findByBuildingId(id);

        return BuildingOccupancyResponse.builder()
                .id(id.toString())
                .name(building.getName())
                .totalCapacity(totalCapacity)
                .totalOccupied(totalOccupied)
                .availableSlots(available)
                .percent(percent)
                .reportDate(LocalDate.now())
                .build();
    }
    public FloorOccupancyResponse getFloorOccupancy(UUID id) {

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

        return FloorOccupancyResponse.builder()
                .id(id.toString())
                .floorName(floor.getFloorName())
                .capacity(totalCapacity)
                .occupied(totalOccupied)
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
    public List<PaymentDetailResponse> getPayments() {
        return paymentRepository.findAll().stream()
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
                .collect(Collectors.toList());
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
    /*
    =============================================================================================================
                                                     CRUD ZONE
    =============================================================================================================
    */

    public Zone createZone(Map<String, Object> body) {
        Floor floor = floorRepository.findById(uuid(body, "floorId"))
                .orElseThrow(() -> new ResourceNotFoundException("Floor không tồn tại"));

        VehicleType vehicleType = vehicleTypeRepository.findById(uuid(body, "vehicleTypeId"))
                .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại"));

        Zone zone = Zone.builder()
                .floor(floor)
                .vehicleType(vehicleType)
                .zoneCode(text(body, "zoneCode"))
                .zoneName(text(body, "zoneName"))
                .capacity(number(body, "capacity", 0))
                .currentCount(0)
                .reservedCount(0)
                .status(Zone.ZoneStatus.ACTIVE)
                .build();

        return zoneRepository.save(zone);
    }

    public Zone updateZone(UUID id, Map<String, Object> body) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone không tồn tại"));

        if (body.containsKey("zoneName"))
            zone.setZoneName(text(body, "zoneName"));
        if (body.containsKey("capacity"))
            zone.setCapacity(number(body, "capacity", zone.getCapacity()));
        if (body.containsKey("status"))
            zone.setStatus(Zone.ZoneStatus.valueOf(text(body, "status").toUpperCase()));

        return zoneRepository.save(zone);
    }
    /*=============================================================================================================
                                             CRUD PricingRule
    =============================================================================================================*/
    public PricingRule createPricingRule(Map<String, Object> body) {
        Building building = buildingRepository.findById(uuid(body, "buildingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Building không tồn tại"));

        VehicleType vehicleType = vehicleTypeRepository.findById(uuid(body, "vehicleTypeId"))
                .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại"));

        PricingRule rule = PricingRule.builder()
                .building(building)
                .vehicleType(vehicleType)
                .pricingType(PricingRule.PricingType.valueOf(
                        textOrDefault(body, "pricingType", "HOURLY").toUpperCase()))
                .pricePerUnit(decimal(body, "pricePerUnit", BigDecimal.ZERO))
                .freeMinutes(number(body, "freeMinutes", 0))
                .build();

        return pricingRuleRepository.save(rule);
    }

    public PricingRule updatePricingRule(UUID id, Map<String, Object> body) {
        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bảng giá không tồn tại"));

        if (body.containsKey("pricingType"))
            rule.setPricingType(PricingRule.PricingType.valueOf(
                    text(body, "pricingType").toUpperCase()));
        if (body.containsKey("pricePerUnit"))
            rule.setPricePerUnit(decimal(body, "pricePerUnit", rule.getPricePerUnit()));
        if (body.containsKey("freeMinutes"))
            rule.setFreeMinutes(number(body, "freeMinutes", rule.getFreeMinutes()));

        return pricingRuleRepository.save(rule);
    }

    public void deletePricingRule(UUID id) {
        pricingRuleRepository.deleteById(id);
    }
    /*=============================================================================================================
                                             CRUD GATE
    =============================================================================================================*/
    @Transactional
    public Gate createGate(Map<String, Object> body) {
        Building building = buildingRepository.findById(uuid(body, "buildingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Building không tồn tại"));

        Gate gate = Gate.builder()
                .building(building)
                .gateCode(text(body, "gateCode"))
                .gateName(text(body, "gateName"))
                .gateType(Gate.GateType.valueOf(textOrDefault(body, "gateType", "MAIN_BOTH").toUpperCase()))
                .isActive(true)
                .build();

        return gateRepository.save(gate);
    }

    @Transactional
    public Gate updateGate(UUID id, Map<String, Object> body) {
        Gate gate = gateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gate không tồn tại"));

        if (body.containsKey("gateName")) gate.setGateName(text(body, "gateName"));
        if (body.containsKey("gateType")) gate.setGateType(Gate.GateType.valueOf(text(body, "gateType").toUpperCase()));
        if (body.containsKey("isActive")) gate.setIsActive(Boolean.parseBoolean(String.valueOf(body.get("isActive"))));

        return gateRepository.save(gate);
    }

    @Transactional
    public void deleteGate(UUID id) {
        if (!gateRepository.existsById(id)) {
            throw new ResourceNotFoundException("Gate không tồn tại");
        }
        gateRepository.deleteById(id);
    }

    /*
    =============================================================================================================
                                             DASHBOARD STATS (from staff-history)
    =============================================================================================================
    */
    public Map<String, Object> getDashboardStats() {
        return parkingSessionService.getDashboardStats();
    }

    // ===================== HELPER METHODS =====================

    private String textOrDefault(Map<String, Object> body, String key, String defaultVal) {
        return body.containsKey(key) ? body.get(key).toString() : defaultVal;
    }

    private BigDecimal decimal(Map<String, Object> body, String key, BigDecimal defaultVal) {
        return body.containsKey(key) ? new BigDecimal(body.get(key).toString()) : defaultVal;
    }

    public void deleteZone(UUID id) {
        zoneRepository.deleteById(id);
    }

    // helper methods
    private UUID uuid(Map<String, Object> body, String key) {
        return UUID.fromString(body.get(key).toString());
    }

    private String text(Map<String, Object> body, String key) {
        return body.get(key).toString();
    }

    private int number(Map<String, Object> body, String key, int defaultVal) {
        return body.containsKey(key) ? Integer.parseInt(body.get(key).toString()) : defaultVal;
    }
}
