package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.*;
import com.smartparking.backend.dto.response.*;
import com.smartparking.backend.service.ParkingSessionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * SessionController — API check-in/out xe (Tùng phụ trách)
 *
 * TODO (Tùng): Implement các endpoint sau:
 * - POST /staff/sessions/checkin     → Check-in xe vào bãi
 * - POST /staff/sessions/checkout    → Check-out xe ra bãi (thanh toán tiền mặt)
 * - GET  /driver/sessions/active     → Xem session đang gửi (theo biển số)
 * - GET  /driver/sessions/history    → Lịch sử gửi xe (theo biển số)
 * - GET  /staff/sessions             → Toàn bộ lịch sử (cho Staff/Manager)
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    // TODO: Inject ParkingSessionService

    // TODO: Implement endpoints
}
