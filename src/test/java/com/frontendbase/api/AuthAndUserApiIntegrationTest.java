package com.frontendbase.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frontendbase.api.auth.dto.AuthTokensResponse;
import com.frontendbase.api.auth.repository.RefreshTokenRepository;
import com.frontendbase.api.role.entity.Role;
import com.frontendbase.api.role.repository.RoleRepository;
import com.frontendbase.api.security.entity.Permission;
import com.frontendbase.api.security.repository.PermissionRepository;
import com.frontendbase.api.security.service.JwtService;
import com.frontendbase.api.user.entity.UserAccount;
import com.frontendbase.api.user.repository.UserRepository;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndUserApiIntegrationTest {
        private static final String ADMIN_PASSWORD = "Integration-test-password-123!";

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private RoleRepository roleRepository;

        @Autowired
        private PermissionRepository permissionRepository;

        @Autowired
        private RefreshTokenRepository refreshTokenRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private JwtService jwtService;

        @BeforeEach
        void setUp() {
                refreshTokenRepository.deleteAll();
                userRepository.deleteAll();
                roleRepository.deleteAll();
                permissionRepository.deleteAll();

                Role adminRole = new Role();
                adminRole.setId(UUID.randomUUID());
                adminRole.setCode("TEST_ADMIN");
                adminRole.setName("Test administrator");

                for (String code : new String[] {
                                "DASHBOARD_VIEW", "USER_VIEW", "USER_CREATE", "USER_UPDATE", "USER_DELETE", "ROLE_VIEW"
                }) {
                        Permission permission = new Permission();
                        permission.setId(UUID.randomUUID());
                        permission.setCode(code);
                        permission.setDescription(code);
                        adminRole.getPermissions().add(permissionRepository.save(permission));
                }
                roleRepository.save(adminRole);

                UserAccount admin = new UserAccount();
                admin.setId(UUID.randomUUID());
                admin.setUsername("integration-admin");
                admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
                admin.setFullName("Integration Admin");
                admin.setEmail("integration-admin@example.test");
                admin.setStatus((short) 1);
                admin.getRoles().add(adminRole);
                userRepository.save(admin);
        }

        @Test
        void loginMeRefreshAndProtectedUserCrudMatchFrontendContract() throws Exception {
                String initialRefreshToken = login();
                String accessToken = loginAccessToken();

                mockMvc.perform(get("/auth/me").header("Authorization", bearer(accessToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").isString())
                                .andExpect(jsonPath("$.username").value("integration-admin"))
                                .andExpect(jsonPath("$.fullName").value("Integration Admin"))
                                .andExpect(jsonPath("$.permissions").isArray())
                                .andExpect(jsonPath("$.permissions").isNotEmpty());

                mockMvc.perform(get("/users?page=1&pageSize=10&keyword=integration"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.message").isNotEmpty())
                                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

                mockMvc.perform(get("/users?page=1&pageSize=10&keyword=integration")
                                .header("Authorization", bearer(accessToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[0].id").isString())
                                .andExpect(jsonPath("$.items[0].username").value("integration-admin"))
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.page").value(1))
                                .andExpect(jsonPath("$.pageSize").value(10));

                String created = mockMvc.perform(post("/users")
                                .header("Authorization", bearer(accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "username": "new-user",
                                                  "fullName": "New User",
                                                  "email": "new-user@example.test",
                                                  "phone": "+84901234567",
                                                  "status": 1
                                                }
                                                """))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").isString())
                                .andExpect(jsonPath("$.fullName").value("New User"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                JsonNode createdJson = objectMapper.readTree(created);
                String newUserId = createdJson.get("id").asText();
                UserAccount createdUser = userRepository.findById(UUID.fromString(newUserId)).orElseThrow();
                assertTrue(passwordEncoder.matches("Abc@12345", createdUser.getPasswordHash()));
                org.junit.jupiter.api.Assertions.assertNotEquals("Abc@12345", createdUser.getPasswordHash());

                mockMvc.perform(put("/users/{id}", newUserId)
                                .header("Authorization", bearer(accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "username": "new-user",
                                                  "fullName": "Updated User",
                                                  "email": "new-user@example.test",
                                                  "phone": null,
                                                  "status": 0
                                                }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.fullName").value("Updated User"))
                                .andExpect(jsonPath("$.status").value(0));

                mockMvc.perform(get("/users/{id}", newUserId).header("Authorization", bearer(accessToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.username").value("new-user"));

                mockMvc.perform(get("/roles").header("Authorization", bearer(accessToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].code").value("TEST_ADMIN"));

                mockMvc.perform(delete("/users/{id}", newUserId).header("Authorization", bearer(accessToken)))
                                .andExpect(status().isNoContent());

                String rotatedRefreshToken = mockMvc.perform(post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.frontendbase.api.auth.dto.RefreshTokenRequest(
                                                                initialRefreshToken))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").isString())
                                .andExpect(jsonPath("$.refreshToken").isString())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                AuthTokensResponse rotated = objectMapper.readValue(rotatedRefreshToken, AuthTokensResponse.class);

                mockMvc.perform(post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.frontendbase.api.auth.dto.RefreshTokenRequest(
                                                                initialRefreshToken))))
                                .andExpect(status().isUnauthorized());

                mockMvc.perform(get("/auth/me").header("Authorization", bearer(rotated.accessToken())))
                                .andExpect(status().isOk());
        }

        @Test
        void rejectsInvalidPayloadAndMissingPermissions() throws Exception {
                String accessToken = loginAccessToken();

                mockMvc.perform(post("/users")
                                .header("Authorization", bearer(accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "username": "",
                                                  "fullName": "",
                                                  "email": "not-an-email",
                                                  "status": 4
                                                }
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                                .andExpect(jsonPath("$.details").isMap());

                String userId = userRepository.findByUsernameIgnoreCase("integration-admin").orElseThrow()
                                .getId().toString();
                String noPermissionToken = jwtService.createAccessToken(userId, "integration-admin", List.of());
                mockMvc.perform(get("/users")
                                .header("Authorization", bearer(noPermissionToken)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

                mockMvc.perform(get("/users?page=0&pageSize=500")
                                .header("Authorization", bearer(accessToken)))
                                .andExpect(status().isBadRequest());
        }

        private String login() throws Exception {
                String response = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.frontendbase.api.auth.dto.LoginRequest(
                                                                "integration-admin",
                                                                ADMIN_PASSWORD))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").isString())
                                .andExpect(jsonPath("$.refreshToken").isString())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                return objectMapper.readValue(response, AuthTokensResponse.class).refreshToken();
        }

        private String loginAccessToken() throws Exception {
                String response = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.frontendbase.api.auth.dto.LoginRequest(
                                                                "integration-admin",
                                                                ADMIN_PASSWORD))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                return objectMapper.readValue(response, AuthTokensResponse.class).accessToken();
        }

        private String bearer(String token) {
                return "Bearer " + token;
        }
}
