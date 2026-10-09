package com.frontendbase.api.payment.service;

import java.util.*;
import com.frontendbase.api.payment.dto.PaymentDtos.*;
import com.frontendbase.api.payment.repository.PaymentRepository;
import com.frontendbase.api.customer.repository.CustomerRepository;
import com.frontendbase.api.sales.repository.SalesRepository;
import com.frontendbase.api.sales.dto.SalesDtos.SalesResponse;
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
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReceivableService {
    private final PaymentRepository repository;
    private final CustomerRepository customers;
    private final SalesRepository sales;
    private final StockOperations operations;
    private final WarehouseAccess access;

    private void page(int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Invalid pagination");
    }

    private String scope(List<Object> args) {
        if (access.isAdmin())
            return "";
        args.add(access.currentUserId());
        return " AND EXISTS (SELECT 1 FROM user_warehouses uw WHERE uw.warehouse_id=t.warehouse_id AND uw.user_id=?)";
    }

    public PageResponse<ReceivableResponse> search(UUID warehouse, UUID customer, String keyword, int page, int size) {
        page(page, size);
        List<Object> args = new ArrayList<>();
        String where = " WHERE t.status='CONFIRMED' AND t.remaining_amount>0 AND t.customer_id IS NOT NULL"
                + scope(args);
        if (warehouse != null) {
            operations.warehouse(warehouse, false, false);
            where += " AND t.warehouse_id=?";
            args.add(warehouse);
        }
        if (customer != null) {
            access.requireWarehouse(customers.customer(customer, false).warehouseId());
            where += " AND t.customer_id=?";
            args.add(customer);
        }
        if (keyword != null && !keyword.isBlank()) {
            where += " AND (LOWER(c.name) LIKE ? ESCAPE '!' OR LOWER(c.code) LIKE ? ESCAPE '!' OR LOWER(COALESCE(c.phone,'')) LIKE ? ESCAPE '!')";
            String pattern = "%"
                    + keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
                    + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        String grouped = " FROM sales_payment_totals t JOIN customers c ON c.id=t.customer_id" + where +
                " GROUP BY t.customer_id,t.warehouse_id,c.code,c.name";
        long total = repository.jdbc().queryForObject(
                "SELECT count(*) FROM (SELECT t.customer_id" + grouped + ") grouped", Long.class, args.toArray());
        args.add(size);
        args.add(((long) page - 1) * size);
        var items = repository.jdbc().query(
                "SELECT t.customer_id,t.warehouse_id,c.code,c.name,count(*) AS orders,SUM(t.total_amount) AS total_amount,"
                        +
                        "SUM(t.paid_amount) AS paid_amount,SUM(t.remaining_amount) AS remaining_amount" + grouped +
                        " ORDER BY SUM(t.remaining_amount) DESC,t.warehouse_id,t.customer_id LIMIT ? OFFSET ?",
                (r, n) -> new ReceivableResponse(r.getObject("customer_id", UUID.class),
                        r.getObject("warehouse_id", UUID.class),
                        r.getString("code"), r.getString("name"), r.getLong("orders"),
                        repository.exact(r.getBigDecimal("total_amount")),
                        repository.exact(r.getBigDecimal("paid_amount")),
                        repository.exact(r.getBigDecimal("remaining_amount"))),
                args.toArray());
        return new PageResponse<>(items, total, page, size);
    }

    private PageResponse<SalesResponse> orders(String where, List<Object> args, int page, int size) {
        long total = repository.jdbc().queryForObject("SELECT count(*) FROM sales_payment_totals t" + where, Long.class,
                args.toArray());
        args.add(size);
        args.add(((long) page - 1) * size);
        var ids = repository.jdbc()
                .query("SELECT t.id FROM sales_payment_totals t JOIN sales_orders s ON s.id=t.id" + where +
                        " ORDER BY s.sale_date DESC,t.id DESC LIMIT ? OFFSET ?", (r, n) -> r.getObject(1, UUID.class),
                        args.toArray());
        return new PageResponse<>(ids.stream().map(id -> sales.order(id, false)).toList(), total, page, size);
    }

    public CustomerReceivables customer(UUID id, int page, int size) {
        page(page, size);
        var c = customers.customer(id, false);
        access.requireWarehouse(c.warehouseId());
        String where = " WHERE t.status='CONFIRMED' AND t.remaining_amount>0 AND t.customer_id=? AND t.warehouse_id=?";
        var args = new ArrayList<Object>(List.of(id, c.warehouseId()));
        long remaining = repository.exact(repository.jdbc().queryForObject(
                "SELECT COALESCE(SUM(t.remaining_amount),0) FROM sales_payment_totals t" + where,
                java.math.BigDecimal.class, args.toArray()));
        return new CustomerReceivables(id, c.warehouseId(), remaining, orders(where, args, page, size));
    }

    public PageResponse<SalesResponse> walkIn(UUID warehouse, int page, int size) {
        page(page, size);
        List<Object> args = new ArrayList<>();
        String where = " WHERE t.status='CONFIRMED' AND t.remaining_amount>0 AND t.customer_id IS NULL" + scope(args);
        if (warehouse != null) {
            operations.warehouse(warehouse, false, false);
            where += " AND t.warehouse_id=?";
            args.add(warehouse);
        }
        return orders(where, args, page, size);
    }
}
