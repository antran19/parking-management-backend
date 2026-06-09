package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.repository.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * AdminManagementController — CRUD quản trị hệ thống (An phụ trách)
 *
 * TODO (An): Implement các endpoint sau:
 * - CRUD /admin/users           → Quản lý tài khoản
 * - CRUD /admin/zones           → Quản lý khu đỗ xe
 * - CRUD /admin/gates           → Quản lý cổng ra/vào
 * - CRUD /admin/pricing-rules   → Quản lý bảng giá
 * - CRUD /admin/parking-passes  → Quản lý vé tháng/quý/năm
 * - PUT  /admin/gates/{id}/barrier → Điều khiển barrier
 * - GET/PUT /admin/settings     → Cài đặt hệ thống
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminManagementController {

    // TODO: Inject repositories cần thiết

    // TODO: Implement CRUD endpoints
}
