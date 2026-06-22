# TEAM ASSIGNMENT — Phân công chi tiết từng thành viên

> Mã đề tài: SU26SWP08 | Môn: SWP391 | FPT University HCM | Summer 2026

---

## Tổng quan phân công

| Thành viên | Role | Branch | Phạm vi |
|---|---|---|---|
| **Quảng** | Driver | `feature/driver-*` | FE Driver pages + BE Driver/Reservation API |
| **Tùng** | Staff | `feature/staff-*` | FE Staff pages + BE Session/Pricing API |
| **Toàn** | Manager | `feature/manager-*` | FE Manager pages + BE Payment/Config/VNPAY API |
| **Thiên** | Security | `feature/security-*` | FE Security pages + BE Security/SOS/Blacklist API |
| **An** | Admin | `feature/admin-*` | FE Admin pages + BE Admin CRUD + DataInitializer |

---

## QUẢNG — Driver Role

### Backend files
| File | Chức năng | Endpoints |
|------|-----------|-----------|
| `DriverController.java` | API biển số, pricing plans, parking pass | GET/POST/DELETE /driver/plates, GET /driver/pricing-plans, POST /driver/parking-passes |
| `ReservationController.java` | API đặt giữ chỗ | POST/GET/DELETE /driver/reservations |
| `ReservationService.java` | Logic đặt giữ chỗ (validate zone, tăng reservedCount) | — |
| `ReservationExpiryScheduler.java` | Background job hủy reservation hết hạn (30s) | — |

### Frontend files
| File | Chức năng |
|------|-----------|
| `DriverDashboard.jsx` | Dashboard chính: session, reservation, lịch sử, thống kê |
| `DriverMapping.jsx` | Sơ đồ bãi xe visual + đặt giữ chỗ |
| `ProfileTab.jsx` | Quản lý biển số + đăng ký parking pass |
| `PaymentReturnPage.jsx` | Trang kết quả thanh toán VNPAY |

### API cần gọi
`staffApi.getDriverPlates()`, `staffApi.addDriverPlate()`, `staffApi.deleteDriverPlate()`,
`staffApi.createReservation()`, `staffApi.getDriverReservations()`, `staffApi.cancelReservation()`,
`staffApi.getActiveSession()`, `staffApi.getSessionHistory()`, `staffApi.confirmPayment()`

---

## TÙNG — Staff Role

### Backend files
| File | Chức năng | Endpoints |
|------|-----------|-----------|
| `SessionController.java` | API check-in/out, xem session | POST /staff/sessions/checkin, POST /staff/sessions/checkout, GET /driver/sessions/* |
| `ParkingSessionService.java` | ⭐ Core: validate → gợi ý zone → tạo session → WebSocket | — |
| `PricingService.java` | Tính phí: ceil((phút - miễn phí) / 60) × giá/giờ | — |
| `ZoneSuggestionService.java` | Gợi ý zone tối ưu (ít xe nhất, gần cổng nhất) | — |

### Frontend files
| File | Chức năng |
|------|-----------|
| `StaffDashboard.jsx` | Thống kê: doanh thu, lượt gửi, xe đang đỗ, công suất |
| `StaffCheckIn.jsx` | ⭐ Form check-in (3 cách: thủ công, QR, AI OCR) |
| `StaffCheckOut.jsx` | Form check-out + WebSocket listener payment |
| `StaffHistory.jsx` | Bảng lịch sử sessions |
| `StaffMapping.jsx` | Sơ đồ bãi xe real-time cho staff |

### API cần gọi
`staffApi.checkIn()`, `staffApi.checkOut()`, `staffApi.getParkingConfig()`,
`staffApi.getActiveSession()`, `staffApi.getAllSessions()`

---

## TOÀN — Manager Role

### Backend files
| File | Chức năng | Endpoints |
|------|-----------|-----------|
| `ParkingConfigController.java` | API cấu hình bãi xe | GET /parking/config |
| `PaymentController.java` | ⭐ API thanh toán VNPAY + chuyển khoản | POST /driver/payments/*, GET /driver/payments/vnpay-* |
| `PublicInfoController.java` | API thông tin công khai | GET /public/parking-info |
| `PaymentConfirmationService.java` | Xác nhận CK → đóng session → WebSocket | — |
| `VnPayService.java` | ⭐ Tạo URL VNPAY, xác minh callback | — |

### Frontend files
| File | Chức năng |
|------|-----------|
| `ManagerDashboard.jsx` | ⭐ Thống kê doanh thu, biểu đồ, lịch sử giao dịch |
| `ParkingDigitalTwin3D.jsx` | Mô phỏng 3D bãi xe (Three.js) |

### API cần gọi
`staffApi.getParkingConfig()`, `staffApi.getAllSessions()`,
`staffApi.getPublicParkingInfo()`

---

## THIÊN — Security Role

### Backend files
| File | Chức năng | Endpoints |
|------|-----------|-----------|
| `SecurityController.java` | ⭐ API sự cố, SOS, blacklist | /security/* |
| `EmergencyPublicController.java` | API SOS công khai | GET /public/emergency/status |
| `SecurityExceptionService.java` | Ghi nhận sự cố an ninh | — |
| `BlacklistService.java` | ⭐ CRUD biển số đen + WebSocket broadcast | — |
| `EmergencyService.java` | ⭐ SOS kích hoạt/hủy + lịch sử + WebSocket | — |

### Frontend files
| File | Chức năng |
|------|-----------|
| `SecurityDashboard.jsx` | ⭐ 4 tabs: Giám sát, Sự cố, SOS, Blacklist |

### API cần gọi
`staffApi.logSecurityException()`, `staffApi.getSecurityExceptions()`,
`staffApi.activateEmergency()`, `staffApi.deactivateEmergency()`,
`staffApi.getBlacklist()`, `staffApi.addToBlacklist()`, `staffApi.removeFromBlacklist()`

---

## AN — Admin Role

### Backend files
| File | Chức năng | Endpoints |
|------|-----------|-----------|
| `AdminManagementController.java` | ⭐ CRUD users, zones, gates, pricing, passes, settings | /admin/* |
| `HealthController.java` | Health check + reset DB cho demo | GET /ping, GET /public/reset-db |

### Frontend files
| File | Chức năng |
|------|-----------|
| `AdminDashboard.jsx` | ⭐ CRUD tables: Users, Zones, Gates, Pricing, Passes |
| `AdminMapping.jsx` | Sơ đồ bãi xe (admin edit mode) |

### API cần gọi
Tất cả `staffApi.admin*()` methods trong parkingApi.js

---

## FILES DÙNG CHUNG (KHÔNG SỬA)

> ⚠️ Các file sau đã được setup sẵn. **KHÔNG** tự ý sửa mà không hỏi Leader.

| Layer | Files |
|-------|-------|
| Entity | Tất cả 16 files trong `entity/` |
| Repository | Tất cả 16 files trong `repository/` |
| DTO | Tất cả files trong `dto/request/` + `dto/response/` |
| Config | `SecurityConfig`, `CorsConfig`, `WebSocketConfig`, `DataInitializer` |
| Security | `JwtAuthFilter`, `UserDetailsServiceImpl` |
| Auth | `AuthController`, `AuthService` |
| FE Shared | `axiosClient.js`, `parkingApi.js`, `App.jsx`, `AppRoutes.jsx`, `LoginScreen.jsx` |

> Nếu cần thêm field vào Entity hoặc thêm method vào Repository → **hỏi Leader trước** để tránh conflict.
