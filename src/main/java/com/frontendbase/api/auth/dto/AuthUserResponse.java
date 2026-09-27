package com.frontendbase.api.auth.dto;

import java.util.List;
import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String username,
        String fullName,
        String email,
        List<String> permissions
) {
}
