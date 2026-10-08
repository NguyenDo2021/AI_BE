package com.frontendbase.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.frontendbase.api.user.entity.UserAccount;
import com.frontendbase.api.user.repository.UserRepository;
import com.frontendbase.api.role.entity.Role;
import com.frontendbase.api.role.repository.RoleRepository;
import com.frontendbase.api.security.entity.Permission;
import com.frontendbase.api.security.repository.PermissionRepository;
import com.frontendbase.api.auth.repository.RefreshTokenRepository;
import com.frontendbase.api.warehouse.repository.*;
import com.frontendbase.api.product.repository.ProductRepository;
import com.frontendbase.api.productGroup.repository.ProductGroupRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@org.springframework.context.annotation.Import(H2MigrationConfiguration.class)
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class InventoryApiIntegrationTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate stockJdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired WarehouseRepository warehouses;
    @Autowired UserWarehouseRepository assignments;
    @Autowired ProductGroupRepository groups;
    @Autowired ProductRepository products;
    @Autowired PasswordEncoder encoder;
    String admin, employee, outsider, reader;
    UUID adminId, employeeId, outsiderId;
    final String password = "Inventory-test-123!";

    @BeforeEach void setup() throws Exception {
        StockTestCleanup.clear(stockJdbc);
        assignments.deleteAll(); products.deleteAll(); groups.deleteAll(); warehouses.deleteAll();
        refreshTokens.deleteAll(); users.deleteAll(); roles.deleteAll(); permissions.deleteAll();
        // Admin deliberately has no permissions: new APIs must recognize the live ADMIN role.
        adminId = account("admin-test", "ADMIN", List.of());
        employeeId = account("employee", "EMPLOYEE", List.of("WAREHOUSE_VIEW", "WAREHOUSE_CREATE", "WAREHOUSE_UPDATE",
                "USER_WAREHOUSE_VIEW", "USER_WAREHOUSE_ASSIGN", "PRODUCT_GROUP_VIEW", "PRODUCT_GROUP_CREATE",
                "PRODUCT_GROUP_UPDATE", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE"));
        outsiderId = account("outsider", "OUTSIDER", List.of("WAREHOUSE_VIEW"));
        account("reader", "READER", List.of("WAREHOUSE_VIEW", "PRODUCT_VIEW"));
        admin = login("admin-test"); employee = login("employee"); outsider = login("outsider"); reader = login("reader");
    }
    UUID account(String name, String roleCode, List<String> codes) {
        Role role = new Role(); role.setId(UUID.randomUUID()); role.setName(roleCode); role.setCode(roleCode);
        for (String code : codes) {
            Permission p = permissions.findAll().stream().filter(x -> code.equals(x.getCode())).findFirst().orElseGet(() -> {
                Permission x = new Permission(); x.setId(UUID.randomUUID()); x.setName(code); x.setCode(code); x.setDescription(code);
                return permissions.save(x);
            });
            role.getPermissions().add(p);
        }
        roles.save(role);
        UserAccount user = new UserAccount(); user.setId(UUID.randomUUID()); user.setUsername(name);
        user.setEmail(name + "@test.local"); user.setFullName(name); user.setPasswordHash(encoder.encode(password));
        user.getRoles().add(role); users.save(user); return user.getId();
    }
    String login(String name) throws Exception {
        return json.readTree(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", name, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
    JsonNode ok(MockHttpServletRequestBuilder request, String token, int expected) throws Exception {
        return json.readTree(mvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(value));
    }
    Map<String,Object> catalog(String code, int state) { return new HashMap<>(Map.of("code", code, "name", "Name " + code, "status", state)); }
    String create(String route, String code, String token) throws Exception {
        var response = ok(body(post(route), catalog(code, 1)), token, 201);
        assertTrue(response.hasNonNull("createdAt"));
        return response.get("id").asText();
    }
    void assign(String warehouse, UUID user) throws Exception {
        ok(put("/users/" + user + "/warehouses/" + warehouse), admin, 200);
    }
    @Test void warehouseScopeAndRevocationWithExistingJwt() throws Exception {
        String a = create("/warehouses", "A", admin), b = create("/warehouses", "B", admin);
        assign(a, employeeId); assign(a, outsiderId); assign(b, outsiderId);
        var page = ok(get("/warehouses?pageSize=1"), employee, 200);
        assertEquals(1, page.get("total").asInt()); assertEquals(a, page.get("items").get(0).get("id").asText());
        assertEquals(2, ok(get("/warehouses"), admin, 200).get("total").asInt());
        assertEquals(0, ok(get("/warehouses?keyword=B"), employee, 200).get("total").asInt());
        assertEquals(0, ok(get("/warehouses"), reader, 200).get("total").asInt());
        assertEquals("WAREHOUSE_ACCESS_DENIED", ok(get("/warehouses/" + b), employee, 403).get("code").asText());
        ok(body(put("/warehouses/" + b), catalog("B", 1)), employee, 403);
        ok(put("/users/" + employeeId + "/warehouses/" + b), employee, 403);
        mvc.perform(delete("/users/" + outsiderId + "/warehouses/" + b).header("Authorization", "Bearer " + employee))
                .andExpect(status().isForbidden());
        var visible = ok(get("/users/" + outsiderId + "/warehouses"), employee, 200);
        assertEquals(1, visible.size()); assertEquals(a, visible.get(0).get("id").asText());
        mvc.perform(delete("/users/" + outsiderId + "/warehouses/" + a).header("Authorization", "Bearer " + employee))
                .andExpect(status().isNoContent());
        assertEquals(b, ok(get("/users/me/warehouses"), outsider, 200).get(0).get("id").asText());
        mvc.perform(delete("/users/" + employeeId + "/warehouses/" + a).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        ok(get("/warehouses/" + a), employee, 403);
        assertEquals(0, ok(get("/warehouses"), employee, 200).get("total").asInt());
        assertEquals(0, ok(get("/users/me/warehouses"), employee, 200).size());
        ok(get("/warehouses/" + UUID.randomUUID()), admin, 404);
    }
    @Test void warehouseCreateAssignmentInactiveAndPermissions() throws Exception {
        mvc.perform(get("/warehouses")).andExpect(status().isUnauthorized());
        ok(body(post("/warehouses"), catalog("DENIED", 1)), reader, 403);
        String id = create("/warehouses", "OWN", employee);
        assertTrue(assignments.existsByUserIdAndWarehouseId(employeeId, UUID.fromString(id)));
        assertFalse(assignments.existsByUserIdAndWarehouseId(adminId, UUID.fromString(id)));
        ok(put("/users/" + outsiderId + "/warehouses/" + id), employee, 200);
        ok(put("/users/" + outsiderId + "/warehouses/" + id), employee, 200);
        ok(body(put("/warehouses/" + id), catalog("OWN", 0)), employee, 200);
        ok(get("/warehouses/" + id), outsider, 200);
        ok(put("/users/" + outsiderId + "/warehouses/" + id), employee, 200);
        assertEquals("WAREHOUSE_INACTIVE", ok(put("/users/" + adminId + "/warehouses/" + id), employee, 409).get("code").asText());
        assertEquals(1, ok(get("/warehouses?status=0&keyword=own"), employee, 200).get("total").asInt());
        ok(body(put("/warehouses/" + id), catalog("OWN", 1)), employee, 200);
        ok(put("/users/" + UUID.randomUUID() + "/warehouses/" + id), admin, 404);
        mvc.perform(delete("/users/" + adminId + "/warehouses/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_WAREHOUSE_NOT_FOUND"));
        mvc.perform(delete("/warehouses/" + id).header("Authorization", "Bearer " + admin)).andExpect(status().isMethodNotAllowed());
    }
    @Test void creatingWarehouseDoesNotGrantPermissions() throws Exception {
        UUID creatorId = account("creator", "CREATOR", List.of("WAREHOUSE_CREATE"));
        String token = login("creator");
        String id = create("/warehouses", "NEW", token);
        assertTrue(assignments.existsByUserIdAndWarehouseId(creatorId, UUID.fromString(id)));
        ok(get("/warehouses/" + id), token, 403);
        var me = ok(get("/auth/me"), token, 200);
        assertEquals(1, me.get("permissions").size());
        assertEquals("WAREHOUSE_CREATE", me.get("permissions").get(0).asText());
        assertEquals(id, ok(get("/users/me/warehouses"), token, 200).get(0).get("id").asText());
    }
    @Test void liveAdminAndAccountStatus() throws Exception {
        String id = create("/warehouses", "A", admin);
        var role = roles.findAll().stream().filter(r -> "ADMIN".equals(r.getCode())).findFirst().orElseThrow();
        role.setStatus((short) 0); roles.saveAndFlush(role);
        ok(get("/warehouses/" + id), admin, 403);
        role.setStatus((short) 1); roles.saveAndFlush(role);
        ok(get("/warehouses/" + id), admin, 200);
        var user = users.findById(adminId).orElseThrow(); user.setStatus((short) 0); users.saveAndFlush(user);
        ok(get("/warehouses"), admin, 401);
    }
    @Test void catalogCrudDefaultsFilteringAndInactiveGroups() throws Exception {
        String group = create("/product-groups", "G1", admin);
        String second = create("/product-groups", "G2", admin);
        Map<String,Object> payload = catalog("P1", 1); payload.put("groupId", group);
        var created = ok(body(post("/products"), payload), employee, 201);
        assertTrue(created.hasNonNull("createdAt"));
        String id = created.get("id").asText();
        assertEquals("cay", created.get("unit").asText()); assertEquals(0, created.get("referencePurchasePrice").asInt());
        assertEquals(0, created.get("defaultSalePrice").asInt()); assertEquals(0, created.get("lowStockThreshold").asInt());
        assertFalse(created.has("stock"));
        payload.put("defaultSalePrice", 123456); payload.put("lengthMeters", 2.4); payload.put("lowStockThreshold", 10);
        var updated = ok(body(put("/products/" + id), payload), employee, 200);
        assertEquals(123456, updated.get("defaultSalePrice").asInt()); assertTrue(updated.hasNonNull("updatedAt"));
        ok(get("/products/" + id), reader, 200);
        assertEquals(1, ok(get("/products?groupId=" + group + "&status=1&keyword=p1"), employee, 200).get("total").asInt());
        assertEquals(0, ok(get("/products?groupId=" + second), admin, 200).get("total").asInt());
        ok(body(put("/product-groups/" + group), catalog("G1", 0)), admin, 200);
        ok(body(put("/products/" + id), payload), employee, 200);
        payload.put("code", "NEW");
        assertEquals("PRODUCT_GROUP_INACTIVE", ok(body(post("/products"), payload), admin, 409).get("code").asText());
        payload.put("code", "P1"); payload.put("groupId", second);
        ok(body(put("/products/" + id), payload), admin, 200);
        payload.put("groupId", group);
        ok(body(put("/products/" + id), payload), admin, 409);
        assertEquals(1, ok(get("/product-groups?status=0&keyword=g1"), employee, 200).get("total").asInt());
        payload.put("groupId", second); payload.put("status", 0);
        ok(body(put("/products/" + id), payload), admin, 200);
        ok(body(post("/products"), payload), admin, 409);
        mvc.perform(delete("/products/" + id).header("Authorization", "Bearer " + admin)).andExpect(status().isMethodNotAllowed());
    }
    @Test void validationAndCaseInsensitiveCodes() throws Exception {
        String group = create("/product-groups", "G1", admin);
        for (String route : List.of("/warehouses", "/product-groups")) {
            String id = create(route, " mixed-1 ", admin);
            assertEquals("MIXED-1", ok(get(route + "/" + id), admin, 200).get("code").asText());
            ok(body(post(route), catalog("mixed-1", 1)), admin, 409);
            ok(body(post(route), catalog("bad code", 1)), admin, 400);
            ok(body(post(route), catalog("bad", 2)), admin, 400);
            var p = catalog("BAD", 1); p.put("status", 0.5); ok(body(post(route), p), admin, 400);
            ok(get(route + "?page=0"), admin, 400); ok(get(route + "?pageSize=101"), admin, 400);
            ok(get(route + "?status=2"), admin, 400);
        }
        Map<String,Object> good = catalog("P", 1); good.put("groupId", group);
        for (String field : List.of("referencePurchasePrice", "defaultSalePrice", "lowStockThreshold")) {
            for (Object bad : List.of(-1, 1.5)) {
                var p = new HashMap<>(good); p.put(field, bad); ok(body(post("/products"), p), admin, 400);
            }
        }
        for (Object bad : List.of(0, -1, 1.1234)) {
            var p = new HashMap<>(good); p.put("lengthMeters", bad); ok(body(post("/products"), p), admin, 400);
        }
        var p = new HashMap<>(good); p.put("unit", "met"); ok(body(post("/products"), p), admin, 400);
        p = new HashMap<>(good); p.remove("groupId"); ok(body(post("/products"), p), admin, 400);
        p = new HashMap<>(good); p.put("groupId", UUID.randomUUID()); ok(body(post("/products"), p), admin, 404);
        p = new HashMap<>(good); p.remove("status"); ok(body(post("/products"), p), admin, 400);
        p = new HashMap<>(good); p.put("name", " "); ok(body(post("/products"), p), admin, 400);
        ok(body(post("/products"), good), reader, 403);
    }
}
