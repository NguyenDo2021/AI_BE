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
class StocktakeApiIntegrationTest {
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
                "PRODUCT_GROUP_UPDATE", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE",
                "STOCK_RECEIPT_VIEW", "STOCK_RECEIPT_CREATE", "STOCK_RECEIPT_UPDATE", "STOCK_RECEIPT_CONFIRM", "STOCK_RECEIPT_CANCEL", "INVENTORY_VIEW", "INVENTORY_MOVEMENT_VIEW", "STOCKTAKE_VIEW", "STOCKTAKE_CREATE", "STOCKTAKE_UPDATE", "STOCKTAKE_CONFIRM", "STOCKTAKE_CANCEL"));
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

    String a,b,p,p2;
    void fixture() throws Exception {
        a=create("/warehouses","A",admin); b=create("/warehouses","B",admin); assign(a,employeeId);
        String group=create("/product-groups","G",admin);
        var payload=catalog("P",1);payload.put("groupId",group);
        p=ok(body(post("/products"),payload),admin,201).get("id").asText();
        payload.put("code","P2"); p2=ok(body(post("/products"),payload),admin,201).get("id").asText();
    }
    Map<String,Object> payload(String warehouse,long quantity,long price) {
        return new HashMap<>(Map.of("warehouseId",warehouse,"receiptDate","2026-10-08","supplierName","Supplier",
            "lines",List.of(Map.of("productId",p,"quantity",quantity,"unitPrice",price))));
    }
    JsonNode receipt(long qty) throws Exception { return ok(body(post("/stock-receipts"),payload(a,qty,2000)),employee,201); }
    JsonNode confirm(JsonNode receipt) throws Exception { return ok(body(post("/stock-receipts/"+receipt.get("id").asText()+"/confirm"),Map.of("version",receipt.get("version").asLong())),employee,200); }
    JsonNode cancel(JsonNode receipt) throws Exception { return ok(body(post("/stock-receipts/"+receipt.get("id").asText()+"/cancel"),Map.of("version",receipt.get("version").asLong(),"reason","Wrong receipt")),employee,200); }
    long stock(String warehouse,String product) throws Exception {
        return ok(get("/warehouses/"+warehouse+"/inventory?productId="+product),admin,200).get("items").get(0).get("quantity").asLong();
    }
    void consistent() {
        Long mismatches=stockJdbc.queryForObject("SELECT count(*) FROM inventory_balances b WHERE b.quantity <> COALESCE((SELECT sum(m.quantity_change) FROM inventory_movements m WHERE m.warehouse_id=b.warehouse_id AND m.product_id=b.product_id),0)",Long.class);
        assertEquals(0L,mismatches);
    }
    int requestStatus(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header("Authorization","Bearer "+employee)).andReturn().getResponse().getStatus();
    }
    List<Integer> concurrent(java.util.concurrent.Callable<Integer> first,java.util.concurrent.Callable<Integer> second) throws Exception {
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);return first.call();});
            var two=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);return second.call();});
            return List.of(one.get(20,java.util.concurrent.TimeUnit.SECONDS),two.get(20,java.util.concurrent.TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    JsonNode take(String warehouse, String... products) throws Exception {
        return ok(body(post("/stocktakes"),Map.of("warehouseId",warehouse,
            "lines",Arrays.stream(products).map(id->Map.of("productId",id)).toList())),employee,201);
    }
    JsonNode edit(JsonNode r, long count) throws Exception {
        return ok(body(put("/stocktakes/"+r.get("id").asText()),Map.of("version",r.get("version").asLong(),
            "lines",List.of(Map.of("productId",p,"countedQuantity",count,"reason","Counted in warehouse")))),employee,200);
    }
    JsonNode action(JsonNode r, String action, int expected) throws Exception {
        var input=new HashMap<String,Object>();input.put("version",r.get("version").asLong());
        if(action.equals("cancel"))input.put("reason","Created in error");
        return ok(body(post("/stocktakes/"+r.get("id").asText()+"/"+action),input),employee,expected);
    }
    long moves() { return stockJdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE type='STOCKTAKE_ADJUSTMENT'",Long.class); }
    @Test void adjustmentDraftIsolationAuditAndIdempotency() throws Exception {
        fixture();confirm(receipt(100));var r=take(a,p);
        assertEquals(0,r.get("version").asLong());assertFalse(r.get("lines").get(0).has("countedQuantity"));
        r=edit(r,97);assertEquals(100,stock(a,p));assertEquals(0,moves());
        var done=action(r,"confirm",200);assertEquals(97,stock(a,p));assertEquals(1,moves());
        assertEquals(employeeId.toString(),done.get("updatedBy").asText());
        assertEquals(done,action(r,"confirm",200));
        var history=ok(get("/warehouses/"+a+"/inventory-movements"),employee,200).get("items");
        var movement=java.util.stream.StreamSupport.stream(history.spliterator(),false)
            .filter(x->x.get("type").asText().equals("STOCKTAKE_ADJUSTMENT")).findFirst().orElseThrow();
        assertEquals(-3,movement.get("quantityChange").asLong());
        assertEquals(done.get("id"),movement.get("stocktakeId"));assertEquals(done.get("code"),movement.get("stocktakeCode"));
        assertFalse(movement.has("receiptId"));assertFalse(movement.has("salesOrderId"));
        assertEquals("P",movement.get("productCode").asText());
        action(done,"cancel",409);ok(body(put("/stocktakes/"+done.get("id").asText()),
            Map.of("version",done.get("version").asLong(),"lines",List.of(Map.of("productId",p)))),employee,409);
        mvc.perform(delete("/stocktakes/"+done.get("id").asText()).header("Authorization","Bearer "+employee))
            .andExpect(status().isMethodNotAllowed());consistent();
    }
    @Test void positiveZeroAndNoDifference() throws Exception {
        fixture();confirm(receipt(100));
        action(edit(take(a,p),105),"confirm",200);assertEquals(105,stock(a,p));assertEquals(1,moves());
        action(edit(take(a,p),105),"confirm",200);assertEquals(1,moves());
        action(edit(take(a,p),0),"confirm",200);assertEquals(0,stock(a,p));assertEquals(2,moves());consistent();
    }
    @Test void uncountedAndMissingReasonAreRejected() throws Exception {
        fixture();confirm(receipt(100));var r=take(a,p);action(r,"confirm",400);
        assertEquals("STOCKTAKE_REASON_REQUIRED",ok(body(put("/stocktakes/"+r.get("id").asText()),
            Map.of("version",0,"lines",List.of(Map.of("productId",p,"countedQuantity",97)))),employee,400).get("code").asText());
        assertEquals(100,stock(a,p));assertEquals(0,moves());
    }
    @Test void staleEvenAfterReturnToSameQuantityAndRefreshRequiresRecount() throws Exception {
        fixture();confirm(receipt(100));var r=edit(take(a,p),97);
        var added=confirm(receipt(5));cancel(added);assertEquals(100,stock(a,p));
        var err=action(r,"confirm",409);assertEquals("STOCKTAKE_STALE",err.get("code").asText());
        assertTrue(err.get("details").has("lines."+p+".currentRevision"));assertEquals(0,moves());
        var unchanged=ok(get("/stocktakes/"+r.get("id").asText()),employee,200);assertEquals(r,unchanged);
        var fresh=action(r,"refresh-snapshot",200);assertEquals(2,fresh.get("version").asLong());
        assertFalse(fresh.get("lines").get(0).has("countedQuantity"));assertFalse(fresh.get("lines").get(0).has("reason"));
        action(fresh,"confirm",400);action(edit(fresh,97),"confirm",200);assertEquals(97,stock(a,p));consistent();
    }
    @Test void absentBalanceSnapshotDetectsSubsequentMovement() throws Exception {
        fixture();var r=edit(take(a,p),1);assertEquals(0,r.get("lines").get(0).get("inventoryRevision").asLong());
        var added=confirm(receipt(2));cancel(added);assertEquals(0,stock(a,p));
        assertEquals("STOCKTAKE_STALE",action(r,"confirm",409).get("code").asText());assertEquals(0,moves());
    }
    @Test void editKeepsOldSnapshotsAndSnapshotsNewLines() throws Exception {
        fixture();confirm(receipt(100));var r=take(a,p);confirm(receipt(5));
        var edited=ok(body(put("/stocktakes/"+r.get("id").asText()),Map.of("version",0,
            "lines",List.of(Map.of("productId",p,"countedQuantity",97,"reason","Missing"),
                Map.of("productId",p2,"countedQuantity",0)))),employee,200);
        var old=java.util.stream.StreamSupport.stream(edited.get("lines").spliterator(),false).filter(x->x.get("productId").asText().equals(p)).findFirst().orElseThrow();
        assertEquals(100,old.get("systemQuantity").asLong());assertEquals(-3,old.get("difference").asLong());
        assertEquals("STOCKTAKE_STALE",action(edited,"confirm",409).get("code").asText());assertEquals(105,stock(a,p));
    }
    @Test void competingStocktakesAndConcurrentRetry() throws Exception {
        fixture();confirm(receipt(100));var first=edit(take(a,p),97);var second=edit(take(a,p),105);
        String route="/stocktakes/"+first.get("id").asText()+"/confirm";
        assertEquals(List.of(200,200),concurrent(()->requestStatus(body(post(route),Map.of("version",1))),
            ()->requestStatus(body(post(route),Map.of("version",1)))));
        assertEquals(97,stock(a,p));assertEquals(1,moves());
        assertEquals("STOCKTAKE_STALE",action(second,"confirm",409).get("code").asText());consistent();
    }
    @Test void zeroDifferenceDoesNotInvalidateAnotherSnapshot() throws Exception {
        fixture();confirm(receipt(100));var first=edit(take(a,p),100);var second=edit(take(a,p),97);
        action(first,"confirm",200);action(second,"confirm",200);assertEquals(1,moves());assertEquals(97,stock(a,p));
    }
    @Test void injectedFailureRollsBackAllLinesAndAudit() throws Exception {
        fixture();var r=take(a,p,p2);
        r=ok(body(put("/stocktakes/"+r.get("id").asText()),Map.of("version",0,"lines",
            List.of(Map.of("productId",p,"countedQuantity",10,"reason","Found"),Map.of("productId",p2,"countedQuantity",20,"reason","Found")))),employee,200);
        String last=UUID.fromString(p).compareTo(UUID.fromString(p2))>0?p:p2;
        stockJdbc.execute("ALTER TABLE inventory_movements ADD CONSTRAINT test_stocktake_failure CHECK(product_id <> '"+last+"')");
        try { action(r,"confirm",409);assertEquals(0,stock(a,p));assertEquals(0,stock(a,p2));assertEquals(0,moves());
            assertEquals(r,ok(get("/stocktakes/"+r.get("id").asText()),employee,200));
        } finally { stockJdbc.execute("ALTER TABLE inventory_movements DROP CONSTRAINT test_stocktake_failure"); }
        action(r,"confirm",200);assertEquals(10,stock(a,p));assertEquals(20,stock(a,p2));consistent();
    }
    @Test void versionConflictAndCancelRetryPreserveFirstAudit() throws Exception {
        fixture();var r=take(a,p);var edited=edit(r,0);
        var error=action(r,"refresh-snapshot",409);assertEquals("VERSION_CONFLICT",error.get("code").asText());
        assertEquals("0",error.get("details").get("expectedVersion").asText());assertEquals("1",error.get("details").get("currentVersion").asText());
        var done=action(edited,"cancel",200);assertEquals(done,action(edited,"cancel",200));
        action(done,"confirm",409);action(done,"refresh-snapshot",409);assertEquals(0,stock(a,p));assertEquals(0,moves());
    }
    @Test void strictInputUnknownFieldsAndLongBoundaries() throws Exception {
        fixture();var r=take(a,p);String route="/stocktakes/"+r.get("id").asText();
        for(Object invalid:List.of(-1,1.5,1.0,"1")) ok(body(put(route),Map.of("version",0,
            "lines",List.of(Map.of("productId",p,"countedQuantity",invalid,"reason","Test")))),employee,400);
        ok(put(route).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"lines\":[{\"productId\":\""+p+"\",\"countedQuantity\":9223372036854775808,\"reason\":\"Test\"}]}"),employee,400);
        for(String field:List.of("warehouseId","systemQuantity","inventoryRevision","difference","createdBy"))
            ok(body(put(route),Map.of("version",0,field,a,"lines",List.of(Map.of("productId",p)))),employee,400);
        ok(body(post("/stocktakes"),Map.of("warehouseId",a,"lines",List.of(Map.of("productId",p,"systemQuantity",100)))),employee,400);
        ok(body(post("/stocktakes"),Map.of("warehouseId",a,"lines",List.of())),employee,400);
        ok(body(post("/stocktakes"),Map.of("warehouseId",a,"lines",List.of(Map.of("productId",p),Map.of("productId",p)))),employee,400);
        ok(body(post("/stocktakes"),Map.of("warehouseId",a,"note","x".repeat(2001),"lines",List.of(Map.of("productId",p)))),employee,400);
        ok(body(post("/stocktakes"),Map.of("warehouseId",a,"lines",Collections.nCopies(1001,Map.of("productId",p)))),employee,400);
        action(edit(r,Long.MAX_VALUE),"confirm",200);assertEquals(Long.MAX_VALUE,stock(a,p));consistent();
    }
    @Test void permissionsScopeAndRevocationAlsoApplyToRetries() throws Exception {
        fixture();var r=take(a,p);String id=r.get("id").asText();
        assign(a,outsiderId);
        for(String action:List.of("confirm","cancel","refresh-snapshot"))
            ok(body(post("/stocktakes/"+id+"/"+action),action.equals("cancel")?Map.of("version",0,"reason","Test"):Map.of("version",0)),outsider,403);
        ok(get("/stocktakes"),outsider,403);ok(get("/stocktakes/"+id),outsider,403);
        ok(body(put("/stocktakes/"+id),Map.of("version",0,"lines",List.of(Map.of("productId",p)))),outsider,403);
        ok(body(post("/stocktakes"),Map.of("warehouseId",a,"lines",List.of(Map.of("productId",p)))),outsider,403);
        ok(body(post("/stocktakes"),Map.of("warehouseId",b,"lines",List.of(Map.of("productId",p)))),employee,403);
        ok(body(post("/stocktakes"),Map.of("warehouseId",b,"lines",List.of(Map.of("productId",p)))),admin,201);
        assertEquals(1,ok(get("/stocktakes?pageSize=1"),employee,200).get("total").asInt());
        action(edit(r,0),"confirm",200);
        mvc.perform(delete("/users/"+employeeId+"/warehouses/"+a).header("Authorization","Bearer "+admin)).andExpect(status().isNoContent());
        ok(get("/stocktakes/"+id),employee,403);action(r,"confirm",403);
        assertEquals(0,ok(get("/stocktakes"),employee,200).get("total").asInt());
    }
    @Test void inactiveWarehouseHistoryCancellationAndInactiveProductHistory() throws Exception {
        fixture();var noHistory=products.findById(UUID.fromString(p2)).orElseThrow();noHistory.setStatus((short)0);products.saveAndFlush(noHistory);
        ok(body(post("/stocktakes"),Map.of("warehouseId",a,"lines",List.of(Map.of("productId",p2)))),employee,409);
        confirm(receipt(100));var product=products.findById(UUID.fromString(p)).orElseThrow();product.setStatus((short)0);products.saveAndFlush(product);
        var r=edit(take(a,p),97);action(r,"confirm",200);assertEquals(97,stock(a,p));
        var draft=take(a,p);var wh=warehouses.findById(UUID.fromString(a)).orElseThrow();wh.setStatus((short)0);warehouses.saveAndFlush(wh);
        action(draft,"refresh-snapshot",409);action(draft,"confirm",409);
        ok(body(post("/stocktakes"),Map.of("warehouseId",a,"lines",List.of(Map.of("productId",p)))),employee,409);
        ok(body(put("/stocktakes/"+draft.get("id").asText()),Map.of("version",0,"lines",List.of(Map.of("productId",p)))),employee,409);
        ok(get("/stocktakes/"+r.get("id").asText()),employee,200);action(r,"confirm",200);action(draft,"cancel",200);
    }
    @Test void dateFiltersUseVietnamDayAndInclusiveBoundaries() throws Exception {
        fixture();var r=take(a,p);
        stockJdbc.update("INSERT INTO stocktakes(id,code,warehouse_id,status,version,created_by,created_at,updated_by,updated_at) VALUES (?,?,?,\'DRAFT\',0,?,?,?,?)",UUID.randomUUID(),"BOUNDARY",UUID.fromString(a),employeeId,java.sql.Timestamp.from(java.time.Instant.parse("2000-10-11T17:00:00Z")),employeeId,java.sql.Timestamp.from(java.time.Instant.parse("2000-10-11T17:00:00Z")));
        assertEquals(1,ok(get("/stocktakes?from=2000-10-12&to=2000-10-12"),employee,200).get("total").asInt());
        assertEquals(0,ok(get("/stocktakes?to=2000-10-11"),employee,200).get("total").asInt());
        ok(get("/stocktakes?from=2026-10-13&to=2026-10-12"),employee,400);ok(get("/stocktakes?page=0"),employee,400);
    }
    @Test void postgresFinalHistoryGuards() throws Exception {
        String db=stockJdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)c->c.getMetaData().getDatabaseProductName());
        org.junit.jupiter.api.Assumptions.assumeTrue("PostgreSQL".equals(db),"Requires PostgreSQL");
        fixture();var r=action(edit(take(a,p),1),"confirm",200);UUID id=UUID.fromString(r.get("id").asText());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->stockJdbc.update("UPDATE stocktakes SET note='Changed',version=version+1 WHERE id=?",id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->stockJdbc.update("DELETE FROM stocktakes WHERE id=?",id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->stockJdbc.update("UPDATE stocktake_lines SET counted_quantity=2 WHERE stocktake_id=?",id));
    }

    JsonNode sale(long quantity) throws Exception {
        var draft=ok(body(post("/sales-orders"),Map.of("warehouseId",a,"saleDate","2026-10-12",
            "lines",List.of(Map.of("productId",p,"quantity",quantity,"unitPrice",10)))),admin,201);
        return ok(body(post("/sales-orders/"+draft.get("id").asText()+"/confirm"),Map.of("version",0)),admin,200);
    }
    @Test void saleAndCancellationInvalidateSnapshotsWithoutChangingQuantity() throws Exception {
        fixture();confirm(receipt(100));var r=edit(take(a,p),97);var sold=sale(5);
        assertEquals("STOCKTAKE_STALE",action(r,"confirm",409).get("code").asText());
        ok(body(post("/sales-orders/"+sold.get("id").asText()+"/cancel"),
            Map.of("version",1,"reason","Returned","goodsReturned",true)),admin,200);
        assertEquals(100,stock(a,p));assertEquals("STOCKTAKE_STALE",action(r,"confirm",409).get("code").asText());
        assertEquals(0,moves());consistent();
    }
    @Test void confirmingStocktakeDoesNotChangeOrdersPaymentsOrReceivables() throws Exception {
        fixture();confirm(receipt(100));var sold=sale(5);String order=sold.get("id").asText();
        var payment=ok(body(post("/sales-orders/"+order+"/payments").header("Idempotency-Key",UUID.randomUUID().toString()),
            Map.of("amount",20,"paymentDate","2026-10-12","method","CASH")),admin,201);
        var before=ok(get("/sales-orders/"+order),admin,200);
        var paymentsBefore=ok(get("/sales-orders/"+order+"/payments"),admin,200);
        var debtBefore=ok(get("/receivables/walk-in-orders?warehouseId="+a),admin,200);
        action(edit(take(a,p),92),"confirm",200);assertEquals(92,stock(a,p));
        assertEquals(before,ok(get("/sales-orders/"+order),admin,200));
        assertEquals(paymentsBefore,ok(get("/sales-orders/"+order+"/payments"),admin,200));
        assertEquals(debtBefore,ok(get("/receivables/walk-in-orders?warehouseId="+a),admin,200));consistent();
    }
    @Test void concurrentReceiptAndStocktakePreserveEveryMovement() throws Exception {
        fixture();confirm(receipt(100));var r=edit(take(a,p),97);var incoming=receipt(5);
        var outcomes=concurrent(
            ()->requestStatus(body(post("/stocktakes/"+r.get("id").asText()+"/confirm"),Map.of("version",1))),
            ()->requestStatus(body(post("/stock-receipts/"+incoming.get("id").asText()+"/confirm"),Map.of("version",0))));
        assertEquals(200,outcomes.get(1));
        if(outcomes.get(0)==200) { assertEquals(102,stock(a,p));assertEquals(1,moves()); }
        else { assertEquals(409,outcomes.get(0));assertEquals(105,stock(a,p));assertEquals(0,moves()); }
        consistent();
    }
    @Test void revisionOverflowRollsBackWithoutChangingStocktake() throws Exception {
        fixture();confirm(receipt(100));
        stockJdbc.update("UPDATE inventory_balances SET revision=? WHERE warehouse_id=? AND product_id=?",Long.MAX_VALUE,UUID.fromString(a),UUID.fromString(p));
        var r=edit(take(a,p),97);
        assertEquals("NUMERIC_OVERFLOW",action(r,"confirm",409).get("code").asText());
        assertEquals(100,stock(a,p));assertEquals(0,moves());assertEquals(r,ok(get("/stocktakes/"+r.get("id").asText()),employee,200));
        var incoming=receipt(1);
        assertEquals("NUMERIC_OVERFLOW",ok(body(post("/stock-receipts/"+incoming.get("id").asText()+"/confirm"),Map.of("version",0)),employee,409).get("code").asText());
        assertEquals(100,stock(a,p));
    }
}
