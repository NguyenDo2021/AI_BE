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
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesApiIntegrationTest {
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate stockJdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UserRepository users;
    @Autowired
    RoleRepository roles;
    @Autowired
    PermissionRepository permissions;
    @Autowired
    RefreshTokenRepository refreshTokens;
    @Autowired
    WarehouseRepository warehouses;
    @Autowired
    UserWarehouseRepository assignments;
    @Autowired
    ProductGroupRepository groups;
    @Autowired
    ProductRepository products;
    @Autowired
    PasswordEncoder encoder;
    String admin, employee, outsider, reader;
    UUID adminId, employeeId, outsiderId;
    final String password = "Inventory-test-123!";

    @BeforeEach
    void setup() throws Exception {
        StockTestCleanup.clear(stockJdbc);
        assignments.deleteAll();
        products.deleteAll();
        groups.deleteAll();
        warehouses.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        roles.deleteAll();
        permissions.deleteAll();
        // Admin deliberately has no permissions: new APIs must recognize the live ADMIN
        // role.
        adminId = account("admin-test", "ADMIN", List.of());
        employeeId = account("employee", "EMPLOYEE", List.of("WAREHOUSE_VIEW", "WAREHOUSE_CREATE", "WAREHOUSE_UPDATE",
                "USER_WAREHOUSE_VIEW", "USER_WAREHOUSE_ASSIGN", "PRODUCT_GROUP_VIEW", "PRODUCT_GROUP_CREATE",
                "PRODUCT_GROUP_UPDATE", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE",
                "STOCK_RECEIPT_VIEW", "STOCK_RECEIPT_CREATE", "STOCK_RECEIPT_UPDATE", "STOCK_RECEIPT_CONFIRM",
                "STOCK_RECEIPT_CANCEL", "INVENTORY_VIEW", "INVENTORY_MOVEMENT_VIEW", "CUSTOMER_VIEW", "CUSTOMER_CREATE",
                "CUSTOMER_UPDATE", "SALES_ORDER_VIEW", "SALES_ORDER_CREATE", "SALES_ORDER_UPDATE",
                "SALES_ORDER_CONFIRM", "SALES_ORDER_CANCEL"));
        outsiderId = account("outsider", "OUTSIDER", List.of("WAREHOUSE_VIEW"));
        account("reader", "READER", List.of("WAREHOUSE_VIEW", "PRODUCT_VIEW"));
        admin = login("admin-test");
        employee = login("employee");
        outsider = login("outsider");
        reader = login("reader");
    }

    UUID account(String name, String roleCode, List<String> codes) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(roleCode);
        role.setCode(roleCode);
        for (String code : codes) {
            Permission p = permissions.findAll().stream().filter(x -> code.equals(x.getCode())).findFirst()
                    .orElseGet(() -> {
                        Permission x = new Permission();
                        x.setId(UUID.randomUUID());
                        x.setName(code);
                        x.setCode(code);
                        x.setDescription(code);
                        return permissions.save(x);
                    });
            role.getPermissions().add(p);
        }
        roles.save(role);
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setUsername(name);
        user.setEmail(name + "@test.local");
        user.setFullName(name);
        user.setPasswordHash(encoder.encode(password));
        user.getRoles().add(role);
        users.save(user);
        return user.getId();
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

    Map<String, Object> catalog(String code, int state) {
        return new HashMap<>(Map.of("code", code, "name", "Name " + code, "status", state));
    }

    String create(String route, String code, String token) throws Exception {
        var response = ok(body(post(route), catalog(code, 1)), token, 201);
        assertTrue(response.hasNonNull("createdAt"));
        return response.get("id").asText();
    }

    void assign(String warehouse, UUID user) throws Exception {
        ok(put("/users/" + user + "/warehouses/" + warehouse), admin, 200);
    }

    String a, b, p, p2;

    void fixture() throws Exception {
        a = create("/warehouses", "A", admin);
        b = create("/warehouses", "B", admin);
        assign(a, employeeId);
        String group = create("/product-groups", "G", admin);
        var payload = catalog("P", 1);
        payload.put("groupId", group);
        p = ok(body(post("/products"), payload), admin, 201).get("id").asText();
        payload.put("code", "P2");
        p2 = ok(body(post("/products"), payload), admin, 201).get("id").asText();
    }

    Map<String, Object> payload(String warehouse, long quantity, long price) {
        return new HashMap<>(Map.of("warehouseId", warehouse, "receiptDate", "2026-10-08", "supplierName", "Supplier",
                "lines", List.of(Map.of("productId", p, "quantity", quantity, "unitPrice", price))));
    }

    JsonNode receipt(long qty) throws Exception {
        return ok(body(post("/stock-receipts"), payload(a, qty, 2000)), employee, 201);
    }

    JsonNode confirm(JsonNode receipt) throws Exception {
        return ok(body(post("/stock-receipts/" + receipt.get("id").asText() + "/confirm"),
                Map.of("version", receipt.get("version").asLong())), employee, 200);
    }

    JsonNode cancel(JsonNode receipt) throws Exception {
        return ok(body(post("/stock-receipts/" + receipt.get("id").asText() + "/cancel"),
                Map.of("version", receipt.get("version").asLong(), "reason", "Wrong receipt")), employee, 200);
    }

    long stock(String warehouse, String product) throws Exception {
        return ok(get("/warehouses/" + warehouse + "/inventory?productId=" + product), admin, 200).get("items").get(0)
                .get("quantity").asLong();
    }

    void consistent() {
        Long mismatches = stockJdbc.queryForObject(
                "SELECT count(*) FROM inventory_balances b WHERE b.quantity <> COALESCE((SELECT sum(m.quantity_change) FROM inventory_movements m WHERE m.warehouse_id=b.warehouse_id AND m.product_id=b.product_id),0)",
                Long.class);
        assertEquals(0L, mismatches);
    }

    int requestStatus(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + employee)).andReturn().getResponse().getStatus();
    }

    List<Integer> concurrent(java.util.concurrent.Callable<Integer> first,
            java.util.concurrent.Callable<Integer> second) throws Exception {
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return first.call();
            });
            var two = pool.submit(() -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return second.call();
            });
            return List.of(one.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    two.get(20, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    Map<String, Object> customerPayload(String warehouse, String name, int state) {
        return new HashMap<>(Map.of("warehouseId", warehouse, "name", name, "phone", "0900000000", "address", "Address",
                "status", state));
    }

    JsonNode customer(String warehouse) throws Exception {
        return ok(body(post("/customers"), customerPayload(warehouse, "Customer", 1)), admin, 201);
    }

    Map<String, Object> salePayload(String warehouse, long qty, long price) {
        return new HashMap<>(Map.of("warehouseId", warehouse, "saleDate", "2026-10-09", "lines",
                List.of(Map.of("productId", p, "quantity", qty, "unitPrice", price))));
    }

    JsonNode sale(long qty) throws Exception {
        return ok(body(post("/sales-orders"), salePayload(a, qty, 2000)), employee, 201);
    }

    String route(JsonNode r, String action) {
        return "/sales-orders/" + r.get("id").asText() + action;
    }

    JsonNode saleConfirm(JsonNode r) throws Exception {
        return ok(body(post(route(r, "/confirm")), Map.of("version", r.get("version").asLong())), employee, 200);
    }

    JsonNode saleCancel(JsonNode r) throws Exception {
        return ok(body(post(route(r, "/cancel")),
                Map.of("version", r.get("version").asLong(), "reason", "All goods recovered", "goodsReturned", true)),
                employee, 200);
    }

    long movements() throws Exception {
        return ok(get("/warehouses/" + a + "/inventory-movements"), employee, 200).get("total").asLong();
    }

    @Test
    void lifecycleAndIdempotencyAndReceiptReversalAfterSale() throws Exception {
        fixture();
        var incoming = confirm(receipt(100));
        var d = sale(10);
        assertEquals(100, stock(a, p));
        assertEquals(20000, d.get("subtotal").asLong());
        assertEquals(0, d.get("discountAmount").asLong());
        var c = saleConfirm(d);
        assertEquals(90, stock(a, p));
        assertEquals(1, c.get("version").asLong());
        assertEquals(employeeId.toString(), c.get("confirmedBy").asText());
        assertTrue(c.hasNonNull("customerSnapshot"));
        assertEquals(c, saleConfirm(d));
        assertEquals(90, stock(a, p));
        assertEquals(2, movements());
        ok(body(post("/stock-receipts/" + incoming.get("id").asText() + "/cancel"),
                Map.of("version", 1, "reason", "Reverse")), employee, 409);
        assertEquals(90, stock(a, p));
        var cancelled = saleCancel(c);
        assertEquals(cancelled, saleCancel(c));
        assertEquals(100, stock(a, p));
        assertEquals(2, cancelled.get("version").asLong());
        assertEquals(3, movements());
        ok(body(post(route(d, "/confirm")), Map.of("version", 2)), employee, 409);
        var history = ok(get("/warehouses/" + a + "/inventory-movements?productId=" + p), employee, 200);
        for (var m : history.get("items")) {
            if (m.get("type").asText().startsWith("SALE")) {
                assertFalse(m.hasNonNull("receiptId"));
                assertEquals(d.get("id").asText(), m.get("salesOrderId").asText());
                assertEquals(d.get("code").asText(), m.get("salesOrderCode").asText());
                assertEquals(m.get("type").asText().equals("SALE_CONFIRM") ? -10 : 10,
                        m.get("quantityChange").asLong());
            } else {
                assertEquals(incoming.get("id").asText(), m.get("receiptId").asText());
                assertFalse(m.hasNonNull("salesOrderId"));
            }
        }
        cancel(incoming);
        assertEquals(0, stock(a, p));
        consistent();
    }

    @Test
    void fullDraftUpdateVersionWarehouseAndFinalState() throws Exception {
        fixture();
        confirm(receipt(100));
        var d = sale(10);
        var x = salePayload(a, 20, 3000);
        x.put("discountAmount", 1000);
        x.put("version", 0);
        var updated = ok(body(put(route(d, "")), x), employee, 200);
        assertEquals(60000, updated.get("subtotal").asLong());
        assertEquals(59000, updated.get("totalAmount").asLong());
        assertEquals(100, stock(a, p));
        ok(body(put(route(d, "")), x), employee, 409);
        ok(body(post(route(d, "/confirm")), Map.of("version", 0)), employee, 409);
        ok(body(post(route(d, "/cancel")), Map.of("version", 0, "reason", "Cancel")), employee, 409);
        x.put("version", 1);
        x.put("warehouseId", b);
        ok(body(put(route(d, "")), x), employee, 409);
        x.remove("warehouseId");
        var noDiscount = ok(
                body(put(route(d, "")),
                        new HashMap<>(Map.of("saleDate", "2026-10-09", "version", 1, "lines", x.get("lines")))),
                employee, 200);
        assertEquals(0, noDiscount.get("discountAmount").asLong());
        var confirmed = saleConfirm(noDiscount);
        x.put("version", 3);
        ok(body(put(route(d, "")), x), employee, 409);
        saleCancel(confirmed);
        ok(body(put(route(d, "")), x), employee, 409);
        ok(delete(route(d, "")), employee, 405);
        consistent();
    }

    @Test
    void draftCancelAndReturnedGoodsConfirmationRequired() throws Exception {
        fixture();
        confirm(receipt(100));
        var d = sale(10);
        ok(body(post(route(d, "/cancel")), Map.of("version", 0, "reason", " ")), employee, 400);
        var dc = ok(body(post(route(d, "/cancel")), Map.of("version", 0, "reason", "No longer needed")), employee, 200);
        assertEquals("CANCELLED", dc.get("status").asText());
        assertEquals(100, stock(a, p));
        assertEquals(1, movements());
        var c = saleConfirm(sale(10));
        for (var value : List.of(Map.of("version", 1, "reason", "Recover"),
                Map.of("version", 1, "reason", "Recover", "goodsReturned", false),
                Map.of("version", 1, "goodsReturned", true)))
            ok(body(post(route(c, "/cancel")), value), employee, 400);
        assertEquals(90, stock(a, p));
        assertEquals(2, movements());
        saleCancel(c);
        consistent();
    }

    @Test
    void concurrentDifferentSalesCannotOversell() throws Exception {
        fixture();
        confirm(receipt(100));
        var one = sale(70);
        var two = sale(70);
        var result = concurrent(() -> requestStatus(body(post(route(one, "/confirm")), Map.of("version", 0))),
                () -> requestStatus(body(post(route(two, "/confirm")), Map.of("version", 0))));
        assertTrue(result.equals(List.of(200, 409)) || result.equals(List.of(409, 200)), result.toString());
        assertEquals(30, stock(a, p));
        assertEquals(2, movements());
        consistent();
    }

    @Test
    void simultaneousConfirmationAndCancellationApplyOnce() throws Exception {
        fixture();
        confirm(receipt(100));
        var d = sale(10);
        assertEquals(List.of(200, 200),
                concurrent(() -> requestStatus(body(post(route(d, "/confirm")), Map.of("version", 0))),
                        () -> requestStatus(body(post(route(d, "/confirm")), Map.of("version", 0)))));
        assertEquals(90, stock(a, p));
        assertEquals(2, movements());
        assertEquals(List.of(200, 200),
                concurrent(
                        () -> requestStatus(body(post(route(d, "/cancel")),
                                Map.of("version", 1, "reason", "Recover", "goodsReturned", true))),
                        () -> requestStatus(body(post(route(d, "/cancel")),
                                Map.of("version", 1, "reason", "Recover", "goodsReturned", true)))));
        assertEquals(100, stock(a, p));
        assertEquals(3, movements());
        consistent();
    }

    @Test
    void confirmRacingEditAndCancel() throws Exception {
        fixture();
        confirm(receipt(100));
        var d = sale(10);
        var x = salePayload(a, 20, 1);
        x.put("version", 0);
        var result = concurrent(() -> requestStatus(body(post(route(d, "/confirm")), Map.of("version", 0))),
                () -> requestStatus(body(put(route(d, "")), x)));
        assertTrue(result.equals(List.of(200, 409)) || result.equals(List.of(409, 200)), result.toString());
        var latest = ok(get(route(d, "")), employee, 200);
        if (latest.get("status").asText().equals("DRAFT"))
            latest = saleConfirm(latest);
        assertEquals(100 - latest.get("lines").get(0).get("quantity").asLong(), stock(a, p));
        var other = sale(10);
        long before = stock(a, p);
        result = concurrent(() -> requestStatus(body(post(route(other, "/confirm")), Map.of("version", 0))),
                () -> requestStatus(body(post(route(other, "/cancel")),
                        Map.of("version", 0, "reason", "Recover", "goodsReturned", true))));
        assertTrue(result.equals(List.of(200, 409)) || result.equals(List.of(409, 200)), result.toString());
        var after = ok(get(route(other, "")), employee, 200);
        assertEquals(before - (after.get("status").asText().equals("CONFIRMED") ? 10 : 0), stock(a, p));
        consistent();
    }

    @Test
    void oneInsufficientLineRejectsWholeSale() throws Exception {
        fixture();
        confirm(receipt(100));
        var x = salePayload(a, 10, 1);
        x.put("lines", List.of(Map.of("productId", p, "quantity", 10, "unitPrice", 1),
                Map.of("productId", p2, "quantity", 1, "unitPrice", 1)));
        var d = ok(body(post("/sales-orders"), x), employee, 201);
        assertEquals("INSUFFICIENT_STOCK",
                ok(body(post(route(d, "/confirm")), Map.of("version", 0)), employee, 409).get("code").asText());
        assertEquals(100, stock(a, p));
        assertEquals(0, stock(a, p2));
        assertEquals(1, movements());
        var after = ok(get(route(d, "")), employee, 200);
        assertEquals("DRAFT", after.get("status").asText());
        assertEquals(0, after.get("version").asLong());
        consistent();
    }

    @Test
    void injectedSecondMovementFailureRollsBack() throws Exception {
        fixture();
        var x = payload(a, 100, 1);
        x.put("lines", List.of(Map.of("productId", p, "quantity", 100, "unitPrice", 1),
                Map.of("productId", p2, "quantity", 100, "unitPrice", 1)));
        confirm(ok(body(post("/stock-receipts"), x), employee, 201));
        x = salePayload(a, 10, 1);
        x.put("lines", List.of(Map.of("productId", p, "quantity", 10, "unitPrice", 1),
                Map.of("productId", p2, "quantity", 10, "unitPrice", 1)));
        var d = ok(body(post("/sales-orders"), x), employee, 201);
        String last = UUID.fromString(p).compareTo(UUID.fromString(p2)) > 0 ? p : p2;
        stockJdbc.execute(
                "ALTER TABLE inventory_movements ADD CONSTRAINT test_sale_failure CHECK (type <> 'SALE_CONFIRM' OR product_id <> '"
                        + last + "')");
        try {
            ok(body(post(route(d, "/confirm")), Map.of("version", 0)), employee, 409);
            assertEquals(100, stock(a, p));
            assertEquals(100, stock(a, p2));
            assertEquals(2, movements());
            var after = ok(get(route(d, "")), employee, 200);
            assertEquals("DRAFT", after.get("status").asText());
            assertEquals(0, after.get("version").asLong());
            assertFalse(after.hasNonNull("customerSnapshot"));
        } finally {
            stockJdbc.execute("ALTER TABLE inventory_movements DROP CONSTRAINT test_sale_failure");
        }
        saleConfirm(d);
        consistent();
    }

    @Test
    void customerCrudValidationSearchAndImmutableWarehouse() throws Exception {
        fixture();
        var c = customer(a);
        customer(a);
        customer(b);
        String id = c.get("id").asText();
        assertTrue(c.get("code").asText().startsWith("KH-"));
        assertTrue(c.hasNonNull("updatedAt"));
        assertEquals(2,
                ok(get("/customers?keyword=CUSTOMER&status=1&pageSize=1"), employee, 200).get("total").asLong());
        assertEquals(3, ok(get("/customers"), admin, 200).get("total").asLong());
        var x = customerPayload(a, "Renamed", 0);
        x.remove("warehouseId");
        var edited = ok(body(put("/customers/" + id), x), employee, 200);
        assertEquals(c.get("code"), edited.get("code"));
        assertEquals(c.get("createdAt"), edited.get("createdAt"));
        assertEquals(1, ok(get("/customers?keyword=renamed&status=0"), employee, 200).get("total").asLong());
        assertEquals(0, ok(get("/customers?keyword=%"), employee, 200).get("total").asLong());
        x.put("warehouseId", b);
        ok(body(put("/customers/" + id), x), employee, 409);
        for (var bad : List.of("", " ", "x".repeat(161))) {
            var y = customerPayload(a, bad, 1);
            ok(body(post("/customers"), y), employee, 400);
        }
        for (var field : List.of("phone", "address", "note")) {
            var y = customerPayload(a, "Valid", 1);
            y.put(field, "x".repeat(field.equals("phone") ? 21 : field.equals("address") ? 501 : 2001));
            ok(body(post("/customers"), y), employee, 400);
        }
        for (var state : List.of(2, -1, "1", 1.0)) {
            var y = customerPayload(a, "Valid", 1);
            y.put("status", state);
            ok(body(post("/customers"), y), employee, 400);
        }
        ok(delete("/customers/" + id), employee, 405);
        ok(get("/customers?status=2"), employee, 400);
    }

    @Test
    void customerWarehouseMismatchAndActiveRechecksAndSnapshot() throws Exception {
        fixture();
        confirm(receipt(100));
        var c = customer(a);
        var other = customer(b);
        var x = salePayload(a, 10, 2000);
        x.put("customerId", other.get("id").asText());
        assertEquals("CUSTOMER_WAREHOUSE_MISMATCH",
                ok(body(post("/sales-orders"), x), employee, 409).get("code").asText());
        x.put("customerId", c.get("id").asText());
        var d = ok(body(post("/sales-orders"), x), employee, 201);
        var cp = customerPayload(a, "At confirmation", 0);
        ok(body(put("/customers/" + c.get("id").asText()), cp), admin, 200);
        ok(body(post("/sales-orders"), x), employee, 409);
        x.put("version", 0);
        ok(body(put(route(d, "")), x), employee, 409);
        assertEquals("CUSTOMER_INACTIVE",
                ok(body(post(route(d, "/confirm")), Map.of("version", 0)), employee, 409).get("code").asText());
        cp.put("status", 1);
        ok(body(put("/customers/" + c.get("id").asText()), cp), admin, 200);
        var product = products.findById(UUID.fromString(p)).orElseThrow();
        product.setCode("CONFIRM-CODE");
        product.setName("At confirmation");
        products.saveAndFlush(product);
        var confirmed = saleConfirm(d);
        assertEquals("At confirmation", confirmed.get("customerSnapshot").get("name").asText());
        cp.put("name", "Later");
        cp.put("status", 0);
        ok(body(put("/customers/" + c.get("id").asText()), cp), admin, 200);
        product.setCode("LATER");
        product.setStatus((short) 0);
        products.saveAndFlush(product);
        var wh = warehouses.findById(UUID.fromString(a)).orElseThrow();
        wh.setStatus((short) 0);
        warehouses.saveAndFlush(wh);
        assertEquals(confirmed, saleConfirm(d));
        var historical = ok(get(route(d, "")), employee, 200);
        assertEquals("At confirmation", historical.get("customerSnapshot").get("name").asText());
        assertEquals("CONFIRM-CODE", historical.get("lines").get(0).get("productCode").asText());
        saleCancel(confirmed);
        assertEquals(100, stock(a, p));
        consistent();
    }

    @Test
    void inactiveWarehouseAndProductBlockConfirmation() throws Exception {
        fixture();
        confirm(receipt(100));
        var d = sale(10);
        var product = products.findById(UUID.fromString(p)).orElseThrow();
        product.setStatus((short) 0);
        products.saveAndFlush(product);
        ok(body(post(route(d, "/confirm")), Map.of("version", 0)), employee, 409);
        ok(body(post("/sales-orders"), salePayload(a, 1, 1)), employee, 409);
        var x = salePayload(a, 1, 1);
        x.put("version", 0);
        ok(body(put(route(d, "")), x), employee, 409);
        product.setStatus((short) 1);
        products.saveAndFlush(product);
        var wh = warehouses.findById(UUID.fromString(a)).orElseThrow();
        wh.setStatus((short) 0);
        warehouses.saveAndFlush(wh);
        ok(body(post(route(d, "/confirm")), Map.of("version", 0)), employee, 409);
        ok(body(post("/sales-orders"), salePayload(a, 1, 1)), employee, 409);
        ok(body(put(route(d, "")), x), employee, 409);
        saleCancel(d);
        assertEquals(100, stock(a, p));
        consistent();
    }

    @Test
    void scopePermissionsRevocationAndSalesOnlyEmployee() throws Exception {
        fixture();
        confirm(receipt(100));
        var c = customer(a);
        var cb = customer(b);
        var d = sale(10);
        var db = ok(body(post("/sales-orders"), salePayload(b, 1, 1)), admin, 201);
        assertEquals(1, ok(get("/sales-orders?pageSize=1"), employee, 200).get("total").asLong());
        assertEquals(2, ok(get("/sales-orders"), admin, 200).get("total").asLong());
        for (var path : List.of(route(db, ""), "/sales-orders?warehouseId=" + b, "/customers/" + cb.get("id").asText(),
                "/customers?warehouseId=" + b))
            ok(get(path), employee, 403);
        ok(body(post("/sales-orders"), salePayload(b, 1, 1)), employee, 403);
        ok(body(post("/customers"), customerPayload(b, "X", 1)), employee, 403);
        ok(body(put("/customers/" + cb.get("id").asText()), customerPayload(b, "X", 1)), employee, 403);
        for (var action : List.of("confirm", "cancel"))
            ok(body(post(route(db, "/" + action)), Map.of("version", 0, "reason", "Cancel", "goodsReturned", true)),
                    employee, 403);
        var edit = salePayload(b, 1, 1);
        edit.put("version", 0);
        ok(body(put(route(db, "")), edit), employee, 403);
        assign(a, outsiderId);
        for (var path : List.of("/sales-orders", route(d, ""), "/customers", "/customers/" + c.get("id").asText()))
            ok(get(path), outsider, 403);
        ok(body(post("/sales-orders"), salePayload(a, 1, 1)), outsider, 403);
        edit.put("warehouseId", a);
        ok(body(put(route(d, "")), edit), outsider, 403);
        ok(body(post("/customers"), customerPayload(a, "X", 1)), outsider, 403);
        ok(body(put("/customers/" + c.get("id").asText()), customerPayload(a, "X", 1)), outsider, 403);
        for (var action : List.of("confirm", "cancel"))
            ok(body(post(route(d, "/" + action)), Map.of("version", 0, "reason", "Cancel", "goodsReturned", true)),
                    outsider, 403);
        UUID sellerId = account("seller", "SELLER", List.of("SALES_ORDER_CONFIRM"));
        assign(a, sellerId);
        String seller = login("seller");
        var confirmed = ok(body(post(route(d, "/confirm")), Map.of("version", 0)), seller, 200);
        assertEquals(sellerId.toString(), confirmed.get("confirmedBy").asText());
        ok(get(route(d, "")), seller, 403);
        ok(body(post("/stock-receipts"), payload(a, 1, 1)), seller, 403);
        mvc.perform(delete("/users/" + employeeId + "/warehouses/" + a).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertEquals(0, ok(get("/sales-orders"), employee, 200).get("total").asLong());
        assertEquals(0, ok(get("/customers"), employee, 200).get("total").asLong());
        for (var path : List.of(route(d, ""), "/customers/" + c.get("id").asText()))
            ok(get(path), employee, 403);
        for (var action : List.of("confirm", "cancel"))
            ok(body(post(route(d, "/" + action)), Map.of("version", 1, "reason", "Cancel", "goodsReturned", true)),
                    employee, 403);
        ok(body(put(route(d, "")), edit), employee, 403);
        ok(body(post("/sales-orders"), salePayload(a, 1, 1)), employee, 403);
        ok(body(put("/customers/" + c.get("id").asText()), customerPayload(a, "X", 1)), employee, 403);
        ok(body(post("/customers"), customerPayload(a, "X", 1)), employee, 403);
    }

    @Test
    void strictNumbersLimitsAndServerComputedTotals() throws Exception {
        fixture();
        for (var qty : List.of(0, -1, 1.5, 1.0, "1")) {
            var x = salePayload(a, 1, 1);
            x.put("lines", List.of(Map.of("productId", p, "quantity", qty, "unitPrice", 1)));
            ok(body(post("/sales-orders"), x), employee, 400);
        }
        for (var price : List.of(-1, 1.5, 1.0, "1")) {
            var x = salePayload(a, 1, 1);
            x.put("lines", List.of(Map.of("productId", p, "quantity", 1, "unitPrice", price)));
            ok(body(post("/sales-orders"), x), employee, 400);
        }
        for (var discount : List.of(-1, 1.5, 1.0, "1", 11)) {
            var x = salePayload(a, 1, 10);
            x.put("discountAmount", discount);
            ok(body(post("/sales-orders"), x), employee, 400);
        }
        var x = salePayload(a, 1, 1);
        x.put("lines", List.of());
        ok(body(post("/sales-orders"), x), employee, 400);
        x.put("lines", Collections.nCopies(1001, Map.of("productId", p, "quantity", 1, "unitPrice", 1)));
        ok(body(post("/sales-orders"), x), employee, 400);
        x.put("lines", List.of(Map.of("productId", p, "quantity", 1, "unitPrice", 1),
                Map.of("productId", p, "quantity", 1, "unitPrice", 2)));
        ok(body(post("/sales-orders"), x), employee, 400);
        ok(body(post("/sales-orders"), salePayload(a, Long.MAX_VALUE, 2)), employee, 409);
        x.put("lines", List.of(Map.of("productId", p, "quantity", 1, "unitPrice", Long.MAX_VALUE),
                Map.of("productId", p2, "quantity", 1, "unitPrice", 1)));
        ok(body(post("/sales-orders"), x), employee, 409);
        var invalid = salePayload(a, 1, 1);
        invalid.put("lines", List.of(
                Map.of("productId", p, "quantity", new java.math.BigInteger("9223372036854775808"), "unitPrice", 0)));
        ok(body(post("/sales-orders"), invalid), employee, 400);
        x = salePayload(a, 1, 10);
        x.put("totalAmount", 1);
        x.put("subtotal", 1);
        x.put("createdBy", adminId);
        x.put("discountAmount", 2);
        var d = ok(body(post("/sales-orders"), x), employee, 201);
        assertEquals(10, d.get("subtotal").asLong());
        assertEquals(8, d.get("totalAmount").asLong());
        assertEquals(employeeId.toString(), d.get("createdBy").asText());
        for (var ver : List.of(-1, 1.0, "0"))
            ok(body(post(route(d, "/confirm")), Map.of("version", ver)), employee, 400);
        assertEquals(0, products.findById(UUID.fromString(p)).orElseThrow().getDefaultSalePrice().longValue());
        var free = salePayload(a, Long.MAX_VALUE, 0);
        var max = ok(body(post("/sales-orders"), free), employee, 201);
        assertEquals(0, max.get("totalAmount").asLong());
    }

    @Test
    void cancellationOverflowRollsBackAndLongMaxSaleSucceeds() throws Exception {
        fixture();
        confirm(ok(body(post("/stock-receipts"), payload(a, Long.MAX_VALUE, 0)), employee, 201));
        var d = ok(body(post("/sales-orders"), salePayload(a, Long.MAX_VALUE, 0)), employee, 201);
        var c = saleConfirm(d);
        assertEquals(0, stock(a, p));
        confirm(receipt(1));
        assertEquals("NUMERIC_OVERFLOW",
                ok(body(post(route(c, "/cancel")), Map.of("version", 1, "reason", "Recover", "goodsReturned", true)),
                        employee, 409).get("code").asText());
        assertEquals(1, stock(a, p));
        assertEquals(3, movements());
        assertEquals("CONFIRMED", ok(get(route(c, "")), employee, 200).get("status").asText());
        consistent();
    }

    @Test
    void listFiltersInclusiveDatesAndPaginationAndMissingEntities() throws Exception {
        fixture();
        var c = customer(a);
        var x = salePayload(a, 1, 1);
        x.put("customerId", c.get("id").asText());
        var d = ok(body(post("/sales-orders"), x), employee, 201);
        sale(2);
        assertEquals(1,
                ok(get("/sales-orders?customerId=" + c.get("id").asText()
                        + "&from=2026-10-09&to=2026-10-09&status=DRAFT&pageSize=1"), employee, 200).get("total")
                        .asLong());
        assertEquals(0, ok(get("/sales-orders?to=2026-10-08"), employee, 200).get("total").asLong());
        assertEquals(2, ok(get("/sales-orders?page=3&pageSize=1"), employee, 200).get("total").asLong());
        assertEquals(0, ok(get("/sales-orders?page=3&pageSize=1"), employee, 200).get("items").size());
        for (var path : List.of("/sales-orders", "/customers")) {
            ok(get(path + "?page=0"), employee, 400);
            ok(get(path + "?pageSize=101"), employee, 400);
            ok(get(path + "/" + UUID.randomUUID()), employee, 404);
        }
        ok(get("/sales-orders?from=2026-10-10&to=2026-10-09"), employee, 400);
        ok(get("/sales-orders?status=WRONG"), employee, 400);
        x.put("customerId", UUID.randomUUID());
        ok(body(post("/sales-orders"), x), employee, 404);
        x.remove("customerId");
        x.put("lines", List.of(Map.of("productId", UUID.randomUUID(), "quantity", 1, "unitPrice", 1)));
        ok(body(post("/sales-orders"), x), employee, 404);
        ok(get("/sales-orders"), "invalid-token", 401);
    }

    @Test
    void postgresAuditGuardsAndMovementUniqueness() throws Exception {
        String db = stockJdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) c -> c.getMetaData()
                .getDatabaseProductName());
        org.junit.jupiter.api.Assumptions.assumeTrue("PostgreSQL".equals(db), "PL/pgSQL guards require PostgreSQL");
        fixture();
        confirm(receipt(100));
        var c = customer(a);
        var x = salePayload(a, 10, 1);
        x.put("customerId", c.get("id").asText());
        var d = ok(body(post("/sales-orders"), x), employee, 201);
        var confirmed = saleConfirm(d);
        UUID id = UUID.fromString(d.get("id").asText());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> stockJdbc
                .update("UPDATE sales_order_lines SET product_name='Tampered' WHERE sales_order_id=?", id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> stockJdbc
                .update("UPDATE sales_orders SET customer_name='Tampered',version=version+1 WHERE id=?", id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> stockJdbc.update("DELETE FROM sales_orders WHERE id=?", id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> stockJdbc.update("DELETE FROM customers WHERE id=?", UUID.fromString(c.get("id").asText())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> stockJdbc
                .update("UPDATE inventory_movements SET quantity_change=-20 WHERE sales_order_id=?", id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> stockJdbc.update(
                "INSERT INTO inventory_movements SELECT CAST(? AS UUID),warehouse_id,product_id,quantity_change,type,receipt_id,performed_by,performed_at,sales_order_id FROM inventory_movements WHERE sales_order_id=?",
                UUID.randomUUID(), id));
        saleCancel(confirmed);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> stockJdbc.update("UPDATE sales_orders SET note='Tampered',version=version+1 WHERE id=?", id));
        consistent();
    }

    // Payment fixtures reuse sales/stock setup and exercise the complete HTTP
    // contract.
    void paymentFixture() throws Exception {
        fixture();
        confirm(receipt(100));
        UUID cashier = account("cashier", "CASHIER",
                List.of("PAYMENT_VIEW", "PAYMENT_CREATE", "PAYMENT_CANCEL", "RECEIVABLE_VIEW"));
        assign(a, cashier);
        payer = login("cashier");
        payerId = cashier;
    }

    String payer;
    UUID payerId;

    Map<String, Object> paymentPayload(Object amount) {
        return new HashMap<>(
                Map.of("amount", amount, "paymentDate", "2026-10-09", "method", "CASH", "note", "First collection"));
    }

    MockHttpServletRequestBuilder paymentRequest(JsonNode order, String key, Object amount) throws Exception {
        return body(post(route(order, "/payments")).header("Idempotency-Key", key), paymentPayload(amount));
    }

    JsonNode collect(JsonNode order, long amount, String key) throws Exception {
        return ok(paymentRequest(order, key, amount), payer, 201);
    }

    JsonNode paymentCancel(JsonNode result) throws Exception {
        return ok(body(post("/payments/" + result.get("payment").get("id").asText() + "/cancel"),
                Map.of("reason", "Incorrect recording")), payer, 200);
    }

    JsonNode pricedOrder(long amount, JsonNode customer) throws Exception {
        var payload = salePayload(a, 1, amount);
        if (customer != null)
            payload.put("customerId", customer.get("id").asText());
        return saleConfirm(ok(body(post("/sales-orders"), payload), employee, 201));
    }

    int payerStatus(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + payer)).andReturn().getResponse().getStatus();
    }

    @Test
    void paymentLifecycleIdempotencyCurrentTotalsAndStockIsolation() throws Exception {
        paymentFixture();
        var customer = customer(a);
        var order = pricedOrder(1000000, customer);
        assertEquals("UNPAID", order.get("paymentStatus").asText());
        assertEquals(1000000, order.get("remainingAmount").asLong());
        long stockBefore = stock(a, p), movementBefore = movements();
        String key = UUID.randomUUID().toString();
        var first = collect(order, 600000, key);
        assertEquals(600000, first.get("order").get("paidAmount").asLong());
        assertEquals(400000, first.get("order").get("remainingAmount").asLong());
        assertEquals(payerId.toString(), first.get("payment").get("createdBy").asText());
        assertEquals(first.get("payment").get("id"), collect(order, 600000, key).get("payment").get("id"));
        assertEquals("IDEMPOTENCY_CONFLICT", ok(paymentRequest(order, key, 600001), payer, 409).get("code").asText());
        var changed = paymentPayload(600000);
        changed.put("note", "Changed");
        ok(body(post(route(order, "/payments")).header("Idempotency-Key", key), changed), payer, 409);
        assertEquals("PAYMENT_EXCEEDS_REMAINING",
                ok(paymentRequest(order, UUID.randomUUID().toString(), 400001), payer, 409).get("code").asText());
        var second = collect(order, 400000, UUID.randomUUID().toString());
        assertEquals("PAID", second.get("order").get("paymentStatus").asText());
        assertEquals(0, second.get("order").get("remainingAmount").asLong());
        assertEquals("SALES_ORDER_HAS_PAYMENTS", ok(body(post(route(order, "/cancel")),
                Map.of("version", 1, "reason", "Cancel", "goodsReturned", true)), employee, 409).get("code").asText());
        var cancelled = paymentCancel(second);
        assertEquals(400000, cancelled.get("order").get("remainingAmount").asLong());
        assertEquals(cancelled, paymentCancel(second));
        paymentCancel(first);
        var retry = collect(order, 600000, key);
        assertEquals("CANCELLED", retry.get("payment").get("status").asText());
        assertEquals(1000000, retry.get("order").get("remainingAmount").asLong());
        assertEquals(2, ok(get(route(order, "/payments")), payer, 200).get("total").asLong());
        assertEquals(stockBefore, stock(a, p));
        assertEquals(movementBefore, movements());
        saleCancel(order);
        assertEquals(2, ok(get(route(order, "/payments")), payer, 200).get("total").asLong());
        var historicalRetry = collect(order, 600000, key);
        assertEquals(0, historicalRetry.get("order").get("remainingAmount").asLong());
        assertFalse(historicalRetry.get("order").hasNonNull("paymentStatus"));
        ok(paymentRequest(order, UUID.randomUUID().toString(), 1), payer, 409);
    }

    @Test
    void concurrentPaymentsAndSameKeyDoNotOverCollect() throws Exception {
        paymentFixture();
        var order = pricedOrder(1000000, null);
        var result = concurrent(
                () -> payerStatus(paymentRequest(order, UUID.randomUUID().toString(), 600000)),
                () -> payerStatus(paymentRequest(order, UUID.randomUUID().toString(), 600000)));
        assertTrue(result.equals(List.of(201, 409)) || result.equals(List.of(409, 201)), result.toString());
        assertEquals(600000, ok(get(route(order, "")), employee, 200).get("paidAmount").asLong());
        String key = UUID.randomUUID().toString();
        assertEquals(List.of(201, 201), concurrent(
                () -> payerStatus(paymentRequest(order, key, 400000)),
                () -> payerStatus(paymentRequest(order, key, 400000))));
        assertEquals(2, ok(get(route(order, "/payments")), payer, 200).get("total").asLong());
        assertEquals(1000000, ok(get(route(order, "")), employee, 200).get("paidAmount").asLong());
    }

    @Test
    void concurrentPaymentAndOrderCancellationAreExclusive() throws Exception {
        paymentFixture();
        var order = pricedOrder(1000000, null);
        long stockBefore = stock(a, p);
        var result = concurrent(
                () -> payerStatus(paymentRequest(order, UUID.randomUUID().toString(), 600000)),
                () -> requestStatus(body(post(route(order, "/cancel")),
                        Map.of("version", 1, "reason", "Cancel", "goodsReturned", true))));
        assertTrue(result.equals(List.of(201, 409)) || result.equals(List.of(409, 200)), result.toString());
        var current = ok(get(route(order, "")), employee, 200);
        if (current.get("status").asText().equals("CANCELLED")) {
            assertEquals(0, current.get("paidAmount").asLong());
            assertEquals(stockBefore + 1, stock(a, p));
        } else {
            assertEquals(600000, current.get("paidAmount").asLong());
            assertEquals(stockBefore, stock(a, p));
        }
        consistent();
    }

    @Test
    void concurrentCancellationReversesPaymentOnceAndKeysAreActorWide() throws Exception {
        paymentFixture();
        var one = pricedOrder(100, null);
        var two = pricedOrder(100, null);
        String key = UUID.randomUUID().toString();
        var result = concurrent(
                () -> payerStatus(paymentRequest(one, key, 60)),
                () -> payerStatus(paymentRequest(two, key, 60)));
        assertTrue(result.equals(List.of(201, 409)) || result.equals(List.of(409, 201)), result.toString());
        var payment = ok(get("/payments"), payer, 200).get("items").get(0);
        String cancel = "/payments/" + payment.get("id").asText() + "/cancel";
        assertEquals(List.of(200, 200), concurrent(
                () -> payerStatus(body(post(cancel), Map.of("reason", "Wrong record"))),
                () -> payerStatus(body(post(cancel), Map.of("reason", "Wrong record")))));
        assertEquals(0, ok(get(route(one, "")), employee, 200).get("paidAmount").asLong());
        assertEquals(0, ok(get(route(two, "")), employee, 200).get("paidAmount").asLong());
        // A different actor can use the same key.
        ok(paymentRequest(one, key, 20), admin, 201);
        assertEquals(2, ok(get("/payments"), payer, 200).get("total").asLong());
    }

    @Test
    void paymentValidationZeroDraftCancelledLongLimitsAndRollback() throws Exception {
        paymentFixture();
        var draft = sale(1);
        ok(paymentRequest(draft, UUID.randomUUID().toString(), 1), payer, 409);
        saleCancel(draft);
        ok(paymentRequest(draft, UUID.randomUUID().toString(), 1), payer, 409);
        var free = pricedOrder(0, null);
        assertEquals("PAID", free.get("paymentStatus").asText());
        ok(paymentRequest(free, UUID.randomUUID().toString(), 1), payer, 409);
        var order = pricedOrder(Long.MAX_VALUE, null);
        for (Object amount : List.of(0, -1, 1.0, 1.5, "1", new java.math.BigInteger("9223372036854775808")))
            ok(paymentRequest(order, UUID.randomUUID().toString(), amount), payer, 400);
        for (String key : List.of("bad", "1-1-1-1-1", ""))
            assertEquals("INVALID_IDEMPOTENCY_KEY", ok(paymentRequest(order, key, 1), payer, 400).get("code").asText());
        ok(body(post(route(order, "/payments")), paymentPayload(1)), payer, 400);
        for (String field : List.of("warehouseId", "customerId", "createdBy", "actor")) {
            var payload = paymentPayload(1);
            payload.put(field, UUID.randomUUID());
            ok(body(post(route(order, "/payments")).header("Idempotency-Key", UUID.randomUUID().toString()), payload),
                    payer, 400);
        }
        var payload = paymentPayload(1);
        payload.put("method", "CARD");
        ok(body(post(route(order, "/payments")).header("Idempotency-Key", UUID.randomUUID().toString()), payload),
                payer, 400);
        payload = paymentPayload(1);
        payload.put("paymentDate", "bad-date");
        ok(body(post(route(order, "/payments")).header("Idempotency-Key", UUID.randomUUID().toString()), payload),
                payer, 400);
        String key = UUID.randomUUID().toString();
        stockJdbc.execute("ALTER TABLE payments ADD CONSTRAINT test_payment_failure CHECK(amount<>1)");
        try {
            ok(paymentRequest(order, key, 1), payer, 409);
        } finally {
            stockJdbc.execute("ALTER TABLE payments DROP CONSTRAINT test_payment_failure");
        }
        assertEquals(0, ok(get(route(order, "")), employee, 200).get("paidAmount").asLong());
        collect(order, 1, key); // rollback did not reserve the key
        var max = collect(order, Long.MAX_VALUE - 1, UUID.randomUUID().toString());
        assertEquals(Long.MAX_VALUE, max.get("order").get("paidAmount").asLong());
        ok(paymentRequest(order, UUID.randomUUID().toString(), 1), payer, 409);
        var id = max.get("payment").get("id").asText();
        for (var reason : List.of("", "   "))
            ok(body(post("/payments/" + id + "/cancel"), Map.of("reason", reason)), payer, 400);
        paymentCancel(max);
        assertEquals(Long.MAX_VALUE - 1, ok(get(route(order, "")), employee, 200).get("remainingAmount").asLong());
    }

    @Test
    void receivablesScopeFullCustomerTotalPaginationWalkInAndNumericOverflow() throws Exception {
        paymentFixture();
        var customer = customer(a);
        var first = pricedOrder(1000000, customer);
        pricedOrder(2000000, customer);
        var walkIn = pricedOrder(700000, null);
        pricedOrder(0, customer);
        var draft = salePayload(a, 1, 8000000);
        draft.put("customerId", customer.get("id").asText());
        ok(body(post("/sales-orders"), draft), employee, 201); // drafts do not contribute
        collect(first, 600000, UUID.randomUUID().toString());
        var grouped = ok(get("/receivables?pageSize=1"), payer, 200);
        assertEquals(1, grouped.get("total").asLong());
        assertEquals(2400000, grouped.get("items").get(0).get("remainingAmount").asLong());
        var detail = ok(get("/customers/" + customer.get("id").asText() + "/receivables?pageSize=1"), payer, 200);
        assertEquals(2400000, detail.get("remainingAmount").asLong());
        assertEquals(2, detail.get("orders").get("total").asLong());
        assertEquals(1, detail.get("orders").get("items").size());
        assertEquals(1, ok(get("/receivables/walk-in-orders"), payer, 200).get("total").asLong());
        assertEquals(walkIn.get("id"),
                ok(get("/receivables/walk-in-orders"), payer, 200).get("items").get(0).get("id"));
        assertEquals(0, ok(get("/receivables?keyword=missing"), payer, 200).get("total").asLong());
        assertEquals(0, ok(get("/receivables?keyword=%25"), payer, 200).get("total").asLong());
        assertEquals(0, ok(get("/receivables?page=2&pageSize=1"), payer, 200).get("items").size());
        var other = customer(b);
        ok(get("/customers/" + other.get("id").asText() + "/receivables"), payer, 403);
        ok(get("/receivables?warehouseId=" + b), payer, 403);
        ok(get("/receivables/walk-in-orders?warehouseId=" + b), payer, 403);
        pricedOrder(Long.MAX_VALUE, customer);
        assertEquals("NUMERIC_OVERFLOW",
                ok(get("/customers/" + customer.get("id").asText() + "/receivables"), payer, 409).get("code").asText());
        assertEquals("NUMERIC_OVERFLOW", ok(get("/receivables"), payer, 409).get("code").asText());
    }

    @Test
    void paymentFiltersHistoryPermissionsRevocationAndInactiveSource() throws Exception {
        paymentFixture();
        assign(a, outsiderId);
        var customer = customer(a);
        var order = pricedOrder(1000000, customer);
        var payload = customerPayload(a, "Customer", 0);
        ok(body(put("/customers/" + customer.get("id").asText()), payload), admin, 200);
        var wh = warehouses.findById(UUID.fromString(a)).orElseThrow();
        wh.setStatus((short) 0);
        warehouses.saveAndFlush(wh);
        String key = UUID.randomUUID().toString();
        var payment = collect(order, 600000, key);
        String id = payment.get("payment").get("id").asText();
        ok(get(route(order, "")), payer, 403); // payment-only user does not need sales view
        assertEquals(1, ok(get(route(order, "/payments")), payer, 200).get("total").asLong());
        ok(get("/payments/" + id), payer, 200);
        String filters = "/payments?warehouseId=" + a + "&salesOrderId=" + order.get("id").asText() + "&customerId="
                + customer.get("id").asText() +
                "&status=ACTIVE&method=CASH&from=2026-10-09&to=2026-10-09&pageSize=1";
        assertEquals(1, ok(get(filters), payer, 200).get("total").asLong());
        for (var query : List.of("?method=BANK_TRANSFER", "?to=2026-10-08", "?status=CANCELLED"))
            assertEquals(0, ok(get("/payments" + query), payer, 200).get("total").asLong());
        paymentCancel(payment);
        assertEquals(0, ok(get(filters), payer, 200).get("total").asLong());
        ok(get("/payments?from=2026-10-10&to=2026-10-09"), payer, 400);
        ok(get("/payments?page=0"), payer, 400);
        ok(get("/payments?pageSize=101"), payer, 400);
        ok(get("/payments?status=WRONG"), payer, 400);
        ok(get("/payments/" + UUID.randomUUID()), payer, 404);
        ok(get("/payments"), "bad-token", 401);
        ok(get("/payments"), outsider, 403);
        ok(paymentRequest(order, UUID.randomUUID().toString(), 1), outsider, 403);
        ok(body(post("/payments/" + id + "/cancel"), Map.of("reason", "Incorrect")), outsider, 403);
        ok(get("/receivables"), outsider, 403);
        ok(get("/payments?warehouseId=" + b), payer, 403);
        // Revoke live permission while JWT remains valid.
        stockJdbc.update(
                "DELETE FROM role_permissions WHERE role_id IN (SELECT role_id FROM user_roles WHERE user_id=?) AND permission_id IN (SELECT id FROM permissions WHERE code='PAYMENT_CREATE')",
                payerId);
        ok(paymentRequest(order, key, 600000), payer, 403);
        mvc.perform(delete("/users/" + payerId + "/warehouses/" + a).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        ok(get("/payments/" + id), payer, 403);
        ok(body(post("/payments/" + id + "/cancel"), Map.of("reason", "Incorrect")), payer, 403);
        assertEquals(0, ok(get("/payments"), payer, 200).get("total").asLong());
        assertEquals(0, ok(get("/receivables"), payer, 200).get("total").asLong());
        ok(get("/customers/" + customer.get("id").asText() + "/receivables"), payer, 403);
        ok(get(route(order, "/payments")), payer, 403);
        // Read/cancel and retry authorization apply even to previously cancelled
        // payments.
    }

    @Test
    void postgresPaymentAuditGuardsRejectMutationDeletionAndPaidSaleCancellation() throws Exception {
        String db = stockJdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) c -> c.getMetaData()
                .getDatabaseProductName());
        org.junit.jupiter.api.Assumptions.assumeTrue("PostgreSQL".equals(db), "PL/pgSQL guards require PostgreSQL");
        paymentFixture();
        var order = pricedOrder(100, null);
        var result = collect(order, 60, UUID.randomUUID().toString());
        UUID payment = UUID.fromString(result.get("payment").get("id").asText());
        UUID sale = UUID.fromString(order.get("id").asText());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> stockJdbc.update("UPDATE payments SET amount=1 WHERE id=?", payment));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> stockJdbc.update("DELETE FROM payments WHERE id=?", payment));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> stockJdbc.update(
                "UPDATE sales_orders SET status='CANCELLED',version=version+1,cancelled_by=?,cancelled_at=CURRENT_TIMESTAMP,cancellation_reason='Tampering',goods_returned=true WHERE id=?",
                adminId, sale));
        paymentCancel(result);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> stockJdbc.update("UPDATE payments SET cancellation_reason='Changed' WHERE id=?", payment));
        assertEquals(100, ok(get(route(order, "")), employee, 200).get("remainingAmount").asLong());
    }

    @Test
    void concurrentSameKeyAcrossWarehousesAndRetryAfterScopeRevocation() throws Exception {
        paymentFixture();
        assign(b, payerId);
        var first = pricedOrder(100, null);
        var receiptB = ok(body(post("/stock-receipts"), payload(b, 10, 0)), admin, 201);
        ok(body(post("/stock-receipts/" + receiptB.get("id").asText() + "/confirm"), Map.of("version", 0)), admin, 200);
        var second = ok(body(post("/sales-orders"), salePayload(b, 1, 100)), admin, 201);
        second = ok(body(post(route(second, "/confirm")), Map.of("version", 0)), admin, 200);
        final var orderB = second;
        String key = UUID.randomUUID().toString();
        var result = concurrent(
                () -> payerStatus(paymentRequest(first, key, 60)),
                () -> payerStatus(paymentRequest(orderB, key, 60)));
        assertTrue(result.equals(List.of(201, 409)) || result.equals(List.of(409, 201)), result.toString());
        assertEquals(1, ok(get("/payments"), payer, 200).get("total").asLong());
        var successful = result.get(0) == 201 ? first : orderB;
        String warehouse = successful.get("warehouseId").asText();
        mvc.perform(delete("/users/" + payerId + "/warehouses/" + warehouse).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertEquals("WAREHOUSE_ACCESS_DENIED",
                ok(paymentRequest(successful, key, 60), payer, 403).get("code").asText());
        assertEquals(1, ok(get("/payments"), admin, 200).get("total").asLong());
    }

    @Test
    void paymentCorsPreflightAllowsIdempotencyHeader() throws Exception {
        mvc.perform(options("/sales-orders/" + UUID.randomUUID() + "/payments")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("idempotency-key")));
    }
}
