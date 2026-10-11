package com.frontendbase.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

@org.springframework.context.annotation.Import(H2MigrationConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentPrintApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID actor, warehouse, sale, payment, role;
    String code;

    @BeforeEach void setup() {
        actor = UUID.randomUUID(); warehouse = UUID.randomUUID(); sale = UUID.randomUUID();
        payment = UUID.randomUUID(); role = UUID.randomUUID(); code = "PT-" + payment.toString().replace("-", "");
        jdbc.update("""
            INSERT INTO users(id,username,username_normalized,email,email_normalized,full_name,status,created_at)
            VALUES (?,?,?,?,?,'Người tạo phiếu',1,CURRENT_TIMESTAMP)
            """, actor, actor.toString(), actor.toString(), actor + "@test.local", actor + "@test.local");
        jdbc.update("INSERT INTO roles(id,name,code,status,created_at) VALUES (?,?,?,1,CURRENT_TIMESTAMP)",
                role, role.toString(), role.toString());
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", actor, role);
        grant();
        jdbc.update("INSERT INTO warehouses(id,code,name,address,status,created_at) VALUES (?,?,?, ?,1,CURRENT_TIMESTAMP)",
                warehouse, warehouse.toString().toUpperCase(Locale.ROOT), "Kho hiện tại", "Địa chỉ kho hiện tại");
        jdbc.update("INSERT INTO user_warehouses(user_id,warehouse_id) VALUES (?,?)", actor, warehouse);
        jdbc.update("""
            INSERT INTO sales_orders(id,code,warehouse_id,sale_date,status,subtotal,total_amount,
                customer_name,customer_address,created_by,created_at,updated_at,confirmed_by,confirmed_at)
            VALUES (?,?,?,DATE '2026-01-01','CONFIRMED',9000000,9000000,
                'Khách trong snapshot','Địa chỉ snapshot',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)
            """, sale, "DH-" + sale.toString().substring(0, 8), warehouse, actor, actor);
        jdbc.update("""
            INSERT INTO payments(id,code,sales_order_id,warehouse_id,amount,payment_date,method,status,created_by,created_at,idempotency_key)
            VALUES (?,?,?,?,1250000,DATE '2026-02-03','CASH','ACTIVE',?,CURRENT_TIMESTAMP,?)
            """, payment, code, sale, warehouse, actor, UUID.randomUUID());
    }
    void grant() {
        if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code='PAYMENT_VIEW'", Long.class) == 0)
            jdbc.update("INSERT INTO permissions(id,name,code,description,status) VALUES (?,'PAYMENT_VIEW','PAYMENT_VIEW','View payment',1)",
                    UUID.randomUUID());
        jdbc.update("""
            INSERT INTO role_permissions(role_id,permission_id)
            SELECT ?,id FROM permissions WHERE code='PAYMENT_VIEW'
            """, role);
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor auth() {
        return authentication(new UsernamePasswordAuthenticationToken(actor.toString(), null, List.of()));
    }
    String text(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) { return new PDFTextStripper().getText(document); }
    }

    @Test void printsWithoutSalesPermissionAndDoesNotMutate() throws Exception {
        var before = jdbc.queryForList("SELECT * FROM payments WHERE id=?", payment);
        var salesBefore = jdbc.queryForList("SELECT * FROM sales_payment_totals WHERE id=?", sale);
        var movements = jdbc.queryForList("SELECT * FROM inventory_movements");
        for (int i = 0; i < 2; i++) {
            var response = mvc.perform(get("/payments/" + payment + "/print").with(auth())
                    .header("Origin", "http://localhost:5173").accept("application/pdf"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"bien-lai-" + code + ".pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Location, Content-Disposition"))
                .andReturn().getResponse();
            String text = text(response.getContentAsByteArray());
            assertTrue(text.contains("Khách trong snapshot"));
            assertTrue(text.contains("Địa chỉ snapshot"));
            assertTrue(text.contains("Ngày 03 tháng 02 năm 2026"));
            assertTrue(text.contains("1.250.000 đồng"));
            assertTrue(text.contains("Người tạo phiếu"));
            assertFalse(text.contains("9.000.000"));
        }
        assertEquals(before, jdbc.queryForList("SELECT * FROM payments WHERE id=?", payment));
        assertEquals(salesBefore, jdbc.queryForList("SELECT * FROM sales_payment_totals WHERE id=?", sale));
        assertEquals(movements, jdbc.queryForList("SELECT * FROM inventory_movements"));
    }

    @Test void permissionsScopeAndJsonErrors() throws Exception {
        mvc.perform(get("/payments/" + payment + "/print")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        jdbc.update("DELETE FROM role_permissions WHERE role_id=?", role);
        mvc.perform(get("/payments/" + payment + "/print").with(auth()).accept("application/pdf"))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        grant();
        jdbc.update("DELETE FROM user_warehouses WHERE user_id=?", actor);
        mvc.perform(get("/payments/" + payment + "/print").with(auth()).accept("application/pdf"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WAREHOUSE_ACCESS_DENIED"));
        mvc.perform(get("/payments/" + UUID.randomUUID() + "/print").with(auth()).accept("application/pdf"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        mvc.perform(get("/payments/not-uuid/print").with(auth()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void adminBypassAndCancelledRetailReceipt() throws Exception {
        UUID adminRole = jdbc.query("SELECT id FROM roles WHERE code='ADMIN'", (r,n) -> r.getObject(1,UUID.class))
                .stream().findFirst().orElse(null);
        if (adminRole == null) {
            jdbc.update("UPDATE roles SET code='ADMIN' WHERE id=?", role);
        } else {
            jdbc.update("DELETE FROM user_roles WHERE user_id=?", actor);
            jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", actor, adminRole);
            jdbc.update("DELETE FROM role_permissions WHERE role_id=?", adminRole);
        }
        jdbc.update("DELETE FROM role_permissions WHERE role_id=?", role);
        jdbc.update("DELETE FROM user_warehouses WHERE user_id=?", actor);
        jdbc.update("UPDATE sales_orders SET customer_name='Khách lẻ',customer_address=NULL WHERE id=?", sale);
        jdbc.update("""
            UPDATE payments SET status='CANCELLED',cancelled_by=?,cancelled_at=CURRENT_TIMESTAMP,
                cancellation_reason='Nhập nhầm lần thu' WHERE id=?
            """, actor, payment);
        String text = text(mvc.perform(get("/payments/" + payment + "/print").with(auth()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertTrue(text.contains("ĐÃ HỦY"));
        assertTrue(text.contains("Nhập nhầm lần thu"));
        assertTrue(text.contains("Khách lẻ"));
    }
}
