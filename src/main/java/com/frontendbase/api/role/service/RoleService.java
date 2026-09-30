package com.frontendbase.api.role.service;

import com.frontendbase.api.role.dto.RoleResponse;
import com.frontendbase.api.role.dto.RolePageResponse;
import com.frontendbase.api.role.dto.RolePayload;
import com.frontendbase.api.role.entity.Role;
import com.frontendbase.api.role.repository.RoleRepository;
import com.frontendbase.api.user.repository.UserRepository;
import com.frontendbase.api.common.exception.ApiException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
    private static final int MAX_PAGE_SIZE = 100;

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public RoleService(RoleRepository roleRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public RolePageResponse findRoles(int page, int pageSize, String keyword, String name, String code,
            Integer status) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "Số trang hoặc kích thước trang không hợp lệ");
        }
        Page<Role> result = roleRepository.search(
                normalizeFilter(keyword),
                normalizeFilter(name),
                normalizeFilter(code),
                status == null ? null : status.shortValue(),
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "name")));
        return new RolePageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                page,
                pageSize);
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(UUID id) {
        return toResponse(findRole(id));
    }

    @Transactional
    public RoleResponse createRole(RolePayload payload) {
        String name = payload.name().trim();
        String code = normalizeCode(payload.code());
        ensureUnique(name, code, null);

        Role role = new Role();
        role.setId(UUID.randomUUID());
        applyPayload(role, payload, name, code);
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse updateRole(UUID id, RolePayload payload) {
        Role role = findRole(id);
        String name = payload.name().trim();
        String code = normalizeCode(payload.code());
        if ("ADMIN".equalsIgnoreCase(role.getCode()) && !"ADMIN".equals(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "SYSTEM_ROLE_PROTECTED", "Không thể đổi mã vai trò ADMIN");
        }
        ensureUnique(name, code, id);

        applyPayload(role, payload, name, code);
        role.setUpdatedAt(Instant.now());
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = findRole(id);
        if ("ADMIN".equalsIgnoreCase(role.getCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "SYSTEM_ROLE_PROTECTED", "Không thể xóa vai trò ADMIN");
        }
        if (userRepository.existsByRoles_Id(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_IN_USE", "Không thể xóa vai trò đang được sử dụng.");
        }
        roleRepository.delete(role);
    }

    private Role findRole(UUID id) {
        return roleRepository.findById(id).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "Không tìm thấy vai trò"));
    }

    private void ensureUnique(String name, String code, UUID currentId) {
        boolean duplicateName = currentId == null
                ? roleRepository.existsByNameIgnoreCase(name)
                : roleRepository.existsByNameIgnoreCaseAndIdNot(name, currentId);
        if (duplicateName) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_NAME_EXISTS", "Tên vai trò đã tồn tại");
        }
        boolean duplicateCode = currentId == null
                ? roleRepository.existsByCodeIgnoreCase(code)
                : roleRepository.existsByCodeIgnoreCaseAndIdNot(code, currentId);
        if (duplicateCode) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_CODE_EXISTS", "Mã vai trò đã tồn tại");
        }
    }

    private void applyPayload(Role role, RolePayload payload, String name, String code) {
        role.setName(name);
        role.setCode(code);
        String description = payload.description() == null ? null : payload.description().trim();
        role.setDescription(description == null || description.isEmpty() ? null : description);
        role.setStatus(payload.status().shortValue());
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getCode(), role.getDescription(),
                role.getStatus(), role.getCreatedAt(), role.getUpdatedAt());
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
