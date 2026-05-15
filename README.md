# 🅿️ SmartParking — Hệ thống Quản lý Tòa nhà Gửi xe Thông minh

> **Mã đề tài:** SU26SWP08 | **Môn:** SWP391 | **FPT University HCM** | **Summer 2026**

---

## 🚀 Cách chạy project

### Bước 1: Cài Docker Desktop
Tải tại: https://www.docker.com/products/docker-desktop

### Bước 2: Start database
```bash
docker-compose up -d
```
Lệnh này khởi động PostgreSQL (port 5432) và Redis (port 6379).

### Bước 3: Chạy Backend
```bash
cd backend
./mvnw spring-boot:run
```
Hoặc mở bằng IntelliJ/VS Code → Run `SmartParkingApplication.java`

### Bước 4: Test API
Mở Postman hoặc trình duyệt → `http://localhost:8080/actuator/health`

Phải thấy:
```json
{ "status": "UP" }
```

---

## 📁 Cấu trúc thư mục

```
smartparking/
├── backend/                    ← Spring Boot (Java 17)
│   └── src/main/java/com/smartparking/backend/
│       ├── entity/             ← 13 bảng DB
│       ├── repository/         ← Query database
│       ├── service/            ← Logic nghiệp vụ ⭐
│       ├── controller/         ← REST API endpoints
│       ├── dto/                ← Request/Response objects
│       ├── security/           ← JWT filter
│       ├── config/             ← Security + WebSocket
│       ├── exception/          ← Xử lý lỗi
│       └── util/               ← JwtUtil
├── frontend/                   ← ReactJS + Vite (chưa tạo)
├── docs/
│   ├── API_SPEC.md             ← Đặc tả API cho Frontend
│   └── CODE_WALKTHROUGH.md     ← Giải thích toàn bộ code
├── docker-compose.yml          ← PostgreSQL + Redis
├── SRS_ParkingSystem.md        ← Đặc tả yêu cầu (26 Use Cases)
└── task.md                     ← Phân công & tiến độ
```

---

## 👥 Nhóm & Phân công

| STT | Thành viên | Vai trò | Khu vực code |
|-----|-----------|---------|-------------|
| 1 | Trần Nguyễn Minh An | Leader / Backend Core | entity, service, controller, config |
| 2 | Nguyễn Duy Tùng | Backend Security + Reservation | security, service (reservation, exception, payment) |
| 3 | Nguyễn Khắc Toàn | Database + Report + Admin | repository, service (report, admin), migration |
| 4 | Bùi Ngọc Tá Thiên | Frontend Architecture | frontend/ (layout, parking map, check-in/out) |
| 5 | Huỳnh Ngọc Quảng | Frontend Driver + QA | frontend/ (driver app, dashboard, testing) |

---

## 🔀 Git Workflow

```
main          ← Chỉ merge khi xong sprint, code phải chạy
  └── develop ← Branch làm việc chính
       ├── feature/register-api        (Tùng)
       ├── feature/data-seeder         (Toàn)
       ├── feature/frontend-setup      (Thiên)
       └── feature/figma-mockup        (Quảng)
```

**Quy trình:**
1. `git checkout develop`
2. `git pull origin develop`
3. `git checkout -b feature/ten-chuc-nang`
4. Code → commit → push
5. Tạo Pull Request vào `develop`
6. Leader review → Approve → Merge

---

## 📖 Đọc thêm

- [API_SPEC.md](docs/API_SPEC.md) — Chi tiết tất cả API endpoints
- [CODE_WALKTHROUGH.md](docs/CODE_WALKTHROUGH.md) — Giải thích từng file code
- [SRS_ParkingSystem.md](SRS_ParkingSystem.md) — 26 Use Cases đầy đủ
- [task.md](task.md) — Phân công nhiệm vụ & timeline
