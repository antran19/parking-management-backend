#  Parking Building Management System

Backend API for Parking Building Management System  
**SWP391 - SU26SWP08 | FPT University**

## Tech Stack
- Java 17 + Spring Boot 3.x
- Spring Security + JWT + RBAC
- PostgreSQL 15
- Docker + Docker Compose

## Team
| Name | Role |
|------|------|
| Trần Nguyễn Minh An | Team Leader / Backend |
| Nguyễn Duy Tùng | Backend / Auth & API |
| Nguyễn Khắc Toàn | Backend / AI Slot Allocation |
| Bùi Ngọc Tá Thiên | Lead Frontend |
| Huỳnh Ngọc Quảng | Frontend / QA |

## Branch Strategy
- `main` → Code ổn định, production-ready
- `develop` → Nhánh phát triển chính
- `feature/*` → Tính năng mới (VD: feature/auth, feature/parking-session)

## Getting Started
```bash
# Clone repo
git clone https://github.com/antran19/parking-management-backend.git

# Build project  
mvn clean install

# Run application
mvn spring-boot:run
