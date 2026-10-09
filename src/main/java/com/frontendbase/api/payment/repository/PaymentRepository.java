package com.frontendbase.api.payment.repository;

import java.sql.*;
import java.time.*;
import java.util.*;
import com.frontendbase.api.payment.dto.PaymentDtos.*;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PaymentRepository {
    private final JdbcTemplate jdbc;

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    private Instant instant(ResultSet r, String key) throws SQLException {
        var t = r.getTimestamp(key);
        return t == null ? null : t.toInstant();
    }

    public PaymentResponse map(ResultSet r, int n) throws SQLException {
        return new PaymentResponse(r.getObject("id", UUID.class), r.getString("code"),
                r.getObject("sales_order_id", UUID.class), r.getObject("warehouse_id", UUID.class),
                r.getObject("customer_id", UUID.class), r.getLong("amount"),
                r.getObject("payment_date", LocalDate.class),
                Method.valueOf(r.getString("method")), r.getString("reference"), r.getString("note"),
                Status.valueOf(r.getString("status")), r.getObject("created_by", UUID.class), instant(r, "created_at"),
                r.getObject("cancelled_by", UUID.class), instant(r, "cancelled_at"),
                r.getString("cancellation_reason"));
    }

    public PaymentResponse payment(UUID id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM payments WHERE id=?" + (lock ? " FOR UPDATE" : ""), this::map, id);
        if (rows.isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found");
        return rows.getFirst();
    }

    public Optional<PaymentResponse> byKey(UUID actor, UUID key) {
        return jdbc.query("SELECT * FROM payments WHERE created_by=? AND idempotency_key=?", this::map, actor, key)
                .stream().findFirst();
    }

    public long exact(java.math.BigDecimal value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            throw new ApiException(HttpStatus.CONFLICT, "NUMERIC_OVERFLOW", "Amount limit exceeded");
        }
    }
}
