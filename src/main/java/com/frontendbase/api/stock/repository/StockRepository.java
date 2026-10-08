package com.frontendbase.api.stock.repository;
import java.sql.*;
import java.time.*;
import java.util.*;
import com.frontendbase.api.stock.dto.StockDtos.*;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
/** JDBC participates in the existing JPA transaction manager on the same datasource. */
@Repository @RequiredArgsConstructor
public class StockRepository {
    private final JdbcTemplate jdbc;
    public JdbcTemplate jdbc() { return jdbc; }
    private UUID uuid(ResultSet r, String key) throws SQLException { return r.getObject(key, UUID.class); }
    private Instant instant(ResultSet r, String key) throws SQLException {
        var t = r.getTimestamp(key); return t == null ? null : t.toInstant();
    }
    public ReceiptResponse receipt(UUID id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM stock_receipts WHERE id=?" + (lock ? " FOR UPDATE" : ""), (r,n) ->
            new ReceiptResponse(uuid(r,"id"),r.getString("code"),uuid(r,"warehouse_id"),r.getObject("receipt_date",LocalDate.class),
                r.getString("supplier_name"),r.getString("note"),ReceiptStatus.valueOf(r.getString("status")),r.getLong("total_amount"),r.getLong("version"),
                uuid(r,"created_by"),instant(r,"created_at"),uuid(r,"confirmed_by"),instant(r,"confirmed_at"),
                uuid(r,"cancelled_by"),instant(r,"cancelled_at"),r.getString("cancellation_reason"),List.of()), id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"STOCK_RECEIPT_NOT_FOUND","Stock receipt not found");
        var h = rows.getFirst();
        return new ReceiptResponse(h.id(),h.code(),h.warehouseId(),h.receiptDate(),h.supplierName(),h.note(),h.status(),h.totalAmount(),h.version(),
            h.createdBy(),h.createdAt(),h.confirmedBy(),h.confirmedAt(),h.cancelledBy(),h.cancelledAt(),h.cancellationReason(),lines(id));
    }
    public List<LineResponse> lines(UUID id) {
        return jdbc.query("SELECT * FROM stock_receipt_lines WHERE receipt_id=? ORDER BY product_id", (r,n) ->
            new LineResponse(uuid(r,"product_id"),r.getLong("quantity"),r.getLong("unit_price"),r.getLong("line_total"),
                r.getString("product_code"),r.getString("product_name"),r.getString("unit")),id);
    }
    public InventoryResponse inventory(ResultSet r, int n) throws SQLException {
        return new InventoryResponse(uuid(r,"warehouse_id"),uuid(r,"product_id"),r.getString("code"),r.getString("name"),r.getString("unit"),r.getShort("status"),r.getLong("quantity"));
    }
    public MovementResponse movement(ResultSet r, int n) throws SQLException {
        return new MovementResponse(uuid(r,"id"),uuid(r,"warehouse_id"),uuid(r,"product_id"),r.getLong("quantity_change"),r.getString("type"),uuid(r,"receipt_id"),
            r.getString("receipt_code"),r.getString("product_code"),r.getString("product_name"),r.getString("unit"),uuid(r,"performed_by"),instant(r,"performed_at"));
    }
}
