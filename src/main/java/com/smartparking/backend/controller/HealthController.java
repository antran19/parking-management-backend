package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * HealthController — API kiểm tra sức khỏe hệ thống (An phụ trách)
 *
 * TODO (An): Implement các endpoint sau:
 * - GET /ping           → Health check (trả status + timestamp)
 * - GET /public/reset-db → Reset database cho demo/test (xóa sessions, reset zones)
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health Check", description = "APIs for system health monitoring and status")
public class HealthController {

    // TODO: Implement endpoints
}
