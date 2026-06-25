package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.entity.*;
import com.smartparking.backend.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ParkingConfigController — API cấu hình bãi xe (Toàn phụ trách)
 *
 * Đã implement:
 * - GET /parking/config           → Trả về toàn bộ config (vehicleTypes, buildings, floors, gates, zones, pricingRules)
 * - PUT /parking/zones/{id}/status → Cập nhật trạng thái zone
 */

@RestController
@RequestMapping("/api/v1/parking")
@Tag(name = "Parking Config", description = "APIs for parking lot configuration (shared by all roles)")
public class ParkingConfigController {

    private final VehicleTypeRepository vehicleTypeRepository;
    private final GateRepository gateRepository;
    private final ZoneRepository zoneRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final PricingRuleRepository pricingRuleRepository;

    public ParkingConfigController(VehicleTypeRepository vehicleTypeRepository,
                                   GateRepository gateRepository,
                                   ZoneRepository zoneRepository,
                                   BuildingRepository buildingRepository,
                                   FloorRepository floorRepository,
                                   PricingRuleRepository pricingRuleRepository) {
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.gateRepository = gateRepository;
        this.zoneRepository = zoneRepository;
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.pricingRuleRepository = pricingRuleRepository;
    }

    @GetMapping("/config")
    @Operation(summary = "Get full parking config (vehicleTypes, buildings, floors, gates, zones, pricingRules)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getParkingConfig() {
        List<Map<String, Object>> vehicleTypes = vehicleTypeRepository.findAll().stream()
                .map(vt -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", vt.getId());
                    m.put("name", vt.getName());
                    m.put("description", vt.getDescription());
                    return m;
                }).collect(Collectors.toList());

        List<Map<String, Object>> buildings = buildingRepository.findAll().stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getId());
                    m.put("name", b.getName());
                    m.put("address", b.getAddress());
                    return m;
                }).collect(Collectors.toList());

        List<Map<String, Object>> floors = floorRepository.findAll().stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", f.getId());
                    m.put("floorName", f.getFloorName());
                    m.put("floorNumber", f.getFloorNumber());
                    m.put("totalSlots", f.getTotalSlots());
                    m.put("buildingId", f.getBuilding().getId());
                    m.put("buildingName", f.getBuilding().getName());
                    if (f.getVehicleType() != null) {
                        m.put("vehicleTypeId", f.getVehicleType().getId());
                        m.put("vehicleTypeName", f.getVehicleType().getName());
                    }
                    return m;
                }).collect(Collectors.toList());

        List<Map<String, Object>> pricingRules = pricingRuleRepository.findAll().stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("buildingId", r.getBuilding().getId());
                    m.put("buildingName", r.getBuilding().getName());
                    m.put("vehicleTypeId", r.getVehicleType().getId());
                    m.put("vehicleTypeName", r.getVehicleType().getName());
                    m.put("pricingType", r.getPricingType().name());
                    m.put("pricePerUnit", r.getPricePerUnit());
                    m.put("freeMinutes", r.getFreeMinutes());
                    return m;
                }).collect(Collectors.toList());

        List<Map<String, Object>> gates = gateRepository.findAll().stream()
                .map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", g.getId());
                    m.put("buildingId", g.getBuilding().getId());
                    m.put("gateCode", g.getGateCode());
                    m.put("gateName", g.getGateName());
                    m.put("gateType", g.getGateType().name());
                    m.put("isActive", g.getIsActive());
                    if (g.getZone() != null) {
                        m.put("zoneId", g.getZone().getId());
                        m.put("zoneCode", g.getZone().getZoneCode());
                    }
                    return m;
                }).collect(Collectors.toList());

        List<Map<String, Object>> zones = zoneRepository.findAll().stream()
                .map(z -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", z.getId());
                    m.put("zoneCode", z.getZoneCode());
                    m.put("zoneName", z.getZoneName());
                    m.put("capacity", z.getCapacity());
                    m.put("currentCount", z.getCurrentCount());
                    m.put("reservedCount", z.getReservedCount());
                    m.put("status", z.getStatus().name());
                    m.put("vehicleTypeId", z.getVehicleType().getId());
                    m.put("vehicleTypeName", z.getVehicleType().getName());
                    if (z.getFloor() != null) {
                        m.put("floorId", z.getFloor().getId());
                        m.put("floorName", z.getFloor().getFloorName());
                        m.put("floorNumber", z.getFloor().getFloorNumber());
                    }
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("vehicleTypes", vehicleTypes);
        config.put("buildings", buildings);
        config.put("floors", floors);
        config.put("gates", gates);
        config.put("zones", zones);
        config.put("pricingRules", pricingRules);

        return ResponseEntity.ok(ApiResponse.success("Lấy cấu hình bãi xe thành công", config));
    }

    @PutMapping("/zones/{id}/status")
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Update zone status (ACTIVE/MAINTENANCE/CLOSED)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateZoneStatus(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Zone zone = zoneRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Zone không tồn tại"));
        String status = String.valueOf(body.getOrDefault("status", "ACTIVE")).toUpperCase();
        zone.setStatus(Zone.ZoneStatus.valueOf(status));
        Zone saved = zoneRepository.save(zone);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", saved.getId());
        map.put("status", saved.getStatus().name());
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật trạng thái zone", map));
    }
}
