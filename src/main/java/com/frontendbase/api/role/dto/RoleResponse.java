package com.frontendbase.api.role.dto;

import java.time.Instant;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String code,
        String description,
        short status,
        Instant createdAt,
        Instant updatedAt) {
}
