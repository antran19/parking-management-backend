# SmartParking — API Specification v1.0

> Base URL: `http://localhost:8080/api/v1`
> Authentication: Bearer JWT Token (trừ endpoints public)

---

## 1. AUTHENTICATION

### POST `/auth/login`
**Mô tả:** Đăng nhập hệ thống
**Quyền:** Public

**Request Body:**
```json
{
  "email": "staff@parking.vn",
  "password": "123456"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Đăng nhập thành công",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": "uuid",
      "email": "staff@parking.vn",
      "fullName": "Nguyễn Văn A",
      "role": "STAFF"
    }
  }
}
```

---

### POST `/auth/refresh`
**Mô tả:** Làm mới access token
**Header:** `Authorization: Bearer <refresh_token>`

**Response (200):** Giống login response (access token mới)

---

### POST `/auth/logout`
**Mô tả:** Đăng xuất (client xóa token)
**Quyền:** Authenticated

---

## 2. PARKING SESSION (Staff Operations)

### POST `/staff/sessions/checkin`
**Mô tả:** Check-in xe vào bãi (UC-04)
**Quyền:** STAFF, MANAGER, ADMIN

**Request Body:**
```json
{
  "licensePlate": "51A-12345",
  "vehicleTypeId": "uuid-of-motorbike",
  "gateEntryId": "uuid-of-gate-a",
  "reservationCode": null,
  "notes": ""
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Check-in thành công. Vui lòng đến Tầng B1 - Ô số B1-A03",
  "data": {
    "sessionId": "uuid",
    "sessionCode": "PS20260516-A3F",
    "slotCode": "B1-A03",
    "floorName": "B1",
    "zoneName": "Khu A - Xe máy",
    "licensePlate": "51A-12345",
    "vehicleType": "Xe máy",
    "entryTime": "2026-05-16T08:30:00",
    "status": "ACTIVE",
    "guideMessage": "Vui lòng đến Tầng B1 - Ô số B1-A03"
  }
}
```

**Error Cases:**
- `400` — Biển số đang có session chưa đóng
- `400` — Không còn slot trống cho loại xe

---

### POST `/staff/sessions/checkout`
**Mô tả:** Check-out xe ra bãi (UC-05)
**Quyền:** STAFF, MANAGER, ADMIN

**Request Body (cần ít nhất 1 trong 3 tiêu chí):**
```json
{
  "sessionId": "uuid",
  "sessionCode": "PS20260516-A3F",
  "licensePlate": "51A-12345",
  "gateExitId": "uuid-of-gate-b",
  "paymentMethod": "CASH",
  "exceptionNote": ""
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Check-out thành công. Tổng phí: 10000đ",
  "data": {
    "sessionId": "uuid",
    "sessionCode": "PS20260516-A3F",
    "slotCode": "B1-A03",
    "licensePlate": "51A-12345",
    "vehicleType": "Xe máy",
    "entryTime": "2026-05-16T08:30:00",
    "exitTime": "2026-05-16T10:45:00",
    "durationMinutes": 135,
    "totalFee": 15000,
    "status": "COMPLETED",
    "paymentStatus": "COMPLETED"
  }
}
```

---

### GET `/sessions/active?plate={licensePlate}`
**Mô tả:** Xem session đang hoạt động (UC-14)
**Quyền:** Authenticated (Driver/Staff/Manager)

**Response (200):** Giống check-in response + thêm `totalFee` tạm tính

---

## 3. SLOT MAP (Public)

### GET `/public/slots/map/{buildingId}`
**Mô tả:** Sơ đồ bãi xe real-time (UC-06, UC-11)
**Quyền:** Public

**Response (200):**
```json
{
  "success": true,
  "data": {
    "buildingId": "uuid",
    "buildingName": "SmartParking Tower",
    "totalSlots": 200,
    "availableSlots": 85,
    "occupiedSlots": 100,
    "reservedSlots": 15,
    "floors": [
      {
        "floorId": "uuid",
        "floorName": "B2",
        "floorNumber": -2,
        "zones": [
          {
            "zoneId": "uuid",
            "zoneCode": "A",
            "zoneName": "Khu A - Xe máy",
            "vehicleType": "Xe máy",
            "slots": [
              { "slotId": "uuid", "slotCode": "B2-A01", "status": "AVAILABLE" },
              { "slotId": "uuid", "slotCode": "B2-A02", "status": "OCCUPIED" },
              { "slotId": "uuid", "slotCode": "B2-A03", "status": "RESERVED" }
            ]
          }
        ]
      }
    ]
  }
}
```

---

## 4. WEBSOCKET — Real-time Slot Updates

### Endpoint: `/ws` (SockJS)
### Subscribe: `/topic/slots/{buildingId}`

**Message format (server → client):**
```json
{
  "slotId": "uuid",
  "slotCode": "B1-A03",
  "status": "OCCUPIED",
  "floorName": "B1",
  "timestamp": "2026-05-16T08:30:00"
}
```

**Cách kết nối (FE):**
```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);
stompClient.connect({}, () => {
  stompClient.subscribe('/topic/slots/' + buildingId, (message) => {
    const slotUpdate = JSON.parse(message.body);
    // Cập nhật slot trên sơ đồ
  });
});
```

---

## 5. ERROR RESPONSE FORMAT

Tất cả error trả về cùng format:

```json
{
  "success": false,
  "message": "Mô tả lỗi bằng tiếng Việt",
  "data": null
}
```

| HTTP Code | Khi nào |
|-----------|---------|
| 400 | Validation error, Business logic error |
| 401 | Token hết hạn hoặc không hợp lệ |
| 403 | Không có quyền truy cập |
| 404 | Entity không tìm thấy |
| 500 | Lỗi server nội bộ |

---

## 6. ROLE-BASED ACCESS MATRIX

| Endpoint Pattern | DRIVER | STAFF | MANAGER | ADMIN |
|-----------------|--------|-------|---------|-------|
| `/auth/**` | ✅ | ✅ | ✅ | ✅ |
| `/public/**` | ✅ | ✅ | ✅ | ✅ |
| `/sessions/active` | ✅ | ✅ | ✅ | ✅ |
| `/staff/**` | ❌ | ✅ | ✅ | ✅ |
| `/manager/**` | ❌ | ❌ | ✅ | ✅ |
| `/admin/**` | ❌ | ❌ | ❌ | ✅ |

---

*API Spec v1.0 — SmartParking SU26SWP08*
