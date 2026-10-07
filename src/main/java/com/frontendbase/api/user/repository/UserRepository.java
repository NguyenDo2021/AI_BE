package com.frontendbase.api.user.repository;

import com.frontendbase.api.user.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = { "roles", "roles.permissions" })
    Optional<UserAccount> findWithRolesAndPermissionsByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = { "roles", "roles.permissions" })
    Optional<UserAccount> findWithRolesAndPermissionsById(UUID id);

    @EntityGraph(attributePaths = { "roles" })
    Optional<UserAccount> findWithRolesById(UUID id);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRoles_Id(UUID roleId);

    Optional<UserAccount> findByEmailIgnoreCase(String email);
}
