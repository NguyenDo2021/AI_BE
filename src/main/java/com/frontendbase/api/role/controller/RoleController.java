package com.frontendbase.api.role.controller;

import com.frontendbase.api.role.dto.RoleResponse;
import com.frontendbase.api.role.dto.RolePageResponse;
import com.frontendbase.api.role.dto.RolePayload;
import com.frontendbase.api.role.dto.RolePermissionsPayload;
import com.frontendbase.api.role.service.RoleService;
import com.frontendbase.api.security.dto.PermissionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import java.util.List;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/roles")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class RoleController {
    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public RolePageResponse listRoles(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer pageSize,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        int resolvedPageSize = size != null ? size : pageSize != null ? pageSize : 10;
        return roleService.findRoles(page, resolvedPageSize, keyword, name, code, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public RoleResponse getRole(@PathVariable UUID id) {
        return roleService.getRole(id);
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public List<PermissionResponse> getRolePermissions(@PathVariable UUID id) {
        return roleService.getRolePermissions(id);
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    public List<PermissionResponse> updateRolePermissions(
            @PathVariable UUID id,
            @Valid @RequestBody RolePermissionsPayload payload) {
        return roleService.updateRolePermissions(id, payload.permissionIds());
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRolePermission(@PathVariable UUID roleId, @PathVariable UUID permissionId) {
        roleService.removeRolePermission(roleId, permissionId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse createRole(@Valid @RequestBody RolePayload payload) {
        return roleService.createRole(payload);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    public RoleResponse updateRole(@PathVariable UUID id, @Valid @RequestBody RolePayload payload) {
        return roleService.updateRole(id, payload);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
    }
}
