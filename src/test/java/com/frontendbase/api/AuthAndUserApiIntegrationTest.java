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
                                "DASHBOARD_VIEW", "USER_VIEW", "USER_CREATE", "USER_UPDATE", "USER_DELETE",
                                "ROLE_VIEW", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE"
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
                                .andExpect(jsonPath("$.items[0].code").value("TEST_ADMIN"))
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.page").value(1))
                                .andExpect(jsonPath("$.pageSize").value(10));

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

        @Test
        void roleCrudSupportsSearchAndEnforcesBusinessRules() throws Exception {
                String accessToken = loginAccessToken();
                String authorization = bearer(accessToken);
                UUID assignedRoleId = roleRepository.findAll().get(0).getId();

                mockMvc.perform(get("/roles?page=1&size=1&keyword=Test&status=1")
                                .header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[0].code").value("TEST_ADMIN"))
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.pageSize").value(1));

                mockMvc.perform(get("/roles/{id}", assignedRoleId).header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(assignedRoleId.toString()))
                                .andExpect(jsonPath("$.createdAt").isNotEmpty());

                String createdJson = mockMvc.perform(post("/roles")
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                        "name": "  Nhân viên  ",
                                                        "code": "  staff  ",
                                                        "description": "  Nhân viên hệ thống  ",
                                                        "status": 1
                                                }
                                                """))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value("Nhân viên"))
                                .andExpect(jsonPath("$.code").value("STAFF"))
                                .andExpect(jsonPath("$.description").value("Nhân viên hệ thống"))
                                .andExpect(jsonPath("$.status").value(1))
                                .andReturn().getResponse().getContentAsString();
                UUID createdId = UUID.fromString(objectMapper.readTree(createdJson).get("id").asText());

                mockMvc.perform(post("/roles")
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Other staff","code":"STAFF","description":null,"status":1}
                                                """))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("ROLE_CODE_EXISTS"));

                mockMvc.perform(post("/roles")
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"   ","code":"bad-code","description":"x","status":4}
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

                mockMvc.perform(put("/roles/{id}", createdId)
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                        "name":"Nhân viên cập nhật",
                                                        "code":"staff_v2",
                                                        "description":"Đã cập nhật",
                                                        "status":0
                                                }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("STAFF_V2"))
                                .andExpect(jsonPath("$.status").value(0))
                                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

                mockMvc.perform(get("/roles?name=cập nhật&code=V2&status=0&page=1&pageSize=10")
                                .header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.items[0].id").value(createdId.toString()));

                mockMvc.perform(put("/roles/{id}", UUID.randomUUID())
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Missing","code":"MISSING","description":null,"status":1}
                                                """))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));

                mockMvc.perform(put("/roles/{id}", createdId)
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Changed","code":"TEST_ADMIN","description":null,"status":1}
                                                """))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("ROLE_CODE_EXISTS"));

                mockMvc.perform(delete("/roles/{id}", assignedRoleId).header("Authorization", authorization))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("ROLE_IN_USE"));

                Role systemAdminRole = new Role();
                systemAdminRole.setId(UUID.randomUUID());
                systemAdminRole.setName("System administrator");
                systemAdminRole.setCode("ADMIN");
                systemAdminRole = roleRepository.save(systemAdminRole);
                mockMvc.perform(delete("/roles/{id}", systemAdminRole.getId())
                                .header("Authorization", authorization))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("SYSTEM_ROLE_PROTECTED"));

                mockMvc.perform(delete("/roles/{id}", createdId).header("Authorization", authorization))
                                .andExpect(status().isNoContent());
                mockMvc.perform(get("/roles/{id}", createdId).header("Authorization", authorization))
                                .andExpect(status().isNotFound());
                mockMvc.perform(delete("/roles/{id}", UUID.randomUUID()).header("Authorization", authorization))
                                .andExpect(status().isNotFound());

                String noPermissionToken = jwtService.createAccessToken(
                                userRepository.findByUsernameIgnoreCase("integration-admin").orElseThrow()
                                                .getId().toString(),
                                "integration-admin",
                                List.of());
                String noPermission = bearer(noPermissionToken);
                mockMvc.perform(get("/roles").header("Authorization", noPermission))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post("/roles")
                                .header("Authorization", noPermission)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Denied","code":"DENIED","description":null,"status":1}
                                                """))
                                .andExpect(status().isForbidden());
                mockMvc.perform(put("/roles/{id}", createdId)
                                .header("Authorization", noPermission)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Denied","code":"DENIED","description":null,"status":1}
                                                """))
                                .andExpect(status().isForbidden());
                mockMvc.perform(delete("/roles/{id}", createdId).header("Authorization", noPermission))
                                .andExpect(status().isForbidden());
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
