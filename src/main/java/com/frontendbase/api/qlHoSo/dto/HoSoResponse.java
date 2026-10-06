package com.frontendbase.api.qlHoSo.dto;

import java.time.Instant;
import java.util.UUID;

public record HoSoResponse(
                UUID id,
                String username,
                String fullName,
                String email,
                String phone,
                short status,
                Instant createdAt,
                UUID updatedBy,
                Instant updatedAt) {
}
