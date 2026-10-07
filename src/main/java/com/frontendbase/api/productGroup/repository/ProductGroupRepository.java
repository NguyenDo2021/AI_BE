package com.frontendbase.api.productGroup.repository;

import java.util.*;
import com.frontendbase.api.productGroup.entity.ProductGroup;
import org.springframework.data.jpa.repository.*;
public interface ProductGroupRepository extends JpaRepository<ProductGroup, UUID>, JpaSpecificationExecutor<ProductGroup> {
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
