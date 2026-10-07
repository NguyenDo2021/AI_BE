package com.frontendbase.api.warehouse.entity;

import java.util.*;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
@Entity
@Table(name = "warehouses")
@Getter @Setter @NoArgsConstructor
public class Warehouse {
    @Id private UUID id;
    @Column(nullable = false, length = 80, unique = true)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 500)
    private String address;
    @Column(length = 20)
    private String phone;
    @Column(length = 2000)
    private String note;
    @Column(nullable = false, columnDefinition = "smallint")
    private short status;
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant updatedAt;
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
}
