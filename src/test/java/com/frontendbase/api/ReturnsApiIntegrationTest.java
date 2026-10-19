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
class ReturnsApiIntegrationTest {
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


    String operator;
    UUID operatorId;
    void returnsFixture() throws Exception {
        fixture(); confirm(receipt(100));
        operatorId=account("returns-operator","RETURNS",List.of("SALES_RETURN_VIEW","SALES_RETURN_CREATE",
            "SALES_RETURN_UPDATE","SALES_RETURN_CONFIRM","SALES_RETURN_CANCEL","REFUND_VIEW","REFUND_CREATE",
            "REFUND_CANCEL","PAYMENT_VIEW","PAYMENT_CREATE","PAYMENT_CANCEL"));
        assign(a,operatorId); operator=login("returns-operator");
    }
    JsonNode order(long qty,long price,long discount) throws Exception {
        var payload=salePayload(a,qty,price);payload.put("discountAmount",discount);
        return saleConfirm(ok(body(post("/sales-orders"),payload),employee,201));
    }
    Map<String,Object> returnPayload(JsonNode order,long qty) {
        return new HashMap<>(Map.of("salesOrderId",order.get("id").asText(),"returnDate","2026-10-16",
            "reason","Unused goods","lines",List.of(Map.of("productId",p,"quantity",qty))));
    }
    JsonNode draftReturn(JsonNode order,long qty) throws Exception {
        return ok(body(post("/sales-returns"),returnPayload(order,qty)),operator,201);
    }
    MockHttpServletRequestBuilder confirmReturnRequest(JsonNode r) throws Exception {
        return body(post("/sales-returns/"+r.get("id").asText()+"/confirm"),Map.of("version",r.get("version").asLong()));
    }
    JsonNode confirmReturn(JsonNode r) throws Exception { return ok(confirmReturnRequest(r),operator,200); }
    JsonNode collect(JsonNode order,long amount) throws Exception {
        return ok(body(post(route(order,"/payments")).header("Idempotency-Key",UUID.randomUUID().toString()),
            Map.of("amount",amount,"paymentDate","2026-10-16","method","CASH")),operator,201);
    }
    Map<String,Object> refundPayload(Object amount) {
        return new HashMap<>(Map.of("amount",amount,"refundDate","2026-10-16","method","CASH","reason","Returned goods"));
    }
    MockHttpServletRequestBuilder refundRequest(JsonNode order,Object amount,String key) throws Exception {
        return body(post(route(order,"/refunds")).header("Idempotency-Key",key),refundPayload(amount));
    }
    JsonNode refund(JsonNode order,long amount,String key) throws Exception {
        return ok(refundRequest(order,amount,key),operator,201);
    }
    JsonNode current(JsonNode order) throws Exception { return ok(get(route(order,"")),admin,200); }
    int asOperator(MockHttpServletRequestBuilder r) throws Exception {
        return mvc.perform(r.header("Authorization","Bearer "+operator)).andReturn().getResponse().getStatus();
    }
    void amounts(JsonNode s,long returned,long adjusted,long paid,long refunded,long remaining,long refundable) {
        assertEquals(returned,s.get("returnedAmount").asLong());
        assertEquals(adjusted,s.get("adjustedTotalAmount").asLong());
        assertEquals(paid,s.get("paidAmount").asLong());
        assertEquals(refunded,s.get("refundedAmount").asLong());
        assertEquals(paid-refunded,s.get("netPaidAmount").asLong());
        assertEquals(remaining,s.get("remainingAmount").asLong());
        assertEquals(refundable,s.get("refundableAmount").asLong());
    }

    @Test void lifecycleSettlementRefundRetryAndReceiptReversal() throws Exception {
        returnsFixture(); var order=order(10,100000,0); var payment=collect(order,600000);
        long count=movements(); var first=confirmReturn(draftReturn(order,2));
        amounts(current(order),200000,800000,600000,0,200000,0);
        assertEquals(count+1,movements()); confirmReturn(first); assertEquals(count+1,movements());
        assertEquals("PAYMENT_EXCEEDS_REMAINING",ok(body(post(route(order,"/payments")).header("Idempotency-Key",UUID.randomUUID().toString()),
            Map.of("amount",200001,"paymentDate","2026-10-16","method","CASH")),operator,409).get("code").asText());
        confirmReturn(draftReturn(order,5));
        amounts(current(order),700000,300000,600000,0,0,300000);
        assertEquals("PAID",current(order).get("paymentStatus").asText());
        var candidates=ok(get("/refundables"),operator,200);
        assertEquals(1,candidates.get("total").asLong()); assertTrue(candidates.get("items").get(0).has("customerSnapshot"));
        String key=UUID.randomUUID().toString(); var refund=refund(order,300000,key);
        amounts(refund.get("order"),700000,300000,600000,300000,0,0);
        assertEquals(refund.get("refund").get("id"),refund(order,300000,key).get("refund").get("id"));
        assertEquals("IDEMPOTENCY_CONFLICT",ok(refundRequest(order,1,key),operator,409).get("code").asText());
        assertEquals("REFUND_EXCEEDS_REFUNDABLE",ok(refundRequest(order,1,UUID.randomUUID().toString()),operator,409).get("code").asText());
        assertEquals("PAYMENT_BELOW_REFUNDED",ok(body(post("/payments/"+payment.get("payment").get("id").asText()+"/cancel"),
            Map.of("reason","wrong")),operator,409).get("code").asText());
        long stock=stock(a,p);
        var cancelled=ok(body(post("/refunds/"+refund.get("refund").get("id").asText()+"/cancel"),Map.of("reason","wrong record")),operator,200);
        assertEquals("CANCELLED",cancelled.get("refund").get("status").asText());
        assertEquals("CANCELLED",refund(order,300000,key).get("refund").get("status").asText());
        assertEquals(stock,stock(a,p));
        var reversed=ok(body(post("/payments/"+payment.get("payment").get("id").asText()+"/cancel"),Map.of("reason","wrong record")),operator,200);
        amounts(reversed.get("order"),700000,300000,0,0,300000,0);
        assertEquals(order.get("version"),current(order).get("version"));
        consistent();
    }

    @Test void cumulativeRoundingQuoteConflictReviewAndFullReturn() throws Exception {
        returnsFixture(); var order=order(3,1,1);
        var a=draftReturn(order,1); var b=draftReturn(order,1);
        assertEquals(0,a.get("totalAmount").asLong()); confirmReturn(a);
        var conflict=ok(confirmReturnRequest(b),operator,409);
        assertEquals("RETURN_QUOTE_CHANGED",conflict.get("code").asText());
        assertEquals(1,conflict.get("details").get("quote").get("totalAmount").asLong());
        var update=new HashMap<>(returnPayload(order,1));update.remove("salesOrderId");update.put("version",b.get("version").asLong());
        b=ok(body(put("/sales-returns/"+b.get("id").asText()),update),operator,200);
        confirmReturn(b); confirmReturn(draftReturn(order,1));
        amounts(current(order),2,0,0,0,0,0);
        assertEquals(100,stock(this.a,p)); consistent();
    }

    @Test void freeReturnsBlockLegacyCancellationAndCancelledSourceBlocksDraft() throws Exception {
        returnsFixture(); var free=order(3,0,0); var r=confirmReturn(draftReturn(free,3));
        amounts(current(free),0,0,0,0,0,0);
        assertEquals("SALES_ORDER_HAS_RETURNS",ok(body(post(route(free,"/cancel")),
            Map.of("version",free.get("version").asLong(),"reason","recovered","goodsReturned",true)),employee,409).get("code").asText());
        assertEquals("RETURN_ADJUSTMENT_NOT_SUPPORTED",ok(body(post("/sales-returns/"+r.get("id").asText()+"/cancel"),
            Map.of("version",r.get("version").asLong(),"reason","wrong")),operator,409).get("code").asText());
        var other=order(1,10,0);var draft=draftReturn(other,1);saleCancel(other);
        assertEquals("INVALID_SALES_ORDER_STATUS",ok(confirmReturnRequest(draft),operator,409).get("code").asText());
        ok(body(post("/sales-returns/"+draft.get("id").asText()+"/cancel"),
            Map.of("version",draft.get("version").asLong(),"reason","source cancelled")),operator,200);
        consistent();
    }

    @Test void inactiveSourcesAndStrictInputsPermissionsAndWarehouseScope() throws Exception {
        returnsFixture();var customer=customer(a);
        var payload=salePayload(a,3,10);payload.put("customerId",customer.get("id").asText());
        var order=saleConfirm(ok(body(post("/sales-orders"),payload),employee,201));
        stockJdbc.update("UPDATE products SET status=0 WHERE id=?",UUID.fromString(p));
        stockJdbc.update("UPDATE warehouses SET status=0 WHERE id=?",UUID.fromString(a));
        stockJdbc.update("UPDATE customers SET status=0 WHERE id=?",UUID.fromString(customer.get("id").asText()));
        var returnable=ok(get(route(order,"/returnable-lines")),operator,200);
        assertEquals(3,returnable.get(0).get("returnableQuantity").asLong());
        ok(get(route(order,"/returnable-lines")),employee,403); // operator needs no order/inventory view
        var invalid=returnPayload(order,1);invalid.put("warehouseId",b);ok(body(post("/sales-returns"),invalid),operator,400);
        for(Object qty:List.of(0,-1,1.5,"1")) {
            invalid=returnPayload(order,1);invalid.put("lines",List.of(Map.of("productId",p,"quantity",qty)));
            ok(body(post("/sales-returns"),invalid),operator,400);
        }
        invalid=returnPayload(order,1);invalid.put("lines",List.of(Map.of("productId",p,"quantity",1),Map.of("productId",p,"quantity",1)));
        assertEquals("DUPLICATE_PRODUCT",ok(body(post("/sales-returns"),invalid),operator,400).get("code").asText());
        invalid=returnPayload(order,1);invalid.put("lines",List.of(Map.of("productId",p2,"quantity",1)));
        assertEquals("PRODUCT_NOT_IN_SALES_ORDER",ok(body(post("/sales-returns"),invalid),operator,400).get("code").asText());
        var draft=draftReturn(order,1);
        ok(body(post("/sales-returns/"+draft.get("id").asText()+"/confirm"),Map.of("version",99)),operator,409);
        confirmReturn(draft);assertEquals("RETURN_EXCEEDS_QUANTITY",ok(body(post("/sales-returns"),returnPayload(order,3)),operator,409).get("code").asText());
        stockJdbc.update("DELETE FROM user_warehouses WHERE user_id=?",operatorId);
        ok(get("/sales-returns/"+draft.get("id").asText()),operator,403);
        ok(confirmReturnRequest(draft),operator,403);
        assertEquals(0,ok(get("/sales-returns"),operator,200).get("total").asLong());
        ok(get("/refundables?warehouseId="+a),operator,403);
    }

    @Test void reportsDashboardMovementsAndWorkbooksMatchNewTotals() throws Exception {
        returnsFixture();var order=order(10,100000,0);collect(order,600000);var r=confirmReturn(draftReturn(order,7));
        var refunded=refund(order,300000,UUID.randomUUID().toString());
        String period="?from=2026-10-16&to=2026-10-16";
        var sales=ok(get("/reports/sales"+period),admin,200).get("summary");
        assertEquals(700000,sales.get("returnedSalesAmount").asLong());assertEquals(300000,sales.get("netSalesAmount").asLong());
        var payments=ok(get("/reports/payments"+period),admin,200).get("summary");
        assertEquals(600000,payments.get("netRecordedAmount").asLong());assertEquals(300000,payments.get("netRefundedAmount").asLong());
        assertEquals(300000,payments.get("netCashFlowAmount").asLong());
        var inventory=ok(get("/reports/inventory"+period),admin,200).get("items").get(0);
        assertEquals(7,inventory.get("saleReturnQuantity").asLong());assertEquals(97,inventory.get("closingQuantity").asLong());
        var movements=ok(get("/warehouses/"+a+"/inventory-movements?type=SALE_RETURN"),admin,200).get("items");
        assertEquals(r.get("id"),movements.get(0).get("salesReturnId"));assertEquals(r.get("code"),movements.get(0).get("salesReturnCode"));
        for(String report:List.of("inventory","sales","payments")) {
            var response=mvc.perform(get("/reports/"+report+"/export"+period).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andReturn().getResponse();
            try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
                assertNotNull(workbook.getSheet("Chi tiết"));
            }
        }
        var dashboard=ok(get("/reports/dashboard"+period),admin,200);
        assertNotNull(dashboard.get("currentSections").get("refunds"));
        ok(body(post("/refunds/"+refunded.get("refund").get("id").asText()+"/cancel"),Map.of("reason","wrong")),operator,200);
        payments=ok(get("/reports/payments"+period),admin,200).get("summary");
        assertEquals(300000,payments.get("reversedRefundCashAmount").asLong());assertEquals(0,payments.get("netRefundedAmount").asLong());
        assertEquals(300000,ok(get("/reports/dashboard"+period),admin,200).get("currentSections").get("refunds").get("refundableAmount").asLong());
        consistent();
    }

    @Test void twoReturnsCannotOverReturnAndConcurrentRetryAddsOneMovement() throws Exception {
        returnsFixture();var order=order(1,100,0);var first=draftReturn(order,1);var second=draftReturn(order,1);
        var statuses=concurrent(()->asOperator(confirmReturnRequest(first)),()->asOperator(confirmReturnRequest(second)));
        assertTrue(statuses.contains(200));assertTrue(statuses.contains(409));
        assertEquals(100,current(order).get("returnedAmount").asLong());assertEquals(100,stock(a,p));
        statuses=concurrent(()->asOperator(confirmReturnRequest(statusesReturn(first,second))),
            ()->asOperator(confirmReturnRequest(statusesReturn(first,second))));
        assertEquals(List.of(200,200),statuses);consistent();
    }
    JsonNode statusesReturn(JsonNode first,JsonNode second) throws Exception {
        var r=ok(get("/sales-returns/"+first.get("id").asText()),operator,200);
        return "CONFIRMED".equals(r.get("status").asText())?r:ok(get("/sales-returns/"+second.get("id").asText()),operator,200);
    }

    @Test void returnVersusOrderCancellationSerializes() throws Exception {
        returnsFixture();var order=order(1,100,0);var r=draftReturn(order,1);
        var statuses=concurrent(()->asOperator(confirmReturnRequest(r)),()->requestStatus(body(post(route(order,"/cancel")),
            Map.of("version",order.get("version").asLong(),"reason","recovered","goodsReturned",true))));
        assertTrue(statuses.contains(200));assertTrue(statuses.contains(409));assertEquals(100,stock(a,p));consistent();
    }

    @Test void concurrentRefundsAndIdempotentRetriesNeverOverRefund() throws Exception {
        returnsFixture();var order=order(1,100,0);collect(order,100);confirmReturn(draftReturn(order,1));
        var statuses=concurrent(()->asOperator(refundRequest(order,100,UUID.randomUUID().toString())),
            ()->asOperator(refundRequest(order,100,UUID.randomUUID().toString())));
        assertTrue(statuses.contains(201));assertTrue(statuses.contains(409));
        assertEquals(100,current(order).get("refundedAmount").asLong());
        var order2=order(1,100,0);collect(order2,100);confirmReturn(draftReturn(order2,1));var key=UUID.randomUUID().toString();
        assertEquals(List.of(201,201),concurrent(()->asOperator(refundRequest(order2,100,key)),()->asOperator(refundRequest(order2,100,key))));
        assertEquals(1L,stockJdbc.queryForObject("SELECT count(*) FROM refunds WHERE sales_order_id=?",Long.class,UUID.fromString(order2.get("id").asText())));
    }

    @Test void refundVersusPaymentReversalAndCollectionVersusReturnSerialize() throws Exception {
        returnsFixture();var order=order(1,100,0);var payment=collect(order,100);confirmReturn(draftReturn(order,1));
        var statuses=concurrent(()->asOperator(refundRequest(order,100,UUID.randomUUID().toString())),
            ()->asOperator(body(post("/payments/"+payment.get("payment").get("id").asText()+"/cancel"),Map.of("reason","wrong"))));
        assertEquals(1,statuses.stream().filter(x->x==409).count());
        var s=current(order);assertTrue(s.get("paidAmount").asLong()>=s.get("refundedAmount").asLong());
        var order2=order(1,100,0);var r=draftReturn(order2,1);
        statuses=concurrent(()->asOperator(confirmReturnRequest(r)),
            ()->asOperator(body(post(route(order2,"/payments")).header("Idempotency-Key",UUID.randomUUID().toString()),
                Map.of("amount",100,"paymentDate","2026-10-16","method","CASH"))));
        assertEquals(200,statuses.get(0)); assertTrue(statuses.get(1)==201||statuses.get(1)==409);
        s=current(order2);assertEquals(0,s.get("remainingAmount").asLong());
        assertEquals(s.get("paidAmount").asLong(),s.get("refundableAmount").asLong());consistent();
    }

    @Test void confirmationRollsBackAllStockWhenLaterLineOverflows() throws Exception {
        returnsFixture(); var payload=salePayload(a,1,1);
        payload.put("lines",List.of(Map.of("productId",p,"quantity",1,"unitPrice",1),Map.of("productId",p2,"quantity",1,"unitPrice",1)));
        var receipt=payload(a,1,0);receipt.put("lines",List.of(Map.of("productId",p2,"quantity",1,"unitPrice",0)));
        confirm(ok(body(post("/stock-receipts"),receipt),employee,201));
        var order=saleConfirm(ok(body(post("/sales-orders"),payload),employee,201));
        var rp=returnPayload(order,1);rp.put("lines",List.of(Map.of("productId",p,"quantity",1),Map.of("productId",p2,"quantity",1)));
        var r=ok(body(post("/sales-returns"),rp),operator,201);
        stockJdbc.update("UPDATE inventory_balances SET quantity=? WHERE warehouse_id=? AND product_id=?",Long.MAX_VALUE,UUID.fromString(a),UUID.fromString(p2));
        long before=stock(a,p),movements=movements();
        assertEquals("NUMERIC_OVERFLOW",ok(confirmReturnRequest(r),operator,409).get("code").asText());
        assertEquals(before,stock(a,p));assertEquals(movements,movements());
        assertEquals("DRAFT",ok(get("/sales-returns/"+r.get("id").asText()),operator,200).get("status").asText());
    }

    @Test void postgresAuditGuardsProtectConfirmedReturnsRefundsAndOldPayments() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue("PostgreSQL".equals(stockJdbc.execute(
            (org.springframework.jdbc.core.ConnectionCallback<String>)c->c.getMetaData().getDatabaseProductName())));
        returnsFixture();var order=order(1,100,0);collect(order,100);var r=confirmReturn(draftReturn(order,1));
        var refund=refund(order,100,UUID.randomUUID().toString());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->stockJdbc.update("DELETE FROM sales_returns WHERE id=?",UUID.fromString(r.get("id").asText())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->stockJdbc.update("UPDATE sales_return_lines SET return_amount=0 WHERE sales_return_id=?",UUID.fromString(r.get("id").asText())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->stockJdbc.update("UPDATE refunds SET amount=1 WHERE id=?",UUID.fromString(refund.get("refund").get("id").asText())));
        consistent();
    }

    @Test void returnRevisionMakesExistingStocktakeStaleAndDebtUsesAdjustedAmounts() throws Exception {
        returnsFixture();var customer=customer(a);var payload=salePayload(a,10,100000);payload.put("customerId",customer.get("id").asText());
        var order=saleConfirm(ok(body(post("/sales-orders"),payload),employee,201));collect(order,600000);
        var stocktake=ok(body(post("/stocktakes"),Map.of("warehouseId",a,"lines",List.of(Map.of("productId",p)))),admin,201);
        stocktake=ok(body(put("/stocktakes/"+stocktake.get("id").asText()),Map.of("version",stocktake.get("version").asLong(),
            "lines",List.of(Map.of("productId",p,"countedQuantity",90)))),admin,200);
        long revision=stocktake.get("lines").get(0).get("inventoryRevision").asLong();
        confirmReturn(draftReturn(order,2));
        assertEquals(revision+1,stockJdbc.queryForObject("SELECT revision FROM inventory_balances WHERE warehouse_id=? AND product_id=?",Long.class,UUID.fromString(a),UUID.fromString(p)));
        assertEquals("STOCKTAKE_STALE",ok(body(post("/stocktakes/"+stocktake.get("id").asText()+"/confirm"),
            Map.of("version",stocktake.get("version").asLong())),admin,409).get("code").asText());
        assertEquals(200000,ok(get("/receivables"),admin,200).get("items").get(0).get("remainingAmount").asLong());
        assertEquals(200000,ok(get("/reports/receivables-summary"),admin,200).get("totalRemainingAmount").asLong());
        assertEquals(0,ok(get("/receivables/walk-in-orders"),admin,200).get("total").asLong());
        confirmReturn(draftReturn(order,5));
        assertEquals(0,ok(get("/receivables"),admin,200).get("total").asLong());
        var refundable=ok(get("/refundables"),operator,200).get("items").get(0);
        assertEquals(customer.get("id"),refundable.get("customerId"));
        consistent();
    }

    @Test void hugeLongValuesKeepExactQuoteAndPostgresValuation() throws Exception {
        returnsFixture();
        var receipt=ok(body(post("/stock-receipts"),payload(a,Long.MAX_VALUE-100,0)),employee,201);confirm(receipt);
        var order=order(Long.MAX_VALUE,1,1);
        var first=confirmReturn(draftReturn(order,Long.MAX_VALUE-1));
        assertEquals(Long.MAX_VALUE-2,first.get("totalAmount").asLong());
        var last=confirmReturn(draftReturn(order,1));assertEquals(1,last.get("totalAmount").asLong());
        amounts(current(order),Long.MAX_VALUE-1,0,0,0,0,0);assertEquals(Long.MAX_VALUE,stock(a,p));consistent();
    }

    @Test void originalSnapshotsAndLargestRemainderAcrossMultipleLinesRemainImmutable() throws Exception {
        returnsFixture();
        confirm(ok(body(post("/stock-receipts"),Map.of("warehouseId",a,"receiptDate","2026-10-16",
            "lines",List.of(Map.of("productId",p2,"quantity",3,"unitPrice",0)))),employee,201));
        var payload=salePayload(a,3,1);payload.put("discountAmount",1);
        payload.put("lines",List.of(Map.of("productId",p,"quantity",3,"unitPrice",1),Map.of("productId",p2,"quantity",3,"unitPrice",1)));
        var order=saleConfirm(ok(body(post("/sales-orders"),payload),employee,201));
        var lines=ok(get(route(order,"/returnable-lines")),operator,200);
        String firstId=List.of(p,p2).stream().sorted().findFirst().orElseThrow();
        for(var line:lines) assertEquals(line.get("productId").asText().equals(firstId)?1:0,line.get("allocatedDiscountAmount").asLong());
        stockJdbc.update("UPDATE products SET name='Renamed current catalog' WHERE id=?",UUID.fromString(p));
        var rp=returnPayload(order,1);rp.put("lines",List.of(Map.of("productId",p,"quantity",3),Map.of("productId",p2,"quantity",3)));
        var r=confirmReturn(ok(body(post("/sales-returns"),rp),operator,201));
        assertEquals(5,r.get("totalAmount").asLong());
        for(var line:r.get("lines")) assertNotEquals("Renamed current catalog",line.get("productName").asText());
        amounts(current(order),5,0,0,0,0,0);consistent();
    }

    @Test void refundValidationBankTransferReplaysPermissionsAndScopedLists() throws Exception {
        returnsFixture();var order=order(1,100,0);collect(order,100);confirmReturn(draftReturn(order,1));
        for(Object amount:List.of(0,-1,1.5,"1")) ok(refundRequest(order,amount,UUID.randomUUID().toString()),operator,400);
        ok(body(post(route(order,"/refunds")),refundPayload(1)),operator,400);
        ok(refundRequest(order,1,"bad-key"),operator,400);
        var payload=refundPayload(100);payload.put("customerId",UUID.randomUUID());ok(body(post(route(order,"/refunds")).header("Idempotency-Key",UUID.randomUUID().toString()),payload),operator,400);
        var key=UUID.randomUUID().toString();payload=refundPayload(100);payload.put("method","BANK_TRANSFER");
        var created=ok(body(post(route(order,"/refunds")).header("Idempotency-Key",key),payload),operator,201);
        var id=created.get("refund").get("id").asText();
        assertEquals(1,ok(get("/refunds?warehouseId="+a+"&salesOrderId="+order.get("id").asText()+"&method=BANK_TRANSFER&from=2026-10-16&to=2026-10-16"),operator,200).get("total").asLong());
        assertEquals(0,ok(get("/refunds?to=2026-10-15"),operator,200).get("total").asLong());
        var metrics=ok(get("/reports/payments?from=2026-10-16&to=2026-10-16"),admin,200).get("summary");
        assertEquals(100,metrics.get("recordedRefundBankTransferAmount").asLong());
        assertEquals(0,metrics.get("recordedRefundCashAmount").asLong());
        ok(body(post("/refunds/"+id+"/cancel"),Map.of("reason"," ")),operator,400);
        var changed=refundPayload(100);changed.put("method","BANK_TRANSFER");changed.put("reason","changed");
        ok(body(post(route(order,"/refunds")).header("Idempotency-Key",key),changed),operator,409);
        stockJdbc.update("DELETE FROM user_warehouses WHERE user_id=?",operatorId);
        ok(body(post(route(order,"/refunds")).header("Idempotency-Key",key),payload),operator,403);
        ok(get("/refunds/"+id),operator,403);ok(body(post("/refunds/"+id+"/cancel"),Map.of("reason","wrong")),operator,403);
        assertEquals(0,ok(get("/refunds"),operator,200).get("total").asLong());
        ok(get("/refunds"),employee,403);
    }

    @Test void dashboardOmitsRefundSectionWithoutLiveRefundPermission() throws Exception {
        returnsFixture();var readerId=account("dashboard-reader","DASHBOARD_READER",List.of("REPORT_DASHBOARD_VIEW"));
        assign(a,readerId);String dashboardReader=login("dashboard-reader");
        var dashboard=ok(get("/reports/dashboard?from=2026-10-16&to=2026-10-16"),dashboardReader,200);
        assertFalse(dashboard.hasNonNull("currentSections"));
    }

    JsonNode findReturnableOrders(String code, String token, int page, int size) throws Exception {
        return ok(get("/sales-orders/returnable-orders").param("code",code)
                .param("page",Integer.toString(page)).param("pageSize",Integer.toString(size)),token,200);
    }

    @Test void returnableOrderCodeSearchTrimsCaseAndRejectsWildcardsAndBadInputs() throws Exception {
        returnsFixture();
        var order=order(3,100,0);
        String code=order.get("code").asText();
        for(String query:List.of("  "+code.toLowerCase(Locale.ROOT)+"  ",code.substring(0,12).toUpperCase(Locale.ROOT))) {
            var response=findReturnableOrders(query,operator,1,10);
            assertEquals(1,response.get("total").asLong());
            var item=response.get("items").get(0);
            assertEquals(order.get("id"),item.get("id"));
            assertEquals(code,item.get("code").asText());
            assertEquals(a,item.get("warehouseId").asText());
            assertEquals("Name A",item.get("warehouseName").asText());
            assertEquals("2026-10-09",item.get("saleDate").asText());
            assertEquals("Kh\u00e1ch l\u1ebb",item.get("customerName").asText());
            assertEquals(300,item.get("totalAmount").asLong());
            assertEquals(7,item.size());
        }
        for(String query:List.of("NO-SUCH-CODE","%","_","!","' OR 1=1 --"))
            assertEquals(0,findReturnableOrders(query,operator,1,10).get("total").asLong());
        var empty=findReturnableOrders("NO-SUCH-CODE",operator,1,10);
        assertEquals(0,empty.get("items").size());assertEquals(1,empty.get("page").asInt());assertEquals(10,empty.get("pageSize").asInt());
        ok(get("/sales-orders/returnable-orders"),operator,400);
        for(String query:List.of(""," ","\t"))
            assertEquals("INVALID_REQUEST",ok(get("/sales-orders/returnable-orders").param("code",query),operator,400).get("code").asText());
        for(String query:List.of("page=0","page=-1","pageSize=0","pageSize=101"))
            assertEquals("INVALID_PAGINATION",ok(get("/sales-orders/returnable-orders?code=BH-&"+query),operator,400).get("code").asText());
        ok(get("/sales-orders/returnable-orders?code=BH-&page=abc"),operator,400);
    }

    @Test void returnableOrderEligibilityUsesConfirmedQuantitiesIncludingFreeGoods() throws Exception {
        returnsFixture();
        var free=order(2,0,0);
        var draft=draftReturn(free,2);
        assertEquals(1,findReturnableOrders(free.get("code").asText(),operator,1,10).get("total").asLong());
        ok(body(post("/sales-returns/"+draft.get("id").asText()+"/cancel"),
                Map.of("version",0,"reason","Choose partial return")),operator,200);
        confirmReturn(draftReturn(free,1));
        assertEquals(1,findReturnableOrders(free.get("code").asText(),operator,1,10).get("total").asLong());
        confirmReturn(draftReturn(free,1));
        assertEquals(0,findReturnableOrders(free.get("code").asText(),operator,1,10).get("total").asLong());
        assertEquals(0,current(free).get("returnedAmount").asLong()); // money alone cannot determine eligibility

        var salesDraft=sale(1);
        assertEquals(0,findReturnableOrders(salesDraft.get("code").asText(),operator,1,10).get("total").asLong());
        var cancelled=saleCancel(order(1,100,0));
        assertEquals(0,findReturnableOrders(cancelled.get("code").asText(),operator,1,10).get("total").asLong());
        consistent();
    }

    @Test void returnableOrderCountAndPaginationAreScopedBeforePagingWithInactiveHistory() throws Exception {
        returnsFixture();
        var customer=customer(a);
        var payload=salePayload(a,1,100);payload.put("customerId",customer.get("id").asText());
        var named=saleConfirm(ok(body(post("/sales-orders"),payload),employee,201));
        order(1,200,0);order(1,300,0);
        var received=ok(body(post("/stock-receipts"),payload(b,1,0)),admin,201);
        ok(body(post("/stock-receipts/"+received.get("id").asText()+"/confirm"),Map.of("version",0)),admin,200);
        var outside=ok(body(post("/sales-orders"),salePayload(b,1,100)),admin,201);
        outside=ok(body(post(route(outside,"/confirm")),Map.of("version",0)),admin,200);
        String outsideCode=outside.get("code").asText();
        assertEquals(0,findReturnableOrders(outsideCode,operator,1,1).get("total").asLong());
        assertEquals(1,findReturnableOrders(outsideCode,admin,1,1).get("total").asLong());
        assertEquals(4,findReturnableOrders("bh-",admin,1,1).get("total").asLong());

        UUID viewerId=account("return-search-viewer","RETURN_SEARCH_VIEWER",List.of("SALES_RETURN_VIEW"));
        assign(a,viewerId);String viewer=login("return-search-viewer");

        stockJdbc.update("UPDATE customers SET name='Changed current name',status=0 WHERE id=?",UUID.fromString(customer.get("id").asText()));
        stockJdbc.update("UPDATE warehouses SET status=0 WHERE id=?",UUID.fromString(a));
        stockJdbc.update("UPDATE products SET status=0 WHERE id=?",UUID.fromString(p));
        var seen=new HashSet<String>();
        for(int page=1;page<=3;page++) {
            var response=findReturnableOrders("bh-",operator,page,1);
            assertEquals(3,response.get("total").asLong());assertEquals(1,response.get("items").size());
            assertEquals(page,response.get("page").asInt());assertEquals(1,response.get("pageSize").asInt());
            assertEquals(a,response.get("items").get(0).get("warehouseId").asText());
            assertTrue(seen.add(response.get("items").get(0).get("id").asText()));
            assertEquals(response,findReturnableOrders("bh-",operator,page,1));
        }
        var beyond=findReturnableOrders("bh-",operator,4,1);
        assertEquals(3,beyond.get("total").asLong());assertEquals(0,beyond.get("items").size());
        assertEquals("Customer",findReturnableOrders(named.get("code").asText(),operator,1,10).get("items").get(0).get("customerName").asText());

        assertEquals(3,findReturnableOrders("BH-",viewer,1,10).get("total").asLong());
        ok(get("/sales-orders"),viewer,403);
        ok(get("/warehouses/"+a+"/inventory"),viewer,403);
        ok(get("/sales-orders/returnable-orders").param("code","BH-"),employee,403);
        stockJdbc.update("DELETE FROM user_warehouses WHERE user_id=?",viewerId);
        assertEquals(0,findReturnableOrders("BH-",viewer,1,10).get("total").asLong());
    }

    @Test void returnableOrderWithOneExhaustedLineStillHasAnotherReturnableLine() throws Exception {
        returnsFixture();
        confirm(ok(body(post("/stock-receipts"),Map.of("warehouseId",a,"receiptDate","2026-10-16",
                "lines",List.of(Map.of("productId",p2,"quantity",1,"unitPrice",0)))),employee,201));
        var payload=salePayload(a,1,0);
        payload.put("lines",List.of(Map.of("productId",p,"quantity",1,"unitPrice",0),
                Map.of("productId",p2,"quantity",1,"unitPrice",0)));
        var order=saleConfirm(ok(body(post("/sales-orders"),payload),employee,201));
        confirmReturn(draftReturn(order,1));
        assertEquals(1,findReturnableOrders(order.get("code").asText(),operator,1,10).get("total").asLong());
        var rp=returnPayload(order,1);rp.put("lines",List.of(Map.of("productId",p2,"quantity",1)));
        confirmReturn(ok(body(post("/sales-returns"),rp),operator,201));
        assertEquals(0,findReturnableOrders(order.get("code").asText(),operator,1,10).get("total").asLong());
        consistent();
    }
}
