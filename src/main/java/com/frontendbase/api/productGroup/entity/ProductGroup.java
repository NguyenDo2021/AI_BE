package com.frontendbase.api.productGroup.entity;

import java.util.*;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
@Entity
@Table(name = "product_groups")
@Getter @Setter @NoArgsConstructor
public class ProductGroup {
    @Id private UUID id;
    @Column(nullable = false, length = 80, unique = true)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 2000)
    private String description;
    @Column(nullable = false, columnDefinition = "smallint")
    private short status;
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant updatedAt;
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
}
