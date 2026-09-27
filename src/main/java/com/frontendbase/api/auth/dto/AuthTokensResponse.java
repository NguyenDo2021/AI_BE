package com.frontendbase.api.auth.dto;

public record AuthTokensResponse(String accessToken, String refreshToken) {
}
