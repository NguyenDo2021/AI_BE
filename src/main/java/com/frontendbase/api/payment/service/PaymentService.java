package com.frontendbase.api.payment.service;

import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import com.frontendbase.api.payment.dto.PaymentDtos.*;
import com.frontendbase.api.payment.repository.PaymentRepository;
import com.frontendbase.api.sales.dto.SalesDtos.SalesResponse;
import com.frontendbase.api.sales.dto.SalesDtos.SalesStatus;
import com.frontendbase.api.sales.repository.SalesRepository;
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
public class PaymentService {
    private final PaymentRepository repository;
    private final SalesRepository sales;
    private final StockOperations operations;
    private final WarehouseAccess access;

    private SalesResponse locked(UUID id) {
        var initial = sales.order(id, false);
        operations.warehouse(initial.warehouseId(), true, false);
        var order = sales.order(id, true);
        access.requireWarehouse(order.warehouseId());
        return order;
    }

    private OrderPaymentSummary summary(SalesResponse s) {
        return new OrderPaymentSummary(s.id(), s.totalAmount(), s.paidAmount(), s.remainingAmount(), s.paymentStatus());
    }

    private UUID key(String raw) {
        if (raw == null || !raw.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be a UUID");
        return UUID.fromString(raw);
    }

    @Transactional
    public MutationResponse create(UUID orderId, String rawKey, CreatePayload p) {
        UUID key = key(rawKey);
        var order = locked(orderId);
        UUID actor = access.currentUserId();
        // Warehouse -> order -> actor -> payment. Serialize actor-wide keys even across
        // warehouses.
        repository.jdbc().queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", UUID.class, actor);
        access.requireWarehouse(order.warehouseId());
        var old = repository.byKey(actor, key);
        if (old.isPresent()) {
            var payment = old.get();
            access.requireWarehouse(payment.warehouseId());
            if (!payment.salesOrderId().equals(orderId) || payment.amount() != p.amount()
                    || !payment.paymentDate().equals(p.paymentDate()) || payment.method() != p.method()
                    || !Objects.equals(payment.reference(), p.reference()) || !Objects.equals(payment.note(), p.note()))
                throw operations.conflict("IDEMPOTENCY_CONFLICT",
                        "Key was already used for a different payment payload");
            return new MutationResponse(payment, summary(order));
        }
        if (order.status() != SalesStatus.CONFIRMED)
            throw operations.conflict("INVALID_SALES_ORDER_STATUS", "Payments require a confirmed sales order");
        if (p.amount() > order.remainingAmount())
            throw operations.conflict("PAYMENT_EXCEEDS_REMAINING", "Payment exceeds the remaining amount");
        operations.add(order.paidAmount(), p.amount());
        UUID id = UUID.randomUUID();
        repository.jdbc().update(
                "INSERT INTO payments(id,code,sales_order_id,warehouse_id,customer_id,amount,payment_date,method,reference,note,status,created_by,created_at,idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?)",
                id, "PT-" + id.toString().replace("-", ""), orderId, order.warehouseId(), order.customerId(),
                p.amount(),
                p.paymentDate(), p.method().name(), p.reference(), p.note(), actor, Timestamp.from(Instant.now()), key);
        return new MutationResponse(repository.payment(id, false), summary(sales.order(orderId, false)));
    }

    @Transactional
    public MutationResponse cancel(UUID id, CancelPayload p) {
        var initial = repository.payment(id, false);
        locked(initial.salesOrderId());
        var payment = repository.payment(id, true);
        access.requireWarehouse(payment.warehouseId());
        if (payment.status() == Status.ACTIVE)
            repository.jdbc().update(
                    "UPDATE payments SET status='CANCELLED',cancelled_by=?,cancelled_at=?,cancellation_reason=? WHERE id=?",
                    access.currentUserId(), Timestamp.from(Instant.now()), p.reason().trim(), id);
        return new MutationResponse(repository.payment(id, false), summary(sales.order(payment.salesOrderId(), false)));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PaymentResponse get(UUID id) {
        var payment = repository.payment(id, false);
        access.requireWarehouse(payment.warehouseId());
        return payment;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<PaymentResponse> orderPayments(UUID id, Status status, int page, int size) {
        var order = sales.order(id, false);
        access.requireWarehouse(order.warehouseId());
        return search(null, id, null, status, null, null, null, page, size);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<PaymentResponse> search(UUID warehouse, UUID order, UUID customer, Status status, Method method,
            LocalDate from, LocalDate to, int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Invalid pagination");
        if (from != null && to != null && from.isAfter(to))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Invalid date range");
        String where = " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (!access.isAdmin()) {
            where += " AND EXISTS (SELECT 1 FROM user_warehouses uw WHERE uw.warehouse_id=p.warehouse_id AND uw.user_id=?)";
            args.add(access.currentUserId());
        }
        if (warehouse != null) {
            operations.warehouse(warehouse, false, false);
            where += " AND p.warehouse_id=?";
            args.add(warehouse);
        }
        if (order != null) {
            access.requireWarehouse(sales.order(order, false).warehouseId());
            where += " AND p.sales_order_id=?";
            args.add(order);
        }
        if (customer != null) {
            where += " AND p.customer_id=?";
            args.add(customer);
        }
        if (status != null) {
            where += " AND p.status=?";
            args.add(status.name());
        }
        if (method != null) {
            where += " AND p.method=?";
            args.add(method.name());
        }
        if (from != null) {
            where += " AND p.payment_date>=?";
            args.add(from);
        }
        if (to != null) {
            where += " AND p.payment_date<=?";
            args.add(to);
        }
        long total = repository.jdbc().queryForObject("SELECT count(*) FROM payments p" + where, Long.class,
                args.toArray());
        args.add(size);
        args.add(((long) page - 1) * size);
        var items = repository.jdbc().query("SELECT p.* FROM payments p" + where +
                " ORDER BY p.created_at DESC,p.id DESC LIMIT ? OFFSET ?", repository::map, args.toArray());
        return new PageResponse<>(items, total, page, size);
    }
}
