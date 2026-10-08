package com.frontendbase.api.stock.service;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import com.frontendbase.api.stock.dto.StockDtos.*;
import com.frontendbase.api.stock.repository.StockRepository;
import com.frontendbase.api.warehouse.service.WarehouseAccess;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class StockService {
    private final StockRepository repository;
    private final WarehouseAccess access;
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT,code,message); }
    private void pagination(int page,int size) {
        if (page<1 || size<1 || size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION","Invalid pagination");
    }
    private long offset(int page,int size) { return ((long)page-1)*size; }
    private void warehouse(UUID id, boolean lock, boolean active) {
        access.requireWarehouse(id);
        var states=repository.jdbc().query("SELECT status FROM warehouses WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->r.getShort(1),id);
        if(states.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"WAREHOUSE_NOT_FOUND","Warehouse not found");
        if(active && states.getFirst()!=1) throw conflict("WAREHOUSE_INACTIVE","Warehouse is inactive");
    }
    // Always warehouse -> receipt -> products in UUID order. The existing warehouse row
    // serializes balance creation as well as updates, including when no balance exists yet.
    private ReceiptResponse locked(UUID id,boolean active) {
        var initial=repository.receipt(id,false);
        warehouse(initial.warehouseId(),true,active);
        return repository.receipt(id,true);
    }
    private void version(ReceiptResponse r,long expected) {
        if(r.version()!=expected) throw conflict("VERSION_CONFLICT","Receipt has changed; reload before retrying");
        if(r.version()==Long.MAX_VALUE) throw conflict("NUMERIC_OVERFLOW","Version limit exceeded");
    }
    private void draft(ReceiptResponse r) {
        if(r.status()!=ReceiptStatus.DRAFT) throw conflict("INVALID_RECEIPT_STATUS","Only draft receipts may be updated or confirmed");
    }
    private long add(long a,long b) {
        try { return Math.addExact(a,b); }
        catch(ArithmeticException e) { throw conflict("NUMERIC_OVERFLOW","Quantity or amount limit exceeded"); }
    }
    private List<LineResponse> validatedLines(List<LinePayload> lines) {
        Set<UUID> ids=new HashSet<>(); List<LineResponse> result=new ArrayList<>();
        for(var line:lines.stream().sorted(Comparator.comparing(LinePayload::productId)).toList()) {
            if(!ids.add(line.productId())) throw new ApiException(HttpStatus.BAD_REQUEST,"DUPLICATE_PRODUCT","Duplicate product in receipt");
            var products=repository.jdbc().query("SELECT code,name,unit,status FROM products WHERE id=? FOR UPDATE",(r,n)-> {
                if(r.getShort("status")!=1) throw conflict("PRODUCT_INACTIVE","Product is inactive");
                long total;
                try { total=Math.multiplyExact(line.quantity(),line.unitPrice()); }
                catch(ArithmeticException e) { throw conflict("NUMERIC_OVERFLOW","Line amount limit exceeded"); }
                return new LineResponse(line.productId(),line.quantity(),line.unitPrice(),total,r.getString("code"),r.getString("name"),r.getString("unit"));
            },line.productId());
            if(products.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","Product not found");
            result.add(products.getFirst());
        }
        return result;
    }
    private long total(List<LineResponse> lines) { long amount=0; for(var line:lines) amount=add(amount,line.lineTotal()); return amount; }
    private void saveLines(UUID id,List<LineResponse> lines) {
        repository.jdbc().update("DELETE FROM stock_receipt_lines WHERE receipt_id=?",id);
        for(var line:lines) repository.jdbc().update("INSERT INTO stock_receipt_lines(receipt_id,product_id,quantity,unit_price,line_total,product_code,product_name,unit) VALUES (?,?,?,?,?,?,?,?)",
            id,line.productId(),line.quantity(),line.unitPrice(),line.lineTotal(),line.productCode(),line.productName(),line.unit());
    }
    @Transactional
    public ReceiptResponse create(CreatePayload p) {
        warehouse(p.warehouseId(),true,true);
        var lines=validatedLines(p.lines()); long amount=total(lines); UUID id=UUID.randomUUID();
        repository.jdbc().update("INSERT INTO stock_receipts(id,code,warehouse_id,receipt_date,supplier_name,note,status,total_amount,version,created_by,created_at) VALUES (?,?,?,?,?,?,'DRAFT',?,0,?,?)",
            id,"PN-"+id.toString().replace("-",""),p.warehouseId(),p.receiptDate(),p.supplierName(),p.note(),amount,access.currentUserId(),Timestamp.from(Instant.now()));
        saveLines(id,lines); return repository.receipt(id,false);
    }
    @Transactional
    public ReceiptResponse update(UUID id,UpdatePayload p) {
        var r=locked(id,true); draft(r); version(r,p.version());
        if(p.warehouseId()!=null && !p.warehouseId().equals(r.warehouseId())) throw conflict("WAREHOUSE_IMMUTABLE","Receipt warehouse cannot be changed");
        var lines=validatedLines(p.lines()); long amount=total(lines);
        repository.jdbc().update("UPDATE stock_receipts SET receipt_date=?,supplier_name=?,note=?,total_amount=?,version=version+1 WHERE id=?",
            p.receiptDate(),p.supplierName(),p.note(),amount,id);
        saveLines(id,lines); return repository.receipt(id,false);
    }
    @Transactional
    public ReceiptResponse confirm(UUID id,ConfirmPayload p) {
        // Scope is checked even for an idempotent retry. Inactive catalog does not block a completed retry.
        var r=locked(id,false);
        if(r.status()==ReceiptStatus.CONFIRMED) return r;
        draft(r); version(r,p.version()); warehouse(r.warehouseId(),false,true);
        var lines=validatedLines(r.lines().stream().map(l->new LinePayload(l.productId(),l.quantity(),l.unitPrice())).toList());
        // Snapshot is refreshed at confirmation, before immutable movement rows reference the lines.
        saveLines(id,lines);
        Instant now=Instant.now(); UUID actor=access.currentUserId();
        movements(r,lines,false,actor,now);
        repository.jdbc().update("UPDATE stock_receipts SET status='CONFIRMED',confirmed_by=?,confirmed_at=?,version=version+1 WHERE id=?",actor,Timestamp.from(now),id);
        return repository.receipt(id,false);
    }
    @Transactional
    public ReceiptResponse cancel(UUID id,CancelPayload p) {
        var r=locked(id,false);
        if(r.status()==ReceiptStatus.CANCELLED) return r;
        version(r,p.version());
        boolean reverse=r.status()==ReceiptStatus.CONFIRMED;
        if(reverse && (p.reason()==null || p.reason().isBlank())) throw new ApiException(HttpStatus.BAD_REQUEST,"CANCELLATION_REASON_REQUIRED","Cancellation of a confirmed receipt requires a reason");
        Instant now=Instant.now(); UUID actor=access.currentUserId();
        if(reverse) movements(r,r.lines(),true,actor,now);
        repository.jdbc().update("UPDATE stock_receipts SET status='CANCELLED',cancelled_by=?,cancelled_at=?,cancellation_reason=?,version=version+1 WHERE id=?",
            actor,Timestamp.from(now),p.reason(),id);
        return repository.receipt(id,false);
    }
    private void movements(ReceiptResponse r,List<LineResponse> lines,boolean reverse,UUID actor,Instant now) {
        Map<UUID,Long> balances=new LinkedHashMap<>();
        // Validate every balance and overflow before applying any movement.
        for(var line:lines) {
            var current=repository.jdbc().query("SELECT quantity FROM inventory_balances WHERE warehouse_id=? AND product_id=? FOR UPDATE",(rs,n)->rs.getLong(1),r.warehouseId(),line.productId());
            long old=current.isEmpty()?0:current.getFirst();
            if(reverse && old<line.quantity()) throw conflict("INSUFFICIENT_STOCK","Insufficient stock to reverse the entire receipt");
            balances.put(line.productId(),reverse?old-line.quantity():add(old,line.quantity()));
        }
        for(var line:lines) {
            int updated=repository.jdbc().update("UPDATE inventory_balances SET quantity=? WHERE warehouse_id=? AND product_id=?",balances.get(line.productId()),r.warehouseId(),line.productId());
            if(updated==0) repository.jdbc().update("INSERT INTO inventory_balances(warehouse_id,product_id,quantity) VALUES (?,?,?)",r.warehouseId(),line.productId(),balances.get(line.productId()));
            repository.jdbc().update("INSERT INTO inventory_movements(id,warehouse_id,product_id,quantity_change,type,receipt_id,performed_by,performed_at) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),r.warehouseId(),line.productId(),reverse?-line.quantity():line.quantity(),reverse?"RECEIPT_CANCEL":"RECEIPT_CONFIRM",r.id(),actor,Timestamp.from(now));
        }
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ReceiptResponse get(UUID id) {
        var r=repository.receipt(id,false); access.requireWarehouse(r.warehouseId()); return r;
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<ReceiptResponse> search(int page,int size,UUID warehouseId,ReceiptStatus status,LocalDate from,LocalDate to) {
        pagination(page,size); dates(from,to);
        String where=" WHERE 1=1"; List<Object> args=new ArrayList<>();
        if(!access.isAdmin()) { where+=" AND EXISTS (SELECT 1 FROM user_warehouses uw WHERE uw.warehouse_id=r.warehouse_id AND uw.user_id=?)"; args.add(access.currentUserId()); }
        if(warehouseId!=null) { warehouse(warehouseId,false,false); where+=" AND r.warehouse_id=?"; args.add(warehouseId); }
        if(status!=null) { where+=" AND r.status=?"; args.add(status.name()); }
        if(from!=null) { where+=" AND r.receipt_date>=?"; args.add(from); }
        if(to!=null) { where+=" AND r.receipt_date<=?"; args.add(to); }
        long count=repository.jdbc().queryForObject("SELECT count(*) FROM stock_receipts r"+where,Long.class,args.toArray());
        args.add(size);args.add(offset(page,size));
        var ids=repository.jdbc().query("SELECT r.id FROM stock_receipts r"+where+" ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?",(rs,n)->rs.getObject(1,UUID.class),args.toArray());
        return new PageResponse<>(ids.stream().map(id->repository.receipt(id,false)).toList(),count,page,size);
    }
    private <T extends Comparable<? super T>> void dates(T from,T to) {
        if(from!=null && to!=null && from.compareTo(to)>0) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DATE_RANGE","Invalid date range");
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<InventoryResponse> inventory(UUID warehouseId,int page,int size,UUID productId) {
        pagination(page,size); warehouse(warehouseId,false,false);
        String where=" FROM products p LEFT JOIN inventory_balances b ON b.product_id=p.id AND b.warehouse_id=? WHERE (p.status=1 OR EXISTS (SELECT 1 FROM inventory_movements m WHERE m.product_id=p.id AND m.warehouse_id=?))";
        List<Object> args=new ArrayList<>(List.of(warehouseId,warehouseId));
        if(productId!=null) { where+=" AND p.id=?";args.add(productId); }
        long count=repository.jdbc().queryForObject("SELECT count(*)"+where,Long.class,args.toArray());
        // warehouse_id must also be present for zero balance rows.
        List<Object> queryArgs=new ArrayList<>(); queryArgs.add(warehouseId); queryArgs.addAll(args); queryArgs.add(size);queryArgs.add(offset(page,size));
        var items=repository.jdbc().query("SELECT CAST(? AS UUID) warehouse_id,p.id product_id,p.code,p.name,p.unit,p.status,COALESCE(b.quantity,0) quantity"+where+" ORDER BY p.code,p.id LIMIT ? OFFSET ?",repository::inventory,queryArgs.toArray());
        return new PageResponse<>(items,count,page,size);
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<MovementResponse> history(UUID warehouseId,int page,int size,UUID productId,Instant from,Instant to) {
        pagination(page,size);dates(from,to);warehouse(warehouseId,false,false);
        String where=" WHERE m.warehouse_id=?";List<Object> args=new ArrayList<>(List.of(warehouseId));
        if(productId!=null) {where+=" AND m.product_id=?";args.add(productId);}
        if(from!=null) {where+=" AND m.performed_at>=?";args.add(Timestamp.from(from));}
        if(to!=null) {where+=" AND m.performed_at<?";args.add(Timestamp.from(to));}
        long count=repository.jdbc().queryForObject("SELECT count(*) FROM inventory_movements m"+where,Long.class,args.toArray());
        args.add(size);args.add(offset(page,size));
        var items=repository.jdbc().query("SELECT m.*,r.code receipt_code,l.product_code,l.product_name,l.unit FROM inventory_movements m JOIN stock_receipts r ON r.id=m.receipt_id JOIN stock_receipt_lines l ON l.receipt_id=m.receipt_id AND l.product_id=m.product_id"+where+" ORDER BY m.performed_at DESC,m.id DESC LIMIT ? OFFSET ?",repository::movement,args.toArray());
        return new PageResponse<>(items,count,page,size);
    }
}
