package com.frontendbase.api.report.service;

import java.util.*;
import com.frontendbase.api.warehouse.service.WarehouseAccess;
import com.frontendbase.api.stock.service.StockOperations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component("reportAccess")
@RequiredArgsConstructor
public class ReportAccess {
    private final WarehouseAccess access;
    private final StockOperations operations;

    // Same JWT authorities as catalog/stock/sales/stocktake APIs; ADMIN is checked
    // live.
    public boolean allowed(String code) {
        if (access.isAdmin())
            return true;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> code.equals(a.getAuthority()));
    }

    public Scope scope(UUID warehouseId) {
        if (warehouseId != null)
            operations.warehouse(warehouseId, false, false);
        return new Scope(access.isAdmin() ? null : access.currentUserId(), warehouseId);
    }

    public record Scope(UUID userId, UUID warehouseId) {
        public String where(String warehouseColumn, List<Object> args) {
            String result = "";
            if (userId != null) {
                result += " AND EXISTS (SELECT 1 FROM user_warehouses uw WHERE uw.warehouse_id="
                        + warehouseColumn + " AND uw.user_id=?)";
                args.add(userId);
            }
            if (warehouseId != null) {
                result += " AND " + warehouseColumn + "=?";
                args.add(warehouseId);
            }
            return result;
        }
    }
}
