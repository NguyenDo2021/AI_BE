package com.frontendbase.api.product.entity;

import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor
public class Product {
    @Id private UUID id;
    @Column(nullable = false, length = 80, unique = true)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false)
    private UUID groupId;
    @Column(length = 100)
    private String material;
    @Column(length = 100)
    private String color;
    @Column(length = 255)
    private String dimensions;
    @Column(precision = 12, scale = 3)
    private BigDecimal lengthMeters;
    @Column(nullable = false)
    private String unit;
    @Column(precision = 19, scale = 0, nullable = false)
    private BigDecimal referencePurchasePrice;
    @Column(precision = 19, scale = 0, nullable = false)
    private BigDecimal defaultSalePrice;
    @Column(nullable = false)
    private Integer lowStockThreshold;
    @Column(length = 2000)
    private String description;
    @Column(nullable = false, columnDefinition = "smallint")
    private short status;
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant updatedAt;
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
}
