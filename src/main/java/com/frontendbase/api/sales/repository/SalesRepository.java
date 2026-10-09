package com.frontendbase.api.sales.repository;

import java.sql.*;
import java.time.*;
import java.util.*;
import com.frontendbase.api.sales.dto.SalesDtos.*;
import com.frontendbase.api.stock.dto.StockDtos.LineResponse;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SalesRepository {
    private final JdbcTemplate jdbc;

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    private UUID uuid(ResultSet r, String key) throws SQLException {
        return r.getObject(key, UUID.class);
    }

    private Instant instant(ResultSet r, String key) throws SQLException {
        var t = r.getTimestamp(key);
        return t == null ? null : t.toInstant();
    }

    public SalesResponse order(UUID id, boolean lock) {
        if (lock)
            jdbc.query("SELECT id FROM sales_orders WHERE id=? FOR UPDATE", (r, n) -> r.getObject(1, UUID.class), id);
        var rows = jdbc.query(
                "SELECT s.*,t.paid_amount,t.remaining_amount FROM sales_orders s JOIN sales_payment_totals t ON t.id=s.id WHERE s.id=?",
                (r, n) -> new SalesResponse(
                        uuid(r, "id"), r.getString("code"), uuid(r, "warehouse_id"), uuid(r, "customer_id"),
                        r.getObject("sale_date", LocalDate.class), r.getString("note"),
                        SalesStatus.valueOf(r.getString("status")), r.getLong("subtotal"), r.getLong("discount_amount"),
                        r.getLong("total_amount"), r.getBigDecimal("paid_amount").longValueExact(),
                        r.getBigDecimal("remaining_amount").longValueExact(),
                        !"CONFIRMED".equals(r.getString("status")) ? null
                                : r.getBigDecimal("remaining_amount").signum() == 0 ? PaymentStatus.PAID
                                        : r.getBigDecimal("paid_amount").signum() == 0 ? PaymentStatus.UNPAID
                                                : PaymentStatus.PARTIALLY_PAID,
                        r.getLong("version"),
                        uuid(r, "created_by"), instant(r, "created_at"), instant(r, "updated_at"),
                        uuid(r, "confirmed_by"), instant(r, "confirmed_at"),
                        uuid(r, "cancelled_by"), instant(r, "cancelled_at"), r.getString("cancellation_reason"),
                        r.getObject("goods_returned", Boolean.class),
                        r.getString("customer_name") == null ? null
                                : new CustomerSnapshot(r.getString("customer_code"), r.getString("customer_name"),
                                        r.getString("customer_phone"), r.getString("customer_address")),
                        List.of()),
                id);
        if (rows.isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND, "SALES_ORDER_NOT_FOUND", "Sales order not found");
        var h = rows.getFirst();
        return new SalesResponse(h.id(), h.code(), h.warehouseId(), h.customerId(), h.saleDate(), h.note(), h.status(),
                h.subtotal(), h.discountAmount(), h.totalAmount(), h.paidAmount(), h.remainingAmount(),
                h.paymentStatus(), h.version(),
                h.createdBy(), h.createdAt(), h.updatedAt(), h.confirmedBy(), h.confirmedAt(), h.cancelledBy(),
                h.cancelledAt(), h.cancellationReason(), h.goodsReturned(), h.customerSnapshot(), lines(id));
    }

    public List<LineResponse> lines(UUID id) {
        return jdbc.query("SELECT * FROM sales_order_lines WHERE sales_order_id=? ORDER BY product_id",
                (r, n) -> new LineResponse(
                        uuid(r, "product_id"), r.getLong("quantity"), r.getLong("unit_price"), r.getLong("line_total"),
                        r.getString("product_code"), r.getString("product_name"), r.getString("unit")),
                id);
    }
}
