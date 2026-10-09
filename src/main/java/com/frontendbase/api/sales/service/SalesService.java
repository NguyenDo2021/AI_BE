package com.frontendbase.api.sales.service;

import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import com.frontendbase.api.sales.dto.SalesDtos.*;
import com.frontendbase.api.sales.repository.SalesRepository;
import com.frontendbase.api.customer.repository.CustomerRepository;
import com.frontendbase.api.customer.dto.CustomerDtos.CustomerResponse;
import com.frontendbase.api.stock.dto.StockDtos.LinePayload;
import com.frontendbase.api.stock.dto.StockDtos.LineResponse;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;
import com.frontendbase.api.stock.service.StockOperations;
import com.frontendbase.api.warehouse.service.WarehouseAccess;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SalesService {
    private final SalesRepository repository;
    private final CustomerRepository customers;
    private final StockOperations operations;
    private final WarehouseAccess access;

    private CustomerResponse customer(UUID id, UUID warehouse) {
        if (id == null)
            return null;
        var c = customers.customer(id, true);
        if (!c.warehouseId().equals(warehouse))
            throw operations.conflict("CUSTOMER_WAREHOUSE_MISMATCH", "Customer belongs to another warehouse");
        if (c.status() != 1)
            throw operations.conflict("CUSTOMER_INACTIVE", "Customer is inactive");
        return c;
    }

    private SalesResponse locked(UUID id) {
        var initial = repository.order(id, false);
        operations.warehouse(initial.warehouseId(), true, false);
        return repository.order(id, true);
    }

    private void version(SalesResponse r, long expected) {
        if (r.version() != expected)
            throw operations.conflict("VERSION_CONFLICT", "Sales order has changed; reload before retrying");
        if (r.version() == Long.MAX_VALUE)
            throw operations.conflict("NUMERIC_OVERFLOW", "Version limit exceeded");
    }

    private void draft(SalesResponse r) {
        if (r.status() != SalesStatus.DRAFT)
            throw operations.conflict("INVALID_SALES_ORDER_STATUS",
                    "Only draft sales orders may be updated or confirmed");
    }

    private long subtotal(List<LineResponse> lines) {
        long amount = 0;
        for (var l : lines)
            amount = operations.add(amount, l.lineTotal());
        return amount;
    }

    private long discount(Long value, long subtotal) {
        long d = value == null ? 0 : value;
        if (d > subtotal)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISCOUNT", "Discount exceeds subtotal");
        return d;
    }

    private void saveLines(UUID id, List<LineResponse> lines) {
        repository.jdbc().update("DELETE FROM sales_order_lines WHERE sales_order_id=?", id);
        for (var l : lines)
            repository.jdbc().update(
                    "INSERT INTO sales_order_lines(sales_order_id,product_id,quantity,unit_price,line_total,product_code,product_name,unit) VALUES (?,?,?,?,?,?,?,?)",
                    id, l.productId(), l.quantity(), l.unitPrice(), l.lineTotal(), l.productCode(), l.productName(),
                    l.unit());
    }

    @Transactional
    public SalesResponse create(CreatePayload p) {
        operations.warehouse(p.warehouseId(), true, true);
        customer(p.customerId(), p.warehouseId());
        var lines = operations.validatedLines(p.lines());
        long subtotal = subtotal(lines), discount = discount(p.discountAmount(), subtotal);
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        repository.jdbc().update(
                "INSERT INTO sales_orders(id,code,warehouse_id,customer_id,sale_date,note,status,subtotal,discount_amount,total_amount,version,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,'DRAFT',?,?,?,0,?,?,?)",
                id, "BH-" + id.toString().replace("-", ""), p.warehouseId(), p.customerId(), p.saleDate(), p.note(),
                subtotal, discount, Math.subtractExact(subtotal, discount), access.currentUserId(), now, now);
        saveLines(id, lines);
        return repository.order(id, false);
    }

    @Transactional
    public SalesResponse update(UUID id, UpdatePayload p) {
        var r = locked(id);
        draft(r);
        version(r, p.version());
        if (p.warehouseId() != null && !p.warehouseId().equals(r.warehouseId()))
            throw operations.conflict("WAREHOUSE_IMMUTABLE", "Sales warehouse cannot be changed");
        operations.warehouse(r.warehouseId(), false, true);
        customer(p.customerId(), r.warehouseId());
        var lines = operations.validatedLines(p.lines());
        long subtotal = subtotal(lines), discount = discount(p.discountAmount(), subtotal);
        repository.jdbc().update(
                "UPDATE sales_orders SET customer_id=?,sale_date=?,note=?,subtotal=?,discount_amount=?,total_amount=?,updated_at=?,version=version+1 WHERE id=?",
                p.customerId(), p.saleDate(), p.note(), subtotal, discount, Math.subtractExact(subtotal, discount),
                Timestamp.from(Instant.now()), id);
        saveLines(id, lines);
        return repository.order(id, false);
    }

    @Transactional
    public SalesResponse confirm(UUID id, ConfirmPayload p) {
        var r = locked(id);
        if (r.status() == SalesStatus.CONFIRMED)
            return r;
        draft(r);
        version(r, p.version());
        operations.warehouse(r.warehouseId(), false, true);
        var c = customer(r.customerId(), r.warehouseId());
        var lines = operations.validatedLines(
                r.lines().stream().map(l -> new LinePayload(l.productId(), l.quantity(), l.unitPrice())).toList());
        saveLines(id, lines);
        Instant now = Instant.now();
        UUID actor = access.currentUserId();
        operations.movements(r.warehouseId(), id, lines, false, true, "SALE_CONFIRM", actor, now);
        repository.jdbc().update(
                "UPDATE sales_orders SET status='CONFIRMED',customer_code=?,customer_name=?,customer_phone=?,customer_address=?,confirmed_by=?,confirmed_at=?,updated_at=?,version=version+1 WHERE id=?",
                c == null ? null : c.code(), c == null ? "Kh\u00e1ch l\u1ebb" : c.name(), c == null ? null : c.phone(),
                c == null ? null : c.address(), actor, Timestamp.from(now), Timestamp.from(now), id);
        return repository.order(id, false);
    }

    @Transactional
    public SalesResponse cancel(UUID id, CancelPayload p) {
        var r = locked(id);
        if (r.status() == SalesStatus.CANCELLED)
            return r;
        version(r, p.version());
        if (r.paidAmount() > 0)
            throw operations.conflict("SALES_ORDER_HAS_PAYMENTS",
                    "Cannot cancel a paid sales order; refunds are not supported");
        boolean reverse = r.status() == SalesStatus.CONFIRMED;
        if (reverse && !Boolean.TRUE.equals(p.goodsReturned()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "GOODS_RETURNED_REQUIRED",
                    "Confirm that all goods were recovered or were never delivered");
        Instant now = Instant.now();
        UUID actor = access.currentUserId();
        if (reverse)
            operations.movements(r.warehouseId(), id, r.lines(), true, true, "SALE_CANCEL", actor, now);
        repository.jdbc().update(
                "UPDATE sales_orders SET status='CANCELLED',cancelled_by=?,cancelled_at=?,cancellation_reason=?,goods_returned=?,updated_at=?,version=version+1 WHERE id=?",
                actor, Timestamp.from(now), p.reason().trim(), reverse ? Boolean.TRUE : p.goodsReturned(),
                Timestamp.from(now), id);
        return repository.order(id, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SalesResponse get(UUID id) {
        var r = repository.order(id, false);
        access.requireWarehouse(r.warehouseId());
        return r;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<SalesResponse> search(UUID warehouse, UUID customer, SalesStatus status, LocalDate from,
            LocalDate to, int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Invalid pagination");
        if (from != null && to != null && from.isAfter(to))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Invalid date range");
        String where = " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (!access.isAdmin()) {
            where += " AND EXISTS (SELECT 1 FROM user_warehouses uw WHERE uw.warehouse_id=s.warehouse_id AND uw.user_id=?)";
            args.add(access.currentUserId());
        }
        if (warehouse != null) {
            operations.warehouse(warehouse, false, false);
            where += " AND s.warehouse_id=?";
            args.add(warehouse);
        }
        if (customer != null) {
            where += " AND s.customer_id=?";
            args.add(customer);
        }
        if (status != null) {
            where += " AND s.status=?";
            args.add(status.name());
        }
        if (from != null) {
            where += " AND s.sale_date>=?";
            args.add(from);
        }
        if (to != null) {
            where += " AND s.sale_date<=?";
            args.add(to);
        }
        long count = repository.jdbc().queryForObject("SELECT count(*) FROM sales_orders s" + where, Long.class,
                args.toArray());
        args.add(size);
        args.add(((long) page - 1) * size);
        var ids = repository.jdbc().query(
                "SELECT s.id FROM sales_orders s" + where + " ORDER BY s.created_at DESC,s.id DESC LIMIT ? OFFSET ?",
                (r, n) -> r.getObject(1, UUID.class), args.toArray());
        return new PageResponse<>(ids.stream().map(id -> repository.order(id, false)).toList(), count, page, size);
    }
}
