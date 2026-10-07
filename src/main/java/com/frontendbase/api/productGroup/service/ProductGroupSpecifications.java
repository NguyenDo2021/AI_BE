package com.frontendbase.api.productGroup.service;

import java.util.*;
import com.frontendbase.api.productGroup.entity.ProductGroup;
import org.springframework.data.jpa.domain.Specification;
public final class ProductGroupSpecifications {
    private ProductGroupSpecifications() {}
    public static Specification<ProductGroup> matches(String keyword, Integer status) {
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
