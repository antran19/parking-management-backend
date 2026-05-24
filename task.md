# SmartParking v2 — Phân công nhiệm vụ nhóm

> Cập nhật: 24/05/2026 — Sau khi dọn DB, thêm ParkingPass, sửa Gate, xóa Slot

---

## 1. Trạng thái hiện tại (đã hoàn thành ✅)

### Backend — ĐÃ CÓ
- [x] 5 roles: ADMIN, MANAGER, STAFF, DRIVER, SECURITY
- [x] DataInitializer: 5 user mẫu + 5 cổng (2 main + 3 zone) + 6 zone + 3 tầng
- [x] Entity: User, Building, Floor, Zone, Gate, ParkingSession, ParkingPass, Payment, Reservation, PricingRule, VehicleType, ExceptionLog
- [x] ParkingSession → quản lý theo `zone_id` (đã xóa `slot_id`)
- [x] Gate → phân biệt `MAIN_ENTRY/MAIN_EXIT/ZONE_BOTH`
- [x] MonthlyPass → đổi tên `ParkingPass` (tháng/quý/năm)
- [x] Check-in/Check-out service (zone-based)
- [x] ZoneSuggestionService (gợi ý khu)
- [x] PricingService (tính phí)
- [x] JWT Auth + Login/Register
- [x] WebSocket broadcast zone changes

### Frontend — ĐÃ CÓ
- [x] Login page (slideshow + animation)
- [x] Staff: Dashboard, CheckIn, CheckOut, ParkingMap
- [x] Driver: Dashboard, ParkingMap
- [x] Admin: Dashboard (cơ bản)
- [x] Manager: Dashboard (placeholder)
- [x] DashboardLayout (sidebar + routing)

### Đã xóa sạch
- [x] Slot.java, SlotRepository, SlotAssignmentService, SlotMapService, SlotMapResponse, SlotController
- [x] MonthlyPass.java → ParkingPass.java

---

## 2. Nguyên tắc làm việc

1. Không dùng khái niệm `slot` trong code mới
2. Dùng: `Zone`, `Gate (MAIN/ZONE)`, `ParkingSession`, `ParkingPass`, `QR`
3. Driver vãng lai **không cần account**
4. Account Driver dùng cho: đặt chỗ, vé tháng/quý/năm, lịch sử
5. Camera/OCR giai đoạn đầu là **giả lập** (nhập biển số thủ công)
6. Push branch riêng, **không push thẳng main/develop**
7. Không commit `node_modules`, `target`, `.env`

---

## 3. Phân công theo thành viên

---

### 3.1 MINH AN — Leader / Backend Core

**Vai trò:** Kiến trúc, review code, integration, database

#### Đã hoàn thành
- [x] Database schema v2 (zone-based)
- [x] Xóa Slot, đổi MonthlyPass → ParkingPass
- [x] Sửa Gate (MAIN/ZONE)
- [x] ParkingSessionService (check-in/out theo zone)
- [x] DataInitializer (5 role, 5 cổng, 6 zone)

#### Cần làm tiếp
- [ ] Review PR của thành viên
- [ ] Tích hợp FE + BE (test luồng check-in/out end-to-end)
- [ ] Viết `SMARTPARKING_V2_REQUIREMENTS.md` bản chính thức
- [ ] Hỗ trợ member khi bị stuck
- [ ] Chuẩn bị demo + báo cáo cuối

---

### 3.2 DUY TÙNG — Backend Auth / Driver / Payment

**Vai trò:** Bảo mật, Driver features, thanh toán

#### Cần làm

**Auth (ưu tiên cao):**
- [ ] Kiểm tra JWT trả đúng 5 role
- [ ] API đổi mật khẩu
- [ ] API xem/sửa thông tin cá nhân Driver

**Driver Booking (ưu tiên cao):**
- [ ] API `POST /api/v1/driver/reservations` — đặt chỗ theo zone
- [ ] API `GET /api/v1/driver/reservations` — xem đặt chỗ của mình
- [ ] API `DELETE /api/v1/driver/reservations/{id}` — hủy đặt chỗ
- [ ] Khi check-in, check reservation → driverType = `PRE_BOOKED`

**ParkingPass — Vé gửi xe (ưu tiên TB):**
- [ ] API `POST /api/v1/driver/passes` — mua vé (MONTHLY/QUARTERLY/YEARLY)
- [ ] API `GET /api/v1/driver/passes` — xem vé đang active
- [ ] Khi check-in, check pass theo biển số → driverType = `SUBSCRIBER`

**Payment (ưu tiên TB):**
- [ ] API thanh toán giả lập: CASH / BANK_TRANSFER
- [ ] Tạo Payment record khi check-out hoặc mua vé

#### Kết quả cần có
- Driver đăng ký/login ✅, đặt chỗ, mua vé
- Check-in nhận biết: vãng lai / đặt trước / có vé

---

### 3.3 KHẮC TOÀN — Backend Redis / Zone API / Report

**Vai trò:** Redis, zone counter, thống kê, báo cáo Manager

#### Cần làm

**Redis Zone Counter (ưu tiên cao):**
- [ ] Service `RedisZoneCounterService`:
  - `INCR zone:count:{zoneId}` — xe vào
  - `DECR zone:count:{zoneId}` — xe ra
  - `GET zone:count:{zoneId}` — check còn chỗ
- [ ] Sync Redis ← DB khi app start
- [ ] Distributed lock khi zone gần đầy:
  - `SETNX zone:lock:{zoneId} sessionId 30s`

**Redis Cache (ưu tiên TB):**
- [ ] Cache QR session: `SET session:qr:{code} {data} EX 86400`
- [ ] Cache monthly pass: `SET pass:{licensePlate} {data} EX 2592000`
- [ ] Lookup cache trước DB khi quét QR

**API Zone (ưu tiên cao):**
- [ ] `GET /api/v1/zones?buildingId=` — danh sách zone + còn bao nhiêu chỗ
- [ ] `GET /api/v1/zones/{id}` — chi tiết 1 zone

**API Report cho Manager (ưu tiên TB):**
- [ ] `GET /api/v1/manager/dashboard` — tổng quan:
  - Tổng xe đang trong bãi
  - Số lượt vào/ra hôm nay
  - Doanh thu hôm nay
  - Zone nào gần đầy
- [ ] `GET /api/v1/manager/reports/revenue?from=&to=` — doanh thu theo khoảng

#### Kết quả cần có
- Redis zone counter hoạt động
- Manager xem dashboard được
- Zone API cho FE hiển thị sơ đồ

---

### 3.4 TÁ THIÊN — Frontend Staff / Security

**Vai trò:** Giao diện Staff + Security

#### Đã có (cần sửa lại cho đúng flow v2)
- [x] StaffDashboard.jsx
- [x] StaffCheckIn.jsx
- [x] StaffCheckOut.jsx
- [x] StaffMapping.jsx

#### Cần làm

**Staff Check-in v2 (ưu tiên cao):**
- [ ] Form: nhập biển số (hoặc giả lập camera OCR)
- [ ] Chọn loại xe (xe máy / ô tô)
- [ ] Hiển thị zone được gợi ý (gọi API)
- [ ] Khi confirm → tạo session → hiển thị QR code
- [ ] Phân biệt 3 loại driver khi check-in:
  - Vãng lai → nhập biển số, scan mới
  - Đặt trước → nhập mã reservation
  - Có vé → scan biển số, tự nhận diện

**Staff Check-out v2 (ưu tiên cao):**
- [ ] Quét QR hoặc nhập biển số
- [ ] Hiện: thời gian gửi, loại xe, zone, giá tiền
- [ ] Chọn phương thức thanh toán (tiền mặt / chuyển khoản)
- [ ] Confirm → session completed

**Security UI (ưu tiên TB):**
- [ ] Trang Security Dashboard (danh sách sự cố)
- [ ] Form báo cáo sự cố mới (mất QR, sai biển số, xe quá hạn)
- [ ] Nút "Mở cổng thủ công"
- [ ] Bảng log xe ra/vào gần đây

#### Kết quả cần có
- Staff check-in/out chạy đúng flow 2 cổng
- Security có UI xử lý ngoại lệ cơ bản

---

### 3.5 NGỌC QUẢNG — Frontend Driver / Admin / QA

**Vai trò:** Driver UI, Admin UI, kiểm thử

#### Đã có (cần sửa/nâng cấp)
- [x] DriverDashboard.jsx
- [x] DriverMapping.jsx
- [x] AdminDashboard.jsx

#### Cần làm

**Driver UI (ưu tiên cao):**
- [ ] Trang xem zone còn chỗ (gọi API zones)
- [ ] Trang đặt chỗ trước (chọn zone + loại xe + thời gian)
- [ ] Trang mua vé tháng/quý/năm (chọn biển số + loại xe + gói)
- [ ] Trang lịch sử gửi xe (danh sách session cũ)
- [ ] Trang xem vé đang active

**Admin UI (ưu tiên TB):**
- [ ] Trang quản lý user (CRUD: thêm/sửa/xóa/khóa tài khoản)
- [ ] Trang phân quyền (đổi role)
- [ ] Trang quản lý zone/tầng (xem capacity, sửa)

**Manager UI (ưu tiên TB):**
- [ ] Trang dashboard thống kê (gọi API report)
- [ ] Biểu đồ doanh thu, lượt xe

**QA (ưu tiên thấp — cuối sprint):**
- [ ] Test case 5 luồng chính:
  1. Staff check-in xe vãng lai
  2. Staff check-out xe vãng lai
  3. Driver đặt chỗ trước
  4. Driver mua vé tháng
  5. Security xử lý mất QR
- [ ] Ghi bug list + gửi nhóm

#### Kết quả cần có
- Driver có UI đặt chỗ, mua vé, lịch sử
- Admin quản lý user được
- Có test case + bug list

---

## 4. Lịch trình MVP

| Phase | Mục tiêu | Thời gian |
|-------|---------|-----------|
| ✅ Phase 1 | Schema v2, dọn code cũ | Đã xong |
| Phase 2 | Backend: Redis, Reservation, Pass API | Tuần này |
| Phase 3 | Frontend: Staff check-in/out v2, Driver booking | Tuần này |
| Phase 4 | Tích hợp FE + BE, test luồng chính | Tuần sau |
| Phase 5 | Security UI, Admin UI, Manager report | Tuần sau |
| Phase 6 | Fix bug, polish UI, chuẩn bị demo | Trước demo |

---

## 5. MVP bắt buộc để demo

### Phải có:
- [x] Login 5 role
- [ ] Staff check-in xe vãng lai (cổng chính → cổng tầng)
- [ ] Hệ thống gợi ý zone còn chỗ
- [ ] Sinh QR cho session
- [ ] Staff check-out (cổng tầng → cổng chính → thanh toán)
- [ ] Tính phí tự động
- [ ] Redis zone counter tăng/giảm
- [ ] Driver đặt chỗ trước
- [ ] Driver mua vé tháng
- [ ] Security xử lý mất QR cơ bản
- [ ] Manager xem zone capacity

### Chưa bắt buộc MVP:
- [ ] OCR camera thật
- [ ] VNPay/Momo thật
- [ ] AI nhận diện loại xe từ ảnh
- [ ] Auto barrier thật
- [ ] Dashboard biểu đồ nâng cao

---

## 6. FAQ cho team

**Q: Có còn dùng Slot không?**
A: Không. Đã xóa hết. V2 quản lý theo `Zone/Floor`.

**Q: Staff và Security khác gì?**
A: Staff = luồng bình thường (check-in/out). Security = ngoại lệ (mất QR, sai biển, mở cổng thủ công).

**Q: Driver vãng lai có cần account không?**
A: Không. Chỉ cần biển số. Account Driver dùng cho đặt chỗ, vé, lịch sử.

**Q: MonthlyPass đổi tên gì?**
A: `ParkingPass` — bao gồm MONTHLY/QUARTERLY/YEARLY.

**Q: Gate có mấy loại?**
A: 6 loại: `MAIN_ENTRY, MAIN_EXIT, MAIN_BOTH, ZONE_ENTRY, ZONE_EXIT, ZONE_BOTH`.

**Q: Redis dùng làm gì?**
A: Zone counter (đếm xe), distributed lock (chống race condition), QR cache (lookup nhanh), pass cache (check vé tháng nhanh).

---

## 7. Quy tắc Git/PR

- Không push thẳng vào `main` / `develop`
- Mỗi người tạo branch: `feature/tên-chức-năng`
- PR ghi rõ: làm gì, test thế nào, ảnh UI nếu có
- Leader (An) review trước khi merge
- Không commit: `node_modules/`, `target/`, `.env`
