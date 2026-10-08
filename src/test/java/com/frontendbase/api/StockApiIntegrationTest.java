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
class StockApiIntegrationTest {
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
                "STOCK_RECEIPT_VIEW", "STOCK_RECEIPT_CREATE", "STOCK_RECEIPT_UPDATE", "STOCK_RECEIPT_CONFIRM", "STOCK_RECEIPT_CANCEL", "INVENTORY_VIEW", "INVENTORY_MOVEMENT_VIEW"));
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
    @Test void draftIsolationConfirmationCancellationAndRetries() throws Exception {
        fixture();var draft=receipt(100); assertEquals(200000,draft.get("totalAmount").asLong());
        assertEquals(0,stock(a,p));assertEquals(0,stock(b,p));
        assertEquals(0,ok(get("/warehouses/"+a+"/inventory-movements"),employee,200).get("total").asLong());
        var confirmed=confirm(draft);assertEquals(1,confirmed.get("version").asLong());
        assertEquals(employeeId.toString(),confirmed.get("confirmedBy").asText());
        assertEquals(100,stock(a,p));assertEquals(0,stock(b,p));
        confirm(draft);assertEquals(100,stock(a,p));
        var cancelled=cancel(confirmed);cancel(confirmed);assertEquals(0,stock(a,p));assertEquals(2,cancelled.get("version").asLong());
        assertEquals(2,ok(get("/warehouses/"+a+"/inventory-movements"),employee,200).get("total").asLong());
        ok(body(post("/stock-receipts/"+draft.get("id").asText()+"/confirm"),Map.of("version",2)),employee,409);
        assertEquals(0,products.findById(UUID.fromString(p)).orElseThrow().getReferencePurchasePrice().longValue());consistent();
    }
    @Test void draftEditVersionWarehouseImmutabilityAndCancellation() throws Exception {
        fixture();var draft=receipt(100);String id=draft.get("id").asText();
        var update=payload(a,50,3000);update.put("version",0);
        var edited=ok(body(put("/stock-receipts/"+id),update),employee,200);assertEquals(150000,edited.get("totalAmount").asLong());
        ok(body(put("/stock-receipts/"+id),update),employee,409);assertEquals(0,stock(a,p));
        update.put("version",1);update.put("warehouseId",b);ok(body(put("/stock-receipts/"+id),update),employee,409);
        cancel(edited);assertEquals(0,stock(a,p));assertEquals(0,ok(get("/warehouses/"+a+"/inventory-movements"),employee,200).get("total").asLong());
        update.put("warehouseId",a);update.put("version",2);ok(body(put("/stock-receipts/"+id),update),employee,409);
        mvc.perform(delete("/stock-receipts/"+id).header("Authorization","Bearer "+employee)).andExpect(status().isMethodNotAllowed());
    }
    @Test void concurrentDifferentReceiptsAndSameReceipt() throws Exception {
        fixture();var one=receipt(100);var two=receipt(70);
        String first="/stock-receipts/"+one.get("id").asText()+"/confirm", second="/stock-receipts/"+two.get("id").asText()+"/confirm";
        assertEquals(List.of(200,200),concurrent(()->requestStatus(body(post(first),Map.of("version",0))),()->requestStatus(body(post(second),Map.of("version",0)))));
        assertEquals(170,stock(a,p));
        var three=receipt(30);String route="/stock-receipts/"+three.get("id").asText()+"/confirm";
        assertEquals(List.of(200,200),concurrent(()->requestStatus(body(post(route),Map.of("version",0))),()->requestStatus(body(post(route),Map.of("version",0)))));
        assertEquals(200,stock(a,p)); assertEquals(3,ok(get("/warehouses/"+a+"/inventory-movements"),employee,200).get("total").asLong());consistent();
    }
    @Test void confirmRacingUpdateAndCancel() throws Exception {
        fixture(); var one=receipt(100);String id=one.get("id").asText();var update=payload(a,50,2000);update.put("version",0);
        var result=concurrent(()->requestStatus(body(post("/stock-receipts/"+id+"/confirm"),Map.of("version",0))),()->requestStatus(body(put("/stock-receipts/"+id),update)));
        assertTrue(result.equals(List.of(200,409)) || result.equals(List.of(409,200)),result.toString());
        var latest=ok(get("/stock-receipts/"+id),employee,200);if(latest.get("status").asText().equals("DRAFT")) confirm(latest);
        assertEquals(latest.get("lines").get(0).get("quantity").asLong(),stock(a,p));
        var two=receipt(20);String id2=two.get("id").asText();long before=stock(a,p);
        var raced=concurrent(()->requestStatus(body(post("/stock-receipts/"+id2+"/confirm"),Map.of("version",0))),()->requestStatus(body(post("/stock-receipts/"+id2+"/cancel"),Map.of("version",0,"reason","Cancel"))));
        assertTrue(raced.equals(List.of(200,409)) || raced.equals(List.of(409,200)),raced.toString());consistent();
        var after=ok(get("/stock-receipts/"+id2),employee,200);
        assertEquals(before+(after.get("status").asText().equals("CONFIRMED")?20:0),stock(a,p));
    }
    @Test void simultaneousCancelsReverseOnce() throws Exception {
        fixture();var one=confirm(receipt(100));String route="/stock-receipts/"+one.get("id").asText()+"/cancel";
        assertEquals(List.of(200,200),concurrent(()->requestStatus(body(post(route),Map.of("version",1,"reason","Cancel"))),()->requestStatus(body(post(route),Map.of("version",1,"reason","Cancel")))));
        assertEquals(0,stock(a,p));assertEquals(2,ok(get("/warehouses/"+a+"/inventory-movements"),employee,200).get("total").asLong());consistent();
    }
    @Test void insufficientStockRejectsEntireReversal() throws Exception {
        fixture();var input=payload(a,100,2000); input.put("lines",List.of(Map.of("productId",p,"quantity",100,"unitPrice",2000),Map.of("productId",p2,"quantity",40,"unitPrice",1000)));
        var confirmed=confirm(ok(body(post("/stock-receipts"),input),employee,201));
        // Simulate downstream consumption without implementing outbound inventory.
        stockJdbc.update("UPDATE inventory_balances SET quantity=1 WHERE warehouse_id=? AND product_id=?",UUID.fromString(a),UUID.fromString(p2));
        var err=ok(body(post("/stock-receipts/"+confirmed.get("id").asText()+"/cancel"),Map.of("version",1,"reason","Reverse")),employee,409);
        assertEquals("INSUFFICIENT_STOCK",err.get("code").asText());assertEquals(100,stock(a,p));assertEquals(1,stock(a,p2));
        assertEquals("CONFIRMED",ok(get("/stock-receipts/"+confirmed.get("id").asText()),employee,200).get("status").asText());
        assertEquals(2,ok(get("/warehouses/"+a+"/inventory-movements"),employee,200).get("total").asLong());
    }
    @Test void injectedMidTransactionFailureRollsBackEverything() throws Exception {
        fixture();var input=payload(a,100,1); input.put("lines",List.of(Map.of("productId",p,"quantity",100,"unitPrice",1),Map.of("productId",p2,"quantity",40,"unitPrice",1)));
        var draft=ok(body(post("/stock-receipts"),input),employee,201);
        String last=UUID.fromString(p).compareTo(UUID.fromString(p2))>0?p:p2;
        stockJdbc.execute("ALTER TABLE inventory_movements ADD CONSTRAINT test_fail_movement CHECK (product_id <> '"+last+"')");
        try {
            ok(body(post("/stock-receipts/"+draft.get("id").asText()+"/confirm"),Map.of("version",0)),employee,409);
            assertEquals(0,stock(a,p));assertEquals(0,stock(a,p2));
            assertEquals(0L,stockJdbc.queryForObject("SELECT count(*) FROM inventory_balances",Long.class));
            assertEquals(0L,stockJdbc.queryForObject("SELECT count(*) FROM inventory_movements",Long.class));
            var after=ok(get("/stock-receipts/"+draft.get("id").asText()),employee,200);assertEquals("DRAFT",after.get("status").asText());assertEquals(0,after.get("version").asLong());
        } finally { stockJdbc.execute("ALTER TABLE inventory_movements DROP CONSTRAINT test_fail_movement"); }
        confirm(draft);consistent();
    }
    @Test void permissionsScopeFilteringAndRevocationWithOldJwt() throws Exception {
        fixture();var draft=receipt(10);var other=ok(body(post("/stock-receipts"),payload(b,20,0)),admin,201);
        assertEquals(1,ok(get("/stock-receipts?pageSize=1"),employee,200).get("total").asLong());
        assertEquals(2,ok(get("/stock-receipts"),admin,200).get("total").asLong());
        for(String route:List.of("/stock-receipts/"+other.get("id").asText(),"/warehouses/"+b+"/inventory","/warehouses/"+b+"/inventory-movements","/stock-receipts?warehouseId="+b)) ok(get(route),employee,403);
        ok(body(post("/stock-receipts"),payload(b,1,1)),employee,403);
        ok(body(post("/stock-receipts/"+other.get("id").asText()+"/confirm"),Map.of("version",0)),employee,403);
        var edit=payload(b,1,1);edit.put("version",0);ok(body(put("/stock-receipts/"+other.get("id").asText()),edit),employee,403);
        ok(body(post("/stock-receipts/"+other.get("id").asText()+"/cancel"),Map.of("version",0)),employee,403);
        assign(a,outsiderId);
        for(String route:List.of("/stock-receipts","/stock-receipts/"+draft.get("id").asText(),"/warehouses/"+a+"/inventory","/warehouses/"+a+"/inventory-movements")) ok(get(route),outsider,403);
        ok(body(post("/stock-receipts"),payload(a,1,1)),outsider,403);
        edit.put("warehouseId",a);ok(body(put("/stock-receipts/"+draft.get("id").asText()),edit),outsider,403);
        for(String action:List.of("confirm","cancel")) ok(body(post("/stock-receipts/"+draft.get("id").asText()+"/"+action),Map.of("version",0)),outsider,403);
        mvc.perform(delete("/users/"+employeeId+"/warehouses/"+a).header("Authorization","Bearer "+admin)).andExpect(status().isNoContent());
        assertEquals(0,ok(get("/stock-receipts"),employee,200).get("total").asLong());
        for(String route:List.of("/stock-receipts/"+draft.get("id").asText(),"/warehouses/"+a+"/inventory","/warehouses/"+a+"/inventory-movements")) ok(get(route),employee,403);
        ok(body(post("/stock-receipts/"+draft.get("id").asText()+"/confirm"),Map.of("version",0)),employee,403);
        ok(body(post("/stock-receipts/"+draft.get("id").asText()+"/cancel"),Map.of("version",0)),employee,403);
        ok(body(put("/stock-receipts/"+draft.get("id").asText()),edit),employee,403);
        ok(body(post("/stock-receipts"),payload(a,1,1)),employee,403);
    }
    @Test void inactiveCatalogConfirmationRecheckSnapshotAndHistoricalReversal() throws Exception {
        fixture();var draft=receipt(10); var product=products.findById(UUID.fromString(p)).orElseThrow();product.setStatus((short)0);products.saveAndFlush(product);
        ok(body(post("/stock-receipts/"+draft.get("id").asText()+"/confirm"),Map.of("version",0)),employee,409);
        ok(body(post("/stock-receipts"),payload(a,1,1)),employee,409);
        var edit=payload(a,5,1);edit.put("version",0);ok(body(put("/stock-receipts/"+draft.get("id").asText()),edit),employee,409);
        product.setStatus((short)1);product.setCode("RENAMED");product.setName("At confirmation");products.saveAndFlush(product);
        var wh=warehouses.findById(UUID.fromString(a)).orElseThrow();wh.setStatus((short)0);warehouses.saveAndFlush(wh);
        ok(body(post("/stock-receipts/"+draft.get("id").asText()+"/confirm"),Map.of("version",0)),employee,409);
        ok(body(post("/stock-receipts"),payload(a,1,1)),employee,409);
        ok(body(put("/stock-receipts/"+draft.get("id").asText()),edit),employee,409);
        wh.setStatus((short)1);warehouses.saveAndFlush(wh);var confirmed=confirm(draft);assertEquals("RENAMED",confirmed.get("lines").get(0).get("productCode").asText());
        product.setCode("LATER");product.setName("After confirmation");product.setStatus((short)0);products.saveAndFlush(product);wh.setStatus((short)0);warehouses.saveAndFlush(wh);
        assertEquals("RENAMED",ok(get("/stock-receipts/"+draft.get("id").asText()),employee,200).get("lines").get(0).get("productCode").asText());
        assertEquals(10,stock(a,p));confirm(draft);cancel(confirmed);assertEquals(0,stock(a,p));
        var history=ok(get("/warehouses/"+a+"/inventory-movements?productId="+p+"&from=2020-01-01T00:00:00Z&to=2100-01-01T00:00:00Z"),employee,200);
        assertEquals(2,history.get("total").asLong());assertEquals("RENAMED",history.get("items").get(0).get("productCode").asText());consistent();
    }
    @Test void strictNumericValidationDuplicateEmptyAndOverflow() throws Exception {
        fixture();
        for(Object qty:List.of(0,-1,1.5,"1",1.0)) {var x=payload(a,1,1);x.put("lines",List.of(Map.of("productId",p,"quantity",qty,"unitPrice",1)));ok(body(post("/stock-receipts"),x),employee,400);}
        for(Object price:List.of(-1,1.5,"1",1.0)) {var x=payload(a,1,1);x.put("lines",List.of(Map.of("productId",p,"quantity",1,"unitPrice",price)));ok(body(post("/stock-receipts"),x),employee,400);}
        var x=payload(a,1,1);x.put("lines",List.of());ok(body(post("/stock-receipts"),x),employee,400);
        x.put("lines",List.of(Map.of("productId",p,"quantity",1,"unitPrice",1),Map.of("productId",p,"quantity",1,"unitPrice",2)));ok(body(post("/stock-receipts"),x),employee,400);
        ok(body(post("/stock-receipts"),payload(a,Long.MAX_VALUE,2)),employee,409);
        x.put("lines",List.of(Map.of("productId",p,"quantity",1,"unitPrice",Long.MAX_VALUE),Map.of("productId",p2,"quantity",1,"unitPrice",1)));ok(body(post("/stock-receipts"),x),employee,409);
        confirm(ok(body(post("/stock-receipts"),payload(a,Long.MAX_VALUE,0)),employee,201));var overflow=receipt(1);ok(body(post("/stock-receipts/"+overflow.get("id").asText()+"/confirm"),Map.of("version",0)),employee,409);
        assertEquals(Long.MAX_VALUE,stock(a,p));assertEquals("DRAFT",ok(get("/stock-receipts/"+overflow.get("id").asText()),employee,200).get("status").asText());consistent();
    }
    @Test void reasonStaleVersionsImmutableConfirmedAndPagination() throws Exception {
        fixture();var draft=receipt(10);String id=draft.get("id").asText();
        ok(body(post("/stock-receipts/"+id+"/confirm"),Map.of("version",3)),employee,409);
        ok(body(post("/stock-receipts/"+id+"/confirm"),Map.of()),employee,400);
        var confirmed=confirm(draft);
        ok(body(post("/stock-receipts/"+id+"/cancel"),Map.of("version",1)),employee,400);
        ok(body(post("/stock-receipts/"+id+"/cancel"),Map.of("version",0,"reason","wrong")),employee,409);
        var edit=payload(a,5,1);edit.put("version",1);ok(body(put("/stock-receipts/"+id),edit),employee,409);
        assertEquals(10,stock(a,p));
        for(String route:List.of("/stock-receipts","/warehouses/"+a+"/inventory","/warehouses/"+a+"/inventory-movements")) {ok(get(route+"?page=0"),employee,400);ok(get(route+"?pageSize=101"),employee,400);}
        ok(get("/stock-receipts?from=2026-10-09&to=2026-10-08"),employee,400);
        ok(get("/warehouses/"+a+"/inventory-movements?from=2026-10-09T00:00:00Z&to=2026-10-08T00:00:00Z"),employee,400);
        assertEquals(1,ok(get("/stock-receipts?status=CONFIRMED&from=2026-10-08&to=2026-10-08&pageSize=1"),employee,200).get("total").asLong());
        assertEquals(0,ok(get("/stock-receipts?status=DRAFT"),employee,200).get("total").asLong());
        assertEquals(2,ok(get("/warehouses/"+a+"/inventory?pageSize=1"),employee,200).get("total").asLong());
        assertEquals(0,ok(get("/warehouses/"+a+"/inventory-movements?to=2020-01-01T00:00:00Z"),employee,200).get("total").asLong());
        cancel(confirmed);consistent();
    }
    @Test void postgresAuditGuardsRejectDirectMutation() throws Exception {
        String database=stockJdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) c->c.getMetaData().getDatabaseProductName());
        org.junit.jupiter.api.Assumptions.assumeTrue("PostgreSQL".equals(database),"PL/pgSQL guards require PostgreSQL");
        fixture();var confirmed=confirm(receipt(10));UUID id=UUID.fromString(confirmed.get("id").asText());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->stockJdbc.update("UPDATE inventory_movements SET quantity_change=20 WHERE receipt_id=?",id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->stockJdbc.update("DELETE FROM inventory_movements WHERE receipt_id=?",id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->stockJdbc.update("UPDATE stock_receipt_lines SET product_name='Tampered' WHERE receipt_id=?",id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->stockJdbc.update("UPDATE stock_receipts SET note='Tampered',version=version+1 WHERE id=?",id));
        cancel(confirmed);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->stockJdbc.update("UPDATE stock_receipts SET status='DRAFT',version=version+1 WHERE id=?",id));
        consistent();
    }

}
