package com.frontendbase.api.security.service;

import com.frontendbase.api.common.exception.ApiException;
import com.frontendbase.api.security.dto.PermissionPageResponse;
import com.frontendbase.api.security.dto.PermissionPayload;
import com.frontendbase.api.security.dto.PermissionResponse;
import com.frontendbase.api.security.entity.Permission;
import com.frontendbase.api.security.repository.PermissionRepository;
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
public class PermissionService {
    private static final int MAX_PAGE_SIZE = 100;

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public PermissionPageResponse findPermissions(
            int page, int pageSize, String keyword, String name, String code, Integer status) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "Số trang hoặc kích thước trang không hợp lệ");
        }
        Page<Permission> result = permissionRepository.search(
                normalizeFilter(keyword),
                normalizeFilter(name),
                normalizeFilter(code),
                status == null ? null : status.shortValue(),
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "code")));
        return new PermissionPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                page,
                pageSize);
    }

    @Transactional(readOnly = true)
    public PermissionResponse getPermission(UUID id) {
        return toResponse(findPermission(id));
    }

    @Transactional
    public PermissionResponse createPermission(PermissionPayload payload) {
        String code = normalizeCode(payload.code());
        ensureUniqueCode(code, null);
        Permission permission = new Permission();
        permission.setId(UUID.randomUUID());
        applyPayload(permission, payload, code);
        return toResponse(permissionRepository.save(permission));
    }

    @Transactional
    public PermissionResponse updatePermission(UUID id, PermissionPayload payload) {
        Permission permission = findPermission(id);
        String code = normalizeCode(payload.code());
        ensureUniqueCode(code, id);
        applyPayload(permission, payload, code);
        permission.setUpdatedAt(Instant.now());
        return toResponse(permissionRepository.save(permission));
    }

    @Transactional
    public void deletePermission(UUID id) {
        permissionRepository.delete(findPermission(id));
    }

    private Permission findPermission(UUID id) {
        return permissionRepository.findById(id).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "PERMISSION_NOT_FOUND", "Không tìm thấy quyền"));
    }

    private void ensureUniqueCode(String code, UUID currentId) {
        boolean duplicate = currentId == null
                ? permissionRepository.existsByCodeIgnoreCase(code)
                : permissionRepository.existsByCodeIgnoreCaseAndIdNot(code, currentId);
        if (duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, "PERMISSION_CODE_EXISTS", "Mã quyền đã tồn tại");
        }
    }

    private void applyPayload(Permission permission, PermissionPayload payload, String code) {
        permission.setName(payload.name().trim());
        permission.setCode(code);
        permission.setDescription(payload.description().trim());
        permission.setStatus(payload.status().shortValue());
    }

    private PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getName(),
                permission.getCode(),
                permission.getDescription(),
                permission.getStatus(),
                permission.getCreatedAt(),
                permission.getUpdatedAt());
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}