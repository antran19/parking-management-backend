package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO cho việc cập nhật URL ảnh của phiên đỗ xe (Parking Session).
 */
@Data
public class UpdateImagesRequest {

    private String plateUrl;

    private String faceUrl;

    @NotNull(message = "Trạng thái lối vào (isEntry) không được để trống")
    private Boolean isEntry;

}
