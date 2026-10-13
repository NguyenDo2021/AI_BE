package com.frontendbase.api.stocktake.repository;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import com.frontendbase.api.stocktake.dto.StocktakeDtos.*;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository @RequiredArgsConstructor
public class StocktakeRepository {
    private final JdbcTemplate jdbc;
    public JdbcTemplate jdbc() { return jdbc; }
    private UUID uuid(ResultSet r, String key) throws SQLException { return r.getObject(key, UUID.class); }
    private Instant instant(ResultSet r, String key) throws SQLException {
        var t = r.getTimestamp(key); return t == null ? null : t.toInstant();
    }
    public Response get(UUID id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM stocktakes WHERE id=?" + (lock ? " FOR UPDATE" : ""), (r,n) ->
            new Response(uuid(r,"id"), r.getString("code"), uuid(r,"warehouse_id"), r.getString("note"),
                Status.valueOf(r.getString("status")), r.getLong("version"),
                uuid(r,"created_by"), instant(r,"created_at"), uuid(r,"updated_by"), instant(r,"updated_at"),
                uuid(r,"confirmed_by"), instant(r,"confirmed_at"), uuid(r,"cancelled_by"), instant(r,"cancelled_at"),
                r.getString("cancellation_reason"), List.of()), id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"STOCKTAKE_NOT_FOUND","Stocktake not found");
        var h = rows.getFirst();
        return new Response(h.id(),h.code(),h.warehouseId(),h.note(),h.status(),h.version(),
            h.createdBy(),h.createdAt(),h.updatedBy(),h.updatedAt(),h.confirmedBy(),h.confirmedAt(),
            h.cancelledBy(),h.cancelledAt(),h.cancellationReason(),lines(id));
    }
    public List<LineResponse> lines(UUID id) {
        return jdbc.query("SELECT * FROM stocktake_lines WHERE stocktake_id=? ORDER BY product_id", (r,n) -> {
            Long counted = r.getObject("counted_quantity",Long.class);
            long system = r.getLong("system_quantity");
            return new LineResponse(uuid(r,"product_id"),system,r.getLong("inventory_revision"),
                counted,counted == null ? null : Math.subtractExact(counted,system),r.getString("reason"),
                r.getString("product_code"),r.getString("product_name"),r.getString("unit"));
        },id);
    }
    public void saveLines(UUID id, List<LineResponse> lines) {
        jdbc.update("DELETE FROM stocktake_lines WHERE stocktake_id=?",id);
        for (var l : lines) jdbc.update(
            "INSERT INTO stocktake_lines(stocktake_id,product_id,system_quantity,inventory_revision,counted_quantity,reason,product_code,product_name,unit) VALUES (?,?,?,?,?,?,?,?,?)",
            id,l.productId(),l.systemQuantity(),l.inventoryRevision(),l.countedQuantity(),l.reason(),l.productCode(),l.productName(),l.unit());
    }
}
