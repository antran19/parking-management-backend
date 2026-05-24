# SmartParking — Phân công Nhiệm vụ

> Cập nhật: 18/05/2026 | Hiện tại: **TUẦN 1** (của 9 tuần)

---

## 📌 TÌNH HÌNH HIỆN TẠI

Minh An (Leader) đã code xong phần **lõi backend**: đăng nhập, xe vào bãi, xe ra bãi, thuật toán tìm chỗ đỗ tốt nhất, tính phí tự động. Code đã push lên GitHub branch `develop`.

**Từ tuần 2, mỗi người bắt đầu làm phần của mình.**

---

## 👥 AI LÀM GÌ?

---

### 1. MINH AN (Leader)

**Đã xong:** Toàn bộ phần lõi backend (đăng nhập, check-in/out, tính phí, tìm slot).

**Tuần 2 làm gì:**
- Tạo dữ liệu mẫu trong database (tòa nhà, tầng, slot, user mẫu) để cả nhóm test
- Hướng dẫn từng người hiểu code
- Review code khi mọi người gửi Pull Request

**Từ tuần 3:** Hỗ trợ nhóm, sửa bug, kiểm tra chất lượng code.

---

### 2. DUY TÙNG (Backend)

**Phần bạn phụ trách:** Bảo mật + Đặt chỗ trước + Thanh toán

**Tuần 2 — Việc đầu tiên (dễ, làm quen):**
- [ ] Clone repo về máy, chạy project theo hướng dẫn trong README.md
- [ ] Đọc hiểu 3 file bảo mật mà Leader đã viết (hỏi Leader nếu không hiểu)
- [ ] Tạo chức năng **Đăng ký tài khoản**: người dùng mới nhập email + mật khẩu → hệ thống tạo tài khoản

**Tuần 3:**
- [ ] Tạo chức năng **Đặt chỗ trước**: Driver chọn slot → giữ chỗ 30 phút → nếu không đến thì tự hủy

**Tuần 4–5:**
- [ ] Tạo chức năng **Xử lý sự cố**: Staff ghi nhận khi xe mất thẻ, đậu sai chỗ, biển số không khớp

**Tuần 6–7:**
- [ ] Tạo chức năng **Thanh toán**: hiển thị QR code cho tài xế quét trả tiền

---

### 3. KHẮC TOÀN (Backend)

**Phần bạn phụ trách:** Database + Báo cáo + Quản trị

**Tuần 2 — Việc đầu tiên (dễ, làm quen):**
- [ ] Clone repo về máy, chạy project theo hướng dẫn trong README.md
- [ ] Mở pgAdmin (phần mềm xem database) → kết nối vào PostgreSQL → xem các bảng đã tạo
- [ ] Đọc 13 file Entity (trong thư mục `entity/`) → **vẽ lại sơ đồ quan hệ các bảng** (ERD) bằng draw.io hoặc vẽ tay
- [ ] Viết file SQL tạo dữ liệu mẫu (1 tòa nhà, 3 tầng, 20 slot, vài user)

**Tuần 3:**
- [ ] Viết thêm các câu truy vấn (query): đếm số xe theo ngày, tổng doanh thu, tỷ lệ slot đầy

**Tuần 4–5:**
- [ ] Tạo chức năng **Báo cáo cho Manager**: xem doanh thu ngày/tháng, giờ cao điểm, loại xe phổ biến

**Tuần 6–7:**
- [ ] Tạo chức năng **Quản trị**: Admin thêm/sửa/xóa tài khoản nhân viên, thay đổi bảng giá

---

### 4. TÁ THIÊN (Frontend)

**Phần bạn phụ trách:** Giao diện web chính (React)

**Tuần 2 — Việc đầu tiên:**
- [ ] Clone repo về máy, đọc file `docs/API_SPEC.md` để hiểu Backend trả về gì
- [ ] Tạo project React mới (dùng Vite) trong thư mục `frontend/`
- [ ] Tạo layout cơ bản: thanh menu bên trái + header trên cùng
- [ ] Tạo file gọi API: mỗi lần gọi Backend đều tự đính kèm token đăng nhập

**Tuần 3:**
- [ ] Làm **trang Đăng nhập**: ô nhập email + mật khẩu → bấm Login → lưu token → chuyển trang

**Tuần 4–5:**
- [ ] Làm **trang Sơ đồ bãi xe**: hiển thị lưới slot (xanh = trống, đỏ = có xe), tự cập nhật khi có xe vào/ra
- [ ] Làm **trang Check-in**: Staff nhập biển số → bấm Check-in → hiển thị "Đến Tầng B1 - Ô B1-A01"
- [ ] Làm **trang Check-out**: Staff nhập biển số → hiển thị phí → bấm Xác nhận

**Tuần 6–7:**
- [ ] Làm đẹp giao diện, thêm hiệu ứng, responsive (xem được trên điện thoại)

---

### 5. NGỌC QUẢNG (Frontend + QA)

**Phần bạn phụ trách:** Giao diện cho Driver + Dashboard Manager + Kiểm thử

**Tuần 2 — Việc đầu tiên:**
- [ ] Clone repo về máy, đọc file `SRS_ParkingSystem.md` để hiểu hệ thống
- [ ] Mở Figma → vẽ phác thảo giao diện 5 màn hình: Đăng nhập, Sơ đồ bãi xe, Check-in, Dashboard, Trang Driver
- [ ] Liệt kê tất cả trang web cần làm → gửi nhóm review

**Tuần 3:**
- [ ] Làm **trang xem bãi xe (công khai)**: ai cũng vào xem được, hiển thị còn bao nhiêu slot trống
- [ ] Làm **trang Đăng ký**: Driver đăng ký tài khoản mới

**Tuần 4–5:**
- [ ] Làm **trang Đặt chỗ trước**: Driver xem slot trống → chọn slot → giữ chỗ
- [ ] Làm **trang Phiên gửi xe**: Driver xem xe đang đỗ ở đâu, bao lâu, phí tạm tính

**Tuần 6–7:**
- [ ] Làm **Dashboard cho Manager**: biểu đồ doanh thu, thống kê lượt xe
- [ ] Làm **trang Thanh toán QR**: hiển thị mã QR để tài xế quét

**Tuần 8:**
- [ ] Kiểm thử toàn bộ hệ thống, viết danh sách lỗi, test lại sau khi sửa

---

## 📅 LỊCH TRÌNH 9 TUẦN

| Tuần | Mục tiêu | Ai làm gì |
|------|---------|-----------|
| **1** ✅ | Backend lõi xong | Minh An đã xong |
| **2** | Cả nhóm bắt đầu | Tùng: đăng ký, Toàn: ERD + data, Thiên: setup React, Quảng: Figma |
| **3** (Chấm lần 1) | Demo được: login + check-in + check-out | Thiên: trang Login, Quảng: trang xem bãi xe |
| **4–5** | Làm thêm tính năng | Sơ đồ bãi xe, đặt chỗ, báo cáo, xử lý sự cố |
| **6–7** (Chấm lần 2) | Tích hợp FE + BE | Dashboard, thanh toán, quản trị, làm đẹp UI |
| **8** | Hoàn thiện | Test, sửa bug, tối ưu |
| **9** (Chấm cuối) | Bảo vệ | Cả nhóm demo + trả lời câu hỏi |

---

## 🚀 BẮT ĐẦU NHƯ THẾ NÀO?

**Tất cả mọi người, tuần 2 làm 3 bước này trước:**

```
Bước 1: Cài Git, cài Docker Desktop
Bước 2: Mở Terminal, gõ:
         git clone https://github.com/antran19/parking-management-backend.git
         cd parking-management-backend
         git checkout develop
Bước 3: Đọc file README.md → làm theo hướng dẫn chạy project
```

**Nếu bị lỗi hoặc không hiểu → hỏi Minh An.**

---

## ❓ CÂU HỎI THƯỜNG GẶP

**Q: Tôi chưa biết Spring Boot / React, làm sao code?**
A: Đọc file code Leader đã viết + Google + hỏi Leader. Không ai biết hết từ đầu.

**Q: Backend và Frontend khác nhau chỗ nào?**
A: Backend = code Java xử lý logic + database (chạy ngầm, không có giao diện). Frontend = code React tạo giao diện web (nút bấm, form, bảng).

**Q: Tại sao tôi phải clone repo Backend trong khi tôi làm Frontend?**
A: Vì bạn cần chạy Backend trên máy để test. Frontend gọi API vào Backend → nếu Backend không chạy → Frontend không hoạt động.

**Q: Pull Request (PR) là gì?**
A: Khi code xong 1 chức năng → bạn push lên GitHub → tạo PR (yêu cầu merge) → Leader review → approve → code của bạn được gộp vào project chính.
