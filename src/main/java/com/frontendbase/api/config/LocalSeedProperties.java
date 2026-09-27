package com.frontendbase.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed")
public record LocalSeedProperties(
        boolean enabled,
        Admin admin
) {
    public record Admin(String username, String password, String email, String fullName) {
    }
}
