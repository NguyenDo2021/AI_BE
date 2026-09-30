package com.frontendbase.api.role.repository;

import com.frontendbase.api.role.entity.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query("""
            select role from Role role
            where (:keyword is null or lower(role.name) like lower(concat(concat('%', :keyword), '%')))
                and (:name is null or lower(role.name) like lower(concat(concat('%', :name), '%')))
                and (:code is null or lower(role.code) like lower(concat(concat('%', :code), '%')))
                and (:status is null or role.status = :status)
            """)
    Page<Role> search(
            @Param("keyword") String keyword,
            @Param("name") String name,
            @Param("code") String code,
            @Param("status") Short status,
            Pageable pageable);
}
