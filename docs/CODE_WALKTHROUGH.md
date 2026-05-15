# SmartParking — Code Walkthrough & Giải thích Toàn bộ

---

## ⚠️ VẤN ĐỀ CẦN FIX NGAY

> [!CAUTION]
> **Package name vẫn là `com.aistudyhub.backend`** — đây là tên project cũ (AI Study Hub). 
> Tất cả file Java đều dùng `package com.aistudyhub.backend;` thay vì `package com.smartparking.backend;`.
> Class main vẫn tên `AiStudyHubApplication.java`.
> 
> **Tại sao chưa đổi?** Vì việc rename package trong Java yêu cầu:
> 1. Đổi tên thư mục từ `com/aistudyhub/backend` → `com/smartparking/backend`
> 2. Đổi dòng `package` trong **tất cả** ~30 file Java
> 3. Đổi tất cả dòng `import` tham chiếu chéo
> 
> Đây là thay đổi lớn, nên tôi chưa làm tự ý. **Cần làm trước khi push lên GitHub.**

---

## 1. TẠI SAO PACKAGE ĐẶT NHƯ VẬY?

### Quy ước package trong Java

```
com.smartparking.backend
 │       │          │
 │       │          └── module (backend / frontend / common)
 │       └── tên tổ chức hoặc dự án (smartparking)
 └── domain ngược (com = thương mại)
```

**Quy tắc:** Java dùng **tên miền đảo ngược** làm package gốc để tránh trùng tên giữa các dự án trên toàn thế giới.

- `com.google.maps` → Google Maps
- `com.fpt.university` → FPT University
- `com.smartparking.backend` → SmartParking Backend

### Tại sao chia thành nhiều sub-package?

Đây là **Layered Architecture** — kiến trúc phân tầng, phổ biến nhất cho project SWP391:

```
com.smartparking.backend/
│
├── entity/        ← TẦNG 1: Dữ liệu (bảng trong DB)
├── repository/    ← TẦNG 2: Truy vấn DB
├── service/       ← TẦNG 3: Logic nghiệp vụ
├── controller/    ← TẦNG 4: Nhận request từ FE
├── dto/           ← Trung gian: Chuyển đổi dữ liệu
├── config/        ← Cấu hình: Security, WebSocket
├── security/      ← Bảo mật: JWT filter
├── exception/     ← Xử lý lỗi
└── util/          ← Tiện ích
```

**Luồng 1 request chạy qua các tầng:**

```
FE gửi request
      ↓
  Controller (nhận request, validate)
      ↓
  Service (xử lý logic nghiệp vụ)
      ↓
  Repository (query database)
      ↓
  Entity (map sang bảng PostgreSQL)
      ↓
  Database
```

---

## 2. GIẢI THÍCH TỪNG PACKAGE

---

### 📦 `entity/` — Bảng trong Database

> Mỗi class ở đây = 1 bảng trong PostgreSQL. JPA/Hibernate tự tạo bảng từ annotation.

| File | Bảng DB | Mục đích |
|------|---------|----------|
| `Building.java` | `buildings` | Tòa nhà gửi xe (tên, địa chỉ, giờ hoạt động) |
| `Floor.java` | `floors` | Tầng trong tòa nhà (B1, B2, T1, T2...) |
| `Zone.java` | `zones` | Khu vực trong tầng (Khu A xe máy, Khu B ô tô) |
| `VehicleType.java` | `vehicle_types` | Loại xe (Xe máy, Ô tô, Xe điện) |
| `Slot.java` | `slots` | Vị trí đỗ xe cụ thể (B1-A03) |
| `Gate.java` | `gates` | Cổng ra/vào (GATE-A, GATE-B) |
| `User.java` | `users` | Tài khoản (4 role: ADMIN/MANAGER/STAFF/DRIVER) |
| `ParkingSession.java` | `parking_sessions` | Lượt gửi xe (từ check-in đến check-out) |
| `Payment.java` | `payments` | Giao dịch thanh toán |
| `PricingRule.java` | `pricing_rules` | Bảng giá theo loại xe + block giờ |
| `Reservation.java` | `reservations` | Đặt chỗ trước |
| `MonthlyPass.java` | `monthly_passes` | Vé tháng |
| `ExceptionLog.java` | `exception_logs` | Nhật ký xử lý ngoại lệ |

**Quan hệ giữa các bảng:**

```
Building (1) → (N) Floor (1) → (N) Zone (1) → (N) Slot
Building (1) → (N) Gate
Slot (1) → (N) ParkingSession
ParkingSession (1) → (1) Payment
User → ParkingSession (staffEntry / staffExit)
VehicleType → Slot, ParkingSession, PricingRule
```

**Annotation quan trọng:**

```java
@Entity                              // Đánh dấu: class này là bảng DB
@Table(name = "slots")               // Tên bảng trong PostgreSQL
@Id @GeneratedValue(strategy = UUID)  // Primary key tự sinh UUID
@ManyToOne(fetch = FetchType.LAZY)    // FK N-1 (nhiều slot thuộc 1 tầng)
@JoinColumn(name = "floor_id")        // Tên cột FK
@Enumerated(EnumType.STRING)          // Lưu enum dưới dạng text ("AVAILABLE")
@CreationTimestamp                    // Tự điền thời gian khi INSERT
```

---

### 📦 `repository/` — Truy vấn Database

> Mỗi file = 1 interface kế thừa `JpaRepository`. Spring Data JPA **tự sinh code** cho CRUD cơ bản.

**Tại sao chỉ cần viết interface?**

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);  // Spring tự tạo SQL!
}
```

Spring đọc tên method → tự sinh query:
- `findByEmail(...)` → `SELECT * FROM users WHERE email = ?`
- `findByLicensePlateAndStatus(...)` → `SELECT * FROM parking_sessions WHERE license_plate = ? AND status = ?`

**Custom query (khi tên method không đủ):**

```java
@Query("SELECT s FROM Slot s WHERE s.vehicleType.id = :vehicleTypeId AND s.status = 'AVAILABLE'")
List<Slot> findAvailableSlotsByVehicleType(@Param("vehicleTypeId") UUID vehicleTypeId);
```

| Repository | Dùng cho |
|------------|----------|
| `UserRepository` | Login, quản lý tài khoản |
| `SlotRepository` | Tìm slot trống, đếm slot, sơ đồ bãi xe |
| `ParkingSessionRepository` | Tìm session theo biển số, mã vé |
| `BuildingRepository` | CRUD tòa nhà |
| `GateRepository` | Tìm cổng theo tòa nhà |
| `VehicleTypeRepository` | Tìm loại xe |
| `PaymentRepository` | Ghi nhận thanh toán |
| `PricingRuleRepository` | Lấy bảng giá để tính phí |

---

### 📦 `service/` — Logic Nghiệp vụ (NƠI QUAN TRỌNG NHẤT)

> Tất cả business logic nằm ở đây. Controller chỉ gọi service, không chứa logic.

#### `AuthService.java` — Đăng nhập

```
Input: email + password
  ↓
1. AuthenticationManager xác thực (so sánh password hash)
2. Nếu đúng → JwtUtil tạo accessToken + refreshToken
3. Trả LoginResponse (token + user info)
```

#### `ParkingSessionService.java` — ⭐ CORE (Check-in / Check-out)

**Check-in flow:**
```
Input: biển số, loại xe, cổng vào
  ↓
1. Kiểm tra biển số chưa có session ACTIVE (BR-06)
2. Tạo ParkingSession mới (status = ACTIVE)
3. Gọi SlotAssignmentService → tìm slot tối ưu + lock Redis
4. Gán slot cho session
5. Broadcast slot change qua WebSocket
6. Trả response: "Vui lòng đến Tầng B1 - Ô số B1-A03"
```

**Check-out flow:**
```
Input: biển số HOẶC mã vé HOẶC session ID + cổng ra
  ↓
1. Tìm session ACTIVE
2. Tính thời gian = exitTime - entryTime
3. Gọi PricingService → tính phí theo bảng giá
4. Tạo Payment record
5. Đóng session (status = COMPLETED)
6. Giải phóng slot (status → AVAILABLE) + xóa Redis lock
7. Broadcast slot change qua WebSocket
```

#### `SlotAssignmentService.java` — Thuật toán AI phân bổ slot

```
Input: loại xe + session ID
  ↓
1. Query tất cả slot AVAILABLE đúng loại xe
2. Sort theo scoring:
   - Tầng thấp hơn (|floorNumber| nhỏ) → ưu tiên cao
   - Gần cổng/thang máy hơn (distanceToGate nhỏ) → ưu tiên cao
3. Duyệt từng slot theo thứ tự:
   - Redis: setIfAbsent("slot:lock:{id}", sessionId, TTL=60s)
   - Nếu lock thành công → slot này thuộc về session này
   - Nếu đã bị lock → skip, thử slot tiếp
4. Cập nhật slot.status = OCCUPIED
5. Trả về slot đã lock
```

**Tại sao cần Redis lock?**
> Khi 10 xe check-in cùng lúc, tất cả đều thấy slot B1-A01 trống.
> Nếu không có lock → 10 session cùng gán vào 1 slot → lỗi.
> Redis `setIfAbsent` đảm bảo chỉ 1 session được lock slot đầu tiên.

#### `PricingService.java` — Tính phí

```
Input: buildingId, vehicleTypeId, durationMinutes
  ↓
1. Tìm PricingRule (HOURLY) cho building + vehicleType
2. Trừ freeMinutes (phút miễn phí)
3. Tính block giờ = ceil(chargeableMinutes / 60)
4. Fee = blocks × pricePerUnit
```

Ví dụ: Xe máy, gửi 2 giờ 15 phút, giá 5000đ/h, free 15 phút:
- Chargeable = 135 - 15 = 120 phút
- Blocks = ceil(120/60) = 2
- Fee = 2 × 5000 = 10.000đ

#### `SlotMapService.java` — Sơ đồ bãi xe cho FE

```
Input: buildingId
  ↓
1. Query tất cả slot trong building (JOIN FETCH floor, building)
2. Nhóm theo Floor → Zone
3. Đếm: available, occupied, reserved
4. Trả về cấu trúc JSON cho FE render sơ đồ
```

---

### 📦 `controller/` — Nhận Request từ Frontend

> Controller chỉ làm 3 việc: (1) nhận request, (2) gọi service, (3) trả response.

| Controller | Base URL | Endpoints |
|-----------|---------|-----------|
| `AuthController` | `/api/v1/auth` | POST `/login`, `/refresh`, `/logout` |
| `SessionController` | `/api/v1` | POST `/staff/sessions/checkin`, `/checkout`; GET `/sessions/active` |
| `SlotController` | `/api/v1` | GET `/public/slots/map/{buildingId}` |
| `HealthController` | `/actuator` | GET `/health` |

**Tại sao URL có prefix khác nhau?**
```
/api/v1/public/**   → Ai cũng truy cập được (xem bãi xe)
/api/v1/auth/**     → Ai cũng truy cập được (đăng nhập)
/api/v1/staff/**    → Chỉ STAFF, MANAGER, ADMIN
/api/v1/manager/**  → Chỉ MANAGER, ADMIN
/api/v1/admin/**    → Chỉ ADMIN
```

Quy tắc này được cấu hình trong `SecurityConfig.java`.

---

### 📦 `dto/` — Data Transfer Object

> DTO = đối tượng dùng để chuyển dữ liệu giữa FE ↔ BE. KHÔNG phải entity.

**Tại sao cần DTO thay vì trả thẳng Entity?**
1. **Bảo mật:** Entity `User` có `passwordHash` — không bao giờ trả cho FE
2. **Linh hoạt:** Response có thể gom dữ liệu từ nhiều entity
3. **Validation:** `@NotBlank`, `@Email` trên request DTO

```
dto/
├── request/
│   ├── LoginRequest.java      → FE gửi lên khi đăng nhập
│   ├── CheckInRequest.java    → FE gửi lên khi check-in xe
│   └── CheckOutRequest.java   → FE gửi lên khi check-out xe
└── response/
    ├── ApiResponse.java       → Wrapper chung cho TẤT CẢ response
    ├── LoginResponse.java     → Trả token + user info
    ├── SessionResponse.java   → Trả thông tin phiên gửi xe
    └── SlotMapResponse.java   → Trả sơ đồ bãi xe (Floor > Zone > Slot)
```

---

### 📦 `security/` — JWT Authentication

**Luồng xác thực mỗi request:**

```
FE gửi request với header: "Authorization: Bearer eyJhbGci..."
                    ↓
         JwtAuthFilter.java (1)
         - Đọc token từ header
         - Gọi JwtUtil.extractUsername(token) → lấy email
                    ↓
      UserDetailsServiceImpl.java (2)
         - Dùng email → query DB lấy User
         - Map role → GrantedAuthority ("ROLE_STAFF")
                    ↓
         JwtUtil.isTokenValid() (3)
         - Check chữ ký + check hết hạn
                    ↓
      SecurityContextHolder.setAuthentication() (4)
         - Spring Security biết "request này là của user X, role Y"
                    ↓
         Controller method chạy
         - @PreAuthorize("hasRole('STAFF')") → kiểm tra quyền
```

---

### 📦 `config/` — Cấu hình hệ thống

#### `SecurityConfig.java`
- Tắt CSRF (vì dùng JWT, không dùng session)
- Đặt SessionCreationPolicy = STATELESS
- Cấu hình URL nào public, URL nào cần role gì
- Wire JwtAuthFilter vào trước UsernamePasswordAuthenticationFilter

#### `WebSocketConfig.java`
- Endpoint: `/ws` (SockJS fallback)
- Client subscribe: `/topic/slots/{buildingId}`
- Khi slot thay đổi → server broadcast message tới tất cả client đang subscribe

---

### 📦 `exception/` — Xử lý lỗi tập trung

```
BusinessException        → Lỗi nghiệp vụ (biển số trùng, bãi đầy)
ResourceNotFoundException → Không tìm thấy entity (404)
GlobalExceptionHandler    → Bắt TẤT CẢ exception, trả ApiResponse.error()
```

**Tại sao cần GlobalExceptionHandler?**
Không có nó → FE nhận HTML stacktrace. Có nó → FE luôn nhận JSON:
```json
{ "success": false, "message": "Biển số 51A-12345 đang có phiên gửi xe chưa kết thúc", "data": null }
```

---

## 3. CONFIGURATION FILES

### `pom.xml` — Dependencies
```
spring-boot-starter-web         → REST API
spring-boot-starter-security    → JWT + RBAC
spring-boot-starter-data-jpa    → ORM (Hibernate)
spring-boot-starter-validation  → @NotBlank, @Email
spring-boot-starter-websocket   → Real-time slot updates
spring-boot-starter-data-redis  → Distributed lock
spring-boot-starter-actuator    → Health check endpoint
postgresql                      → PostgreSQL driver
jjwt-api/impl/jackson           → JWT token library
lombok                          → Giảm boilerplate (@Getter, @Builder...)
```

### `application.yml` — Runtime config
```yaml
spring.datasource.url    → jdbc:postgresql://localhost:5432/smartparking_db
spring.data.redis        → localhost:6379
jwt.secret               → key ký token
jwt.expiration           → 1 giờ (access token)
jwt.refresh-expiration   → 7 ngày (refresh token)
smartparking.reservation.hold-minutes → 30 phút giữ chỗ
```

### `docker-compose.yml` — Môi trường dev
```
PostgreSQL 15 (port 5432) → lưu dữ liệu
Redis 7 (port 6379)       → lock slot + cache
```

---

## 4. TÓM TẮT FLOW END-TO-END

```
[Tài xế đến cổng]
       ↓
[Staff mở web → POST /staff/sessions/checkin]
       ↓
[AuthController → JwtAuthFilter kiểm tra token → OK]
       ↓
[SessionController.checkIn() → ParkingSessionService.checkIn()]
       ↓
[1. Validate biển số chưa có session]
[2. SlotAssignmentService.assignOptimalSlot()]
[   → Query slot trống → Sort → Redis lock → DB update]
[3. Tạo ParkingSession → save DB]
[4. WebSocket broadcast → FE cập nhật sơ đồ]
       ↓
[Trả response: "Vui lòng đến B1-A03"]
       ↓
[Tài xế đến slot, đậu xe]
       ↓
... thời gian trôi qua ...
       ↓
[Tài xế ra cổng]
       ↓
[Staff → POST /staff/sessions/checkout]
       ↓
[ParkingSessionService.checkOut()]
[1. Tìm session ACTIVE theo biển số]
[2. PricingService.calculateFee() → 15.000đ]
[3. Tạo Payment (CASH/QR)]
[4. Đóng session → COMPLETED]
[5. Giải phóng slot → AVAILABLE]
[6. WebSocket broadcast]
       ↓
[Trả response: "Tổng phí 15.000đ"]
```
