package com.smartparking.backend.controller;


import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.repository.VehicleTypeRepository;
import com.smartparking.backend.repository.GateRepository;
import com.smartparking.backend.repository.PricingRuleRepository;
import com.smartparking.backend.repository.ZoneRepository;
import com.smartparking.backend.repository.FloorRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import com.smartparking.backend.dto.response.ApiResponse;



/**
 * ParkingConfigController — API cấu hình bãi xe (Toàn phụ trách)
 * TODO (Toàn): Implement các endpoint sau:
 * - GET /parking/config    → Trả về danh sách vehicleTypes, gates, zones (cho FE load form)
 * - PUT /parking/zones/{id}/status → Cập nhật trạng thái zone (ACTIVE/MAINTENANCE/CLOSED)
 * - Dùng List để lưu các Object trong API, Dùng LinkedHashMap để chuẩn hóa dữ liệu theo kiểu JSON
 */



@RestController
@RequestMapping("/api/v1/parking")
public class ParkingConfigController {

    @Autowired
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired
    private GateRepository gateRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private FloorRepository floorRepository;

    @Autowired
    private PricingRuleRepository pricingRuleRepository;
    // TODO : PUT /parking/zones/{id}/status → Cập nhật trạng thái zone (ACTIVE/MAINTENANCE/CLOSED)
    @PutMapping("/zones/{id}/status")
    @Transactional
    @PreAuthorize("hasAnyAuthority('STAFF','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateZoneStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String status = String.valueOf(body.getOrDefault("status", "ACTIVE")).toUpperCase();
        Zone zone = zoneRepository.findById(id).orElseThrow(() -> new RuntimeException("Zone not found"));
        zone.setStatus(Zone.ZoneStatus.valueOf(status));
        Zone saved = zoneRepository.save(zone);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", saved.getId());
        map.put("status", saved.getStatus().name());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái zone thành công",map));
    }





    // TODO : Trả về danh sách vehicleTypes, gates, zones (cho FE load form)
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getParkingConfig() {
        // Config cho xe
        List<Map<String, Object>> vehicleConfig = vehicleTypeRepository.findAll().stream()
                .map(vehicleType -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", vehicleType.getId());
                    map.put("name", vehicleType.getName());
                    map.put("description", vehicleType.getDescription());
                    return map;
                }).collect(Collectors.toList());

        // Config cho zone
        List<Map<String, Object>> zoneConfig = zoneRepository.findAll().stream()
                .map(zone -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", zone.getId());
                    map.put("zoneCode", zone.getZoneCode());
                    map.put("zoneName", zone.getZoneName());
                    map.put("capacity", zone.getCapacity());
                    map.put("currentCount", zone.getCurrentCount());
                    map.put("reservedCount", zone.getReservedCount());
                    map.put("status", zone.getStatus().name());
                    map.put("vehicleTypeId", zone.getVehicleType().getId());
                    map.put("vehicleTypeName", zone.getVehicleType().getName());
                    return map;
                }).collect(Collectors.toList());




        // Config cho floor
        List<Map<String, Object>> floorConfig = floorRepository.findAll().stream()
                .map(floor -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", floor.getId());
                    map.put("floorName", floor.getFloorName());
                    map.put("floorNumber", floor.getFloorNumber());
                    map.put("totalSlots", floor.getTotalSlots());
                    map.put("buildingId", floor.getBuilding().getId());
                    map.put("buildingName", floor.getBuilding().getName());
                    return map;
                }).collect(Collectors.toList());



        // Config cho gate
        List<Map<String, Object>> gateConfig = gateRepository.findAll().stream()
                .map(gate -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", gate.getId());
                    map.put("buildingId", gate.getBuilding().getId());
                    map.put("gateCode", gate.getGateCode());
                    map.put("gateName", gate.getGateName());
                    map.put("gateType", gate.getGateType().name());
                    map.put("isActive", gate.getIsActive());
                    return map;
                }).collect(Collectors.toList());



        // Config pricing rule
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
                })
                .collect(Collectors.toList());


        Map<String, Object> response = new HashMap<>();
        response.put("vehicleConfig", vehicleConfig);
        response.put("zoneConfig", zoneConfig);
        response.put("floorConfig", floorConfig);
        response.put("gateConfig", gateConfig);
        response.put("pricingRules", pricingRules);
        return ResponseEntity.ok(ApiResponse.success("Lấy cấu hình bãi xe thành công",response));
    }
}
