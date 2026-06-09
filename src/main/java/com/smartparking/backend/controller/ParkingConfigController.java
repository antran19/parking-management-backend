package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.repository.*;
import org.springframework.web.bind.annotation.*;

/**
 * ParkingConfigController — API cấu hình bãi xe (Toàn phụ trách)
 *
 * TODO (Toàn): Implement các endpoint sau:
 * - GET /parking/config    → Trả về danh sách vehicleTypes, gates, zones (cho FE load form)
 * - PUT /parking/zones/{id}/status → Cập nhật trạng thái zone (ACTIVE/MAINTENANCE/CLOSED)
 */
@RestController
@RequestMapping("/api/v1/parking")
public class ParkingConfigController {

    // TODO: Inject VehicleTypeRepository, GateRepository, ZoneRepository, FloorRepository

    // TODO: Implement endpoints
}
