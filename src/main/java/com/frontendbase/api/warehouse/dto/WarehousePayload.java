package com.frontendbase.api.warehouse.dto;

import java.util.*;
import jakarta.validation.constraints.*;
public record WarehousePayload(
    @NotBlank @Size(max = 80) @Pattern(regexp = "^\\s*[A-Za-z0-9_.-]+\\s*$") String code,
    @NotBlank @Size(max = 160) String name,
    @Size(max = 500) String address,
    @Size(max = 20) String phone,
    @Size(max = 2000) String note,
    @NotNull @Min(0) @Max(1) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.frontendbase.api.common.validation.StrictIntegerDeserializer.class) Integer status
) {}
