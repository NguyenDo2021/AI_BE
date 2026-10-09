package com.frontendbase.api.stock.service;

import java.util.*;
import java.time.Instant;
import java.sql.Timestamp;
import com.frontendbase.api.stock.dto.StockDtos.*;
import com.frontendbase.api.stock.repository.StockRepository;
import com.frontendbase.api.warehouse.service.WarehouseAccess;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/**
 * Shared receipt/sale locking and exact arithmetic. Caller must hold a
 * transaction.
 */
@Component
@RequiredArgsConstructor
public class StockOperations {
    private final StockRepository repository;
    private final WarehouseAccess access;

    public ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public void warehouse(UUID id, boolean lock, boolean active) {
        access.requireWarehouse(id);
        var states = repository.jdbc().query("SELECT status FROM warehouses WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (r, n) -> r.getShort(1), id);
        if (states.isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Warehouse not found");
        if (active && states.getFirst() != 1)
            throw conflict("WAREHOUSE_INACTIVE", "Warehouse is inactive");
    }

    public long add(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            throw conflict("NUMERIC_OVERFLOW", "Quantity or amount limit exceeded");
        }
    }

    public List<LineResponse> validatedLines(List<LinePayload> lines) {
        Set<UUID> ids = new HashSet<>();
        List<LineResponse> result = new ArrayList<>();
        for (var line : lines.stream().sorted(Comparator.comparing(LinePayload::productId)).toList()) {
            if (!ids.add(line.productId()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_PRODUCT", "Duplicate product in document");
            var products = repository.jdbc().query("SELECT code,name,unit,status FROM products WHERE id=? FOR UPDATE",
                    (r, n) -> {
                        if (r.getShort("status") != 1)
                            throw conflict("PRODUCT_INACTIVE", "Product is inactive");
                        long total;
                        try {
                            total = Math.multiplyExact(line.quantity(), line.unitPrice());
                        } catch (ArithmeticException e) {
                            throw conflict("NUMERIC_OVERFLOW", "Line amount limit exceeded");
                        }
                        return new LineResponse(line.productId(), line.quantity(), line.unitPrice(), total,
                                r.getString("code"), r.getString("name"), r.getString("unit"));
                    }, line.productId());
            if (products.isEmpty())
                throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found");
            result.add(products.getFirst());
        }
        return result;
    }

    // Warehouse -> document -> products (UUID order) -> balances, shared with
    // receipts.
    // The warehouse lock also serializes creation of previously absent balance
    // rows.
    public void movements(UUID warehouse, UUID source, List<LineResponse> lines, boolean increase, boolean sale,
            String type, UUID actor, Instant now) {
        Map<UUID, Long> balances = new LinkedHashMap<>();
        for (var line : lines.stream().sorted(Comparator.comparing(LineResponse::productId)).toList()) {
            var current = repository.jdbc().query(
                    "SELECT quantity FROM inventory_balances WHERE warehouse_id=? AND product_id=? FOR UPDATE",
                    (rs, n) -> rs.getLong(1), warehouse, line.productId());
            long old = current.isEmpty() ? 0 : current.getFirst();
            if (!increase && old < line.quantity())
                throw conflict("INSUFFICIENT_STOCK", "Insufficient stock for the entire document");
            balances.put(line.productId(),
                    increase ? add(old, line.quantity()) : Math.subtractExact(old, line.quantity()));
        }
        for (var line : lines) {
            int updated = repository.jdbc().update(
                    "UPDATE inventory_balances SET quantity=? WHERE warehouse_id=? AND product_id=?",
                    balances.get(line.productId()), warehouse, line.productId());
            if (updated == 0)
                repository.jdbc().update(
                        "INSERT INTO inventory_balances(warehouse_id,product_id,quantity) VALUES (?,?,?)", warehouse,
                        line.productId(), balances.get(line.productId()));
            repository.jdbc().update(
                    "INSERT INTO inventory_movements(id,warehouse_id,product_id,quantity_change,type,receipt_id,sales_order_id,performed_by,performed_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), warehouse, line.productId(), increase ? line.quantity() : -line.quantity(), type,
                    sale ? null : source, sale ? source : null, actor, Timestamp.from(now));
        }
    }
}
