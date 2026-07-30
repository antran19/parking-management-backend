package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.entity.Building;
import com.smartparking.backend.entity.PricingRule;
import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.repository.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * PublicInfoController — API thông tin công khai (Toàn phụ trách)
 *
 * TODO (Toàn): Implement các endpoint sau:
 * - GET /public/parking-info → Thông tin bãi xe (tên, địa chỉ, giờ mở cửa)
 * - GET /public/available-slots → Số chỗ trống theo từng tầng/zone (không cần
 * login)
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public Info", description = "Public APIs for parking lot information, availability and pricing — no authentication required")
public class PublicInfoController {

    private final ZoneRepository zoneRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final BuildingRepository buildingRepository;
    private final ParkingSessionRepository parkingSessionRepository;

    public PublicInfoController(ZoneRepository zoneRepository,
            PricingRuleRepository pricingRuleRepository,
            BuildingRepository buildingRepository,
            ParkingSessionRepository parkingSessionRepository) {
        this.zoneRepository = zoneRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.buildingRepository = buildingRepository;
        this.parkingSessionRepository = parkingSessionRepository;
    }

    /**
     * GET /api/v1/public/parking-info
     * Trả về thông tin tổng hợp bãi xe cho Guest:
     * - Sức chứa tổng quan theo tầng (capacity, occupied, available)
     * - Bảng giá cơ bản (giờ/ngày/tháng theo loại xe)
     * - Thông tin tòa nhà (tên, địa chỉ, giờ hoạt động)
     */
    @Operation(summary = "Thông tin tổng hợp bãi xe công khai", description = "Trả về thông tin tòa nhà, sức chứa theo tầng và bảng giá — không cần đăng nhập")
    @GetMapping("/parking-info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPublicParkingInfo() {

        // 1. Thông tin tòa nhà
        List<Building> buildings = buildingRepository.findAll();
        Map<String, Object> buildingInfo = new LinkedHashMap<>();
        if (!buildings.isEmpty()) {
            Building b = buildings.get(0);
            buildingInfo.put("name", b.getName());
            buildingInfo.put("address", b.getAddress());
            buildingInfo.put("operatingHoursStart", b.getOperatingHoursStart() != null
                    ? b.getOperatingHoursStart().toString()
                    : "06:00");
            buildingInfo.put("operatingHoursEnd", b.getOperatingHoursEnd() != null
                    ? b.getOperatingHoursEnd().toString()
                    : "22:00");
        }

        // 2. Sức chứa tổng hợp theo tầng (aggregate — không tiết lộ chi tiết zone)
        List<Zone> allZones = zoneRepository.findAll();
        int totalCapacity = 0;
        int totalOccupied = 0;
        int totalReserved = 0;

        Map<String, Map<String, Integer>> floorSummary = new LinkedHashMap<>();
        for (Zone z : allZones) {
            String floorName = z.getFloor() != null ? z.getFloor().getFloorName() : "Khác";
            floorSummary.putIfAbsent(floorName, new LinkedHashMap<>());
            Map<String, Integer> fs = floorSummary.get(floorName);

            int cap = z.getCapacity() != null ? z.getCapacity() : 0;
            long activeCount = parkingSessionRepository.countByZoneIdAndStatus(z.getId(), com.smartparking.backend.entity.ParkingSession.SessionStatus.ACTIVE);
            int cur = (int) activeCount;
            if (z.getCurrentCount() == null || z.getCurrentCount() != cur) {
                z.setCurrentCount(cur);
                zoneRepository.save(z);
            }
            int res = z.getReservedCount() != null ? z.getReservedCount() : 0;

            fs.merge("capacity", cap, Integer::sum);
            fs.merge("occupied", cur, Integer::sum);
            fs.merge("reserved", res, Integer::sum);
            fs.merge("available", Math.max(cap - cur - res, 0), Integer::sum);

            totalCapacity += cap;
            totalOccupied += cur;
            totalReserved += res;
        }

        Map<String, Object> availability = new LinkedHashMap<>();
        availability.put("totalCapacity", totalCapacity);
        availability.put("totalOccupied", totalOccupied);
        availability.put("totalReserved", totalReserved);
        availability.put("totalAvailable", Math.max(totalCapacity - totalOccupied - totalReserved, 0));
        availability.put("floors", floorSummary);

        // 3. Bảng giá công khai
        List<PricingRule> rules = pricingRuleRepository.findAll();
        List<Map<String, Object>> pricingList = rules.stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("vehicleType", r.getVehicleType().getName());
                    m.put("pricingType", r.getPricingType().name());
                    m.put("pricePerUnit", r.getPricePerUnit());
                    m.put("freeMinutes", r.getFreeMinutes());
                    if (r.getBuilding() != null) {
                        m.put("buildingName", r.getBuilding().getName());
                    }
                    return m;
                })
                .collect(Collectors.toList());

        // 4. Kết hợp response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("building", buildingInfo);
        result.put("availability", availability);
        result.put("pricing", pricingList);

        return ResponseEntity.ok(ApiResponse.success("Thông tin bãi xe công khai", result));
    }
}
