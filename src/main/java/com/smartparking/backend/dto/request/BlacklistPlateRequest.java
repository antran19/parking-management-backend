package com.smartparking.backend.dto.request;

import com.smartparking.backend.entity.BlacklistPlate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BlacklistPlateRequest {

    @NotBlank(message = "Biển số không được để trống")
    private String licensePlate;

    private String vehicleType;

    @NotNull(message = "Lý do blacklist không được để trống")
    private BlacklistPlate.BlacklistReason reason;

    private String description;

    @NotNull(message = "Người thêm blacklist không được để trống")
    private UUID addedByUserId;

    // Danh sách URL ảnh minh chứng (tuỳ chọn, dùng cho tính năng sửa)
    private List<String> imageUrls;
}
