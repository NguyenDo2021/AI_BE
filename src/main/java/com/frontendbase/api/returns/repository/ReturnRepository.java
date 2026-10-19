package com.frontendbase.api.returns.repository;
import java.sql.*;
import java.time.*;
import java.util.*;
import com.frontendbase.api.returns.dto.ReturnDtos.*;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository @RequiredArgsConstructor
public class ReturnRepository {
    private final JdbcTemplate jdbc;
    public JdbcTemplate jdbc() { return jdbc; }
    private Instant instant(ResultSet r,String k) throws SQLException {
        var t=r.getTimestamp(k); return t==null?null:t.toInstant();
    }
    public List<LineResponse> lines(UUID id) {
        return jdbc.query("SELECT * FROM sales_return_lines WHERE sales_return_id=? ORDER BY product_id",
            (r,n)->new LineResponse(r.getObject("product_id",UUID.class),r.getLong("quantity"),r.getLong("return_amount"),
                r.getLong("sold_quantity"),r.getLong("allocated_discount_amount"),r.getLong("net_line_amount"),
                r.getString("product_code"),r.getString("product_name"),r.getString("unit")),id)
            .stream().sorted(Comparator.comparing(LineResponse::productId)).toList();
    }
    public ReturnResponse get(UUID id,boolean lock) {
        var rows=jdbc.query("SELECT * FROM sales_returns WHERE id=?"+(lock?" FOR UPDATE":""),
            (r,n)->new ReturnResponse(id,r.getString("code"),r.getObject("sales_order_id",UUID.class),
                r.getObject("warehouse_id",UUID.class),r.getObject("customer_id",UUID.class),
                r.getObject("return_date",LocalDate.class),r.getString("reason"),r.getString("note"),
                Status.valueOf(r.getString("status")),r.getLong("total_amount"),r.getLong("version"),
                r.getObject("created_by",UUID.class),instant(r,"created_at"),
                r.getObject("updated_by",UUID.class),instant(r,"updated_at"),
                r.getObject("confirmed_by",UUID.class),instant(r,"confirmed_at"),
                r.getObject("cancelled_by",UUID.class),instant(r,"cancelled_at"),r.getString("cancellation_reason"),lines(id)),id);
        if(rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"SALES_RETURN_NOT_FOUND","Sales return not found");
        return rows.getFirst();
    }
    public Map<UUID,Long> returned(UUID order) {
        Map<UUID,Long> result=new HashMap<>();
        jdbc.query("SELECT l.product_id,SUM(CAST(l.quantity AS NUMERIC)) qty FROM sales_return_lines l JOIN sales_returns r ON r.id=l.sales_return_id WHERE r.sales_order_id=? AND r.status='CONFIRMED' GROUP BY l.product_id",
            (org.springframework.jdbc.core.RowCallbackHandler)r->result.put(r.getObject(1,UUID.class),r.getBigDecimal(2).longValueExact()),order);
        return result;
    }
    public void saveLines(UUID id,List<LineResponse> lines) {
        jdbc.update("DELETE FROM sales_return_lines WHERE sales_return_id=?",id);
        for(var l:lines) jdbc.update("INSERT INTO sales_return_lines(sales_return_id,product_id,quantity,return_amount,sold_quantity,allocated_discount_amount,net_line_amount,product_code,product_name,unit) VALUES (?,?,?,?,?,?,?,?,?,?)",
            id,l.productId(),l.quantity(),l.returnAmount(),l.soldQuantity(),l.allocatedDiscountAmount(),l.netLineAmount(),l.productCode(),l.productName(),l.unit());
    }
}
