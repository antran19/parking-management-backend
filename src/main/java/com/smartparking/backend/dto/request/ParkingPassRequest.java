package com.smartparking.backend.dto.request;

import com.smartparking.backend.entity.ParkingPass.PassType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ParkingPassRequest {

    @NotBlank(message = "Biển số xe không được để trống")
    private String licensePlate;

    @NotNull(message = "Tòa nhà không được để trống")
    private UUID buildingId;

    @NotNull(message = "Loại phương tiện không được để trống")
    private UUID vehicleTypeId;

    @NotNull(message = "Loại gói đăng ký không được để trống")
    private PassType passType; // MONTHLY, QUARTERLY, YEARLY

    @NotNull(message = "Ngày bắt đầu không được để trống")
    @FutureOrPresent(message = "Ngày bắt đầu phải ở hiện tại hoặc tương lai")
    private LocalDate startDate;

    private String paymentMethod = "ONLINE"; // CASH, ONLINE, QR_CODE
}
