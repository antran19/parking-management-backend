package com.smartparking.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class BlacklistRemoveRequest {

    @NotNull(message = "Người gỡ blacklist không được để trống")
    private UUID removedByUserId;
}
