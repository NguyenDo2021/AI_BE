package com.frontendbase.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.frontendbase.api.config.JwtProperties;
import com.frontendbase.api.security.service.JwtService;
import io.jsonwebtoken.JwtException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private final JwtService jwtService = new JwtService(new JwtProperties(
            "unit-test-secret-with-at-least-thirty-two-bytes",
            15,
            7
    ));

    @Test
    void createsAndReadsAccessTokenClaims() {
        String token = jwtService.createAccessToken("user-id", "admin", List.of("USER_VIEW", "ROLE_VIEW"));

        var claims = jwtService.parseAccessToken(token);

        assertThat(claims.subject()).isEqualTo("user-id");
        assertThat(claims.permissions()).containsExactly("USER_VIEW", "ROLE_VIEW");
    }

    @Test
    void rejectsTokenWithInvalidSignature() {
        JwtService otherService = new JwtService(new JwtProperties(
                "another-unit-test-secret-with-at-least-thirty-two-bytes",
                15,
                7
        ));
        String token = jwtService.createAccessToken("user-id", "admin", List.of("USER_VIEW"));

        assertThatThrownBy(() -> otherService.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }
}
