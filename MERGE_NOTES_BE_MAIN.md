# Merge notes - be-main

Base used: `parking-management-backend-develop.zip`.
Reference/old project: `parking-management-backend-main.zip`.

This package keeps the newer team code from branch `develop` and keeps/improves the Driver-specific pieces needed for the assignment.

## Applied changes

1. Used `develop` as the main codebase so the project includes the team's merged modules:
   - Payment/VNPay controller/service
   - Emergency/SOS controller/service
   - Staff session controller
   - Manager/statistics files from develop

2. Restored Driver session ownership validation:
   - `GET /api/v1/driver/sessions/active?plate=...`
   - `GET /api/v1/driver/sessions/history?plate=...`
   - `POST /api/v1/driver/sessions/checkout/vnpay`

   Driver users can only access plates that belong to their own account. Staff/Manager/Admin can still search all plates.

3. Connected Driver parking pass flow to VNPay:
   - `POST /api/v1/driver/parking-passes`
   - `POST /api/v1/driver/parking-passes/{passId}/pay`

   These now create/reuse a `Payment` record with `referenceType = PASS`, return `paymentUrl`, `orderCode`, and `paymentId`, and allow `PaymentController` callback to activate the pass.

4. Added both emergency public URL forms:
   - `GET /api/v1/emergency/status`
   - `GET /api/v1/public/emergency/status`

5. Added blacklist/SOS checks into reservation creation:
   - SOS active => block reservation
   - Blacklisted plate => block reservation

## Important local check

Run this on your Windows machine after extracting:

```bat
cd /d D:\parking\parking-management-backend-be-main
mvn clean compile
mvn spring-boot:run
```

Maven was not available in the sandbox, so full Maven compilation could not be executed here.
