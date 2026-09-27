package com.frontendbase.api.auth.service;

import com.frontendbase.api.auth.dto.AuthTokensResponse;
import com.frontendbase.api.auth.dto.AuthUserResponse;
import com.frontendbase.api.auth.entity.RefreshToken;
import com.frontendbase.api.auth.repository.RefreshTokenRepository;
import com.frontendbase.api.common.exception.ApiException;
import com.frontendbase.api.security.service.JwtService;
import com.frontendbase.api.user.entity.UserAccount;
import com.frontendbase.api.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthTokensResponse login(String username, String password) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
        } catch (AuthenticationException exception) {
            throw unauthorized("Thông tin đăng nhập không chính xác");
        }
        UserAccount user = userRepository.findWithRolesAndPermissionsByUsernameIgnoreCase(username)
                .orElseThrow(() -> unauthorized("Thông tin đăng nhập không chính xác"));
        return createTokens(user);
    }

    @Transactional
    public AuthTokensResponse refresh(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        RefreshToken currentToken = refreshTokenRepository.lockActiveByHash(tokenHash)
                .orElseThrow(() -> unauthorized("Refresh token không hợp lệ hoặc đã hết hạn"));
        Instant now = Instant.now();
        if (!currentToken.getExpiresAt().isAfter(now)) {
            throw unauthorized("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        UserAccount user = userRepository.findWithRolesAndPermissionsById(currentToken.getUser().getId())
                .filter(account -> account.getStatus() == 1)
                .orElseThrow(() -> unauthorized("Tài khoản không hoạt động"));
        currentToken.setRevokedAt(now);
        return createTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse getCurrentUser(UUID userId) {
        UserAccount user = userRepository.findWithRolesAndPermissionsById(userId)
                .filter(account -> account.getStatus() == 1)
                .orElseThrow(() -> unauthorized("Tài khoản không hoạt động"));
        return toAuthUser(user);
    }

    private AuthTokensResponse createTokens(UserAccount user) {
        AuthUserResponse currentUser = toAuthUser(user);
        String accessToken = jwtService.createAccessToken(
                user.getId().toString(),
                user.getUsername(),
                currentUser.permissions()
        );
        String refreshToken = generateRefreshToken();
        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenHash(hashToken(refreshToken));
        storedToken.setUser(user);
        storedToken.setExpiresAt(Instant.now().plusSeconds(jwtService.refreshTokenDays() * 86_400));
        refreshTokenRepository.save(storedToken);
        return new AuthTokensResponse(accessToken, refreshToken);
    }

    private AuthUserResponse toAuthUser(UserAccount user) {
        List<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getCode())
                .distinct()
                .sorted()
                .toList();
        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                permissions
        );
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new AuthenticationServiceException("SHA-256 is not available", exception);
        }
    }

    private ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
