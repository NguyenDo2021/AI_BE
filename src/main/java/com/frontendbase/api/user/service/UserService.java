package com.frontendbase.api.user.service;

import com.frontendbase.api.common.exception.ApiException;
import com.frontendbase.api.role.dto.RoleResponse;
import com.frontendbase.api.role.entity.Role;
import com.frontendbase.api.role.repository.RoleRepository;
import com.frontendbase.api.user.dto.UserPageResponse;
import com.frontendbase.api.user.dto.UserPayload;
import com.frontendbase.api.user.dto.UserResponse;
import com.frontendbase.api.user.dto.UserRolesPayload;
import com.frontendbase.api.user.entity.UserAccount;
import com.frontendbase.api.user.mapper.UserMapper;
import com.frontendbase.api.user.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserPageResponse findUsers(int page, int pageSize, String keyword, Integer status) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "Số trang hoặc kích thước trang không hợp lệ");
        }
        Page<UserAccount> result = userRepository.findAll(
                UserSpecifications.matches(keyword, status),
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        var users = result.getContent();
        Map<UUID, String> updaterNames = resolveUpdaterNames(users);
        return new UserPageResponse(
                users.stream()
                        .map(user -> userMapper.toResponse(user,
                                user.getUpdatedBy() != null ? updaterNames.get(user.getUpdatedBy()) : null))
                        .toList(),
                result.getTotalElements(),
                page,
                pageSize);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return toResponse(findUser(id));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getUserRoles(UUID id) {
        return toRoleResponses(findUserWithRoles(id).getRoles());
    }

    @Transactional
    public List<RoleResponse> updateUserRoles(UUID id, UserRolesPayload payload) {
        UserAccount user = findUserWithRoles(id);
        List<UUID> requestedRoleIds = payload.roleIds();
        Set<UUID> requestedRoleIdSet = new HashSet<>(requestedRoleIds);
        if (requestedRoleIdSet.size() != requestedRoleIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_ROLE_ID", "Danh sách role có ID bị trùng");
        }

        List<Role> requestedRoles = roleRepository.findAllById(requestedRoleIds);
        Set<UUID> foundRoleIds = requestedRoles.stream().map(Role::getId).collect(Collectors.toSet());
        requestedRoleIds.stream()
                .filter(roleId -> !foundRoleIds.contains(roleId))
                .findFirst()
                .ifPresent(roleId -> {
                    throw new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "Không tìm thấy vai trò");
                });

        Set<UUID> currentRoleIds = user.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
        requestedRoles.stream()
                .filter(role -> role.getStatus() != 1 && !currentRoleIds.contains(role.getId()))
                .findFirst()
                .ifPresent(role -> {
                    throw new ApiException(HttpStatus.CONFLICT, "ROLE_INACTIVE",
                            "Không thể gán vai trò đã bị vô hiệu hóa");
                });
        ensureAdminRoleRetained(user, requestedRoleIdSet);

        user.setRoles(new HashSet<>(requestedRoles));
        auditRoleUpdate(user);
        return toRoleResponses(user.getRoles());
    }

    @Transactional
    public void removeUserRole(UUID id, UUID roleId) {
        UserAccount user = findUserWithRoles(id);
        Role role = roleRepository.findById(roleId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "Không tìm thấy vai trò"));
        boolean assigned = user.getRoles().stream().anyMatch(assignedRole -> assignedRole.getId().equals(roleId));
        if (!assigned) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_ROLE_NOT_FOUND",
                    "Người dùng chưa được gán vai trò này");
        }
        if ("ADMIN".equalsIgnoreCase(role.getCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "SYSTEM_ROLE_ASSIGNMENT_PROTECTED",
                    "Không thể gỡ vai trò ADMIN khỏi người dùng");
        }
        user.getRoles().removeIf(assignedRole -> assignedRole.getId().equals(roleId));
        auditRoleUpdate(user);
    }

    @Transactional
    public UserResponse createUser(UserPayload payload) {
        ensureUnique(payload, null);
        UserAccount user = userMapper.toNewEntity(payload);
        user.setPasswordHash(passwordEncoder.encode("Abc@12345"));
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(UUID id, UserPayload payload) {
        UserAccount user = findUser(id);
        ensureUnique(payload, id);
        userMapper.updateEntity(payload, user);
        user.setUpdatedBy(currentUserId());
        user.setUpdatedAt(Instant.now());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(UUID id) {
        UserAccount user = findUser(id);
        userRepository.delete(user);
    }

    private UserAccount findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }

    private UserAccount findUserWithRoles(UUID id) {
        return userRepository.findWithRolesById(id).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }

    private void ensureAdminRoleRetained(UserAccount user, Set<UUID> requestedRoleIds) {
        boolean removesAdmin = user.getRoles().stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getCode())
                        && !requestedRoleIds.contains(role.getId()));
        if (removesAdmin) {
            throw new ApiException(HttpStatus.CONFLICT, "SYSTEM_ROLE_ASSIGNMENT_PROTECTED",
                    "Không thể gỡ vai trò ADMIN khỏi người dùng");
        }
    }

    private void auditRoleUpdate(UserAccount user) {
        user.setUpdatedBy(currentUserId());
        user.setUpdatedAt(Instant.now());
    }

    private List<RoleResponse> toRoleResponses(Set<Role> roles) {
        return roles.stream()
                .sorted(Comparator.comparing(Role::getName, String.CASE_INSENSITIVE_ORDER))
                .map(role -> new RoleResponse(role.getId(), role.getName(), role.getCode(), role.getDescription(),
                        role.getStatus(), role.getCreatedAt(), role.getUpdatedAt()))
                .toList();
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String userId)) {
            return null;
        }
        return UUID.fromString(userId);
    }

    private UserResponse toResponse(UserAccount user) {
        String updaterName = user.getUpdatedBy() == null
                ? null
                : userRepository.findById(user.getUpdatedBy()).map(UserAccount::getFullName).orElse(null);
        return userMapper.toResponse(user, updaterName);
    }

    private Map<UUID, String> resolveUpdaterNames(java.util.List<UserAccount> users) {
        Set<UUID> updaterIds = users.stream()
                .map(UserAccount::getUpdatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (updaterIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(updaterIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName, (first, ignored) -> first));
    }

    private void ensureUnique(UserPayload payload, UUID currentId) {
        boolean duplicateUsername = userRepository.existsByUsernameIgnoreCase(payload.username().trim())
                && (currentId == null || userRepository.findByUsernameIgnoreCase(payload.username().trim())
                        .map(user -> !user.getId().equals(currentId)).orElse(false));
        if (duplicateUsername) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "Tên đăng nhập đã tồn tại");
        }
        boolean duplicateEmail = userRepository.existsByEmailIgnoreCase(payload.email().trim())
                && (currentId == null || userRepository.findByEmailIgnoreCase(payload.email().trim())
                        .map(user -> !user.getId().equals(currentId)).orElse(false));
        if (duplicateEmail) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email đã được sử dụng");
        }
    }
}
