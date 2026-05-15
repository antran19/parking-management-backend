# 📋 Hướng dẫn Onboarding — Tuần 1

> Đọc kỹ trước khi code bất cứ thứ gì!

---

## Tuần 1: Mỗi người làm ĐÚNG 1 việc

### Duy Tùng (Backend Security)
1. Clone repo → checkout branch `develop`
2. Cài Docker Desktop → chạy `docker-compose up -d`
3. Mở IntelliJ/VS Code → Run `SmartParkingApplication.java`
4. Mở Postman → GET `http://localhost:8080/actuator/health` → phải trả `{"status":"UP"}`
5. Đọc hiểu 3 file: `JwtUtil.java`, `JwtAuthFilter.java`, `SecurityConfig.java`
6. Ghi chú lại những gì chưa hiểu → hỏi Leader

### Khắc Toàn (Database)
1. Clone repo → checkout branch `develop`
2. Chạy `docker-compose up -d` → mở pgAdmin kết nối `localhost:5432` (user: postgres, pass: postgres, db: smartparking_db)
3. Chạy Spring Boot → xem các bảng được tạo tự động
4. Đọc toàn bộ 13 file trong `entity/`
5. **Vẽ lại ERD trên giấy hoặc draw.io** → nộp cho Leader trước cuối tuần

### Tá Thiên (Frontend)
1. Clone repo → checkout branch `develop`
2. Đọc file `docs/API_SPEC.md` — hiểu rõ API trả về gì
3. Vẽ wireframe tay cho 3 màn hình: Login, Parking Map, Check-in
4. Chưa cần code — tuần 2 mới bắt đầu setup Vite

### Ngọc Quảng (Frontend + QA)
1. Clone repo → checkout branch `develop`
2. Đọc file `SRS_ParkingSystem.md` — hiểu 26 Use Cases
3. Liệt kê tất cả màn hình cần làm (dự kiến ~10 trang)
4. Mở Figma → phác thảo UI flow (chưa cần chi tiết pixel)

---

## Tuần 2: Mỗi người code 1 file nhỏ

| Người | Task | File cần tạo |
|-------|------|-------------|
| Tùng | Tạo CORS config | `config/CorsConfig.java` (~15 dòng) |
| Toàn | Seed dữ liệu mẫu | `DataInitializer.java` |
| Thiên | Setup FE + Login page | `frontend/src/pages/LoginPage.jsx` |
| Quảng | Hoàn thành Figma | Link Figma gửi nhóm |

---

## ⚠️ Quy tắc quan trọng

1. **KHÔNG push thẳng vào `develop`** — luôn tạo feature branch rồi tạo PR
2. **KHÔNG sửa file người khác đang làm** — nếu cần thì báo trước
3. **Commit message tiếng Việt OK** — nhưng phải rõ ràng: "Tạo CorsConfig cho phép FE gọi API"
4. **Hỏi khi không hiểu** — đừng ngồi stuck cả ngày, hỏi Leader ngay
