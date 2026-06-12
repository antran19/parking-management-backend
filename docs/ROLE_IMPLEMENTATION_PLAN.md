# Role Implementation Plan - Smart Parking Team

Tài liệu này dùng để team biết rõ: mỗi thành viên phụ trách role nào, cần làm chức năng gì, nên tạo/sửa controller/service/dto/hàm nào, và khi chức năng liên quan role khác thì phối hợp ra sao.

## 1. Nguyên tắc làm việc chung

### 1.1. Mỗi thành viên làm full FE + BE theo role

| Thành viên | Role | Prefix API | Prefix branch gợi ý |
|---|---|---|---|
| Quảng | Driver | `/api/v1/driver` | `feature/driver-*` |
| Tùng | Staff | `/api/v1/staff` | `feature/staff-*` |
| Toàn | Manager | `/api/v1/manager` | `feature/manager-*` |
| Thiên | Security | `/api/v1/security` | `feature/security-*` |
| An | Admin | `/api/v1/admin` | `feature/admin-*` |

### 1.2. Không tự ý copy logic của role khác

Nếu role A cần dùng logic của role B:

```text
Role B cung cấp Service/API.
Role A gọi Service/API đó.
```

Ví dụ:

```text
Tùng làm Staff check-in cần kiểm tra blacklist.
Thiên cung cấp BlacklistService.
Tùng gọi blacklistService.ensurePlateNotBlacklisted(...) trong check-in.
```

### 1.3. File chung phải ghi rõ lý do trong PR

Các file chung dễ bị nhiều người sửa:

```text
ParkingSessionService.java
ReservationService.java
PricingService.java
PaymentConfirmationService.java
ZoneSuggestionService.java
SecurityConfig.java
DataInitializer.java
ParkingConfigController.java
```

Nếu sửa các file này, PR phải ghi:

```text
File sửa vì chức năng nào?
Liên quan role nào?
Có dependency với PR nào?
```

### 1.4. Không commit file generated/local

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

---

## 2. Quảng - Driver Role

### 2.1. Mục tiêu role Driver

Driver có thể:

```text
- Quản lý biển số cá nhân
- Xem phiên gửi xe đang hoạt động
- Xem lịch sử gửi xe
- Đặt chỗ trước
- Mua gói tháng/quý/năm
- Thanh toán online VNPay khi mua pass hoặc checkout
- Xem trạng thái emergency nếu hệ thống đang SOS
```

### 2.2. Backend files Quảng phụ trách

Nên tạo/sửa:

```text
controller/DriverController.java
controller/ReservationController.java
service/DriverService.java              nếu cần tách logic driver riêng
service/ReservationService.java         phần driver reservation
repository/UserLicensePlateRepository.java
repository/ReservationRepository.java
repository/ParkingPassRepository.java
dto/request/DriverPlateRequest.java     đề xuất tạo
dto/request/ReservationRequest.java
dto/request/ParkingPassPurchaseRequest.java đề xuất tạo
dto/response/DriverSessionResponse.java đề xuất tạo nếu cần
dto/response/ReservationResponse.java
dto/response/ParkingPassResponse.java   đề xuất tạo nếu cần
```

Có thể dùng chung nhưng không sở hữu hoàn toàn:

```text
ParkingSessionService.java              chỉ gọi để xem active/history session
PaymentController.java                  callback VNPay dùng chung
VnPayService.java                       service thanh toán dùng chung
EmergencyPublicController.java          đọc trạng thái SOS public
BlacklistService.java                   chỉ dùng nếu cần chặn biển số blacklist
```

### 2.3. API Driver cần có

#### License plates

```http
GET    /api/v1/driver/plates
POST   /api/v1/driver/plates
DELETE /api/v1/driver/plates?plate={plate}
```

Hàm nên có:

```java
getMyPlates(Authentication authentication)
addPlate(Authentication authentication, DriverPlateRequest request)
deletePlate(Authentication authentication, String plate)
normalizeAndValidatePlate(String plate)
```

#### Active session / history

```http
GET /api/v1/driver/sessions/active?plate={plate}
GET /api/v1/driver/sessions/history?plate={plate}
```

Hàm nên có:

```java
getActiveSession(String licensePlate)
getSessionHistory(String licensePlate)
```

Lưu ý: các hàm này có thể nằm trong `ParkingSessionService`, nhưng Quảng chỉ nên dùng/tích hợp, không rewrite core check-in/check-out của Staff.

#### Reservations

```http
GET    /api/v1/driver/reservations
POST   /api/v1/driver/reservations
DELETE /api/v1/driver/reservations/{reservationId}
```

Hàm nên có:

```java
createReservation(Authentication auth, ReservationRequest request)
getMyReservations(Authentication auth)
cancelReservation(Authentication auth, UUID reservationId)
validateReservationWindow(...)
ensurePlateBelongsToDriver(...)
ensureNotBlacklisted(...)
ensureNormalOperation(...)
```

Dependency:

```text
- Nếu xe blacklist: gọi BlacklistService của Thiên.
- Nếu SOS active: gọi EmergencyService của Thiên.
- Nếu cần zone availability: gọi ZoneSuggestionService/shared zone query.
```

#### Parking pass / VNPay

```http
GET  /api/v1/driver/pricing-plans
GET  /api/v1/driver/parking-passes
POST /api/v1/driver/parking-passes
POST /api/v1/driver/parking-passes/{passId}/pay
```

Hàm nên có:

```java
getPricingPlans()
getMyPasses(Authentication auth)
registerPass(Authentication auth, ParkingPassPurchaseRequest request, HttpServletRequest servletRequest)
continuePassPayment(Authentication auth, UUID passId, HttpServletRequest servletRequest)
createPendingPassPayment(...)
```

Dependency:

```text
- VnPayService dùng chung.
- PaymentRepository dùng chung.
- PricingRuleRepository đọc giá.
```

### 2.4. Frontend Driver Quảng phụ trách

Nên tạo/sửa:

```text
src/pages/driver/DriverDashboard.jsx
src/pages/driver/ProfileTab.jsx
src/pages/driver/PaymentReturnPage.jsx
src/pages/driver/DriverMapping.jsx
src/api/parkingApi.js                  phần driver methods
```

Chức năng FE:

```text
- Quản lý biển số
- Xem phiên hiện tại
- Xem QR/session code
- Đặt chỗ
- Mua pass
- Thanh toán VNPay
- Lịch sử gửi xe
- Hiển thị cảnh báo SOS nếu có
```

---

## 3. Tùng - Staff Role

### 3.1. Mục tiêu role Staff

Staff có thể:

```text
- Check-in xe vào bãi
- Nhận zone suggestion
- Check-out xe ra bãi
- Thu tiền cash/bank transfer/online status
- Xem lịch sử phiên gửi xe
- Nhận cảnh báo xe blacklist khi check-in
- Không cho thao tác nếu hệ thống đang SOS, nếu business rule yêu cầu
```

### 3.2. Backend files Tùng phụ trách

Nên tạo/sửa:

```text
controller/SessionController.java       phần /staff/sessions/*
service/ParkingSessionService.java      checkIn/checkOut staff flow
dto/request/CheckInRequest.java
dto/request/CheckOutRequest.java
dto/response/SessionResponse.java
repository/ParkingSessionRepository.java
```

Có thể dùng nhưng không sở hữu:

```text
BlacklistService.java                   Thiên cung cấp
EmergencyService.java                   Thiên cung cấp
ZoneSuggestionService.java              shared, có thể phối hợp Toàn/Admin
PricingService.java                     shared pricing
PaymentRepository.java                  shared payment
GateRepository.java
VehicleTypeRepository.java
```

### 3.3. API Staff cần có

#### Check-in

```http
POST /api/v1/staff/sessions/checkin
```

Request:

```json
{
  "licensePlate": "30A-12345",
  "vehicleTypeId": "uuid",
  "gateEntryId": "uuid",
  "reservationCode": "optional",
  "driverType": "WALK_IN"
}
```

Hàm nên có trong `ParkingSessionService`:

```java
checkIn(CheckInRequest request)
findVehicleType(UUID vehicleTypeId)
findEntryGate(UUID gateEntryId)
validateNoActiveSession(String licensePlate)
validateBlacklist(String licensePlate)
validateEmergencyStatus()
resolveReservationIfAny(...)
resolveDriverType(...)
generateSessionCode()
createParkingSession(...)
broadcastZoneChange(Zone zone)
buildSessionResponse(...)
```

Dependency rõ ràng:

```java
blacklistService.ensurePlateNotBlacklisted(request.getLicensePlate());
emergencyService.ensureNormalOperation();
zoneSuggestionService.suggestZone(vehicleType);
```

#### Check-out

```http
POST /api/v1/staff/sessions/checkout
```

Request:

```json
{
  "sessionId": "uuid optional",
  "sessionCode": "PS20260610-A3F optional",
  "licensePlate": "30A-12345 optional",
  "gateExitId": "uuid",
  "paymentMethod": "CASH"
}
```

Hàm nên có:

```java
checkOut(CheckOutRequest request)
findActiveSession(CheckOutRequest request)
calculateDurationMinutes(ParkingSession session, LocalDateTime exitTime)
calculateSessionFee(ParkingSession session, int durationMinutes)
createCompletedSessionPayment(...)
releaseZoneCapacity(...)
broadcastZoneChange(Zone zone)
```

#### History / lookup

```http
GET /api/v1/staff/sessions/history
GET /api/v1/staff/sessions/active?plate={plate}     đề xuất bổ sung nếu cần
```

Hàm nên có:

```java
getAllSessions()
getActiveSessionForStaff(String plateOrSessionCode)
```

### 3.4. Frontend Staff Tùng phụ trách

Nên tạo/sửa:

```text
src/pages/staff/StaffDashboard.jsx
src/pages/staff/StaffCheckIn.jsx
src/pages/staff/StaffCheckOut.jsx
src/pages/staff/ParkingMap.jsx
src/api/parkingApi.js                  phần staff methods
```

Chức năng FE:

```text
- Form check-in
- Form check-out
- Hiển thị zone suggestion
- Hiển thị tổng phí checkout
- Hiển thị cảnh báo blacklist/SOS
- Lịch sử session
```

---

## 4. Toàn - Manager Role

### 4.1. Mục tiêu role Manager

Manager có thể:

```text
- Xem dashboard tổng quan vận hành
- Xem doanh thu theo ngày/tháng/khoảng thời gian
- Xem công suất từng zone/floor/building
- Xem số lượt gửi xe
- Xem thống kê thanh toán
- Xem tổng hợp sự cố security
```

### 4.2. Backend files Toàn phụ trách

Nên tạo mới:

```text
controller/ManagerController.java
service/ManagerReportService.java
dto/response/ManagerDashboardResponse.java
dto/response/RevenueReportResponse.java
dto/response/OccupancyReportResponse.java
dto/response/SessionReportResponse.java
dto/response/PaymentReportResponse.java
```

Dùng repository sẵn có:

```text
ParkingSessionRepository.java
PaymentRepository.java
ZoneRepository.java
GateRepository.java
ExceptionLogRepository.java
ParkingPassRepository.java
```

### 4.3. API Manager cần có

#### Dashboard

```http
GET /api/v1/manager/dashboard
```

Hàm nên có:

```java
getDashboard(LocalDate date)
countActiveSessions()
calculateTodayRevenue()
calculateOccupancyPercent()
countCompletedSessionsToday()
countSecurityIncidentsToday()
getEmergencyStatusSummary()
```

Response đề xuất:

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

#### Revenue report

```http
GET /api/v1/manager/reports/revenue?from=2026-06-01&to=2026-06-30&groupBy=day
```

Hàm nên có:

```java
getRevenueReport(LocalDate from, LocalDate to, String groupBy)
getRevenueByDay(...)
getRevenueByMonth(...)
sumCompletedPayments(...)
```

#### Occupancy report

```http
GET /api/v1/manager/reports/occupancy
```

Hàm nên có:

```java
getOccupancyReport()
mapZoneOccupancy(Zone zone)
calculateZonePercent(Zone zone)
calculateBuildingOccupancy(...)
```

#### Session/payment/security summary

```http
GET /api/v1/manager/reports/sessions
GET /api/v1/manager/reports/payments
GET /api/v1/manager/security/incidents-summary
```

Hàm nên có:

```java
getSessionReport(...)
getPaymentReport(...)
getSecurityIncidentSummary(...)
```

### 4.4. Frontend Manager Toàn phụ trách

Nên tạo/sửa:

```text
src/pages/manager/ManagerDashboard.jsx
src/pages/manager/RevenueReport.jsx
src/pages/manager/OccupancyReport.jsx
src/pages/manager/SessionReport.jsx
src/api/parkingApi.js                  phần manager methods
```

Chức năng FE:

```text
- Dashboard cards
- Revenue chart
- Occupancy chart/table
- Session statistics
- Payment summary
- Security incident summary
```

---

## 5. Thiên - Security Role

### 5.1. Mục tiêu role Security

Security có thể:

```text
- Ghi nhận sự cố an ninh
- Xem danh sách sự cố
- Quản lý blacklist biển số
- Broadcast cảnh báo blacklist qua WebSocket
- Kích hoạt/hủy SOS
- Xem trạng thái/lịch sử SOS
- Cung cấp public emergency status cho frontend/IoT/barrier
```

### 5.2. Backend files Thiên phụ trách

Nên tạo/sửa:

```text
controller/SecurityController.java
controller/EmergencyPublicController.java
service/SecurityExceptionService.java
service/BlacklistService.java
service/EmergencyService.java
entity/ExceptionLog.java
entity/BlacklistPlate.java
entity/EmergencyEvent.java
repository/ExceptionLogRepository.java
repository/BlacklistPlateRepository.java
repository/EmergencyEventRepository.java
dto/request/SecurityExceptionRequest.java
dto/request/BlacklistPlateRequest.java
dto/request/BlacklistRemoveRequest.java
dto/request/EmergencyActivateRequest.java
dto/request/EmergencyDeactivateRequest.java
dto/response/BlacklistPlateResponse.java
dto/response/BlacklistAlertResponse.java
dto/response/EmergencyStatusResponse.java
```

Có thể ảnh hưởng nhưng cần giải thích rõ nếu sửa:

```text
ParkingSessionService.java              chặn check-in xe blacklist/SOS
ReservationService.java                 chặn đặt chỗ xe blacklist/SOS
SecurityConfig.java                     permit public emergency endpoint
```

### 5.3. API Security cần có

#### Exceptions

```http
GET  /api/v1/security/exceptions
POST /api/v1/security/exceptions
```

Hàm nên có:

```java
reportException(SecurityExceptionRequest request, Authentication auth)
getExceptions()
resolveException(UUID id, ...)
buildExceptionResponse(...)
```

#### Blacklist

```http
GET    /api/v1/security/blacklist
POST   /api/v1/security/blacklist
DELETE /api/v1/security/blacklist/{id}
```

Hàm nên có:

```java
getBlacklist()
addBlacklistPlate(BlacklistPlateRequest request, Authentication auth)
removeBlacklistPlate(UUID id, BlacklistRemoveRequest request, Authentication auth)
findActiveByPlate(String licensePlate)
ensurePlateNotBlacklisted(String licensePlate)
alertBlacklistAttempt(String licensePlate, BlacklistPlate blacklistPlate, Gate gate)
broadcastBlacklistUpdate(...)
```

Quan trọng cho Staff/Driver:

```java
ensurePlateNotBlacklisted(String licensePlate)
```

Đây là method để Tùng/Quảng gọi, không tự viết lại logic blacklist.

#### Emergency / SOS

```http
POST /api/v1/security/emergency/activate
POST /api/v1/security/emergency/deactivate
GET  /api/v1/security/emergency/status
GET  /api/v1/security/emergency/history
GET  /api/v1/security/emergency/settings
PUT  /api/v1/security/emergency/settings
GET  /api/v1/emergency/status
```

Hàm nên có:

```java
activateEmergency(EmergencyActivateRequest request, Authentication auth)
deactivateEmergency(EmergencyDeactivateRequest request, Authentication auth)
getEmergencyStatus()
getEmergencyHistory()
getEmergencySettings()
updateEmergencySettings(...)
ensureNormalOperation()
broadcastEmergencyStatus(...)
```

Quan trọng cho Staff/Driver:

```java
ensureNormalOperation()
```

Đây là method để Staff check-in/check-out hoặc Driver reservation gọi nếu business rule yêu cầu chặn khi SOS.

### 5.4. Frontend Security Thiên phụ trách

Nên tạo/sửa:

```text
src/pages/security/SecurityDashboard.jsx
src/pages/security/BlacklistPage.jsx
src/pages/security/EmergencyPage.jsx
src/pages/security/ExceptionLogsPage.jsx
src/components/EmergencyOverlay.jsx
src/api/parkingApi.js                  phần security methods
```

Chức năng FE:

```text
- Form ghi nhận sự cố
- Danh sách exception logs
- Thêm/xóa blacklist
- Nút bật/tắt SOS
- Lịch sử SOS
- Nhận WebSocket alert
```

---

## 6. An - Admin Role

### 6.1. Mục tiêu role Admin

Admin có thể:

```text
- Quản lý tài khoản và role
- Tạo/reset mật khẩu tạm
- Quản lý zone/floor/gate
- Quản lý pricing rules
- Quản lý parking pass
- Quản lý settings hệ thống
- Xem danh sách payments
```

### 6.2. Backend files An phụ trách

Nên tạo/sửa:

```text
controller/AdminManagementController.java
service/AdminService.java                 đề xuất tách nếu controller quá lớn
dto/request/AdminCreateUserRequest.java   đề xuất tạo
dto/request/AdminUpdateUserRequest.java   đề xuất tạo
dto/request/ResetPasswordRequest.java     đề xuất tạo
dto/request/ZoneRequest.java              đề xuất tạo
dto/request/GateRequest.java              đề xuất tạo
dto/request/PricingRuleRequest.java       đề xuất tạo
dto/response/AdminUserResponse.java       đề xuất tạo
repository/UserRepository.java
repository/ZoneRepository.java
repository/GateRepository.java
repository/PricingRuleRepository.java
repository/SystemSettingsRepository.java
```

### 6.3. API Admin cần có

#### Users

```http
GET    /api/v1/admin/users
POST   /api/v1/admin/users
PUT    /api/v1/admin/users/{id}
DELETE /api/v1/admin/users/{id}
POST   /api/v1/admin/users/{id}/reset-password
```

Hàm nên có:

```java
getUsers()
createUser(AdminCreateUserRequest request)
updateUser(UUID id, AdminUpdateUserRequest request)
deleteUser(UUID id)
resetUserPassword(UUID id, ResetPasswordRequest request)
generateTemporaryPassword()
userMap(User user)
```

Rule:

```text
Không show password cũ.
Chỉ show temporaryPassword lúc create/reset.
Không trả passwordHash.
```

#### Zones

```http
POST   /api/v1/admin/zones
PUT    /api/v1/admin/zones/{id}
DELETE /api/v1/admin/zones/{id}
```

Hàm nên có:

```java
createZone(ZoneRequest request)
updateZone(UUID id, ZoneRequest request)
deleteZone(UUID id)
validateZoneCodeMaxLength(...)
```

Rule:

```text
zoneCode tối đa 10 ký tự.
Zone thuộc Floor.
Zone theo VehicleType.
Không dùng Slot.
```

#### Gates / Pricing / Settings

```http
POST   /api/v1/admin/gates
PUT    /api/v1/admin/gates/{id}
DELETE /api/v1/admin/gates/{id}
PUT    /api/v1/admin/gates/{id}/barrier
POST   /api/v1/admin/pricing-rules
PUT    /api/v1/admin/pricing-rules/{id}
DELETE /api/v1/admin/pricing-rules/{id}
GET    /api/v1/admin/settings
PUT    /api/v1/admin/settings
GET    /api/v1/admin/payments
```

Hàm nên có:

```java
createGate(...)
updateGate(...)
deleteGate(...)
controlBarrier(...)
createPricingRule(...)
updatePricingRule(...)
deletePricingRule(...)
getSettings()
updateSettings(...)
getPayments()
```

### 6.4. Frontend Admin An phụ trách

Nên tạo/sửa:

```text
src/pages/admin/AdminDashboard.jsx
src/pages/admin/AdminMapping.jsx
src/api/parkingApi.js                  phần admin methods
```

Chức năng FE:

```text
- User management
- Temporary password create/reset
- Zone management, hiển thị tầng cụ thể
- Gate management
- Pricing management
- Settings
- Payment list
```

---

## 7. Cross-role Scenarios cụ thể

### 7.1. Staff check-in cần blacklist

Owner logic blacklist: Thiên.

Owner check-in flow: Tùng.

Cách làm:

```java
// Thiên cung cấp
blacklistService.ensurePlateNotBlacklisted(licensePlate);

// Tùng gọi trong ParkingSessionService.checkIn()
blacklistService.ensurePlateNotBlacklisted(request.getLicensePlate());
```

Không nên:

```text
Tùng tự query BlacklistRepository và tự viết blacklist rule riêng.
```

### 7.2. Staff/Driver cần biết SOS

Owner SOS: Thiên.

Tùng/Quảng gọi:

```java
emergencyService.ensureNormalOperation();
```

Hoặc FE gọi public endpoint:

```http
GET /api/v1/emergency/status
```

### 7.3. Manager cần security summary

Owner raw security data: Thiên.

Owner report aggregation: Toàn.

Cách làm:

```text
Thiên đảm bảo ExceptionLog/EmergencyEvent data đúng.
Toàn dùng repository/service để tổng hợp dashboard/report.
```

### 7.4. Admin cấu hình nhưng Security sử dụng

Owner settings UI/API: An.

Owner emergency behavior: Thiên.

Nếu setting ảnh hưởng SOS:

```text
An quản lý SystemSettings.
Thiên đọc SystemSettings trong EmergencyService.
```

---

## 8. Thứ tự implement đề xuất

### Phase 1 - Foundation

```text
1. Auth + User roles ổn định
2. Parking config: buildings, floors, zones, gates, vehicle types
3. Security foundation: blacklist + emergency service methods
4. Staff check-in/check-out core
```

### Phase 2 - Role features

```text
1. Driver reservations + parking pass
2. Security exception logs + blacklist UI
3. Admin CRUD zone/gate/pricing/user
4. Staff UI check-in/check-out
```

### Phase 3 - Manager/report

```text
1. Manager dashboard
2. Revenue report
3. Occupancy report
4. Security incident summary
```

### Phase 4 - Integration polish

```text
1. VNPay sandbox flow
2. WebSocket notifications
3. Swagger cleanup
4. DTO cleanup
5. End-to-end test by role
```

---

## 9. PR review checklist cho leader

Khi review PR, An kiểm tra:

```text
[ ] PR có đúng role owner không?
[ ] Có sửa file ngoài role không? Nếu có, đã giải thích chưa?
[ ] Có commit target/, .class, node_modules, dist, .env không?
[ ] API route có đúng namespace /api/v1/{role}/... không?
[ ] Response có dùng ApiResponse không?
[ ] Có @PreAuthorize đúng role không?
[ ] Có dùng DTO cho request quan trọng không?
[ ] Swagger có hiện endpoint không?
[ ] Build backend/frontend có pass không?
[ ] Có conflict với PR khác không?
```

---

## 10. Tóm tắt cho team

```text
Mỗi người làm role của mình end-to-end.
Role nào sở hữu service thì role đó định nghĩa logic.
Role khác cần dùng thì gọi service, không copy logic.
File chung phải giải thích rõ trong PR.
Manager hiện đang thiếu API riêng, cần ưu tiên bổ sung.
```
