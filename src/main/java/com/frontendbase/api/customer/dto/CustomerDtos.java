package com.frontendbase.api.customer.dto;

import java.util.UUID;
import java.time.Instant;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.frontendbase.api.common.validation.StrictIntegerDeserializer;

public final class CustomerDtos {
    private CustomerDtos() {
    }

    public record CreatePayload(@NotNull UUID warehouseId, @NotBlank @Size(max = 160) String name,
            @Size(max = 20) String phone, @Size(max = 500) String address, @Size(max = 2000) String note,
            @NotNull @Min(0) @Max(1) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer status) {
    }

    public record UpdatePayload(UUID warehouseId, @NotBlank @Size(max = 160) String name,
            @Size(max = 20) String phone, @Size(max = 500) String address, @Size(max = 2000) String note,
            @NotNull @Min(0) @Max(1) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer status) {
    }

    public record CustomerResponse(UUID id, UUID warehouseId, String code, String name, String phone, String address,
            String note,
            int status, Instant createdAt, Instant updatedAt) {
    }
}
