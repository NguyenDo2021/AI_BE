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
import com.frontendbase.api.user.dto.UserRolesPayload;
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

@org.springframework.context.annotation.Import(H2MigrationConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndUserApiIntegrationTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate stockJdbc;
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
        StockTestCleanup.clear(stockJdbc);
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
                                "ROLE_VIEW", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE",
                                "PERMISSION_VIEW", "PERMISSION_CREATE", "PERMISSION_UPDATE", "PERMISSION_DELETE"
                }) {
                        Permission permission = new Permission();
                        permission.setId(UUID.randomUUID());
                        permission.setName(code);
                        permission.setCode(code);
                        permission.setDescription(code);
                        permission.setStatus((short) 1);
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
                                                        "code": "staff",
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

        @Test
        void userRoleEndpointsReadReplaceRemoveAndEnforceRules() throws Exception {
                String authorization = bearer(loginAccessToken());
                UserAccount user = new UserAccount();
                user.setId(UUID.randomUUID());
                user.setUsername("role-target");
                user.setFullName("Role Target");
                user.setEmail("role-target@example.test");
                user.setStatus((short) 1);
                user = userRepository.save(user);
                String userId = user.getId().toString();

                mockMvc.perform(get("/users/{id}/roles", userId).header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$").isEmpty());

                Role firstRole = saveRole("ROLE_FIRST", "First role", (short) 1);
                Role secondRole = saveRole("ROLE_SECOND", "Second role", (short) 1);
                Role inactiveRole = saveRole("ROLE_DISABLED", "Disabled role", (short) 0);
                mockMvc.perform(put("/users/{id}/roles", userId)
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new UserRolesPayload(
                                                List.of(firstRole.getId(), secondRole.getId())))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$.length()").value(2))
                                .andExpect(jsonPath("$[0].id").value(firstRole.getId().toString()));
                org.junit.jupiter.api.Assertions.assertEquals(2,
                                userRepository.findWithRolesById(user.getId()).orElseThrow().getRoles().size());

                mockMvc.perform(put("/users/{id}/roles", userId)
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new UserRolesPayload(
                                                List.of(firstRole.getId(), firstRole.getId())))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("DUPLICATE_ROLE_ID"));

                mockMvc.perform(put("/users/{id}/roles", userId)
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new UserRolesPayload(
                                                List.of(inactiveRole.getId())))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("ROLE_INACTIVE"));

                mockMvc.perform(put("/users/{id}/roles", userId)
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new UserRolesPayload(
                                                List.of(UUID.randomUUID())))))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
                org.junit.jupiter.api.Assertions.assertEquals(2,
                                userRepository.findWithRolesById(user.getId()).orElseThrow().getRoles().size());

                mockMvc.perform(put("/users/{id}/roles", userId)
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new UserRolesPayload(
                                                List.of(secondRole.getId())))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].code").value("ROLE_SECOND"));
                mockMvc.perform(delete("/users/{id}/roles/{roleId}", userId, secondRole.getId())
                                .header("Authorization", authorization))
                                .andExpect(status().isNoContent());
                mockMvc.perform(get("/users/{id}/roles", userId).header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isEmpty());

                Role adminRole = saveRole("ADMIN", "Administrator", (short) 1);
                UserAccount persistedUser = userRepository.findWithRolesById(user.getId()).orElseThrow();
                persistedUser.getRoles().add(adminRole);
                userRepository.save(persistedUser);
                mockMvc.perform(delete("/users/{id}/roles/{roleId}", userId, adminRole.getId())
                                .header("Authorization", authorization))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("SYSTEM_ROLE_ASSIGNMENT_PROTECTED"));
                mockMvc.perform(put("/users/{id}/roles", userId)
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new UserRolesPayload(
                                                List.of()))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("SYSTEM_ROLE_ASSIGNMENT_PROTECTED"));
                assertTrue(userRepository.findWithRolesById(user.getId()).orElseThrow().getRoles().stream()
                                .anyMatch(role -> "ADMIN".equals(role.getCode())));

                mockMvc.perform(get("/users/{id}/roles", UUID.randomUUID())
                                .header("Authorization", authorization))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

                String noPermissionToken = jwtService.createAccessToken(
                                userRepository.findByUsernameIgnoreCase("integration-admin").orElseThrow()
                                                .getId().toString(),
                                "integration-admin",
                                List.of());
                mockMvc.perform(get("/users/{id}/roles", userId)
                                .header("Authorization", bearer(noPermissionToken)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
                mockMvc.perform(put("/users/{id}/roles", userId)
                                .header("Authorization", bearer(noPermissionToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"roleIds\":[]}"))
                                .andExpect(status().isForbidden());
        }

        @Test
        void permissionCrudSupportsSearchValidationAndAuthorization() throws Exception {
                String authorization = bearer(loginAccessToken());
                Permission existing = permissionRepository.findAll().stream()
                                .filter(permission -> "USER_VIEW".equals(permission.getCode()))
                                .findFirst().orElseThrow();

                mockMvc.perform(get("/permissions?keyword=user&page=1&pageSize=10")
                                .header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items").isArray())
                                .andExpect(jsonPath("$.items[0].name").isNotEmpty())
                                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                                .andExpect(jsonPath("$.page").value(1));
                mockMvc.perform(get("/permissions/{id}", existing.getId())
                                .header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("USER_VIEW"))
                                .andExpect(jsonPath("$.status").value(1));

                String createdResponse = mockMvc.perform(post("/permissions")
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"  View invoices  ","code":"invoice_view",
                                                 "description":"  Read invoice data  ","status":1}
                                                """))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value("View invoices"))
                                .andExpect(jsonPath("$.code").value("INVOICE_VIEW"))
                                .andExpect(jsonPath("$.description").value("Read invoice data"))
                                .andReturn().getResponse().getContentAsString();
                UUID createdId = UUID.fromString(objectMapper.readTree(createdResponse).get("id").asText());
                // The existing API maps its POST response before flush; verify the persisted timestamp after commit.
                mockMvc.perform(get("/permissions/{id}", createdId).header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.createdAt").isNotEmpty());

                mockMvc.perform(post("/permissions")
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Duplicate","code":"user_view",
                                                 "description":"Duplicate code","status":1}
                                                """))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("PERMISSION_CODE_EXISTS"));
                mockMvc.perform(post("/permissions")
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"","code":"bad-code","description":"x","status":4}
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

                mockMvc.perform(put("/permissions/{id}", createdId)
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Invoice reader","code":"INVOICE_VIEW",
                                                 "description":"Updated description","status":0}
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("Invoice reader"))
                                .andExpect(jsonPath("$.status").value(0))
                                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
                mockMvc.perform(get("/permissions?code=INVOICE&status=0&page=1")
                                .header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.items[0].id").value(createdId.toString()));

                mockMvc.perform(delete("/permissions/{id}", createdId).header("Authorization", authorization))
                                .andExpect(status().isNoContent());
                mockMvc.perform(get("/permissions/{id}", createdId).header("Authorization", authorization))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("PERMISSION_NOT_FOUND"));
                mockMvc.perform(get("/permissions"))
                                .andExpect(status().isUnauthorized());

                String userId = userRepository.findByUsernameIgnoreCase("integration-admin").orElseThrow()
                                .getId().toString();
                String noPermissionToken = jwtService.createAccessToken(userId, "integration-admin", List.of());
                mockMvc.perform(get("/permissions").header("Authorization", bearer(noPermissionToken)))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post("/permissions")
                                .header("Authorization", bearer(noPermissionToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Denied\",\"code\":\"DENIED\",\"description\":\"Denied\",\"status\":1}"))
                                .andExpect(status().isForbidden());
        }

        @Test
        void rolePermissionEndpointsReplaceRemoveAndProtectAdmin() throws Exception {
                String authorization = bearer(loginAccessToken());
                Role role = saveRole("INVOICE_MANAGER", "Invoice manager", (short) 1);
                Permission first = savePermission("INVOICE_VIEW", "View invoices", (short) 1);
                Permission second = savePermission("INVOICE_CREATE", "Create invoices", (short) 1);
                Permission inactive = savePermission("INVOICE_EXPORT", "Export invoices", (short) 0);

                mockMvc.perform(get("/roles/{id}/permissions", role.getId()).header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isEmpty());
                mockMvc.perform(put("/roles/{id}/permissions", role.getId())
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.frontendbase.api.role.dto.RolePermissionsPayload(
                                                                List.of(first.getId(), second.getId())))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$.length()").value(2))
                                .andExpect(jsonPath("$[0].code").value("INVOICE_CREATE"));

                mockMvc.perform(put("/roles/{id}/permissions", role.getId())
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.frontendbase.api.role.dto.RolePermissionsPayload(
                                                                List.of(first.getId(), first.getId())))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("DUPLICATE_PERMISSION_ID"));
                mockMvc.perform(put("/roles/{id}/permissions", role.getId())
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.frontendbase.api.role.dto.RolePermissionsPayload(
                                                                List.of(UUID.randomUUID())))))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("PERMISSION_NOT_FOUND"));
                mockMvc.perform(put("/roles/{id}/permissions", role.getId())
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.frontendbase.api.role.dto.RolePermissionsPayload(
                                                                List.of(inactive.getId())))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("PERMISSION_INACTIVE"));
                mockMvc.perform(get("/roles/{id}/permissions", role.getId()).header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(2));

                mockMvc.perform(put("/roles/{id}/permissions", role.getId())
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"permissionIds\":[]}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isEmpty());
                mockMvc.perform(put("/roles/{id}/permissions", role.getId())
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.frontendbase.api.role.dto.RolePermissionsPayload(
                                                                List.of(first.getId())))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1));
                mockMvc.perform(delete("/roles/{roleId}/permissions/{permissionId}", role.getId(), first.getId())
                                .header("Authorization", authorization))
                                .andExpect(status().isNoContent());
                mockMvc.perform(delete("/roles/{roleId}/permissions/{permissionId}", role.getId(), first.getId())
                                .header("Authorization", authorization))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("ROLE_PERMISSION_NOT_FOUND"));
                mockMvc.perform(get("/roles/{id}/permissions", UUID.randomUUID())
                                .header("Authorization", authorization))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));

                Role adminRole = saveRole("ADMIN", "Administrator", (short) 1);
                Permission adminPermission = savePermission("ADMIN_TEST_VIEW", "Admin test permission", (short) 1);
                adminRole.getPermissions().add(adminPermission);
                roleRepository.save(adminRole);
                mockMvc.perform(put("/roles/{id}/permissions", adminRole.getId())
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"permissionIds\":[]}"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("SYSTEM_ROLE_PROTECTED"));
                mockMvc.perform(delete("/roles/{roleId}/permissions/{permissionId}",
                                adminRole.getId(), adminPermission.getId()).header("Authorization", authorization))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("SYSTEM_ROLE_PROTECTED"));
                mockMvc.perform(get("/roles/{id}/permissions", adminRole.getId())
                                .header("Authorization", authorization))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1));

                String userId = userRepository.findByUsernameIgnoreCase("integration-admin").orElseThrow()
                                .getId().toString();
                String noPermissionToken = jwtService.createAccessToken(userId, "integration-admin", List.of());
                mockMvc.perform(get("/roles/{id}/permissions", role.getId())
                                .header("Authorization", bearer(noPermissionToken)))
                                .andExpect(status().isForbidden());
                mockMvc.perform(put("/roles/{id}/permissions", role.getId())
                                .header("Authorization", bearer(noPermissionToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"permissionIds\":[]}"))
                                .andExpect(status().isForbidden());
        }

        private Permission savePermission(String code, String name, short permissionStatus) {
                Permission permission = new Permission();
                permission.setId(UUID.randomUUID());
                permission.setName(name);
                permission.setCode(code);
                permission.setDescription(name);
                permission.setStatus(permissionStatus);
                return permissionRepository.save(permission);
        }

        private Role saveRole(String code, String name, short roleStatus) {
                Role role = new Role();
                role.setId(UUID.randomUUID());
                role.setCode(code);
                role.setName(name);
                role.setStatus(roleStatus);
                return roleRepository.save(role);
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
