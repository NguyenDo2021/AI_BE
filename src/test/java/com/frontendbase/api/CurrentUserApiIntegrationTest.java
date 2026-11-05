package com.frontendbase.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frontendbase.api.auth.dto.AuthUserResponse;
import com.frontendbase.api.auth.repository.RefreshTokenRepository;
import com.frontendbase.api.common.PermissionCodes;
import com.frontendbase.api.config.JwtProperties;
import com.frontendbase.api.role.entity.Role;
import com.frontendbase.api.role.repository.RoleRepository;
import com.frontendbase.api.security.entity.Permission;
import com.frontendbase.api.security.repository.PermissionRepository;
import com.frontendbase.api.security.service.JwtService;
import com.frontendbase.api.user.entity.UserAccount;
import com.frontendbase.api.user.repository.UserRepository;
import com.frontendbase.api.warehouse.repository.UserWarehouseRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Import(H2MigrationConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CurrentUserApiIntegrationTest {
    private static final UUID STAFF_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PASSWORD = "Current-user-test-123!";
    private static final List<String> STAFF_PERMISSIONS = List.of(
            "INVENTORY_VIEW", "PRODUCT_VIEW", "SALES_ORDER_VIEW", "STOCK_RECEIPT_VIEW", "WAREHOUSE_VIEW");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private UserWarehouseRepository warehouses;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwt;
    @Autowired private JwtProperties jwtProperties;

    private Role adminRole;
    private Role staffRole;

    @BeforeEach
    void setUp() {
        StockTestCleanup.clear(jdbc);
        warehouses.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        roles.deleteAll();
        permissions.deleteAll();

        staffRole = role("EMPLOYEE");
        for (String code : STAFF_PERMISSIONS) {
            staffRole.getPermissions().add(permission(code));
        }
        staffRole = roles.save(staffRole);
        adminRole = roles.save(role("ADMIN"));
        String passwordHash = encoder.encode(PASSWORD);
        account(STAFF_ID, "test_nv_1101", staffRole, passwordHash);
        account(ADMIN_ID, "test_admin", adminRole, passwordHash);
    }

    @Test
    void staffWithoutUserViewOrWarehouseGetsFullDirectResponseAndAdminApisRemainForbidden() throws Exception {
        String token = login("test_nv_1101");
        assertThat(jwt.parseAccessToken(token).permissions()).doesNotContain("USER_VIEW", "ROLE_VIEW");
        assertThat(warehouses.count()).isZero();

        me(token).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {
                          "id": "11111111-1111-1111-1111-111111111111",
                          "username": "test_nv_1101",
                          "fullName": "test_nv_1101",
                          "email": "test_nv_1101@example.test",
                          "permissions": ["INVENTORY_VIEW", "PRODUCT_VIEW", "SALES_ORDER_VIEW", "STOCK_RECEIPT_VIEW", "WAREHOUSE_VIEW"],
                          "isAdmin": false
                        }
                        """, JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.isAdmin").isBoolean());

        for (String path : List.of("/users/" + STAFF_ID, "/users/" + STAFF_ID + "/roles")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        mvc.perform(get("/auth/me").param("userId", ADMIN_ID.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(STAFF_ID.toString()))
                .andExpect(jsonPath("$.isAdmin").value(false));
    }

    @Test
    void activeAdminWithoutPermissionsOrWarehouseGetsTrueAndResultsDoNotLeakAcrossUsers() throws Exception {
        String admin = login("test_admin");
        String staff = login("test_nv_1101");
        assertThat(warehouses.count()).isZero();
        assertThat(jwt.parseAccessToken(admin).permissions()).isEmpty();

        me(admin).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {
                          "id": "22222222-2222-2222-2222-222222222222",
                          "username": "test_admin",
                          "fullName": "test_admin",
                          "email": "test_admin@example.test",
                          "permissions": [],
                          "isAdmin": true
                        }
                        """, JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.isAdmin").isBoolean());
        me(staff).andExpect(status().isOk()).andExpect(jsonPath("$.isAdmin").value(false));
        me(admin).andExpect(status().isOk()).andExpect(jsonPath("$.isAdmin").value(true));
    }

    @Test
    void everyPermissionWithoutAdminRoleStillReturnsFalse() throws Exception {
        for (String code : PermissionCodes.ALL) {
            if (!STAFF_PERMISSIONS.contains(code)) {
                staffRole.getPermissions().add(permission(code));
            }
        }
        roles.save(staffRole);
        String token = login("test_nv_1101");
        assertThat(jwt.parseAccessToken(token).permissions()).containsExactlyInAnyOrderElementsOf(PermissionCodes.ALL);
        me(token).andExpect(status().isOk()).andExpect(jsonPath("$.isAdmin").value(false));
    }

    @Test
    void disablingAdminRoleIsVisibleOnNextRequestWithSameToken() throws Exception {
        String token = login("test_admin");
        me(token).andExpect(status().isOk()).andExpect(jsonPath("$.isAdmin").value(true));
        jdbc.update("UPDATE roles SET status=0 WHERE id=?", adminRole.getId());
        me(token).andExpect(status().isOk()).andExpect(jsonPath("$.isAdmin").value(false));
        mvc.perform(get("/warehouses").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void removingAdminAssignmentOverridesStaleAdminClaimsOnNextRequest() throws Exception {
        String token = signedToken(ADMIN_ID, List.of(), List.of("ADMIN"), Instant.now().plusSeconds(300));
        me(token).andExpect(status().isOk()).andExpect(jsonPath("$.isAdmin").value(true));
        // Simulate a committed DB change; the administration API protects the system role.
        jdbc.update("DELETE FROM user_roles WHERE user_id=? AND role_id=?", ADMIN_ID, adminRole.getId());
        me(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.isAdmin").value(false));
        mvc.perform(get("/warehouses").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminClaimsAndClientSuppliedFieldsCannotMakeStaffAdmin() throws Exception {
        String token = signedToken(STAFF_ID, STAFF_PERMISSIONS, List.of("ADMIN"), Instant.now().plusSeconds(300));
        mvc.perform(get("/auth/me").param("isAdmin", "true").param("roles", "ADMIN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAdmin").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "AdMiN"})
    void adminCodeUsesExistingCaseInsensitiveConvention(String code) throws Exception {
        jdbc.update("UPDATE roles SET code=? WHERE id=?", code, adminRole.getId());
        me(login("test_admin")).andExpect(status().isOk()).andExpect(jsonPath("$.isAdmin").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"test_nv_1101", "test_admin"})
    void inactiveUserReturnsUnauthorizedEvenWithPreviouslyValidToken(String username) throws Exception {
        String token = login(username);
        jdbc.update("UPDATE users SET status=0 WHERE username=?", username);
        assertUnauthorized(me(token), "Tài khoản không hoạt động");
    }

    @Test
    void deletedUserReturnsUnauthorizedEvenWithPreviouslyValidToken() throws Exception {
        String token = login("test_admin");
        jdbc.update("DELETE FROM users WHERE id=?", ADMIN_ID);
        assertUnauthorized(me(token), "Tài khoản không hoạt động");
    }

    @Test
    void missingTokenPreservesUnauthorizedEnvelopeAndNoStore() throws Exception {
        assertUnauthorized(mvc.perform(get("/auth/me")), "Phiên đăng nhập hết hạn");
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "wrong-signature", "expired"})
    void invalidTokensPreserveUnauthorizedEnvelopeAndNoStore(String kind) throws Exception {
        String token = switch (kind) {
            case "wrong-signature" -> new JwtService(new JwtProperties(
                    "different-test-secret-with-at-least-32-bytes", 15, 7))
                    .createAccessToken(STAFF_ID.toString(), "test_nv_1101", STAFF_PERMISSIONS);
            case "expired" -> signedToken(STAFF_ID, STAFF_PERMISSIONS, List.of(), Instant.now().minusSeconds(60));
            default -> "invalid-token";
        };
        assertUnauthorized(me(token), "Phiên đăng nhập hết hạn");
    }

    @Test
    void meKeepsDatabasePermissionsWhileBusinessAuthorizationKeepsJwtPermissions() throws Exception {
        String oldToken = login("test_nv_1101");
        Permission userView = permission("USER_VIEW");
        staffRole.getPermissions().add(userView);
        roles.save(staffRole);
        me(oldToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItem("USER_VIEW")))
                .andExpect(jsonPath("$.isAdmin").value(false));
        mvc.perform(get("/users/{id}", STAFF_ID).header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isForbidden());

        String newToken = login("test_nv_1101");
        jdbc.update("DELETE FROM role_permissions WHERE role_id=? AND permission_id=?", staffRole.getId(), userView.getId());
        // Preserve the existing me permission semantics even for inactive roles/permissions.
        jdbc.update("UPDATE roles SET status=0 WHERE id=?", staffRole.getId());
        jdbc.update("UPDATE permissions SET status=0 WHERE code='PRODUCT_VIEW'");
        me(newToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", contains(STAFF_PERMISSIONS.toArray())))
                .andExpect(jsonPath("$.isAdmin").value(false));
        mvc.perform(get("/users/{id}", STAFF_ID).header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void falseIsIncludedEvenIfJacksonDefaultInclusionBecomesNonDefault() throws Exception {
        var response = new AuthUserResponse(STAFF_ID, "staff", "Staff", "staff@example.test", List.of(), false);
        var mapper = json.copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT);
        var serialized = mapper.readTree(mapper.writeValueAsString(response));
        assertThat(serialized.has("isAdmin")).isTrue();
        assertThat(serialized.get("isAdmin").isBoolean()).isTrue();
        assertThat(serialized.get("isAdmin").booleanValue()).isFalse();
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token));
    }

    private void assertUnauthorized(ResultActions result, String message) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.isAdmin").doesNotExist());
    }

    private String login(String username) throws Exception {
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }

    private String signedToken(UUID userId, List<String> codes, List<String> roleCodes, Instant expiresAt) {
        return Jwts.builder().subject(userId.toString())
                .claim("permissions", codes).claim("roles", roleCodes)
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private Role role(String code) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setCode(code);
        role.setName(code);
        return role;
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setId(UUID.randomUUID());
        permission.setCode(code);
        permission.setName(code);
        permission.setDescription(code);
        return permissions.save(permission);
    }

    private void account(UUID id, String username, Role role, String passwordHash) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername(username);
        user.setFullName(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash(passwordHash);
        user.getRoles().add(role);
        users.save(user);
    }
}
