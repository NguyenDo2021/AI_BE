package com.frontendbase.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String username,
        String fullName,
        String email,
        List<String> permissions,
        @JsonProperty("isAdmin") @JsonInclude(JsonInclude.Include.ALWAYS) boolean isAdmin
) {
}
