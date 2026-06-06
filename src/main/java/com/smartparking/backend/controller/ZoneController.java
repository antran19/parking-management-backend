package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.ZoneInfoResponse;
import com.smartparking.backend.repository.ZoneRepository;
import com.smartparking.backend.service.RedisZoneCounterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ZoneController — cung cấp API để frontend hiển thị sơ đồ zone và tình trạng còn chỗ.
 * Endpoints:
 *  - GET /api/v1/zones?buildingId=  -> trả danh sách zone cùng currentCount (đọc từ Redis)
 *  - GET /api/v1/zones/{id}        -> chi tiết 1 zone (tương tự)
 *
 * Ghi chú:
 *  - currentCount được lấy bằng redisZoneCounterService.getCount(zoneId) để phản ánh gần thời gian thực.
 *  - Nếu Redis không có giá trị, service sẽ fallback đọc từ DB (như đã implement trong RedisZoneCounterService).
 */
@RestController
@RequestMapping("/api/v1")
public class ZoneController {

    private final ZoneRepository zoneRepository;
    private final RedisZoneCounterService redisZoneCounterService;

    public ZoneController(ZoneRepository zoneRepository, RedisZoneCounterService redisZoneCounterService) {
        this.zoneRepository = zoneRepository;
        this.redisZoneCounterService = redisZoneCounterService;
    }

    @GetMapping("/zones")
    public ResponseEntity<ApiResponse<List<ZoneInfoResponse>>> list(@RequestParam("buildingId") UUID buildingId) {
        // Trả về list zone cùng các thông tin cơ bản và currentCount lấy từ Redis
        List<ZoneInfoResponse> list = zoneRepository.findAllByBuildingId(buildingId).stream()
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
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/zones/{id}")
    public ResponseEntity<ApiResponse<ZoneInfoResponse>> detail(@PathVariable("id") UUID id) {
        // Chi tiết 1 zone, currentCount đọc từ Redis
        return zoneRepository.findById(id)
                .map(z -> ZoneInfoResponse.builder()
                        .id(z.getId())
                        .zoneCode(z.getZoneCode())
                        .zoneName(z.getZoneName())
                        .floorName(z.getFloor().getFloorName())
                        .capacity(z.getCapacity())
                        .currentCount(redisZoneCounterService.getCount(z.getId()))
                        .status(z.getStatus().name())
                        .build())
                .map(ApiResponse::success)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
