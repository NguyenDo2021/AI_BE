package com.frontendbase.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.frontendbase.api.user.entity.UserAccount;
import com.frontendbase.api.user.repository.UserRepository;
import com.frontendbase.api.role.entity.Role;
import com.frontendbase.api.role.repository.RoleRepository;
import com.frontendbase.api.security.service.JwtService;
import com.frontendbase.api.report.service.ReportPeriod;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@Import(H2MigrationConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportApiIntegrationTest {
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UserRepository users;
    @Autowired
    RoleRepository roles;
    @Autowired
    JwtService jwt;
    UUID actor, employee, a, b, p, p2, group;
    String admin, staff;
    static final String DAY1 = "2026-10-10T17:00:00Z"; // Vietnam 11 October 00:00
    static final String DAY2 = "2026-10-11T17:00:00Z";
    static final String PERIOD = "from=2026-10-11&to=2026-10-11";

    Timestamp time(String value) {
        return Timestamp.from(Instant.parse(value));
    }

    UUID id() {
        return UUID.randomUUID();
    }

    @BeforeEach
    void setup() {
        StockTestCleanup.clear(jdbc);
        jdbc.update("DELETE FROM user_warehouses");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM product_groups");
        jdbc.update("DELETE FROM warehouses");
        jdbc.update("DELETE FROM refresh_tokens");
        users.deleteAll();
        roles.deleteAll();
        Role role = new Role();
        role.setId(id());
        role.setName("Report admin");
        role.setCode("ADMIN");
        roles.saveAndFlush(role);
        actor = account("report-admin", role);
        employee = account("report-staff", null);
        admin = jwt.createAccessToken(actor.toString(), "report-admin", List.of());
        staff = token("REPORT_INVENTORY_VIEW", "REPORT_SALES_VIEW", "REPORT_PAYMENT_VIEW",
                "REPORT_RECEIVABLE_VIEW", "REPORT_LOW_STOCK_VIEW", "REPORT_DASHBOARD_VIEW");
        a = warehouse("A", 1);
        b = warehouse("B", 1);
        group = id();
        jdbc.update("INSERT INTO product_groups(id,code,name,status) VALUES (?,?,?,1)", group, "G", "Group");
        p = product("P", 5, 1);
        p2 = product("P2", 0, 1);
        jdbc.update("INSERT INTO user_warehouses(user_id,warehouse_id) VALUES (?,?)", employee, a);
    }

    UUID account(String name, Role role) {
        UserAccount user = new UserAccount();
        user.setId(id());
        user.setUsername(name);
        user.setEmail(name + "@test.local");
        user.setFullName(name);
        if (role != null)
            user.getRoles().add(role);
        users.saveAndFlush(user);
        return user.getId();
    }

    String token(String... codes) {
        return jwt.createAccessToken(employee.toString(), "report-staff", List.of(codes));
    }

    UUID warehouse(String code, int status) {
        UUID id = id();
        jdbc.update("INSERT INTO warehouses(id,code,name,status) VALUES (?,?,?,?)", id, code, code, status);
        return id;
    }

    UUID product(String code, int threshold, int status) {
        UUID id = id();
        jdbc.update("INSERT INTO products(id,code,name,group_id,low_stock_threshold,status) VALUES (?,?,?,?,?,?)",
                id, code, code, group, threshold, status);
        return id;
    }

    JsonNode getReport(String path, String token, int expected) throws Exception {
        return json.readTree(mvc.perform(get("/reports/" + path).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    JsonNode report(String path) throws Exception {
        return getReport(path, admin, 200);
    }

    UUID receipt(UUID warehouse, UUID product, long quantity, String at) {
        UUID receipt = id();
        jdbc.update(
                "INSERT INTO stock_receipts(id,code,warehouse_id,receipt_date,status,total_amount,created_by,created_at)"
                        + " VALUES (?,?,?,DATE '1999-01-01','DRAFT',0,?,?)",
                receipt, receipt.toString(), warehouse, actor, time(at));
        jdbc.update(
                "INSERT INTO stock_receipt_lines(receipt_id,product_id,quantity,unit_price,line_total,product_code,product_name,unit)"
                        + " VALUES (?,?,?,0,0,'P','P','cay')",
                receipt, product, quantity);
        jdbc.update("UPDATE stock_receipts SET status='CONFIRMED',confirmed_by=?,confirmed_at=?,version=1 WHERE id=?",
                actor, time(at), receipt);
        movement(warehouse, product, quantity, "RECEIPT_CONFIRM", receipt, null, null, at);
        return receipt;
    }

    UUID sale(UUID warehouse, UUID product, long quantity, long price, UUID customer, String at, boolean confirmed) {
        UUID sale = id();
        long amount = Math.multiplyExact(quantity, price);
        jdbc.update("INSERT INTO sales_orders(id,code,warehouse_id,customer_id,sale_date,status,subtotal,total_amount,"
                + "created_by,created_at,updated_at) VALUES (?,?,?,?,DATE '1999-01-01','DRAFT',?,?,?,?,?)",
                sale, sale.toString(), warehouse, customer, amount, amount, actor, time(at), time(at));
        jdbc.update(
                "INSERT INTO sales_order_lines(sales_order_id,product_id,quantity,unit_price,line_total,product_code,product_name,unit)"
                        + " VALUES (?,?,?,?,?,'P','P','cay')",
                sale, product, quantity, price, amount);
        if (confirmed) {
            jdbc.update(
                    "UPDATE sales_orders SET status='CONFIRMED',confirmed_by=?,confirmed_at=?,customer_name='Snapshot',"
                            + "version=1 WHERE id=?",
                    actor, time(at), sale);
            movement(warehouse, product, -quantity, "SALE_CONFIRM", null, sale, null, at);
        }
        return sale;
    }

    void movement(UUID warehouse, UUID product, long quantity, String type, UUID receipt, UUID sale, UUID take,
            String at) {
        jdbc.update("INSERT INTO inventory_movements(id,warehouse_id,product_id,quantity_change,type,receipt_id,"
                + "sales_order_id,stocktake_id,performed_by,performed_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id(), warehouse, product, quantity, type, receipt, sale, take, actor, time(at));
        int updated = jdbc.update("UPDATE inventory_balances SET quantity=quantity+?,revision=revision+1"
                + " WHERE warehouse_id=? AND product_id=?", quantity, warehouse, product);
        if (updated == 0)
            jdbc.update("INSERT INTO inventory_balances(warehouse_id,product_id,quantity,revision) VALUES (?,?,?,1)",
                    warehouse, product, quantity);
    }

    void cancelSale(UUID sale, UUID warehouse, UUID product, long quantity, String at) {
        jdbc.update(
                "UPDATE sales_orders SET status='CANCELLED',cancelled_by=?,cancelled_at=?,cancellation_reason='Return',"
                        + "goods_returned=TRUE,version=version+1 WHERE id=?",
                actor, time(at), sale);
        movement(warehouse, product, quantity, "SALE_CANCEL", null, sale, null, at);
    }

    void adjustment(long previous, long counted, String at) {
        UUID take = id();
        jdbc.update("INSERT INTO stocktakes(id,code,warehouse_id,status,created_by,created_at,updated_by,updated_at)"
                + " VALUES (?,?,?,'DRAFT',?,?,?,?)", take, take.toString(), a, actor, time(at), actor, time(at));
        jdbc.update("INSERT INTO stocktake_lines(stocktake_id,product_id,system_quantity,inventory_revision,"
                + "counted_quantity,reason,product_code,product_name,unit) VALUES (?,?,?,0,?,'Count','P','P','cay')",
                take, p, previous, counted);
        jdbc.update("UPDATE stocktakes SET status='CONFIRMED',confirmed_by=?,confirmed_at=?,version=1 WHERE id=?",
                actor, time(at), take);
        movement(a, p, counted - previous, "STOCKTAKE_ADJUSTMENT", null, null, take, at);
    }

    UUID payment(UUID sale, UUID warehouse, UUID customer, long amount, String method, String at) {
        UUID payment = id();
        jdbc.update("INSERT INTO payments(id,code,sales_order_id,warehouse_id,customer_id,amount,payment_date,method,"
                + "status,created_by,created_at,idempotency_key) VALUES (?,?,?,?,?,?,DATE '1999-01-01',?,'ACTIVE',?,?,?)",
                payment, payment.toString(), sale, warehouse, customer, amount, method, actor, time(at), id());
        return payment;
    }

    void cancelPayment(UUID payment, String at) {
        jdbc.update(
                "UPDATE payments SET status='CANCELLED',cancelled_by=?,cancelled_at=?,cancellation_reason='Error' WHERE id=?",
                actor, time(at), payment);
    }

    @Test
    void inventoryLedgerPreservesPastAndQuietPeriods() throws Exception {
        receipt(a, p, 100, DAY1);
        UUID sold = sale(a, p, 10, 10, null, DAY1, true);
        adjustment(90, 87, DAY1);
        cancelSale(sold, a, p, 10, DAY2);
        var row = report("inventory?" + PERIOD).get("items").get(0);
        assertEquals(100, row.get("receiptQuantity").asLong());
        assertEquals(10, row.get("saleQuantity").asLong());
        assertEquals(3, row.get("adjustmentDecreaseQuantity").asLong());
        assertEquals(87, row.get("closingQuantity").asLong());
        assertEquals(0, row.get("openingQuantity").asLong());
        var next = report("inventory?from=2026-10-12&to=2026-10-12").get("items").get(0);
        assertEquals(87, next.get("openingQuantity").asLong());
        assertEquals(10, next.get("saleCancellationQuantity").asLong());
        assertEquals(97, next.get("closingQuantity").asLong());
        var quiet = report("inventory?from=2026-10-13&to=2026-10-13").get("items").get(0);
        assertEquals(97, quiet.get("openingQuantity").asLong());
        assertEquals(97, quiet.get("closingQuantity").asLong());
        jdbc.update("UPDATE products SET status=0 WHERE id=?", p);
        jdbc.update("UPDATE warehouses SET status=0 WHERE id=?", a);
        assertEquals(0, report("inventory?" + PERIOD).get("items").get(0).get("productStatus").asInt());
    }

    @Test
    void receiptCancellationAndPositiveAdjustmentAreSeparateAndZeroHistoryRemains() throws Exception {
        UUID r = receipt(a, p, 100, DAY1);
        jdbc.update(
                "UPDATE stock_receipts SET status='CANCELLED',cancelled_by=?,cancelled_at=?,cancellation_reason='Error',"
                        + "version=2 WHERE id=?",
                actor, time(DAY1), r);
        movement(a, p, -100, "RECEIPT_CANCEL", r, null, null, DAY1);
        var row = report("inventory?" + PERIOD).get("items").get(0);
        assertEquals(100, row.get("receiptCancellationQuantity").asLong());
        assertEquals(0, row.get("closingQuantity").asLong());
        adjustment(0, 3, DAY2);
        assertEquals(3, report("inventory?from=2026-10-12&to=2026-10-12").get("items").get(0)
                .get("adjustmentIncreaseQuantity").asLong());
    }

    @Test
    void salesEventsSurviveCancellationAndIgnoreDraftCancellationAndSaleDate() throws Exception {
        receipt(a, p, 100, DAY1);
        UUID s = sale(a, p, 10, 10, null, DAY1, true);
        cancelSale(s, a, p, 10, DAY2);
        UUID draft = sale(a, p, 1, 5, null, DAY1, false);
        jdbc.update(
                "UPDATE sales_orders SET status='CANCELLED',cancelled_by=?,cancelled_at=?,cancellation_reason='Draft',"
                        + "version=1 WHERE id=?",
                actor, time(DAY2), draft);
        var first = report("sales?" + PERIOD);
        assertEquals(100, first.get("summary").get("netSalesAmount").asLong());
        assertEquals(10, first.get("items").get(0).get("confirmedQuantity").asLong()); // flattened response
        var second = report("sales?from=2026-10-12&to=2026-10-12").get("summary");
        assertEquals(-100, second.get("netSalesAmount").asLong());
        assertEquals(1, second.get("cancelledConfirmedOrderCount").asLong());
    }

    @Test
    void paymentEventsPreserveRecordedAndReversedAmountsAcrossPeriods() throws Exception {
        receipt(a, p, 100, DAY1);
        UUID s = sale(a, p, 1, 100, null, DAY1, true);
        UUID cash = payment(s, a, null, 30, "CASH", DAY1), bank = payment(s, a, null, 20, "BANK_TRANSFER", DAY1);
        cancelPayment(cash, DAY2);
        cancelPayment(bank, DAY2);
        var first = report("payments?" + PERIOD).get("summary");
        assertEquals(50, first.get("netRecordedAmount").asLong());
        assertEquals(2, first.get("createdPaymentCount").asLong());
        var second = report("payments?from=2026-10-12&to=2026-10-12").get("summary");
        assertEquals(-30, second.get("netCashAmount").asLong());
        assertEquals(-20, second.get("netBankTransferAmount").asLong());
        assertEquals(-50, second.get("netRecordedAmount").asLong());
        assertEquals(2, second.get("cancelledPaymentCount").asLong());
    }

    @Test
    void boundariesUseVietnamMidnightAndExclusiveNextDay() throws Exception {
        receipt(a, p, 100, "2026-10-10T16:59:59Z");
        sale(a, p, 1, 10, null, "2026-10-10T16:59:59Z", true);
        sale(a, p, 1, 20, null, DAY1, true);
        sale(a, p, 1, 30, null, "2026-10-11T16:59:59Z", true);
        sale(a, p, 1, 40, null, DAY2, true);
        assertEquals(50, report("sales?" + PERIOD).get("summary").get("netSalesAmount").asLong());
        var row = report("inventory?" + PERIOD).get("items").get(0);
        assertEquals(99, row.get("openingQuantity").asLong());
        assertEquals(97, row.get("closingQuantity").asLong());
        assertEquals(Instant.parse(DAY2), ReportPeriod.parse("2026-10-11", "2026-10-11").end().toInstant());
    }

    @Test
    void summariesAndCountsUseFullScopeBeforePagination() throws Exception {
        receipt(a, p, 100, DAY1);
        receipt(b, p, 100, DAY1);
        UUID sa = sale(a, p, 1, 100, null, DAY1, true), sb = sale(b, p, 1, 200, null, DAY1, true);
        payment(sa, a, null, 40, "CASH", DAY1);
        payment(sb, b, null, 60, "CASH", DAY1);
        for (String route : List.of("sales", "payments")) {
            var small = report(route + "?" + PERIOD + "&pageSize=1");
            var large = report(route + "?" + PERIOD + "&pageSize=100");
            assertEquals(small.get("summary"), large.get("summary"));
            assertEquals(2, small.get("total").asLong());
            assertEquals(1, small.get("items").size());
            assertEquals(2, large.get("items").size());
            assertEquals(small.get("summary"), report(route + "?" + PERIOD + "&page=3&pageSize=1").get("summary"));
            var employeeResult = getReport(route + "?" + PERIOD, staff, 200);
            assertEquals(1, employeeResult.get("total").asLong());
            assertEquals(a.toString(), employeeResult.get("items").get(0).get("warehouseId").asText());
        }
    }

    @Test
    void currentReceivablesIncludeInactiveCustomerAndWalkInWithoutSyntheticCustomer() throws Exception {
        UUID customer = id();
        jdbc.update("INSERT INTO customers(id,warehouse_id,code,name,status,created_at,updated_at)"
                + " VALUES (?,?,?,'Inactive',0,?,?)", customer, a, "C", time(DAY1), time(DAY1));
        receipt(a, p, 100, DAY1);
        UUID s = sale(a, p, 1, 100, customer, DAY1, true);
        payment(s, a, customer, 40, "CASH", DAY1);
        sale(a, p, 1, 30, null, DAY1, true);
        UUID cancelled = sale(a, p, 1, 50, customer, DAY1, true);
        cancelSale(cancelled, a, p, 1, DAY2);
        var current = report("receivables-summary");
        assertEquals(60, current.get("customerRemainingAmount").asLong());
        assertEquals(1, current.get("customerCount").asLong());
        assertEquals(30, current.get("walkInRemainingAmount").asLong());
        assertEquals(1, current.get("walkInOutstandingOrderCount").asLong());
        assertEquals(90, current.get("totalRemainingAmount").asLong());
        var past = report("dashboard?from=1999-01-01&to=1999-01-01");
        assertEquals(90, past.get("currentSections").get("receivables").get("totalRemainingAmount").asLong());
        assertEquals(0, past.get("periodSections").get("sales").get("netSalesAmount").asLong());
    }

    @Test
    void lowStockIncludesAbsentBalancesAndExcludesDisabledOrInactivePairs() throws Exception {
        assertEquals(2, report("low-stock").get("total").asLong());
        var own = getReport("low-stock", staff, 200);
        assertEquals(1, own.get("total").asLong());
        assertEquals(0, own.get("items").get(0).get("quantity").asLong());
        assertEquals(5, own.get("items").get(0).get("shortageQuantity").asLong());
        assertEquals(0, report("low-stock?productId=" + p2).get("total").asLong());
        receipt(a, p, 5, DAY1);
        assertEquals(1, report("low-stock").get("total").asLong());
        jdbc.update("UPDATE warehouses SET status=0 WHERE id=?", b);
        assertEquals(0, report("low-stock").get("total").asLong());
        jdbc.update("UPDATE products SET status=0 WHERE id=?", p);
        assertEquals(0, report("low-stock").get("total").asLong());
    }

    @Test
    void permissionsScopeRefreshAndDashboardOmission() throws Exception {
        for (String route : List.of("inventory?" + PERIOD, "sales?" + PERIOD, "payments?" + PERIOD,
                "receivables-summary", "low-stock", "dashboard?" + PERIOD)) {
            assertEquals("WAREHOUSE_ACCESS_DENIED",
                    getReport(route + (route.contains("?") ? "&" : "?") + "warehouseId=" + b, staff, 403).get("code")
                            .asText());
            getReport(route, token(), 403);
        }
        var partial = getReport("dashboard?" + PERIOD, token("REPORT_DASHBOARD_VIEW", "REPORT_SALES_VIEW"), 200);
        assertTrue(partial.get("periodSections").has("sales"));
        assertFalse(partial.get("periodSections").has("payments"));
        assertFalse(partial.has("currentSections"));
        var onlyDashboard = getReport("dashboard?" + PERIOD, token("REPORT_DASHBOARD_VIEW"), 200);
        assertFalse(onlyDashboard.has("periodSections"));
        assertFalse(onlyDashboard.has("currentSections"));
        jdbc.update("DELETE FROM user_warehouses WHERE user_id=?", employee);
        assertEquals(0, getReport("low-stock", staff, 200).get("total").asLong());
        getReport("low-stock?warehouseId=" + a, staff, 403);
        assertEquals(2, report("low-stock").get("total").asLong());
        // JWT authorities remain the canonical permission source even without DB role
        // grants.
        assertEquals(0, getReport("sales?" + PERIOD, staff, 200).get("total").asLong());
    }

    @Test
    void invalidInputHasStructuredErrors() throws Exception {
        for (String q : List.of("", "?from=2026-10-11", "?from=2026-10-12&to=2026-10-11",
                "?from=2026-02-30&to=2026-03-01", "?from=2026-1-01&to=2026-10-11"))
            assertEquals("INVALID_DATE_RANGE", getReport("sales" + q, admin, 400).get("code").asText());
        getReport("sales?" + PERIOD + "&page=0", admin, 400);
        getReport("low-stock?pageSize=101", admin, 400);
        getReport("receivables-summary?from=2026-10-11", admin, 400);
        getReport("low-stock?to=2026-10-11", admin, 400);
        getReport("low-stock?warehouseId=invalid", admin, 400);
        getReport("low-stock?warehouseId=" + id(), admin, 404);
        mvc.perform(get("/reports/low-stock")).andExpect(status().isUnauthorized());
    }

    @Test
    void inconsistentImportedBalancesAreRejectedWithoutChangingHistory() throws Exception {
        jdbc.update("INSERT INTO inventory_balances(warehouse_id,product_id,quantity) VALUES (?,?,7)", a, p);
        assertEquals("INVENTORY_HISTORY_INCONSISTENT",
                getReport("inventory?" + PERIOD, admin, 409).get("code").asText());
        assertEquals(7, jdbc.queryForObject(
                "SELECT quantity FROM inventory_balances WHERE warehouse_id=? AND product_id=?", Long.class, a, p));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM inventory_movements", Long.class));
    }

    @Test
    void aggregateOverflowIsReportedInsteadOfWrapping() throws Exception {
        receipt(a, p, 10, DAY1);
        receipt(b, p, 10, DAY1);
        UUID sa = sale(a, p, 1, Long.MAX_VALUE, null, DAY1, true), sb = sale(b, p, 1, Long.MAX_VALUE, null, DAY1, true);
        assertEquals("NUMERIC_OVERFLOW", getReport("sales?" + PERIOD, admin, 409).get("code").asText());
        assertEquals("NUMERIC_OVERFLOW", getReport("receivables-summary", admin, 409).get("code").asText());
        payment(sa, a, null, Long.MAX_VALUE, "CASH", DAY1);
        payment(sb, b, null, Long.MAX_VALUE, "CASH", DAY1);
        assertEquals("NUMERIC_OVERFLOW", getReport("payments?" + PERIOD, admin, 409).get("code").asText());
        assertEquals(Long.MAX_VALUE,
                getReport("sales?" + PERIOD, staff, 200).get("summary").get("netSalesAmount").asLong());
    }

    @Test
    void inventoryComponentOverflowIsReportedEvenWhenClosingStockFits() throws Exception {
        UUID r = receipt(a, p, Long.MAX_VALUE, DAY1);
        jdbc.update(
                "UPDATE stock_receipts SET status='CANCELLED',cancelled_by=?,cancelled_at=?,cancellation_reason='Error',version=2 WHERE id=?",
                actor, time(DAY1), r);
        movement(a, p, -Long.MAX_VALUE, "RECEIPT_CANCEL", r, null, null, DAY1);
        receipt(a, p, 1, DAY1);
        assertEquals("NUMERIC_OVERFLOW", getReport("inventory?" + PERIOD, admin, 409).get("code").asText());
    }

    @Test
    void multipleLinesDoNotMultiplyOrderCountsOrAmountsAndDiscountIsSeparate() throws Exception {
        receipt(a, p, 100, DAY1);
        receipt(a, p2, 10, DAY1);
        UUID s = sale(a, p, 10, 10, null, DAY1, false);
        jdbc.update(
                "INSERT INTO sales_order_lines(sales_order_id,product_id,quantity,unit_price,line_total,product_code,product_name,unit)"
                        + " VALUES (?,?,2,50,100,'P2','P2','cay')",
                s, p2);
        jdbc.update("UPDATE sales_orders SET status='CONFIRMED',confirmed_by=?,confirmed_at=?,customer_name='Snapshot',"
                + "subtotal=200,discount_amount=20,total_amount=180,version=1 WHERE id=?", actor, time(DAY1), s);
        movement(a, p, -10, "SALE_CONFIRM", null, s, null, DAY1);
        movement(a, p2, -2, "SALE_CONFIRM", null, s, null, DAY1);
        var summary = report("sales?" + PERIOD).get("summary");
        assertEquals(1, summary.get("confirmedOrderCount").asLong());
        assertEquals(12, summary.get("confirmedQuantity").asLong());
        assertEquals(200, summary.get("grossSalesAmount").asLong());
        assertEquals(20, summary.get("discountAmount").asLong());
        assertEquals(180, summary.get("confirmedSalesAmount").asLong());
        cancelSale(s, a, p, 10, DAY2);
        movement(a, p2, 2, "SALE_CANCEL", null, s, null, DAY2);
        var cancelled = report("sales?from=2026-10-12&to=2026-10-12").get("summary");
        assertEquals(1, cancelled.get("cancelledConfirmedOrderCount").asLong());
        assertEquals(12, cancelled.get("cancelledQuantity").asLong());
        assertEquals(-180, cancelled.get("netSalesAmount").asLong());
    }

    @Test
    void dashboardUsesFullTotalsAndScopedLowStockCountAndReturnsEmptyMetrics() throws Exception {
        var empty = report("sales?" + PERIOD);
        assertEquals(0, empty.get("total").asLong());
        assertEquals(0, empty.get("summary").get("netSalesAmount").asLong());
        assertEquals(0, report("payments?" + PERIOD).get("summary").get("netRecordedAmount").asLong());
        assertEquals(0, report("receivables-summary").get("totalRemainingAmount").asLong());
        assertEquals(2,
                report("dashboard?" + PERIOD).get("currentSections").get("lowStock").get("lowStockPairCount").asLong());
        assertEquals(1, getReport("dashboard?" + PERIOD, staff, 200).get("currentSections").get("lowStock")
                .get("lowStockPairCount").asLong());
        receipt(a, p, 100, DAY1);
        receipt(b, p, 100, DAY1);
        UUID sa = sale(a, p, 1, 100, null, DAY1, true), sb = sale(b, p, 1, 200, null, DAY1, true);
        payment(sa, a, null, 30, "CASH", DAY1);
        payment(sb, b, null, 40, "CASH", DAY1);
        var dashboard = report("dashboard?" + PERIOD);
        assertEquals(300, dashboard.get("periodSections").get("sales").get("netSalesAmount").asLong());
        assertEquals(70, dashboard.get("periodSections").get("payments").get("netRecordedAmount").asLong());
        assertEquals(230, dashboard.get("currentSections").get("receivables").get("totalRemainingAmount").asLong());
        assertEquals(0, dashboard.get("currentSections").get("lowStock").get("lowStockPairCount").asLong());
        var currentOnly = getReport("dashboard?" + PERIOD, token("REPORT_DASHBOARD_VIEW", "REPORT_RECEIVABLE_VIEW"),
                200);
        assertFalse(currentOnly.has("periodSections"));
        assertFalse(currentOnly.get("currentSections").has("lowStock"));
        assertEquals(70, currentOnly.get("currentSections").get("receivables").get("totalRemainingAmount").asLong());
    }

    @Test
    void inventoryHistoryGuardAndPaginationRespectWarehouseAndProductScope() throws Exception {
        receipt(a, p, 100, DAY1);
        receipt(a, p2, 10, DAY1);
        jdbc.update("INSERT INTO inventory_balances(warehouse_id,product_id,quantity) VALUES (?,?,7)", b, p);
        var scoped = getReport("inventory?" + PERIOD + "&pageSize=1", staff, 200);
        assertEquals(2, scoped.get("total").asLong());
        assertEquals(1, scoped.get("items").size());
        var filtered = getReport("inventory?" + PERIOD + "&productId=" + p, staff, 200);
        assertEquals(1, filtered.get("total").asLong());
        assertEquals(p.toString(), filtered.get("items").get(0).get("productId").asText());
        assertEquals("INVENTORY_HISTORY_INCONSISTENT",
                getReport("inventory?" + PERIOD, admin, 409).get("code").asText());
        assertEquals(1, report("inventory?" + PERIOD + "&productId=" + p2).get("total").asLong());
        assertEquals(0, getReport("inventory?" + PERIOD + "&productId=" + id(), staff, 200).get("total").asLong());
        assertEquals(0, getReport("inventory?from=1999-01-01&to=1999-01-01", staff, 200).get("total").asLong());
    }

    @Test
    void salesQuantityOverflowIsCheckedEvenWhenAmountsAreZero() throws Exception {
        receipt(a, p, Long.MAX_VALUE, DAY1);
        receipt(b, p, Long.MAX_VALUE, DAY1);
        sale(a, p, Long.MAX_VALUE, 0, null, DAY1, true);
        sale(b, p, Long.MAX_VALUE, 0, null, DAY1, true);
        assertEquals("NUMERIC_OVERFLOW", getReport("sales?" + PERIOD, admin, 409).get("code").asText());
        assertEquals(Long.MAX_VALUE,
                getReport("sales?" + PERIOD, staff, 200).get("summary").get("confirmedQuantity").asLong());
    }

    @Test
    void reportsDoNotMutateBusinessData() throws Exception {
        receipt(a, p, 100, DAY1);
        UUID s = sale(a, p, 1, 100, null, DAY1, true);
        payment(s, a, null, 20, "CASH", DAY1);
        var tables = List.of("inventory_balances", "inventory_movements", "stock_receipts", "stock_receipt_lines",
                "sales_orders", "sales_order_lines", "payments", "stocktakes", "stocktake_lines", "customers");
        var before = new HashMap<String, List<Map<String, Object>>>();
        for (String table : tables)
            before.put(table, jdbc.queryForList("SELECT * FROM " + table));
        for (String path : List.of("inventory?" + PERIOD, "sales?" + PERIOD, "payments?" + PERIOD,
                "receivables-summary", "low-stock", "dashboard?" + PERIOD))
            report(path);
        for (String table : tables)
            assertEquals(before.get(table), jdbc.queryForList("SELECT * FROM " + table));
    }
}
