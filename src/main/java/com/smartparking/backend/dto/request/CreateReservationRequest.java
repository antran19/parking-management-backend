package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Request tạo đặt chỗ trước.
 *
 * Driver gửi lên:
 * - slotId: ô muốn đặt
 * - vehicleTypeId: loại xe
 * - licensePlate: biển số xe sẽ dùng khi tới bãi
 */
@Getter
@Setter
public class CreateReservationRequest {

    @NotNull(message = "Slot không được để trống")
    private UUID slotId;

    @NotNull(message = "Loại xe không được để trống")
    private UUID vehicleTypeId;

    @NotBlank(message = "Biển số xe không được để trống")
    @Size(max = 15, message = "Biển số xe tối đa 15 ký tự")
    private String licensePlate;
}
