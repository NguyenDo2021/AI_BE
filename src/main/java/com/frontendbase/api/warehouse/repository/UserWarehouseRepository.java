package com.frontendbase.api.warehouse.repository;

import java.util.*;
import com.frontendbase.api.warehouse.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserWarehouseRepository extends JpaRepository<UserWarehouse, UserWarehouseId> {
    boolean existsByUserIdAndWarehouseId(UUID userId, UUID warehouseId);
}
