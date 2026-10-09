package com.frontendbase.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frontendbase.api.common.exception.ApiErrorResponse;
import com.frontendbase.api.config.CorsProperties;
import com.frontendbase.api.security.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
        private final ObjectMapper objectMapper;

        public SecurityConfig(ObjectMapper objectMapper) {
                this.objectMapper = objectMapper;
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        JwtService jwtService,
                        CorsConfigurationSource corsConfigurationSource) throws Exception {
                return http
                                .csrf(AbstractHttpConfigurer::disable)
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint((request, response, exception) -> writeError(
                                                                response,
                                                                HttpStatus.UNAUTHORIZED,
                                                                "UNAUTHORIZED",
                                                                "Phiên đăng nhập hết hạn"))
                                                .accessDeniedHandler((request, response, exception) -> writeError(
                                                                response,
                                                                HttpStatus.FORBIDDEN,
                                                                "FORBIDDEN",
                                                                "Bạn không có quyền truy cập")))
                                .authorizeHttpRequests(authorize -> authorize
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers("/auth/login", "/auth/refresh").permitAll()
                                                .requestMatchers(
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                                                UsernamePasswordAuthenticationFilter.class)
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable)
                                .logout(AbstractHttpConfigurer::disable)
                                .build();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(properties.allowedOrigins());
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(
                                List.of("Authorization", "Content-Type", "Accept", "Origin", "Idempotency-Key"));
                configuration.setExposedHeaders(List.of("Location"));
                configuration.setAllowCredentials(false);
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
                return configuration.getAuthenticationManager();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder(12);
        }

        private void writeError(
                        HttpServletResponse response,
                        HttpStatus status,
                        String code,
                        String message) throws IOException {
                response.setStatus(status.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                                Instant.now(),
                                status.value(),
                                code,
                                message,
                                Map.of()));
        }
}
