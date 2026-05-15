# TÀI LIỆU ĐẶC TẢ YÊU CẦU PHẦN MỀM (SRS)
## Parking Building Management System
**Mã đề tài:** SU26SWP08 | **Học kỳ:** Summer 2026 | **FPT University**

| Thông tin | Chi tiết |
|-----------|----------|
| Phiên bản | 1.0 |
| Ngày tạo | 15/05/2026 |
| Team Leader | Trần Nguyễn Minh An |

**Thành viên:**
1. Trần Nguyễn Minh An - Team Leader / Backend
2. Nguyễn Duy Tùng - Backend / Auth & API
3. Nguyễn Khắc Toàn - Backend / AI Slot Allocation
4. Bùi Ngọc Tá Thiên - Lead Frontend
5. Huỳnh Ngọc Quảng - Frontend / QA

---

## PHẦN 1: GIỚI THIỆU
*(Người viết: Minh An)*

### 1.1 Mục đích
Tài liệu mô tả yêu cầu chức năng và phi chức năng của hệ thống quản lý tòa nhà gửi xe. Hệ thống hỗ trợ quản lý xe ra/vào, phân bổ slot tự động bằng AI, thu phí và báo cáo vận hành.

### 1.2 Phạm vi
Hệ thống web hỗ trợ 4 nhóm người dùng:
- **Parking Manager:** Quản lý cấu hình, bảng giá, báo cáo
- **Parking Staff:** Xử lý xe vào/ra tại cổng
- **Parking Driver:** Xem thông tin, đặt chỗ, thanh toán online
- **System Admin:** Quản lý tài khoản, phân quyền

### 1.3 Định nghĩa
| Thuật ngữ | Giải thích |
|-----------|------------|
| Parking Session | Một lượt gửi xe (từ lúc vào đến lúc ra) |
| Monthly Pass | Vé tháng - gửi xe cố định theo tháng |
| Slot | Một chỗ đỗ xe cụ thể trong bãi |
| SM-2 | Không áp dụng cho đề tài này |
| AI Scoring | Thuật toán tính điểm để gợi ý slot tối ưu |

### 1.4 Công nghệ sử dụng
- **Backend:** Java 17 + Spring Boot 3.x, Spring Security (JWT + RBAC)
- **Database:** PostgreSQL 15
- **Frontend:** ReactJS 18 + Vite + Tailwind CSS
- **Payment:** VNPay / Momo (tích hợp sandbox)
- **DevOps:** Docker + Docker Compose + GitHub Actions

---

## PHẦN 2: MÔ TẢ TỔNG QUAN
*(Người viết: Minh An)*

### 2.1 Bối cảnh & Vấn đề
Tại các đô thị lớn, tòa nhà gửi xe nhiều tầng có lưu lượng xe ra vào liên tục. Quản lý thủ công dẫn đến:
- Ùn ứ tại cổng vì xử lý chậm
- Sai lệch dữ liệu slot trống/đang dùng
- Khó kiểm soát doanh thu và sức chứa
- Mất thời gian tìm chỗ đỗ phù hợp

### 2.2 Môi trường vận hành
- Trình duyệt: Chrome 110+, Firefox 110+, Edge 110+
- Nhiều cổng vào/ra dùng chung 1 hệ thống web
- Nhân viên mỗi cổng đăng nhập và chọn cổng đang trực

### 2.3 Giả định
- Nhân viên nhập biển số thủ công (không tích hợp camera OCR)
- File không quá 50MB (không áp dụng)
- Hệ thống thanh toán dùng sandbox (không giao dịch thật)

---

## PHẦN 3: YÊU CẦU CHỨC NĂNG
*(Người viết: Duy Tùng + Khắc Toàn)*

---

### 3.1 PARKING MANAGER

**[F1.1] Quản lý tòa nhà**
- Mô tả: Cấu hình thông tin tòa nhà gửi xe
- Input: Tên, địa chỉ, giờ hoạt động
- Output: Thông tin tòa nhà được lưu

**[F1.2] Quản lý tầng & Loại xe**
- Mô tả: Cấu hình từng tầng, gán loại xe được phép đỗ
- Input: Số tầng, tên tầng (B1/T1...), loại xe (Xe máy/Ô tô)
- Output: Cấu trúc tầng được thiết lập

**[F1.3] Quản lý Slot**
- Mô tả: Tạo và quản lý các chỗ đỗ xe
- Input: Mã slot (T1-A01), tầng, loại xe, trạng thái
- Trạng thái slot: AVAILABLE / OCCUPIED / RESERVED / MAINTENANCE / LOCKED
- Output: Sơ đồ slot hiển thị real-time

**[F1.4] Quản lý Cổng**
- Mô tả: Cấu hình các cổng vào/ra
- Input: Mã cổng, tên, loại (ENTRY/EXIT/BOTH)
- Output: Danh sách cổng hoạt động

**[F1.5] Quản lý Bảng giá**
- Mô tả: Thiết lập giá gửi xe theo loại xe và hình thức
- Loại giá: HOURLY (theo giờ) / DAILY (theo ngày) / MONTHLY (theo tháng)
- Input: Loại xe, loại giá, đơn giá, số phút miễn phí
- Output: Bảng giá áp dụng cho hệ thống tính phí

**[F1.6] Báo cáo & Thống kê**
- Mô tả: Xem báo cáo vận hành tòa nhà
- Output:
  - Lượt xe vào/ra theo ngày/tuần/tháng
  - Doanh thu theo loại xe, loại vé
  - Tỷ lệ lấp đầy từng tầng theo giờ
  - Khung giờ cao điểm
  - Top loại xe gửi nhiều nhất

**[F1.7] Quản lý ngoại lệ (Optional)**
- Mô tả: Theo dõi các trường hợp mất vé, sai biển số, quá hạn
- Output: Danh sách exception với trạng thái xử lý

---

### 3.2 PARKING STAFF

**[F2.1] Chọn cổng làm việc**
- Mô tả: Nhân viên chọn cổng đang trực khi bắt đầu ca
- Input: Danh sách cổng active
- Output: Session làm việc gắn với cổng cụ thể

**[F2.2] Xử lý xe VÀO**
- Mô tả: Tạo phiên gửi xe khi xe đến cổng
- Input: Biển số xe, loại xe
- Quy trình:
  1. Nhập biển số + chọn loại xe
  2. Hệ thống AI gợi ý slot tối ưu
  3. Nhân viên xác nhận slot
  4. Tạo parking session + in/hiển thị mã vé
  5. Cập nhật slot → OCCUPIED
- Output: Mã vé (session_code), slot được gán

**[F2.3] Xử lý xe RA**
- Mô tả: Kết thúc phiên gửi xe và thu phí
- Input: Mã vé hoặc biển số xe
- Quy trình:
  1. Tìm parking session đang ACTIVE
  2. Tính phí tự động theo thời gian thực
  3. Nhân viên xác nhận thu phí
  4. Cập nhật session → COMPLETED
  5. Giải phóng slot → AVAILABLE
- Output: Hóa đơn, tổng phí

**[F2.4] Xử lý ngoại lệ**
- Mô tả: Xử lý các tình huống bất thường
- Các loại ngoại lệ:
  - LOST_TICKET: Mất vé → nhân viên xác minh xe và thu phí phạt
  - WRONG_PLATE: Sai biển số → cập nhật lại
  - OVERTIME: Quá hạn vé tháng → thu phí thêm
  - WRONG_ZONE: Gửi sai khu vực → điều chỉnh slot
- Output: Exception log được tạo, slot được cập nhật

---

### 3.3 PARKING DRIVER (Người gửi xe)

**[F3.1] Đăng ký / Đăng nhập**
- Input: Email, mật khẩu, họ tên, số điện thoại
- Validation: Email hợp lệ, mật khẩu 8+ ký tự

**[F3.2] Xem thông tin bãi xe**
- Mô tả: Xem thông tin tòa nhà trước khi đến
- Output:
  - Giờ hoạt động
  - Bảng giá từng loại xe
  - Số slot trống real-time theo tầng
  - Sơ đồ tổng quan tòa nhà

**[F3.3] Đặt chỗ trước**
- Mô tả: Đặt slot trước khi đến bãi
- Input: Loại xe, biển số, thời gian đến, thời gian dự kiến ra
- Quy trình:
  1. Hệ thống gợi ý slot phù hợp còn trống
  2. User chọn slot và xác nhận
  3. Thanh toán đặt cọc online (VNPay/Momo)
  4. Nhận mã đặt chỗ
- Output: Reservation được tạo, slot → RESERVED

**[F3.4] Theo dõi phiên gửi xe**
- Mô tả: Xem thông tin lượt gửi xe đang diễn ra
- Output: Giờ vào, slot đang đỗ, phí tạm tính, thời gian đã gửi

**[F3.5] Thanh toán online**
- Mô tả: Thanh toán phí gửi xe qua cổng thanh toán    
- Phương thức: VNPay / Momo / QR Code
- Output: Biên lai thanh toán

**[F3.6] Mua vé tháng**
- Mô tả: Đăng ký gói vé tháng
- Input: Loại xe, biển số, tháng áp dụng
- Output: Monthly pass được kích hoạt sau thanh toán

**[F3.7] Xem lịch sử gửi xe**
- Output: Danh sách các lần gửi xe, phí, slot đã dùng

**[F3.8] Gửi phản hồi (Optional)**
- Mô tả: Báo cáo sự cố (mất vé, sai phí, slot bị chiếm)

---

### 3.4 SYSTEM ADMIN

**[F4.1] Quản lý tài khoản**
- Tạo / khóa / mở khóa tài khoản người dùng
- Phân quyền: ADMIN / MANAGER / STAFF / DRIVER

**[F4.2] Cấu hình hệ thống**
- Cấu hình các thông số toàn hệ thống

---

### 3.5 AI ENGINE — Smart Slot Allocation
*(Người viết: Khắc Toàn)*

**[F5.1] Gợi ý slot tối ưu**
- Mô tả: Tự động tính toán và gợi ý slot tốt nhất khi xe vào
- Input: Loại xe, thời điểm vào, cổng vào
- Thuật toán tính điểm (Scoring):

```
score = (tỷ lệ trống tầng)   × 0.40
      + (1 / khoảng cách)     × 0.40  
      + (bonus giờ thấp điểm) × 0.20

→ Chọn slot có score cao nhất
```

- Tiêu chí:
  - Ưu tiên tầng có tỷ lệ lấp đầy thấp → cân bằng tải
  - Ưu tiên slot gần cổng vào → giảm thời gian di chuyển
  - Giờ cao điểm → ưu tiên slot gần cổng ra
- Output: Top 3 slot được gợi ý kèm lý do

**[F5.2] Cảnh báo nguy cơ đầy bãi**
- Mô tả: Cảnh báo Manager khi tỷ lệ lấp đầy > 80%
- Output: Notification trên Dashboard Manager

**[F5.3] Phân tích giờ cao điểm**
- Mô tả: Phân tích lịch sử để xác định khung giờ cao điểm
- Output: Biểu đồ heat map theo giờ trong ngày

---

## PHẦN 4: YÊU CẦU PHI CHỨC NĂNG
*(Người viết: Minh An)*

### 4.1 Hiệu suất
- Phản hồi API CRUD: < 2 giây
- Tính phí gửi xe: < 1 giây
- AI gợi ý slot: < 3 giây
- Hỗ trợ tối thiểu 50 người dùng đồng thời

### 4.2 Bảo mật
- JWT Authentication (access token 1h, refresh 7 ngày)
- RBAC: 4 role riêng biệt
- BCrypt mã hóa mật khẩu (strength 12)
- HTTPS bắt buộc trên production

### 4.3 Độ tin cậy
- Dữ liệu slot real-time (không delay)
- Transaction DB để tránh double booking
- Uptime 95% trong thời gian demo

### 4.4 Khả năng bảo trì
- API document bằng Swagger/OpenAPI
- Log đầy đủ request/response/error
- Code theo Clean Architecture

---

## PHẦN 5: KIẾN TRÚC HỆ THỐNG & ERD
*(Người viết: Minh An)*

### 5.1 Kiến trúc tổng quan
```
[React Frontend]
      ↕ REST API / JWT
[Spring Boot Backend]
      ↕            ↕          ↕
[PostgreSQL]  [VNPay/Momo]  [Email Service]
```

### 5.2 ERD
*(Đính kèm ảnh xuất từ dbdiagram.io)*

### 5.3 Danh sách bảng DB (11 bảng)
| Bảng | Mô tả |
|------|-------|
| users | Tài khoản người dùng |
| vehicle_types | Loại phương tiện |
| buildings | Thông tin tòa nhà |
| floors | Các tầng |
| slots | Chỗ đỗ xe |
| gates | Cổng vào/ra |
| pricing_rules | Bảng giá |
| parking_sessions | Phiên gửi xe theo lượt |
| monthly_passes | Vé tháng |
| reservations | Đặt chỗ trước |
| payments | Thanh toán |
| exception_logs | Xử lý ngoại lệ |

---

## PHẦN 6: MÔ TẢ GIAO DIỆN
*(Người viết: Bùi Ngọc Tá Thiên)*

| Màn hình | Mô tả |
|----------|-------|
| SCR-01: Landing/Login | Trang đăng nhập, chọn vai trò |
| SCR-02: Manager Dashboard | Tổng quan: slot trống, doanh thu hôm nay, cảnh báo |
| SCR-03: Slot Map | Sơ đồ tầng real-time, màu sắc theo trạng thái slot |
| SCR-04: Staff - Xe Vào | Form nhập biển số, AI gợi ý slot, tạo phiên |
| SCR-05: Staff - Xe Ra | Tìm phiên, hiển thị phí, xác nhận thu tiền |
| SCR-06: Driver Portal | Xem bãi xe, đặt chỗ, theo dõi phiên, thanh toán |
| SCR-07: Báo cáo | Biểu đồ lượt xe, doanh thu, tỷ lệ lấp đầy |
| SCR-08: Admin Panel | Quản lý tài khoản, phân quyền |

*(TODO: Thiên bổ sung Figma mockup cho từng màn hình)*

---

## PHẦN 7: KẾ HOẠCH KIỂM THỬ
*(Người viết: Huỳnh Ngọc Quảng)*

| Mã TC | Chức năng | Input | Expected | Status |
|-------|-----------|-------|----------|--------|
| TC-01 | Đăng nhập Staff | Email + pass đúng | Vào được trang Staff | [ ] |
| TC-02 | Đăng nhập sai pass | Pass sai | Báo lỗi "Sai thông tin" | [ ] |
| TC-03 | Xe vào - slot hợp lệ | Biển số + loại xe | Tạo session, slot→OCCUPIED | [ ] |
| TC-04 | Xe vào - bãi đầy | Tất cả slot OCCUPIED | Thông báo không còn chỗ | [ ] |
| TC-05 | Xe ra - thanh toán | Mã vé hợp lệ | Tính phí đúng, slot→AVAILABLE | [ ] |
| TC-06 | Mua vé tháng | Thông tin xe + tháng | Monthly pass được tạo | [ ] |
| TC-07 | Đặt chỗ trước | Thời gian + loại xe | Slot→RESERVED, có mã đặt chỗ | [ ] |
| TC-08 | AI gợi ý slot | Loại xe + cổng vào | Top 3 slot kèm điểm số | [ ] |
| TC-09 | Xử lý mất vé | Exception type: LOST_TICKET | Log được tạo, thu phí phạt | [ ] |
| TC-10 | Báo cáo doanh thu | Chọn khoảng thời gian | Số liệu đúng, biểu đồ hiển thị | [ ] |

*(TODO: Quảng bổ sung thêm test cases)*

---

## PHỤ LỤC

### A. Link tài nguyên
- GitHub Backend: [link]
- GitHub Frontend: [link]
- Jira Board: [link]
- Figma Mockup: [link]
- ERD Diagram: [link]

### B. Lịch sử thay đổi
| Version | Ngày | Người thực hiện | Nội dung |
|---------|------|-----------------|----------|
| 1.0 | 15/05/2026 | Trần Nguyễn Minh An | Tạo tài liệu ban đầu |

---
*Parking Building Management System | SU26SWP08 | FPT University*
