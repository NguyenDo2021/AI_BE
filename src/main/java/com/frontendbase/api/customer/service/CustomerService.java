package com.frontendbase.api.customer.service;

import java.util.*;
import java.time.Instant;
import java.sql.Timestamp;
import com.frontendbase.api.customer.dto.CustomerDtos.*;
import com.frontendbase.api.customer.repository.CustomerRepository;
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
public class CustomerService {
    private final CustomerRepository repository;
    private final StockOperations operations;
    private final WarehouseAccess access;

    @Transactional
    public CustomerResponse create(CreatePayload p) {
        operations.warehouse(p.warehouseId(), true, true);
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        repository.jdbc().update(
                "INSERT INTO customers(id,warehouse_id,code,name,phone,address,note,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, p.warehouseId(), "KH-" + id.toString().replace("-", ""), p.name().trim(), p.phone(), p.address(),
                p.note(), p.status(), now, now);
        return repository.customer(id, false);
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdatePayload p) {
        var initial = repository.customer(id, false);
        operations.warehouse(initial.warehouseId(), true, false);
        var c = repository.customer(id, true);
        if (p.warehouseId() != null && !p.warehouseId().equals(c.warehouseId()))
            throw operations.conflict("WAREHOUSE_IMMUTABLE", "Customer warehouse cannot be changed");
        repository.jdbc().update(
                "UPDATE customers SET name=?,phone=?,address=?,note=?,status=?,updated_at=? WHERE id=?",
                p.name().trim(), p.phone(), p.address(), p.note(), p.status(), Timestamp.from(Instant.now()), id);
        return repository.customer(id, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CustomerResponse get(UUID id) {
        var c = repository.customer(id, false);
        access.requireWarehouse(c.warehouseId());
        return c;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<CustomerResponse> search(UUID warehouse, String keyword, Integer status, int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Invalid pagination");
        if (status != null && status != 0 && status != 1)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid customer status");
        String where = " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (!access.isAdmin()) {
            where += " AND EXISTS (SELECT 1 FROM user_warehouses uw WHERE uw.warehouse_id=c.warehouse_id AND uw.user_id=?)";
            args.add(access.currentUserId());
        }
        if (warehouse != null) {
            operations.warehouse(warehouse, false, false);
            where += " AND c.warehouse_id=?";
            args.add(warehouse);
        }
        if (status != null) {
            where += " AND c.status=?";
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            where += " AND (LOWER(c.code) LIKE ? ESCAPE '!' OR LOWER(c.name) LIKE ? ESCAPE '!' OR LOWER(COALESCE(c.phone,'')) LIKE ? ESCAPE '!')";
            String term = "%"
                    + keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
                    + "%";
            args.add(term);
            args.add(term);
            args.add(term);
        }
        long count = repository.jdbc().queryForObject("SELECT count(*) FROM customers c" + where, Long.class,
                args.toArray());
        args.add(size);
        args.add(((long) page - 1) * size);
        var items = repository.jdbc().query(
                "SELECT c.* FROM customers c" + where + " ORDER BY c.created_at DESC,c.id DESC LIMIT ? OFFSET ?",
                repository::map, args.toArray());
        return new PageResponse<>(items, count, page, size);
    }
}
