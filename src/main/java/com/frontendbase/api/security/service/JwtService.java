package com.frontendbase.api.security.service;

import com.frontendbase.api.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        String configuredSecret = properties.secret();
        byte[] keyBytes = configuredSecret.startsWith("base64:")
                ? Decoders.BASE64.decode(configuredSecret.substring("base64:".length()))
                : configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret must contain at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(String userId, String username, List<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.accessTokenMinutes() * 60)))
                .signWith(signingKey)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object rawPermissions = claims.get("permissions");
        List<String> permissions = rawPermissions instanceof List<?> values
                ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
        return new AccessTokenClaims(claims.getSubject(), permissions);
    }

    public long refreshTokenDays() {
        return properties.refreshTokenDays();
    }

    public record AccessTokenClaims(String subject, List<String> permissions) {
    }
}
