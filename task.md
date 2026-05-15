# SmartParking — Task Tracker & Phân công Nhiệm vụ

> Cập nhật: 16/05/2026 | Hiện tại: **TUẦN 1** (của 9 tuần)
> Tiến độ Leader: **Đã hoàn thành tới tuần 4–5 theo WBS**

---

## 📊 ĐÃ HOÀN THÀNH (Tuần 1 — bởi Leader Minh An)

### Backend: 47 files

| Layer | Files | Trạng thái |
|-------|-------|-----------|
| Entity (13) | Building, Floor, Zone, Slot, Gate, User, ParkingSession, Payment, PricingRule, Reservation, MonthlyPass, ExceptionLog, VehicleType | ✅ |
| Repository (8) | User, Slot, ParkingSession, Building, Gate, VehicleType, Payment, PricingRule | ✅ |
| Service (5) | AuthService, ParkingSessionService, SlotAssignmentService, PricingService, SlotMapService | ✅ |
| Controller (4) | AuthController, SessionController, SlotController, HealthController | ✅ |
| DTO Request (3) | LoginRequest, CheckInRequest, CheckOutRequest | ✅ |
| DTO Response (4) | ApiResponse, LoginResponse, SessionResponse, SlotMapResponse | ✅ |
| Security (2) | JwtAuthFilter, UserDetailsServiceImpl | ✅ |
| Config (2) | SecurityConfig (JWT+RBAC), WebSocketConfig (STOMP) | ✅ |
| Exception (4) | GlobalExceptionHandler, BusinessException, ResourceNotFoundException, ErrorResponse | ✅ |
| Util (1) | JwtUtil | ✅ |
| Main (1) | SmartParkingApplication.java | ✅ |

### Infra & Docs
- ✅ pom.xml — groupId=com.smartparking, WebSocket+Redis deps
- ✅ application.yml — smartparking_db, Redis, JWT config
- ✅ docker-compose.yml — PostgreSQL 15 + Redis 7
- ✅ SRS_ParkingSystem.md — 26 Use Cases
- ✅ docs/API_SPEC.md — API đặc tả cho FE
- ✅ Package đổi sang com.smartparking.backend
- ✅ Branch develop đã tạo trên GitHub

---

## 👥 PHÂN CÔNG NHIỆM VỤ (Tuần 2–9)

---

### 1. TRẦN NGUYỄN MINH AN — Leader / Backend Core

> Đã hoàn thành phần lớn backend. Từ tuần 2 chuyển sang: seed data, review code, hỗ trợ nhóm.

| Tuần | Việc | Output |
|------|------|--------|
| **2** | Tạo DataInitializer.java | Seed data: 1 building, 3 tầng, 60 slot, 4 user, bảng giá |
| **2** | Tạo Postman Collection | Test tất cả API đã viết |
| **2** | Hướng dẫn nhóm | Giải thích code cho Tùng, Toàn, Thiên, Quảng |
| **3** | Review code (Assessment 1) | Approve PR của các thành viên trên GitHub |
| **4–5** | Tối ưu SlotAssignment | Thêm cân bằng tải giữa tầng, ưu tiên zone |
| **6–7** | Integration testing | Test end-to-end check-in → check-out |
| **8** | Load testing | Test nhiều check-in đồng thời |

---

### 2. NGUYỄN DUY TÙNG — Backend Security + Reservation + Payment

> Security đã được Leader setup. Tùng review lại + làm thêm module Reservation & Exception.

| Tuần | Việc | Output |
|------|------|--------|
| **2** | Review Security code | Đọc hiểu JwtUtil, JwtAuthFilter, SecurityConfig. Đề xuất cải thiện nếu cần |
| **2** | Register API | `RegisterRequest.java`, thêm register() vào AuthService, endpoint POST /auth/register |
| **2** | CORS Config | `CorsConfig.java` cho phép FE localhost:5173 |
| **3** | ReservationService | Đặt chỗ trước, tự hủy sau 30 phút (UC-13) |
| **3** | ReservationController | POST/GET/DELETE /api/v1/reservations |
| **4–5** | ExceptionHandlingService | Xử lý mất thẻ, sai biển số, quá giờ (UC-08, UC-09, UC-10) |
| **4–5** | ExceptionController | API cho Staff ghi nhận ngoại lệ |
| **6–7** | PaymentService | Tích hợp VNPay/Momo sandbox hoặc mock QR |
| **8** | Security audit | Rate limiting, kiểm tra lỗ hổng |

**Files cần tạo:**
```
config/CorsConfig.java
dto/request/RegisterRequest.java
dto/request/ReservationRequest.java
dto/request/ExceptionReportRequest.java
service/ReservationService.java
service/ExceptionHandlingService.java
service/PaymentService.java
controller/ReservationController.java
controller/ExceptionController.java
controller/PaymentController.java
```

---

### 3. NGUYỄN KHẮC TOÀN — Database + Report + Admin

> Entity đã được Leader tạo. Toàn chuyển sang: migration, seed data, report, admin module.

| Tuần | Việc | Output |
|------|------|--------|
| **2** | Viết Flyway migration | `V1__init_schema.sql` — script SQL chuẩn thay ddl-auto |
| **2** | Hỗ trợ Leader tạo DataInitializer | Viết INSERT data mẫu |
| **3** | Tạo report queries | Query thống kê doanh thu, lượt xe, tỷ lệ lấp đầy |
| **3** | FloorRepository + ZoneRepository | Repository còn thiếu |
| **4–5** | ReportService | Doanh thu ngày/tháng, lượt xe theo loại, peak hours |
| **4–5** | ReportController | GET /api/v1/manager/reports/revenue, /occupancy |
| **6–7** | AdminService + AdminController | CRUD tài khoản, phân quyền, cấu hình hệ thống |
| **6–7** | Query optimization | @Index, fix N+1, Redis caching |
| **8** | Data export | Xuất CSV/Excel cho Manager |

**Files cần tạo:**
```
resources/db/migration/V1__init_schema.sql
repository/FloorRepository.java
repository/ZoneRepository.java
repository/ReservationRepository.java
repository/ExceptionLogRepository.java
service/ReportService.java
service/AdminService.java
controller/ReportController.java
controller/AdminController.java
dto/response/RevenueReportResponse.java
dto/response/OccupancyReportResponse.java
```

---

### 4. BÙI NGỌC TÁ THIÊN — Frontend Architecture

> Chưa có gì trong frontend/. Bắt đầu từ tuần 2.

| Tuần | Việc | Output |
|------|------|--------|
| **2** | Setup Vite + React + Tailwind | Khởi tạo frontend/, cài react-router, zustand, axios, sockjs |
| **2** | Layout chính | Sidebar + Header + routing theo role |
| **2** | Zustand stores | authStore (token, user), slotStore (trạng thái slot) |
| **2** | API layer | axiosClient.js với JWT interceptor (tự gắn token vào header) |
| **3** | Login page | Form login → gọi /auth/login → lưu token → redirect |
| **4–5** | Parking Map | Sơ đồ slot 2D tương tác, màu sắc theo trạng thái |
| **4–5** | WebSocket hook | useWebSocket() — subscribe /topic/slots/{buildingId} |
| **4–5** | Check-in UI | Form nhập biển số → gọi API → hiển thị slot được gán |
| **6–7** | Check-out UI | Tìm session → hiển thị phí → xác nhận thanh toán |
| **8** | Polish | Animations, responsive, dark mode |

**Files cần tạo:**
```
frontend/
├── src/
│   ├── App.jsx
│   ├── main.jsx
│   ├── pages/
│   │   ├── LoginPage.jsx
│   │   ├── ParkingMapPage.jsx
│   │   ├── CheckInPage.jsx
│   │   └── CheckOutPage.jsx
│   ├── components/
│   │   ├── Layout.jsx
│   │   ├── Sidebar.jsx
│   │   ├── SlotGrid.jsx
│   │   └── SlotCard.jsx
│   ├── store/
│   │   ├── authStore.js
│   │   └── slotStore.js
│   ├── api/
│   │   └── axiosClient.js
│   └── hooks/
│       └── useWebSocket.js
```

---

### 5. HUỲNH NGỌC QUẢNG — Frontend Driver + Dashboard + QA

> Mobile web-app cho Driver, Manager Dashboard, kiểm thử.

| Tuần | Việc | Output |
|------|------|--------|
| **2** | Figma mockup | 5 màn hình: Login, Parking Map, Check-in, Dashboard, Driver |
| **3** | Public page | Xem thông tin bãi xe (không cần login) — gọi /public/slots/map |
| **3** | Register page | Form đăng ký Driver |
| **4–5** | Pre-booking page | Driver xem slot trống + đặt chỗ trước |
| **4–5** | My Session page | Driver xem session hiện tại + phí tạm tính |
| **6–7** | Manager Dashboard | Biểu đồ doanh thu (Recharts), heatmap mật độ |
| **6–7** | QR Payment page | Hiển thị QR code thanh toán |
| **8** | Testing | Test cases, Postman collection, manual testing toàn bộ |

**Files cần tạo:**
```
frontend/src/
├── pages/
│   ├── PublicParkingInfo.jsx
│   ├── RegisterPage.jsx
│   ├── PreBookingPage.jsx
│   ├── MySessionPage.jsx
│   ├── DashboardPage.jsx
│   └── QRPaymentPage.jsx
├── components/
│   ├── RevenueChart.jsx
│   ├── HeatmapView.jsx
│   └── QRCodeDisplay.jsx
```

---

## 📅 TIMELINE 9 TUẦN

```
TUẦN 1 (hiện tại): ✅ Leader hoàn thành backend core
TUẦN 2: Cả nhóm bắt đầu → Tùng review Security + Toàn viết migration + Thiên setup FE + Quảng Figma
TUẦN 3 (Assessment 1): Login hoạt động, API chạy, FE có Login page, có data mẫu
TUẦN 4–5 (Sprint 1): Parking Map + Check-in/out UI + Report + Reservation
TUẦN 6–7 (Assessment 2): Dashboard + Payment + Admin + Integration
TUẦN 8 (Sprint 2): QR Payment + Heatmap + Polish + Testing
TUẦN 9 (Assessment 3): Fix bug + Bảo vệ
```

---

## ✅ CHECKLIST ASSESSMENT 1 (Cuối tuần 3)

- [ ] Spring Boot app khởi động thành công
- [ ] Docker Compose chạy PostgreSQL + Redis
- [ ] Database có dữ liệu mẫu
- [ ] POST /auth/login → trả JWT token
- [ ] POST /auth/register → tạo tài khoản Driver
- [ ] POST /staff/sessions/checkin → trả slot
- [ ] POST /staff/sessions/checkout → trả phí
- [ ] GET /public/slots/map/{id} → sơ đồ JSON
- [ ] Frontend: Login page hoạt động
- [ ] GitHub: branch develop + feature branches
- [ ] Tài liệu: SRS + API Spec hoàn thiện
