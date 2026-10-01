package com.frontendbase.api.security.repository;

import com.frontendbase.api.security.entity.Permission;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
	boolean existsByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

	@Query("""
			select permission from Permission permission
			where (:keyword is null or lower(permission.name) like lower(concat(concat('%', cast(:keyword as string)), '%'))
				or lower(permission.code) like lower(concat(concat('%', cast(:keyword as string)), '%'))
				or lower(permission.description) like lower(concat(concat('%', cast(:keyword as string)), '%')))
				and (:name is null or lower(permission.name) like lower(concat(concat('%', cast(:name as string)), '%')))
				and (:code is null or lower(permission.code) like lower(concat(concat('%', cast(:code as string)), '%')))
				and (:status is null or permission.status = :status)
			""")
	Page<Permission> search(
			@Param("keyword") String keyword,
			@Param("name") String name,
			@Param("code") String code,
			@Param("status") Short status,
			Pageable pageable);
}
