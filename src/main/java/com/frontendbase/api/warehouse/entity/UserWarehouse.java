package com.frontendbase.api.warehouse.entity;

import java.util.*;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "user_warehouses") @IdClass(UserWarehouseId.class)
@Getter @Setter @NoArgsConstructor
public class UserWarehouse {
    @Id private UUID userId;
    @Id private UUID warehouseId;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    private Instant updatedAt;
    public UserWarehouse(UUID userId, UUID warehouseId) { this.userId = userId; this.warehouseId = warehouseId; }
}
