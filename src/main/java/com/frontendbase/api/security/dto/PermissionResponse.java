package com.frontendbase.api.security.dto;

import java.time.Instant;
import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String name,
        String code,
        String description,
        short status,
        Instant createdAt,
        Instant updatedAt) {
}