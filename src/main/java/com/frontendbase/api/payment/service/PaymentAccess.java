package com.frontendbase.api.payment.service;

import com.frontendbase.api.warehouse.service.WarehouseAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

/** Consult live RBAC on every request, including idempotency retries. */
@Component("paymentAccess")
@RequiredArgsConstructor
public class PaymentAccess {
    private final WarehouseAccess access;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public boolean allowed(String code) {
        if (access.isAdmin())
            return true;
        return jdbc.queryForObject(
                "SELECT count(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id " +
                        "JOIN role_permissions rp ON rp.role_id=r.id JOIN permissions p ON p.id=rp.permission_id " +
                        "WHERE ur.user_id=? AND r.status=1 AND p.status=1 AND p.code=?",
                Long.class, access.currentUserId(), code) > 0;
    }
}
