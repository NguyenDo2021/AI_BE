package com.frontendbase.api.warehouse.entity;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class UserWarehouseId implements Serializable {
    private UUID userId;
    private UUID warehouseId;
}
