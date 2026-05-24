# SmartParking v2 — Database Schema / ERD Notes

> Schema v2 chuyển từ quản lý exact slot sang quản lý zone/floor capacity.

---

## 1. Thay đổi chính so với v1

### Bỏ trọng tâm `slots`

V1:

```text
parking_sessions.slot_id → slots.id
```

V2:

```text
parking_sessions.zone_id → zones.id
```

Hệ thống không cần biết xe đậu đúng ô nào, chỉ cần biết xe đang thuộc khu/tầng nào.

---

## 2. Danh sách bảng v2

| Bảng | Mục đích |
|---|---|
| users | Tài khoản 5 role |
| vehicle_types | Loại xe: xe máy, ô tô, xe điện |
| buildings | Tòa nhà/bãi xe |
| floors | Tầng trong bãi xe |
| zones | Khu vực gửi xe trong tầng, có capacity/current_count |
| gates | Cổng chính và cổng khu/tầng |
| parking_sessions | Phiên gửi xe từ lúc vào đến lúc ra |
| gate_events | Log mỗi lần scan/quét QR/mở cổng |
| pricing_rules | Bảng giá theo loại xe |
| payments | Thanh toán |
| reservations | Đặt chỗ trước theo zone |
| monthly_passes | Vé tháng theo biển số |
| exception_logs | Sự cố/ngoại lệ do Security xử lý |

---

## 3. `users`

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(30) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);
```

Role hợp lệ:

```text
ADMIN
MANAGER
STAFF
DRIVER
SECURITY
```

---

## 4. `vehicle_types`

```sql
CREATE TABLE vehicle_types (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description TEXT
);
```

Ví dụ:

```text
Xe máy
Ô tô
Xe điện
```

---

## 5. `buildings`

```sql
CREATE TABLE buildings (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    operating_hours_start TIME,
    operating_hours_end TIME,
    created_at TIMESTAMP DEFAULT NOW()
);
```

---

## 6. `floors`

```sql
CREATE TABLE floors (
    id UUID PRIMARY KEY,
    building_id UUID REFERENCES buildings(id),
    floor_number INT NOT NULL,
    floor_name VARCHAR(50) NOT NULL,
    total_capacity INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);
```

Ví dụ:

```text
B2, B1, T1
```

---

## 7. `zones`

Bảng quan trọng nhất thay cho `slots`.

```sql
CREATE TABLE zones (
    id UUID PRIMARY KEY,
    floor_id UUID REFERENCES floors(id),
    zone_code VARCHAR(30) NOT NULL,
    name VARCHAR(50) NOT NULL,
    vehicle_type_id UUID REFERENCES vehicle_types(id),
    capacity INT NOT NULL,
    current_count INT DEFAULT 0,
    reserved_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(floor_id, zone_code)
);
```

Ví dụ:

```text
B1-A: xe máy, capacity 20, current_count 12
B1-B: xe máy, capacity 20, current_count 18
T1-C: ô tô, capacity 15, current_count 7
```

Redis sync:

```text
zone:count:{zoneId}    ↔ zones.current_count
zone:capacity:{zoneId} ↔ zones.capacity
```

---

## 8. `gates`

V2 cần phân biệt cổng chính và cổng khu/tầng.

```sql
CREATE TABLE gates (
    id UUID PRIMARY KEY,
    building_id UUID REFERENCES buildings(id),
    zone_id UUID REFERENCES zones(id),
    gate_code VARCHAR(50) UNIQUE NOT NULL,
    gate_name VARCHAR(100) NOT NULL,
    gate_level VARCHAR(20) NOT NULL,
    gate_type VARCHAR(20) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);
```

`gate_level`:

```text
MAIN
ZONE
```

`gate_type`:

```text
ENTRY
EXIT
BOTH
```

Ví dụ:

```text
MAIN-GATE-01: MAIN + BOTH
B1-A-GATE: ZONE + BOTH, zone_id = B1-A
```

---

## 9. `parking_sessions`

Entity trung tâm của hệ thống.

```sql
CREATE TABLE parking_sessions (
    id UUID PRIMARY KEY,
    session_code VARCHAR(50) UNIQUE NOT NULL,
    qr_code VARCHAR(100) UNIQUE NOT NULL,

    user_id UUID REFERENCES users(id),
    zone_id UUID REFERENCES zones(id),
    vehicle_type_id UUID REFERENCES vehicle_types(id),

    license_plate VARCHAR(20) NOT NULL,
    driver_type VARCHAR(20) NOT NULL,

    entry_main_gate_id UUID REFERENCES gates(id),
    entry_zone_gate_id UUID REFERENCES gates(id),
    exit_zone_gate_id UUID REFERENCES gates(id),
    exit_main_gate_id UUID REFERENCES gates(id),

    entry_time TIMESTAMP NOT NULL,
    zone_entry_time TIMESTAMP,
    zone_exit_time TIMESTAMP,
    exit_time TIMESTAMP,

    duration_minutes INT,
    base_fee DECIMAL(12,2),
    total_fee DECIMAL(12,2),

    payment_status VARCHAR(20) DEFAULT 'PENDING',
    payment_location VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

`driver_type`:

```text
WALK_IN
PRE_BOOKED
MONTHLY
```

`payment_status`:

```text
PENDING
COMPLETED
FAILED
WAIVED
```

`payment_location`:

```text
MAIN_GATE
ZONE_GATE
ONLINE
NONE
```

`status`:

```text
ACTIVE
COMPLETED
CANCELLED
EXCEPTION
```

---

## 10. `gate_events`

Bảng audit cho mọi lần scan/quét QR/mở cổng.

```sql
CREATE TABLE gate_events (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES parking_sessions(id),
    gate_id UUID REFERENCES gates(id),
    handled_by_user_id UUID REFERENCES users(id),

    event_type VARCHAR(30) NOT NULL,
    license_plate VARCHAR(20),
    qr_code VARCHAR(100),
    event_time TIMESTAMP DEFAULT NOW(),
    result VARCHAR(20) NOT NULL,
    note TEXT
);
```

`event_type`:

```text
MAIN_ENTRY_SCAN
ZONE_ENTRY_SCAN
ZONE_EXIT_SCAN
MAIN_EXIT_SCAN
MANUAL_OPEN
EXCEPTION_HANDLED
PAYMENT_CONFIRMED
```

`result`:

```text
SUCCESS
FAILED
MANUAL_REVIEW
```

Ý nghĩa:

```text
Mỗi lần xe qua cổng hoặc quét QR đều tạo 1 GateEvent để truy vết.
```

---

## 11. `pricing_rules`

```sql
CREATE TABLE pricing_rules (
    id UUID PRIMARY KEY,
    building_id UUID REFERENCES buildings(id),
    vehicle_type_id UUID REFERENCES vehicle_types(id),
    pricing_type VARCHAR(20) NOT NULL,
    price_per_unit DECIMAL(12,2) NOT NULL,
    free_minutes INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE
);
```

`pricing_type`:

```text
HOURLY
DAILY
MONTHLY
```

---

## 12. `payments`

```sql
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    reference_type VARCHAR(30) NOT NULL,
    reference_id UUID NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    transaction_id VARCHAR(100),
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
```

`reference_type`:

```text
PARKING_SESSION
RESERVATION
MONTHLY_PASS
```

`payment_method`:

```text
CASH
BANK_TRANSFER
MOMO
VNPAY
QR
```

---

## 13. `reservations`

V2 đặt chỗ theo zone, không theo slot.

```sql
CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    zone_id UUID REFERENCES zones(id),
    vehicle_type_id UUID REFERENCES vehicle_types(id),
    license_plate VARCHAR(20) NOT NULL,
    reservation_code VARCHAR(50) UNIQUE NOT NULL,
    reserved_from TIMESTAMP NOT NULL,
    reserved_to TIMESTAMP NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT NOW()
);
```

`status`:

```text
ACTIVE
USED
EXPIRED
CANCELLED
```

---

## 14. `monthly_passes`

```sql
CREATE TABLE monthly_passes (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    license_plate VARCHAR(20) NOT NULL,
    vehicle_type_id UUID REFERENCES vehicle_types(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);
```

Rule:

```text
1 vé tháng = 1 biển số cố định.
Không giới hạn số lần ra/vào trong thời gian hiệu lực.
```

---

## 15. `exception_logs`

```sql
CREATE TABLE exception_logs (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES parking_sessions(id),
    handled_by_user_id UUID REFERENCES users(id),
    error_type VARCHAR(30) NOT NULL,
    error_message TEXT NOT NULL,
    resolution_note TEXT,
    status VARCHAR(20) DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT NOW(),
    resolved_at TIMESTAMP
);
```

`error_type`:

```text
LOST_QR
WRONG_PLATE
INVALID_QR
WRONG_ZONE
PAYMENT_DISPUTE
MANUAL_GATE_OPEN
MONTHLY_PASS_EXPIRED
OTHER
```

---

## 16. Quan hệ chính

```text
Building 1---n Floor
Floor    1---n Zone
Zone     1---n ParkingSession
Zone     1---n Reservation
Zone     1---n Gate

User     1---n Reservation
User     1---n MonthlyPass
User     1---n GateEvent handled_by

ParkingSession 1---n GateEvent
ParkingSession 1---n ExceptionLog
ParkingSession 1---n Payment
```

---

## 17. Redis keys v2

```text
zone:count:{zoneId}
zone:capacity:{zoneId}
zone:lock:{zoneId}
session:qr:{qrCode}
pass:monthly:{licensePlate}
```

Redis không thay PostgreSQL. Redis dùng để:

- Counter nhanh.
- Lock chống race condition.
- Cache QR session.
- Cache monthly pass theo biển số.

---

## 18. Migration note từ code cũ

Cần sửa:

```text
SlotAssignmentService → ZoneSuggestionService
SlotMapService        → ZoneMapService
SlotController        → ZoneController hoặc ParkingAreaController
ParkingSession.slot   → ParkingSession.zone
Reservation.slot      → Reservation.zone
DataInitializer slots → DataInitializer zones
```

Nếu chưa xóa `Slot` ngay, có thể giữ tạm trong code nhưng không dùng trong luồng v2.
