# Smart Parking Team API Contract

Tài liệu này là hợp đồng API chung cho `ParkingSystem_Team`. Leader định nghĩa route, chuẩn response và phạm vi role; từng thành viên implement full FE + BE theo role được giao.

## 1. Team Ownership

| Thành viên | Role sở hữu | Phạm vi |
|---|---|---|
| Quảng | Driver | Driver FE + BE |
| Tùng | Staff | Staff FE + BE |
| Toàn | Manager | Manager FE + BE |
| Thiên | Security | Security FE + BE |
| An | Admin | Admin FE + BE |

## 2. API Rules Chung

### Base URL

```text
/api/v1
```

### Route namespace theo role

```text
/api/v1/admin/...
/api/v1/driver/...
/api/v1/staff/...
/api/v1/manager/...
/api/v1/security/...
```

### Response format chuẩn

Tất cả API nên dùng `ApiResponse<T>`:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

Lỗi nên trả format thống nhất từ `GlobalExceptionHandler`.

### Authentication

Trừ API public/auth, các API role phải có JWT:

```http
Authorization: Bearer <accessToken>
```

### Authorization

Controller/method phải khai báo role bằng `@PreAuthorize`.

Ví dụ:

```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
```

### DTO rule

API chính nên dùng DTO thay vì raw `Map<String, Object>` nếu có thể.

```text
Request DTO: backend/src/main/java/.../dto/request
Response DTO: backend/src/main/java/.../dto/response
```

### Không commit file generated/local

Không commit:

```text
target/
*.class
node_modules/
dist/
.env
*.log
.idea/
.vscode/
```

## 3. Auth / Common APIs

Owner: dùng chung.

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/auth/login` | Đăng nhập, nhận JWT |
| POST | `/api/v1/auth/refresh` | Refresh token |
| POST | `/api/v1/auth/logout` | Đăng xuất |
| POST | `/api/v1/auth/register` | Đăng ký nếu được bật |
| GET | `/api/v1/parking/config` | Lấy config chung: buildings, floors, zones, gates, vehicle types, pricing |

## 4. Admin APIs - An

### User Management

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/admin/users` | Danh sách tài khoản |
| POST | `/api/v1/admin/users` | Tạo tài khoản + mật khẩu tạm |
| PUT | `/api/v1/admin/users/{id}` | Cập nhật user |
| DELETE | `/api/v1/admin/users/{id}` | Xóa user |
| POST | `/api/v1/admin/users/{id}/reset-password` | Reset mật khẩu user |

Create user request:

```json
{
  "name": "Nguyễn Văn Tùng",
  "email": "tung@smartparking.local",
  "phone": "0900000001",
  "role": "STAFF",
  "password": "Staff@123"
}
```

Create/reset password response phải chỉ hiển thị mật khẩu tạm một lần:

```json
{
  "success": true,
  "message": "Đã tạo tài khoản. Mật khẩu tạm thời chỉ hiển thị một lần",
  "data": {
    "id": "uuid",
    "name": "Nguyễn Văn Tùng",
    "email": "tung@smartparking.local",
    "role": "staff",
    "status": "active",
    "temporaryPassword": "Staff@123"
  }
}
```

Không bao giờ trả `passwordHash` hoặc password cũ trong user list.

### Zone Management

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/admin/zones` | Tạo phân khu |
| PUT | `/api/v1/admin/zones/{id}` | Cập nhật phân khu |
| DELETE | `/api/v1/admin/zones/{id}` | Xóa phân khu |

Create zone request:

```json
{
  "floorId": "uuid",
  "vehicleTypeId": "uuid",
  "zoneCode": "B1-A",
  "zoneName": "Khu A tầng B1",
  "capacity": 50,
  "status": "ACTIVE"
}
```

Rule:

```text
zoneCode tối đa 10 ký tự.
Không dùng slot, chỉ dùng Zone capacity.
```

### Gate Management

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/admin/gates` | Tạo cổng/làn |
| PUT | `/api/v1/admin/gates/{id}` | Cập nhật cổng/làn |
| DELETE | `/api/v1/admin/gates/{id}` | Xóa cổng/làn |
| PUT | `/api/v1/admin/gates/{id}/barrier` | Điều khiển barrier |

Gate type:

```text
MAIN_ENTRY
MAIN_EXIT
MAIN_BOTH
ZONE_ENTRY
ZONE_EXIT
ZONE_BOTH
```

### Pricing / Settings / Payments

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/admin/pricing-rules` | Tạo bảng giá |
| PUT | `/api/v1/admin/pricing-rules/{id}` | Cập nhật bảng giá |
| DELETE | `/api/v1/admin/pricing-rules/{id}` | Xóa bảng giá |
| GET | `/api/v1/admin/settings` | Lấy settings hệ thống |
| PUT | `/api/v1/admin/settings` | Cập nhật settings hệ thống |
| GET | `/api/v1/admin/payments` | Xem danh sách thanh toán |

## 5. Driver APIs - Quảng

### License Plates

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/driver/plates` | Lấy biển số của driver |
| POST | `/api/v1/driver/plates` | Thêm biển số |
| DELETE | `/api/v1/driver/plates?plate={plate}` | Xóa biển số |

### Sessions

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/driver/sessions/active?plate={plate}` | Xem session đang gửi |
| GET | `/api/v1/driver/sessions/history?plate={plate}` | Lịch sử gửi xe |
| POST | `/api/v1/driver/sessions/checkout/vnpay` | Driver checkout thanh toán VNPay |

### Reservations

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/driver/reservations` | Danh sách đặt chỗ |
| POST | `/api/v1/driver/reservations` | Tạo đặt chỗ |
| DELETE | `/api/v1/driver/reservations/{id}` | Hủy đặt chỗ |

### Parking Pass / Payment

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/driver/pricing-plans` | Xem gói giá |
| GET | `/api/v1/driver/parking-passes` | Xem pass của driver |
| POST | `/api/v1/driver/parking-passes` | Mua pass tháng/quý/năm |
| POST | `/api/v1/driver/parking-passes/{id}/pay` | Thanh toán tiếp pass pending |
| GET | `/api/v1/driver/payments/vnpay-return` | VNPay return callback |
| GET | `/api/v1/driver/payments/vnpay-ipn` | VNPay IPN callback |

## 6. Staff APIs - Tùng

### Session Operations

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/staff/sessions/checkin` | Check-in xe vào bãi |
| POST | `/api/v1/staff/sessions/checkout` | Check-out xe ra bãi |
| GET | `/api/v1/staff/sessions/history` | Xem lịch sử phiên gửi xe |
| GET | `/api/v1/staff/sessions/active` | Đề xuất: tìm session active theo plate/sessionCode |

Check-in request:

```json
{
  "licensePlate": "30A-12345",
  "vehicleTypeId": "uuid",
  "gateEntryId": "uuid",
  "reservationCode": "optional",
  "driverType": "WALK_IN"
}
```

Check-in response cần có:

```json
{
  "sessionId": "uuid",
  "sessionCode": "PS20260610-A3F",
  "licensePlate": "30A-12345",
  "zoneCode": "B1-A",
  "floorName": "B1",
  "guideMessage": "Vui lòng đến Tầng B1 - Khu A"
}
```

### Staff dependencies

Staff check-in có thể cần gọi Security module:

```java
blacklistService.ensurePlateNotBlacklisted(licensePlate);
emergencyService.ensureNormalOperation();
```

Rule:

```text
Thiên cung cấp Security service.
Tùng tích hợp service đó vào Staff check-in/check-out.
Không copy logic blacklist/emergency sang Staff.
```

## 7. Manager APIs - Toàn

Hiện Swagger cần bổ sung module Manager riêng.

### Dashboard / Reports

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/manager/dashboard` | Tổng quan vận hành |
| GET | `/api/v1/manager/reports/revenue` | Báo cáo doanh thu theo thời gian |
| GET | `/api/v1/manager/reports/occupancy` | Báo cáo công suất zone/floor |
| GET | `/api/v1/manager/reports/sessions` | Báo cáo lượt gửi xe |
| GET | `/api/v1/manager/reports/payments` | Tổng hợp thanh toán |
| GET | `/api/v1/manager/security/incidents-summary` | Tổng hợp sự cố an ninh |

Revenue query example:

```http
GET /api/v1/manager/reports/revenue?from=2026-06-01&to=2026-06-30&groupBy=day
```

Dashboard response đề xuất:

```json
{
  "todayRevenue": 1500000,
  "activeSessions": 42,
  "occupancyPercent": 76.5,
  "completedSessionsToday": 128,
  "securityIncidentsToday": 3,
  "activeEmergency": false
}
```

## 8. Security APIs - Thiên

### Exceptions

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/security/exceptions` | Danh sách sự cố |
| POST | `/api/v1/security/exceptions` | Ghi nhận sự cố |

Create exception request:

```json
{
  "sessionId": "uuid-optional",
  "licensePlate": "30A-12345",
  "exceptionType": "LOST_TICKET",
  "description": "Driver báo mất vé QR"
}
```

### Blacklist

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/security/blacklist` | Danh sách blacklist |
| POST | `/api/v1/security/blacklist` | Thêm biển số vào blacklist |
| DELETE | `/api/v1/security/blacklist/{id}` | Gỡ blacklist |

Blacklist request:

```json
{
  "licensePlate": "30A-12345",
  "reason": "SECURITY_RISK",
  "description": "Xe có hành vi bất thường"
}
```

### Emergency / SOS

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/security/emergency/activate` | Kích hoạt SOS |
| POST | `/api/v1/security/emergency/deactivate` | Hủy SOS |
| GET | `/api/v1/security/emergency/status` | Trạng thái SOS cho security |
| GET | `/api/v1/security/emergency/history` | Lịch sử SOS |
| GET | `/api/v1/security/emergency/settings` | Xem settings SOS |
| PUT | `/api/v1/security/emergency/settings` | Cập nhật settings SOS |
| GET | `/api/v1/emergency/status` | Public status cho FE/IoT/barrier |

Rule:

```text
Public emergency endpoint phải được permit trong SecurityConfig.
Nếu endpoint thật là /api/v1/public/emergency/status thì phải thống nhất lại với Swagger/code.
```

## 9. Cross-role Dependency Rules

Security là cross-cutting, có thể ảnh hưởng Staff/Driver/Manager/Admin. Tuy nhiên PR phải giải thích rõ file ngoài role được sửa vì rule nào.

Ví dụ hợp lệ:

```text
ParkingSessionService.java: Staff check-in gọi BlacklistService để chặn xe blacklist.
ReservationService.java: Driver đặt chỗ bị chặn nếu biển số nằm trong blacklist.
ManagerReportService.java: Manager lấy summary sự cố từ ExceptionLog.
```

Ví dụ cần hỏi lại:

```text
Security PR sửa PaymentController/VnPayService/PricingService nhưng không giải thích lý do.
Staff PR sửa DriverController/AdminManagementController nhưng không liên quan check-in.
```

## 10. Pull Request Rules

Mỗi chức năng phải làm trên branch riêng:

```text
feature/driver-...
feature/staff-...
feature/manager-...
feature/security-...
feature/admin-...
```

Workflow:

```text
checkout main -> pull latest -> create feature branch -> code -> commit -> push branch -> create Pull Request -> leader review -> merge
```

Không push trực tiếp vào `main` trừ thay đổi cấu hình nhỏ do leader quyết định.

### PR description template

```md
## What changed?
- 

## Role scope
- Owner role: Driver / Staff / Manager / Security / Admin

## API endpoints
- 

## Dependencies
- Depends on PR #...
- Uses service from role ...

## How to test?
- 

## Checklist
- [ ] Build/test local pass
- [ ] No target/, .class, node_modules, dist, .env
- [ ] No unrelated role files changed without explanation
- [ ] Swagger shows endpoints correctly
- [ ] Security roles are correct
```

## 11. Current Swagger Gap

As checked from local Swagger, current API coverage is:

```text
Admin    : good
Driver   : good
Staff    : core only
Security : good
Manager  : missing dedicated /api/v1/manager/* APIs
```

Priority for team planning:

```text
Toàn should implement ManagerController + ManagerReportService next.
```
