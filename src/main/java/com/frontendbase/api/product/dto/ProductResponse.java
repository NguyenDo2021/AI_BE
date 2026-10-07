package com.frontendbase.api.product.dto;

import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public record ProductResponse(UUID id, String code, String name, UUID groupId, String material, String color, String dimensions, BigDecimal lengthMeters, String unit, BigDecimal referencePurchasePrice, BigDecimal defaultSalePrice, Integer lowStockThreshold, String description, Integer status, Instant createdAt, Instant updatedAt) {}
