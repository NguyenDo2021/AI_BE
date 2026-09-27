package com.frontendbase.api.auth.controller;

import com.frontendbase.api.auth.dto.AuthTokensResponse;
import com.frontendbase.api.auth.dto.AuthUserResponse;
import com.frontendbase.api.auth.dto.LoginRequest;
import com.frontendbase.api.auth.dto.RefreshTokenRequest;
import com.frontendbase.api.auth.service.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthTokensResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @PostMapping("/refresh")
    public AuthTokensResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public AuthUserResponse currentUser(@AuthenticationPrincipal String userId) {
        return authService.getCurrentUser(UUID.fromString(userId));
    }
}
