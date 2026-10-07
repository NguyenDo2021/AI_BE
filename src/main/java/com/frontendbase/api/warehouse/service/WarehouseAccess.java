package com.frontendbase.api.warehouse.service;

import java.util.*;
import com.frontendbase.api.common.exception.ApiException;
import com.frontendbase.api.user.repository.UserRepository;
import com.frontendbase.api.warehouse.entity.*;
import com.frontendbase.api.warehouse.repository.UserWarehouseRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
@Component("warehouseAccess") @RequiredArgsConstructor
public class WarehouseAccess {
    private final UserRepository users;
    private final UserWarehouseRepository assignments;
    public UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof String id))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa đăng nhập");
        try { return UUID.fromString(id); }
        catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Phiên không hợp lệ"); }
    }
    @Transactional(readOnly = true)
    public boolean isAdmin() {
        var user = users.findWithRolesById(currentUserId())
                .filter(u -> u.getStatus() == 1)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Tài khoản không hoạt động"));
        return user.getRoles().stream().anyMatch(r -> r.getStatus() == 1 && "ADMIN".equalsIgnoreCase(r.getCode()));
    }
    public void requireWarehouse(UUID warehouseId) {
        if (!isAdmin() && !assignments.existsByUserIdAndWarehouseId(currentUserId(), warehouseId))
            throw new ApiException(HttpStatus.FORBIDDEN, "WAREHOUSE_ACCESS_DENIED", "Kho không thuộc phạm vi được giao");
    }
    public Specification<Warehouse> assignedTo(UUID userId) {
        return (root, query, cb) -> {
            var subquery = query.subquery(UUID.class);
            var assignment = subquery.from(UserWarehouse.class);
            subquery.select(assignment.get("warehouseId"));
            subquery.where(cb.equal(assignment.get("userId"), userId));
            return root.get("id").in(subquery);
        };
    }
    public Specification<Warehouse> visibleWarehouses() {
        return isAdmin() ? (root, query, cb) -> cb.conjunction() : assignedTo(currentUserId());
    }
}
