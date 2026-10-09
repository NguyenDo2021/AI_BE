package com.frontendbase.api.customer.repository;

import java.util.*;
import java.sql.*;
import com.frontendbase.api.customer.dto.CustomerDtos.*;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CustomerRepository {
    private final JdbcTemplate jdbc;

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public CustomerResponse customer(UUID id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM customers WHERE id=?" + (lock ? " FOR UPDATE" : ""), this::map, id);
        if (rows.isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found");
        return rows.getFirst();
    }

    public CustomerResponse map(ResultSet r, int n) throws SQLException {
        return new CustomerResponse(r.getObject("id", UUID.class), r.getObject("warehouse_id", UUID.class),
                r.getString("code"),
                r.getString("name"), r.getString("phone"), r.getString("address"), r.getString("note"),
                r.getInt("status"),
                r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    }
}
