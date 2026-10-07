package com.frontendbase.api.warehouse.repository;

import java.util.*;
import com.frontendbase.api.warehouse.entity.Warehouse;
import org.springframework.data.jpa.repository.*;
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID>, JpaSpecificationExecutor<Warehouse> {
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
