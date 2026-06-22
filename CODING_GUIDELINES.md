# CODING GUIDELINES — SmartParking V2

> Tài liệu quy tắc code cho team. Mọi thành viên **BẮT BUỘC** đọc trước khi code.

---

## 1. QUY TẮC ĐẶT TÊN

### Java (Backend)

| Loại | Quy tắc | Ví dụ đúng | Ví dụ sai |
|------|---------|------------|-----------|
| Class | PascalCase | `ParkingSessionService` | `parkingSessionService` |
| Method | camelCase | `checkIn()` | `CheckIn()` |
| Biến | camelCase | `licensePlate` | `license_plate` |
| Hằng số | UPPER_SNAKE | `MAX_CAPACITY` | `maxCapacity` |
| Package | lowercase | `com.smartparking.backend` | `com.SmartParking` |
| Entity | Danh từ số ít | `ParkingSession` | `ParkingSessions` |
| Repository | Entity + Repository | `ParkingSessionRepository` | `ParkingSessionRepo` |
| Service | Entity/Feature + Service | `PricingService` | `PricingSvc` |
| Controller | Feature + Controller | `SessionController` | `SessionCtrl` |
| DTO Request | Feature + Request | `CheckInRequest` | `CheckInDTO` |
| DTO Response | Feature + Response | `SessionResponse` | `SessionDTO` |

### React (Frontend)

| Loại | Quy tắc | Ví dụ đúng | Ví dụ sai |
|------|---------|------------|-----------|
| Component file | PascalCase.jsx | `StaffCheckIn.jsx` | `staffCheckIn.jsx` |
| Component name | PascalCase | `StaffCheckIn` | `staffCheckIn` |
| Hook/function | camelCase | `handleSubmit` | `HandleSubmit` |
| State variable | camelCase | `isLoading` | `IsLoading` |
| CSS class | kebab-case | `btn-primary` | `btnPrimary` |
| API method | camelCase | `staffApi.checkIn()` | `staffApi.CheckIn()` |
| Constant | UPPER_SNAKE | `API_BASE_URL` | `apiBaseUrl` |

---

## 2. CẤU TRÚC FILE

### Backend — Mỗi Controller phải theo pattern:

```java
package com.smartparking.backend.controller;

// 1. Import (nhóm theo: java, jakarta, spring, project)
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.smartparking.backend.service.*;

// 2. Annotation
@RestController
@RequestMapping("/api/v1")
public class XxxController {

    // 3. Dependencies (final + constructor injection, KHÔNG dùng @Autowired)
    private final XxxService xxxService;

    public XxxController(XxxService xxxService) {
        this.xxxService = xxxService;
    }

    // 4. Endpoints (theo thứ tự: GET → POST → PUT → DELETE)
    @GetMapping("/xxx")
    public ResponseEntity<ApiResponse<...>> getXxx() { ... }

    @PostMapping("/xxx")
    public ResponseEntity<ApiResponse<...>> createXxx(@Valid @RequestBody XxxRequest request) { ... }
}
```

### Frontend — Mỗi Component phải theo pattern:

```jsx
// 1. Imports
import { useState, useEffect } from 'react';
import staffApi from '../../api/parkingApi';

// 2. Component (export default function)
export default function StaffCheckIn({ onLogout }) {
  // 3. State declarations
  const [data, setData] = useState(null);
  const [isLoading, setIsLoading] = useState(false);

  // 4. useEffect hooks
  useEffect(() => {
    loadData();
  }, []);

  // 5. Handler functions
  const handleSubmit = async () => { ... };

  // 6. Helper functions
  const formatCurrency = (amount) => { ... };

  // 7. JSX return
  return (
    <div>...</div>
  );
}
```

---

## 3. API CONVENTIONS

### URL Pattern
- Prefix: `/api/v1`
- Theo role: `/driver/**`, `/staff/**`, `/security/**`, `/admin/**`
- Resource plural: `/sessions`, `/reservations`, `/plates`
- Action: `/sessions/checkin`, `/sessions/checkout`

### Response Format (BẮT BUỘC dùng ApiResponse wrapper)

```json
{
  "success": true,
  "message": "Thao tác thành công",
  "data": { ... }
}
```

```java
// Đúng:
return ResponseEntity.ok(ApiResponse.success("Check-in thành công", response));

// Sai:
return ResponseEntity.ok(response); // KHÔNG wrap ApiResponse
```

### Error Response

```json
{
  "success": false,
  "message": "Biển số đang có phiên chưa kết thúc",
  "data": null
}
```

```java
// Ném exception → GlobalExceptionHandler tự catch + format
throw new BusinessException("Biển số đang có phiên chưa kết thúc");
throw new ResourceNotFoundException("Zone không tồn tại");
```

---

## 4. GIT WORKFLOW

### Branch Naming

```
main              ← Production, chỉ merge khi code đã test xong
  └── develop     ← Branch chính, merge feature vào đây
       ├── feature/driver-dashboard    (Quảng)
       ├── feature/staff-checkin       (Tùng)
       ├── feature/manager-dashboard   (Toàn)
       ├── feature/security-sos        (Thiên)
       └── feature/admin-crud          (An)
```

### Quy tắc đặt tên branch
- `feature/ten-chuc-nang` — tính năng mới
- `fix/ten-bug` — sửa bug
- `refactor/ten-module` — tái cấu trúc

### Commit Message Format

```
<type>: <mô tả ngắn gọn>

type:
  feat     → Tính năng mới
  fix      → Sửa bug
  docs     → Cập nhật tài liệu
  style    → Format code (không ảnh hưởng logic)
  refactor → Tái cấu trúc code
  test     → Thêm test
```

**Ví dụ:**
```
feat: implement check-in API with zone suggestion
fix: reservation count not decremented after checkout
docs: update API spec for payment endpoints
```

### Quy trình merge

1. `git checkout develop && git pull origin develop`
2. `git checkout -b feature/ten-chuc-nang`
3. Code → commit → push
4. Tạo **Pull Request** vào `develop`
5. **Leader review** → Approve → Merge
6. **Không bao giờ push trực tiếp vào main hoặc develop**

---

## 5. QUY TẮC QUAN TRỌNG

### Backend
- ✅ **Constructor injection** (không dùng @Autowired trên field)
- ✅ **@Transactional** cho method có nhiều thao tác write DB
- ✅ **@Valid** cho @RequestBody khi cần validate input
- ✅ **@PreAuthorize** trên mỗi endpoint để kiểm tra quyền
- ❌ **KHÔNG** throw Exception chung, dùng BusinessException hoặc ResourceNotFoundException
- ❌ **KHÔNG** trả ResponseEntity trực tiếp, luôn wrap ApiResponse

### Frontend
- ✅ **try/catch** cho mọi API call
- ✅ **Loading state** khi gọi API (setIsLoading)
- ✅ **Error handling** hiển thị thông báo lỗi cho user
- ✅ Dùng **staffApi.xxx()** từ parkingApi.js (không gọi axios trực tiếp)
- ❌ **KHÔNG** dùng mock data trong production code
- ❌ **KHÔNG** console.log() trong code submit (chỉ dùng khi debug)

### Cả hai
- ✅ Comment bằng **tiếng Việt** cho dễ hiểu
- ✅ Mỗi file PHẢI có JSDoc/Javadoc mô tả chức năng
- ✅ Test thủ công trước khi tạo PR
- ❌ **KHÔNG** sửa file của người khác mà không hỏi trước

---

## 6. TECH STACK REFERENCE

| Tầng | Thư viện | Import |
|------|----------|--------|
| HTTP | Axios | `import staffApi from '../../api/parkingApi'` |
| Routing | React Router | `import { useNavigate } from 'react-router-dom'` |
| Animation | GSAP | `import gsap from 'gsap'` |
| WebSocket | STOMP | `import { Client } from '@stomp/stompjs'` |
| QR Code | html5-qrcode | `import { Html5Qrcode } from 'html5-qrcode'` |
| 3D | Three.js | `import * as THREE from 'three'` |
| CSS | TailwindCSS v4 | Dùng trực tiếp trong className |
