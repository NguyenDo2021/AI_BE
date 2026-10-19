package com.frontendbase.api.refund.service;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import com.frontendbase.api.refund.dto.RefundDtos.*;
import com.frontendbase.api.refund.repository.RefundRepository;
import com.frontendbase.api.payment.dto.PaymentDtos.Method;
import com.frontendbase.api.payment.dto.PaymentDtos.Status;
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
public class RefundService {
    private final RefundRepository repository;
    private final SalesRepository sales;
    private final StockOperations operations;
    private final WarehouseAccess access;
    private SalesResponse locked(UUID id) {
        var s=sales.order(id,false); operations.warehouse(s.warehouseId(),true,false);
        return sales.order(id,true);
    }
    private UUID key(String raw) {
        if(raw==null||!raw.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","Idempotency-Key must be a UUID");
        return UUID.fromString(raw);
    }
    @Transactional public MutationResponse create(UUID orderId,String rawKey,CreatePayload p) {
        var key=key(rawKey); var s=locked(orderId); var actor=access.currentUserId();
        repository.jdbc().queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",UUID.class,actor);
        access.requireWarehouse(s.warehouseId());
        var old=repository.byKey(actor,key);
        if(old.isPresent()) {
            var r=old.get(); access.requireWarehouse(r.warehouseId());
            if(!r.salesOrderId().equals(orderId)||r.amount()!=p.amount()||!r.refundDate().equals(p.refundDate())
                ||r.method()!=p.method()||!Objects.equals(r.reference(),p.reference())
                ||!Objects.equals(r.reason(),p.reason())||!Objects.equals(r.note(),p.note()))
                throw operations.conflict("IDEMPOTENCY_CONFLICT","Key was already used for a different refund payload");
            return new MutationResponse(r,OrderSettlementSummary.from(s));
        }
        if(s.status()!=SalesStatus.CONFIRMED)
            throw operations.conflict("INVALID_SALES_ORDER_STATUS","Refunds require a confirmed sales order");
        if(s.refundableAmount()==0||p.amount()>s.refundableAmount())
            throw operations.conflict("REFUND_EXCEEDS_REFUNDABLE","Refund exceeds current refundable amount");
        operations.add(s.refundedAmount(),p.amount());
        var id=UUID.randomUUID();
        repository.jdbc().update("INSERT INTO refunds(id,code,sales_order_id,warehouse_id,customer_id,amount,refund_date,method,reference,reason,note,status,created_by,created_at,idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?)",
            id,"HT-"+id.toString().replace("-",""),s.id(),s.warehouseId(),s.customerId(),p.amount(),p.refundDate(),p.method().name(),p.reference(),p.reason(),p.note(),actor,Timestamp.from(Instant.now()),key);
        return new MutationResponse(repository.refund(id,false),OrderSettlementSummary.from(sales.order(orderId,false)));
    }
    @Transactional public MutationResponse cancel(UUID id,CancelPayload p) {
        var initial=repository.refund(id,false); locked(initial.salesOrderId());
        var r=repository.refund(id,true); access.requireWarehouse(r.warehouseId());
        if(r.status()==Status.ACTIVE) repository.jdbc().update("UPDATE refunds SET status='CANCELLED',cancelled_by=?,cancelled_at=?,cancellation_reason=? WHERE id=?",
            access.currentUserId(),Timestamp.from(Instant.now()),p.reason().trim(),id);
        return new MutationResponse(repository.refund(id,false),OrderSettlementSummary.from(sales.order(r.salesOrderId(),false)));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public RefundResponse get(UUID id) {
        var r=repository.refund(id,false); access.requireWarehouse(r.warehouseId()); return r;
    }
    private void page(int page,int size,LocalDate from,LocalDate to) {
        if(page<1||size<1||size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION","Invalid pagination");
        if(from!=null&&to!=null&&from.isAfter(to)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DATE_RANGE","Invalid date range");
    }
    private String scope(List<Object> args,String alias,UUID warehouse) {
        String where="";
        if(!access.isAdmin()) { where+=" AND EXISTS(SELECT 1 FROM user_warehouses uw WHERE uw.warehouse_id="+alias+".warehouse_id AND uw.user_id=?)"; args.add(access.currentUserId()); }
        if(warehouse!=null) { operations.warehouse(warehouse,false,false); where+=" AND "+alias+".warehouse_id=?"; args.add(warehouse); }
        return where;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<RefundResponse> search(UUID warehouse,UUID order,UUID customer,Status status,Method method,LocalDate from,LocalDate to,int page,int size) {
        page(page,size,from,to); var args=new ArrayList<Object>();
        String where=" WHERE 1=1"+scope(args,"r",warehouse);
        if(order!=null) { access.requireWarehouse(sales.order(order,false).warehouseId()); where+=" AND r.sales_order_id=?"; args.add(order); }
        if(customer!=null) { where+=" AND r.customer_id=?"; args.add(customer); }
        if(status!=null) { where+=" AND r.status=?"; args.add(status.name()); }
        if(method!=null) { where+=" AND r.method=?"; args.add(method.name()); }
        if(from!=null) { where+=" AND r.refund_date>=?"; args.add(from); }
        if(to!=null) { where+=" AND r.refund_date<=?"; args.add(to); }
        long total=repository.jdbc().queryForObject("SELECT count(*) FROM refunds r"+where,Long.class,args.toArray());
        args.add(size);args.add(((long)page-1)*size);
        return new PageResponse<>(repository.jdbc().query("SELECT r.* FROM refunds r"+where+" ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?",repository::map,args.toArray()),total,page,size);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<OrderSettlementSummary> refundables(UUID warehouse,int page,int size) {
        page(page,size,null,null); var args=new ArrayList<Object>();
        String where=" WHERE t.status='CONFIRMED' AND t.refundable_amount>0"+scope(args,"t",warehouse);
        long total=repository.jdbc().queryForObject("SELECT count(*) FROM sales_payment_totals t"+where,Long.class,args.toArray());
        args.add(size);args.add(((long)page-1)*size);
        var ids=repository.jdbc().query("SELECT t.id FROM sales_payment_totals t"+where+" ORDER BY t.refundable_amount DESC,t.id LIMIT ? OFFSET ?",(r,n)->r.getObject(1,UUID.class),args.toArray());
        return new PageResponse<>(ids.stream().map(id->OrderSettlementSummary.from(sales.order(id,false))).toList(),total,page,size);
    }
}
