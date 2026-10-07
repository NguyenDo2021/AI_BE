package com.frontendbase.api.product.repository;

import java.util.*;
import com.frontendbase.api.product.entity.Product;
import org.springframework.data.jpa.repository.*;
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
