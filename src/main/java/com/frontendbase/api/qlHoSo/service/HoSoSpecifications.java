package com.frontendbase.api.qlHoSo.service;

import com.frontendbase.api.qlHoSo.entity.HoSo;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class HoSoSpecifications {
    private HoSoSpecifications() {
    }

    public static Specification<HoSo> matches(String keyword, Integer status) {
        return (root, query, builder) -> {
            var predicates = builder.conjunction();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates = builder.and(predicates, builder.or(
                        builder.like(builder.lower(root.get("username")), pattern),
                        builder.like(builder.lower(root.get("fullName")), pattern),
                        builder.like(builder.lower(root.get("email")), pattern)));
            }
            if (status != null) {
                predicates = builder.and(predicates, builder.equal(root.get("status"), status.shortValue()));
            }
            return predicates;
        };
    }
}
