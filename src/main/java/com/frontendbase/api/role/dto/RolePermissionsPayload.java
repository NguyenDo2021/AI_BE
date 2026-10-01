package com.frontendbase.api.role.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record RolePermissionsPayload(@NotNull List<@NotNull UUID> permissionIds) {
}