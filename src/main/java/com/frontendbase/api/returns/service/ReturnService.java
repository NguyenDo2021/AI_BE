package com.frontendbase.api.returns.service;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import com.frontendbase.api.returns.dto.ReturnDtos.*;
import com.frontendbase.api.returns.repository.ReturnRepository;
import com.frontendbase.api.sales.repository.SalesRepository;
import com.frontendbase.api.sales.dto.SalesDtos.SalesResponse;
import com.frontendbase.api.sales.dto.SalesDtos.SalesStatus;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;
import com.frontendbase.api.stock.service.StockOperations;
import com.frontendbase.api.warehouse.service.WarehouseAccess;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class ReturnService {
    private final ReturnRepository repository;
    private final SalesRepository sales;
    private final StockOperations operations;
    private final WarehouseAccess access;
    private SalesResponse lockedOrder(UUID id) {
        var s=sales.order(id,false);
        operations.warehouse(s.warehouseId(),true,false);
        return sales.order(id,true);
    }
    private void source(SalesResponse s) {
        if(s.status()!=SalesStatus.CONFIRMED || s.confirmedAt()==null)
            throw operations.conflict("INVALID_SALES_ORDER_STATUS","Returns require a currently confirmed sales order");
    }
    private void draft(ReturnResponse r) {
        if(r.status()==Status.CONFIRMED)
            throw operations.conflict("RETURN_ADJUSTMENT_NOT_SUPPORTED","Adjustments to confirmed returns are not supported");
        if(r.status()!=Status.DRAFT)
            throw operations.conflict("INVALID_SALES_RETURN_STATUS","Only draft returns can be changed");
    }
    private void version(ReturnResponse r,long v) {
        if(r.version()!=v) throw operations.conflict("VERSION_CONFLICT","Sales return has changed; reload");
        operations.add(r.version(),1);
    }
    private List<ReturnableLine> returnable(SalesResponse s) {
        var discounts=ReturnValuation.discounts(s);
        var returned=repository.returned(s.id());
        return s.lines().stream().map(l->new ReturnableLine(l.productId(),l.productCode(),l.productName(),l.unit(),
            l.quantity(),returned.getOrDefault(l.productId(),0L),l.quantity()-returned.getOrDefault(l.productId(),0L),
            l.lineTotal(),discounts.get(l.productId()),l.lineTotal()-discounts.get(l.productId()))).toList();
    }
    private Quote quote(SalesResponse s,List<LinePayload> requested) {
        var available=new HashMap<UUID,ReturnableLine>();
        returnable(s).forEach(l->available.put(l.productId(),l));
        var seen=new HashSet<UUID>();
        var lines=new ArrayList<LineResponse>();
        long total=0;
        for(var p:requested.stream().sorted(Comparator.comparing(LinePayload::productId)).toList()) {
            if(!seen.add(p.productId())) throw new ApiException(HttpStatus.BAD_REQUEST,"DUPLICATE_PRODUCT","Duplicate product");
            var l=available.get(p.productId());
            if(l==null) throw new ApiException(HttpStatus.BAD_REQUEST,"PRODUCT_NOT_IN_SALES_ORDER","Product is not in original order");
            if(p.quantity()>l.returnableQuantity()) throw operations.conflict("RETURN_EXCEEDS_QUANTITY","Return exceeds unreturned sold quantity");
            long amount=ReturnValuation.amount(l.netLineAmount(),l.soldQuantity(),l.returnedQuantity(),p.quantity());
            total=operations.add(total,amount);
            lines.add(new LineResponse(l.productId(),p.quantity(),amount,l.soldQuantity(),l.allocatedDiscountAmount(),
                l.netLineAmount(),l.productCode(),l.productName(),l.unit()));
        }
        return new Quote(total,List.copyOf(lines));
    }
    @Transactional public ReturnResponse create(CreatePayload p) {
        var s=lockedOrder(p.salesOrderId()); source(s);
        var q=quote(s,p.lines()); UUID id=UUID.randomUUID(),actor=access.currentUserId();
        var now=Timestamp.from(Instant.now());
        repository.jdbc().update("INSERT INTO sales_returns(id,code,sales_order_id,warehouse_id,customer_id,return_date,reason,note,status,total_amount,version,created_by,created_at,updated_by,updated_at) VALUES (?,?,?,?,?,?,?,?,'DRAFT',?,0,?,?,?,?)",
            id,"TH-"+id.toString().replace("-",""),s.id(),s.warehouseId(),s.customerId(),p.returnDate(),p.reason(),p.note(),q.totalAmount(),actor,now,actor,now);
        repository.saveLines(id,q.lines()); return repository.get(id,false);
    }
    @Transactional public ReturnResponse update(UUID id,UpdatePayload p) {
        var initial=repository.get(id,false); var s=lockedOrder(initial.salesOrderId());
        var r=repository.get(id,true); draft(r); version(r,p.version()); source(s);
        var q=quote(s,p.lines());
        repository.saveLines(id,q.lines());
        repository.jdbc().update("UPDATE sales_returns SET return_date=?,reason=?,note=?,total_amount=?,version=version+1,updated_by=?,updated_at=? WHERE id=?",
            p.returnDate(),p.reason(),p.note(),q.totalAmount(),access.currentUserId(),Timestamp.from(Instant.now()),id);
        return repository.get(id,false);
    }
    @Transactional public ReturnResponse confirm(UUID id,ConfirmPayload p) {
        var initial=repository.get(id,false); var s=lockedOrder(initial.salesOrderId());
        var r=repository.get(id,true);
        if(r.status()==Status.CONFIRMED) return r;
        draft(r); version(r,p.version()); source(s);
        var q=quote(s,r.lines().stream().map(l->new LinePayload(l.productId(),l.quantity())).toList());
        if(q.totalAmount()!=r.totalAmount() || !q.lines().equals(r.lines()))
            throw new ApiException(HttpStatus.CONFLICT,"RETURN_QUOTE_CHANGED","Return quote changed; review and save the draft again",
                Map.of("quote",q,"version",r.version()));
        if(operations.add(s.returnedAmount(),q.totalAmount())>s.totalAmount())
            throw operations.conflict("RETURN_EXCEEDS_TOTAL","Returned amount exceeds order total");
        // Follow existing warehouse -> order -> document -> sorted products -> balances order.
        for(var l:q.lines()) repository.jdbc().queryForObject("SELECT id FROM products WHERE id=? FOR UPDATE",UUID.class,l.productId());
        var movementLines=q.lines().stream().map(l->new com.frontendbase.api.stock.dto.StockDtos.LineResponse(
            l.productId(),l.quantity(),0,0,l.productCode(),l.productName(),l.unit())).toList();
        var now=Instant.now(); var actor=access.currentUserId();
        operations.returnMovements(r.warehouseId(),id,movementLines,actor,now);
        repository.jdbc().update("UPDATE sales_returns SET status='CONFIRMED',confirmed_by=?,confirmed_at=?,updated_by=?,updated_at=?,version=version+1 WHERE id=?",
            actor,Timestamp.from(now),actor,Timestamp.from(now),id);
        return repository.get(id,false);
    }
    @Transactional public ReturnResponse cancel(UUID id,CancelPayload p) {
        var initial=repository.get(id,false); lockedOrder(initial.salesOrderId());
        var r=repository.get(id,true); draft(r); version(r,p.version());
        var now=Timestamp.from(Instant.now()); var actor=access.currentUserId();
        repository.jdbc().update("UPDATE sales_returns SET status='CANCELLED',cancelled_by=?,cancelled_at=?,cancellation_reason=?,updated_by=?,updated_at=?,version=version+1 WHERE id=?",
            actor,now,p.reason().trim(),actor,now,id);
        return repository.get(id,false);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReturnResponse get(UUID id) {
        var r=repository.get(id,false); access.requireWarehouse(r.warehouseId()); return r;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<ReturnableLine> returnable(UUID id) {
        var s=sales.order(id,false); access.requireWarehouse(s.warehouseId()); source(s); return returnable(s);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<ReturnableOrder> returnableOrders(String code, int page, int size) {
        if (code == null || code.strip().isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "code is required and must not be blank");
        if (page < 1 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Invalid pagination");

        // Treat LIKE metacharacters literally, so '%' or '_' cannot widen the search.
        String pattern = "%" + code.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var args = new ArrayList<Object>();
        args.add(pattern);
        String from = " FROM sales_orders s JOIN warehouses w ON w.id=s.warehouse_id";
        String where = " WHERE s.status='CONFIRMED' AND s.confirmed_at IS NOT NULL"
                + " AND LOWER(s.code) LIKE ? ESCAPE '!'"
                + " AND EXISTS (SELECT 1 FROM sales_order_lines l WHERE l.sales_order_id=s.id"
                + " AND l.quantity > COALESCE((SELECT SUM(CAST(rl.quantity AS NUMERIC))"
                + " FROM sales_return_lines rl JOIN sales_returns r ON r.id=rl.sales_return_id"
                + " WHERE r.sales_order_id=s.id AND r.status='CONFIRMED' AND rl.product_id=l.product_id),0))";
        if (!access.isAdmin()) {
            where += " AND EXISTS (SELECT 1 FROM user_warehouses uw"
                    + " WHERE uw.warehouse_id=s.warehouse_id AND uw.user_id=?)";
            args.add(access.currentUserId());
        }
        long total = repository.jdbc().queryForObject("SELECT count(*)" + from + where, Long.class, args.toArray());
        args.add(size);
        args.add(((long) page - 1) * size);
        var items = repository.jdbc().query(
                "SELECT s.id,s.code,s.warehouse_id,w.name warehouse_name,s.sale_date,s.customer_name,s.total_amount"
                        + from + where + " ORDER BY s.created_at DESC,s.id DESC LIMIT ? OFFSET ?",
                (r,n) -> new ReturnableOrder(r.getObject("id",UUID.class),r.getString("code"),
                        r.getObject("warehouse_id",UUID.class),r.getString("warehouse_name"),
                        r.getObject("sale_date",LocalDate.class),r.getString("customer_name"),r.getLong("total_amount")),
                args.toArray());
        return new PageResponse<>(items, total, page, size);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<ReturnResponse> search(UUID warehouse,UUID order,Status status,LocalDate from,LocalDate to,int page,int size) {
        if(page<1||size<1||size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION","Invalid pagination");
        if(from!=null&&to!=null&&from.isAfter(to)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DATE_RANGE","Invalid date range");
        String where=" WHERE 1=1"; var args=new ArrayList<Object>();
        if(!access.isAdmin()) { where+=" AND EXISTS(SELECT 1 FROM user_warehouses uw WHERE uw.warehouse_id=r.warehouse_id AND uw.user_id=?)"; args.add(access.currentUserId()); }
        if(warehouse!=null) { operations.warehouse(warehouse,false,false); where+=" AND r.warehouse_id=?"; args.add(warehouse); }
        if(order!=null) { access.requireWarehouse(sales.order(order,false).warehouseId()); where+=" AND r.sales_order_id=?"; args.add(order); }
        if(status!=null) { where+=" AND r.status=?"; args.add(status.name()); }
        if(from!=null) { where+=" AND r.return_date>=?"; args.add(from); }
        if(to!=null) { where+=" AND r.return_date<=?"; args.add(to); }
        long total=repository.jdbc().queryForObject("SELECT count(*) FROM sales_returns r"+where,Long.class,args.toArray());
        args.add(size); args.add(((long)page-1)*size);
        var ids=repository.jdbc().query("SELECT r.id FROM sales_returns r"+where+" ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?",(r,n)->r.getObject(1,UUID.class),args.toArray());
        return new PageResponse<>(ids.stream().map(id->repository.get(id,false)).toList(),total,page,size);
    }
}
