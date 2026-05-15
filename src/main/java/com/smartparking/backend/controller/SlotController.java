package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.dto.response.SlotMapResponse;
import com.smartparking.backend.service.SlotMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * SlotController — API lấy sơ đồ bãi xe.
 *
 * Endpoints:
 *   GET /api/v1/public/slots/map/{buildingId}  → Sơ đồ bãi xe (public, không cần login)
 */
@RestController
@RequestMapping("/api/v1")
public class SlotController {

    private final SlotMapService slotMapService;

    public SlotController(SlotMapService slotMapService) {
        this.slotMapService = slotMapService;
    }

    /**
     * Lấy sơ đồ bãi xe theo tòa nhà — endpoint public.
     * Trả về tất cả tầng, khu vực, slot kèm trạng thái (real-time snapshot).
     */
    @GetMapping("/public/slots/map/{buildingId}")
    public ResponseEntity<ApiResponse<SlotMapResponse>> getSlotMap(
            @PathVariable UUID buildingId) {
        SlotMapResponse response = slotMapService.getSlotMap(buildingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
