package com.frontendbase.api.user.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record UserRolesPayload(
        @NotNull(message = "Danh sách role là bắt buộc") List<@NotNull(message = "Role ID không được để trống") UUID> roleIds) {
}