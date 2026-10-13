package com.frontendbase.api.stocktake.service;

import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import com.frontendbase.api.stocktake.dto.StocktakeDtos.*;
import com.frontendbase.api.stocktake.repository.StocktakeRepository;
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
public class StocktakeService {
    private final StocktakeRepository repository;
    private final StockOperations operations;
    private final WarehouseAccess access;

    private Response locked(UUID id, boolean active) {
        var initial = repository.get(id, false);
        operations.warehouse(initial.warehouseId(), true, active);
        return repository.get(id, true);
    }

    private void draft(Response r) {
        if (r.status() != Status.DRAFT)
            throw operations.conflict("INVALID_STOCKTAKE_STATUS", "Only draft stocktakes support this operation");
    }

    private void version(Response r, long expected) {
        if (r.version() != expected)
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Stocktake has changed; reload before retrying", Map.of("stocktakeId", r.id().toString(),
                            "expectedVersion", Long.toString(expected), "currentVersion", Long.toString(r.version())));
        operations.add(r.version(), 1);
    }

    // Caller holds the warehouse lock, including for an absent balance.
    private long[] balance(UUID warehouse, UUID product) {
        var rows = repository.jdbc().query(
                "SELECT quantity,revision FROM inventory_balances WHERE warehouse_id=? AND product_id=? FOR UPDATE",
                (r, n) -> new long[] { r.getLong(1), r.getLong(2) }, warehouse, product);
        return rows.isEmpty() ? new long[] { 0, 0 } : rows.getFirst();
    }

    private void product(UUID warehouse, UUID product) {
        var rows = repository.jdbc().query("SELECT status FROM products WHERE id=? FOR UPDATE",
                (r, n) -> r.getShort(1), product);
        if (rows.isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found");
        if (rows.getFirst() != 1 && !Boolean.TRUE.equals(repository.jdbc().queryForObject(
                "SELECT EXISTS(SELECT 1 FROM inventory_movements WHERE warehouse_id=? AND product_id=?)",
                Boolean.class, warehouse, product)))
            throw operations.conflict("PRODUCT_INACTIVE", "Inactive product has no history in this warehouse");
    }

    private List<LineResponse> snapshots(UUID warehouse, List<LinePayload> input, List<LineResponse> previous,
            boolean refresh) {
        Set<UUID> ids = new HashSet<>();
        Map<UUID, LineResponse> old = new HashMap<>();
        previous.forEach(l -> old.put(l.productId(), l));
        var sorted = input.stream().sorted(Comparator.comparing(LinePayload::productId)).toList();
        // Lock all products before any balance, in the same order as receipts/sales.
        for (var l : sorted) {
            if (!ids.add(l.productId()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_PRODUCT", "Duplicate product in stocktake");
            product(warehouse, l.productId());
        }
        List<LineResponse> result = new ArrayList<>();
        for (var p : sorted) {
            LineResponse baseline = refresh ? null : old.get(p.productId());
            if (baseline == null) {
                long[] b = balance(warehouse, p.productId());
                baseline = repository.jdbc()
                        .query("SELECT code,name,unit FROM products WHERE id=?",
                                (r, n) -> new LineResponse(p.productId(), b[0], b[1], null, null, null, r.getString(1),
                                        r.getString(2), r.getString(3)),
                                p.productId())
                        .getFirst();
            }
            Long counted = refresh ? null : p.countedQuantity();
            Long difference = counted == null ? null : Math.subtractExact(counted, baseline.systemQuantity());
            String reason = refresh ? null : p.reason();
            if (difference != null && difference != 0 && (reason == null || reason.isBlank()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "STOCKTAKE_REASON_REQUIRED",
                        "A difference requires a reason",
                        Map.of("productId", p.productId().toString()));
            result.add(new LineResponse(p.productId(), baseline.systemQuantity(), baseline.inventoryRevision(),
                    counted, difference, reason, baseline.productCode(), baseline.productName(), baseline.unit()));
        }
        return result;
    }

    @Transactional
    public Response create(CreatePayload p) {
        operations.warehouse(p.warehouseId(), true, true);
        var lines = snapshots(p.warehouseId(),
                p.lines().stream().map(l -> new LinePayload(l.productId(), null, null)).toList(), List.of(), false);
        UUID id = UUID.randomUUID(), actor = access.currentUserId();
        Timestamp now = Timestamp.from(Instant.now());
        repository.jdbc().update(
                "INSERT INTO stocktakes(id,code,warehouse_id,note,status,version,created_by,created_at,updated_by,updated_at) VALUES (?,?,?,?,'DRAFT',0,?,?,?,?)",
                id, "KK-" + id.toString().replace("-", ""), p.warehouseId(), p.note(), actor, now, actor, now);
        repository.saveLines(id, lines);
        return repository.get(id, false);
    }

    private void touch(UUID id) {
        repository.jdbc().update("UPDATE stocktakes SET version=version+1,updated_by=?,updated_at=? WHERE id=?",
                access.currentUserId(), Timestamp.from(Instant.now()), id);
    }

    @Transactional
    public Response update(UUID id, UpdatePayload p) {
        var r = locked(id, true);
        draft(r);
        version(r, p.version());
        var lines = snapshots(r.warehouseId(), p.lines(), r.lines(), false);
        repository.saveLines(id, lines);
        repository.jdbc().update("UPDATE stocktakes SET note=?,version=version+1,updated_by=?,updated_at=? WHERE id=?",
                p.note(), access.currentUserId(), Timestamp.from(Instant.now()), id);
        return repository.get(id, false);
    }

    @Transactional
    public Response refresh(UUID id, VersionPayload p) {
        var r = locked(id, true);
        draft(r);
        version(r, p.version());
        var lines = snapshots(r.warehouseId(),
                r.lines().stream().map(l -> new LinePayload(l.productId(), null, null)).toList(), List.of(), true);
        repository.saveLines(id, lines);
        touch(id);
        return repository.get(id, false);
    }

    @Transactional
    public Response confirm(UUID id, VersionPayload p) {
        var r = locked(id, false);
        if (r.status() == Status.CONFIRMED)
            return r;
        draft(r);
        version(r, p.version());
        operations.warehouse(r.warehouseId(), false, true);
        var sorted = r.lines().stream().sorted(Comparator.comparing(LineResponse::productId)).toList();
        for (var l : sorted) {
            product(r.warehouseId(), l.productId());
            if (l.countedQuantity() == null)
                throw new ApiException(HttpStatus.BAD_REQUEST, "STOCKTAKE_COUNT_REQUIRED",
                        "Every product must be counted", Map.of("productId", l.productId().toString()));
            if (l.difference() != 0 && (l.reason() == null || l.reason().isBlank()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "STOCKTAKE_REASON_REQUIRED",
                        "A difference requires a reason",
                        Map.of("productId", l.productId().toString()));
        }
        Map<String, String> stale = new LinkedHashMap<>();
        Map<UUID, Long> revisions = new HashMap<>();
        for (var l : sorted) {
            long[] b = balance(r.warehouseId(), l.productId());
            if (b[1] != l.inventoryRevision() || b[0] != l.systemQuantity()) {
                String key = "lines." + l.productId() + ".";
                stale.put(key + "snapshotRevision", Long.toString(l.inventoryRevision()));
                stale.put(key + "currentRevision", Long.toString(b[1]));
                stale.put(key + "systemQuantity", Long.toString(l.systemQuantity()));
                stale.put(key + "currentQuantity", Long.toString(b[0]));
            }
            if (l.difference() != 0)
                revisions.put(l.productId(), b[1]);
        }
        if (!stale.isEmpty())
            throw new ApiException(HttpStatus.CONFLICT, "STOCKTAKE_STALE",
                    "Inventory changed after the snapshot; refresh and count again", stale);
        revisions.replaceAll((product, revision) -> operations.add(revision, 1));
        UUID actor = access.currentUserId();
        Timestamp now = Timestamp.from(Instant.now());
        for (var l : sorted) {
            if (l.difference() == 0)
                continue;
            int updated = repository.jdbc().update(
                    "UPDATE inventory_balances SET quantity=?,revision=? WHERE warehouse_id=? AND product_id=?",
                    l.countedQuantity(), revisions.get(l.productId()), r.warehouseId(), l.productId());
            if (updated == 0)
                repository.jdbc().update(
                        "INSERT INTO inventory_balances(warehouse_id,product_id,quantity,revision) VALUES (?,?,?,?)",
                        r.warehouseId(), l.productId(), l.countedQuantity(), revisions.get(l.productId()));
            repository.jdbc().update(
                    "INSERT INTO inventory_movements(id,warehouse_id,product_id,quantity_change,type,stocktake_id,performed_by,performed_at) VALUES (?,?,?,?,'STOCKTAKE_ADJUSTMENT',?,?,?)",
                    UUID.randomUUID(), r.warehouseId(), l.productId(), l.difference(), id, actor, now);
        }
        repository.jdbc().update(
                "UPDATE stocktakes SET status='CONFIRMED',version=version+1,updated_by=?,updated_at=?,confirmed_by=?,confirmed_at=? WHERE id=?",
                actor, now, actor, now, id);
        return repository.get(id, false);
    }

    @Transactional
    public Response cancel(UUID id, CancelPayload p) {
        var r = locked(id, false);
        if (r.status() == Status.CANCELLED)
            return r;
        draft(r);
        version(r, p.version());
        UUID actor = access.currentUserId();
        Timestamp now = Timestamp.from(Instant.now());
        repository.jdbc().update(
                "UPDATE stocktakes SET status='CANCELLED',version=version+1,updated_by=?,updated_at=?,cancelled_by=?,cancelled_at=?,cancellation_reason=? WHERE id=?",
                actor, now, actor, now, p.reason(), id);
        return repository.get(id, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Response get(UUID id) {
        var r = repository.get(id, false);
        access.requireWarehouse(r.warehouseId());
        return r;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<Response> search(int page, int size, UUID warehouse, Status status, LocalDate from,
            LocalDate to) {
        if (page < 1 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Invalid pagination");
        if (from != null && to != null && from.isAfter(to))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Invalid date range");
        String where = " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (!access.isAdmin()) {
            where += " AND EXISTS(SELECT 1 FROM user_warehouses uw WHERE uw.warehouse_id=s.warehouse_id AND uw.user_id=?)";
            args.add(access.currentUserId());
        }
        if (warehouse != null) {
            operations.warehouse(warehouse, false, false);
            where += " AND s.warehouse_id=?";
            args.add(warehouse);
        }
        if (status != null) {
            where += " AND s.status=?";
            args.add(status.name());
        }
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        if (from != null) {
            where += " AND s.created_at>=?";
            args.add(Timestamp.from(from.atStartOfDay(zone).toInstant()));
        }
        if (to != null) {
            where += " AND s.created_at<?";
            args.add(Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()));
        }
        long total = repository.jdbc().queryForObject("SELECT count(*) FROM stocktakes s" + where, Long.class,
                args.toArray());
        args.add(size);
        args.add(((long) page - 1) * size);
        var ids = repository.jdbc().query(
                "SELECT s.id FROM stocktakes s" + where + " ORDER BY s.created_at DESC,s.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> rs.getObject(1, UUID.class), args.toArray());
        return new PageResponse<>(ids.stream().map(id -> repository.get(id, false)).toList(), total, page, size);
    }
}
