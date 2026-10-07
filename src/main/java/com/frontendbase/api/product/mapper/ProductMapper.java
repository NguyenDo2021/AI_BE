package com.frontendbase.api.product.mapper;

import java.util.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import com.frontendbase.api.product.entity.Product;
import com.frontendbase.api.product.dto.*;
@Component
public class ProductMapper {
    public void apply(ProductPayload p, Product e) {
        e.setCode(p.code().trim().toUpperCase(Locale.ROOT));
        e.setName(p.name().trim());
        e.setGroupId(p.groupId());
        e.setMaterial(p.material() == null || p.material().isBlank() ? null : p.material().trim());
        e.setColor(p.color() == null || p.color().isBlank() ? null : p.color().trim());
        e.setDimensions(p.dimensions() == null || p.dimensions().isBlank() ? null : p.dimensions().trim());
        e.setLengthMeters(p.lengthMeters());
        e.setUnit("cay");
        e.setReferencePurchasePrice(p.referencePurchasePrice() == null ? BigDecimal.ZERO : p.referencePurchasePrice());
        e.setDefaultSalePrice(p.defaultSalePrice() == null ? BigDecimal.ZERO : p.defaultSalePrice());
        e.setLowStockThreshold(p.lowStockThreshold() == null ? 0 : p.lowStockThreshold());
        e.setDescription(p.description() == null || p.description().isBlank() ? null : p.description().trim());
        e.setStatus(p.status().shortValue());
    }
    public ProductResponse toResponse(Product e) {
        return new ProductResponse(e.getId(), e.getCode(), e.getName(), e.getGroupId(), e.getMaterial(), e.getColor(), e.getDimensions(), e.getLengthMeters(), e.getUnit(), e.getReferencePurchasePrice(), e.getDefaultSalePrice(), e.getLowStockThreshold(), e.getDescription(), (int) e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
