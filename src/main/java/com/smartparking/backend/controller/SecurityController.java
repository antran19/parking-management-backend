package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.*;
import com.smartparking.backend.dto.response.*;
import com.smartparking.backend.service.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * SecurityController — API bảo vệ/an ninh (Thiên phụ trách)
 *
 * TODO (Thiên): Implement các endpoint sau:
 * - POST /security/exceptions             → Báo cáo sự cố an ninh
 * - GET  /security/exceptions             → Xem danh sách sự cố
 * - POST /security/emergency/activate     → Kích hoạt SOS khẩn cấp
 * - POST /security/emergency/deactivate   → Hủy SOS
 * - GET  /security/emergency/status       → Trạng thái SOS
 * - GET  /security/emergency/history      → Lịch sử SOS
 * - GET  /security/blacklist              → Xem danh sách đen biển số
 * - POST /security/blacklist              → Thêm biển số vào blacklist
 * - DELETE /security/blacklist/{id}       → Gỡ biển số khỏi blacklist
 */
@RestController
@RequestMapping("/api/v1/security")
@PreAuthorize("hasAnyRole('SECURITY', 'ADMIN')")
public class SecurityController {

    // TODO: Inject SecurityExceptionService, BlacklistService, EmergencyService

    // TODO: Implement endpoints
}
