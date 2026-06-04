package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentRequest {

    @NotBlank(message = "Loại tham chiếu không được để trống (SESSION hoặc MONTHLY_PASS)")
    private String referenceType; // SESSION | MONTHLY_PASS

    @NotNull(message = "ID tham chiếu không được để trống")
    private UUID referenceId; // ID của session hoặc pass

    @NotBlank(message = "Phương thức thanh toán không được để trống (CASH hoặc BANK_TRANSFER)")
    private String paymentMethod; // CASH | BANK_TRANSFER

    @NotNull(message = "Số tiền thanh toán không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Số tiền không được nhỏ hơn 0")
    private BigDecimal amount;
}
