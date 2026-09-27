package com.frontendbase.api.role.repository;

import com.frontendbase.api.role.entity.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findAllByOrderByNameAsc();
}
