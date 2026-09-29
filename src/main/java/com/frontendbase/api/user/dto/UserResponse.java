package com.frontendbase.api.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
                UUID id,
                String username,
                String fullName,
                String email,
                String phone,
                short status,
                Instant createdAt,
                UUID updatedBy,
                String updatedByName,
                Instant updatedAt) {
}
