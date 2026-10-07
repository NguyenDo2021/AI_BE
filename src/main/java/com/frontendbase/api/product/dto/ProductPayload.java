package com.frontendbase.api.product.dto;

import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record ProductPayload(
    @NotBlank @Size(max = 80) @Pattern(regexp = "^\\s*[A-Za-z0-9_.-]+\\s*$") String code,
    @NotBlank @Size(max = 160) String name,
    @NotNull UUID groupId,
    @Size(max = 100) String material,
    @Size(max = 100) String color,
    @Size(max = 255) String dimensions,
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal lengthMeters,
    @Pattern(regexp = "cay") String unit,
    @DecimalMin("0") @Digits(integer = 19, fraction = 0) BigDecimal referencePurchasePrice,
    @DecimalMin("0") @Digits(integer = 19, fraction = 0) BigDecimal defaultSalePrice,
    @Min(0) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.frontendbase.api.common.validation.StrictIntegerDeserializer.class) Integer lowStockThreshold,
    @Size(max = 2000) String description,
    @NotNull @Min(0) @Max(1) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.frontendbase.api.common.validation.StrictIntegerDeserializer.class) Integer status
) {}
