package com.frontendbase.api.productGroup.dto;

import java.util.*;
import jakarta.validation.constraints.*;
public record ProductGroupPayload(
    @NotBlank @Size(max = 80) @Pattern(regexp = "^\\s*[A-Za-z0-9_.-]+\\s*$") String code,
    @NotBlank @Size(max = 160) String name,
    @Size(max = 2000) String description,
    @NotNull @Min(0) @Max(1) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.frontendbase.api.common.validation.StrictIntegerDeserializer.class) Integer status
) {}
