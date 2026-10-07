package com.frontendbase.api.warehouse.service;

import java.util.*;
import com.frontendbase.api.warehouse.entity.Warehouse;
import org.springframework.data.jpa.domain.Specification;
public final class WarehouseSpecifications {
    private WarehouseSpecifications() {}
    public static Specification<Warehouse> matches(String keyword, Integer status) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                predicate = cb.and(predicate, cb.or(cb.like(cb.lower(root.get("code")), pattern, '!'),
                        cb.like(cb.lower(root.get("name")), pattern, '!')));
            }
            if (status != null) predicate = cb.and(predicate, cb.equal(root.get("status"), status.shortValue()));
            return predicate;
        };
    }
}
