package com.frontendbase.api.productGroup.mapper;

import java.util.*;
import org.springframework.stereotype.Component;
import com.frontendbase.api.productGroup.entity.ProductGroup;
import com.frontendbase.api.productGroup.dto.*;
@Component
public class ProductGroupMapper {
    public void apply(ProductGroupPayload p, ProductGroup e) {
        e.setCode(p.code().trim().toUpperCase(Locale.ROOT));
        e.setName(p.name().trim());
        e.setDescription(p.description() == null || p.description().isBlank() ? null : p.description().trim());
        e.setStatus(p.status().shortValue());
    }
    public ProductGroupResponse toResponse(ProductGroup e) {
        return new ProductGroupResponse(e.getId(), e.getCode(), e.getName(), e.getDescription(), (int) e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
