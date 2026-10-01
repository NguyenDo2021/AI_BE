package com.frontendbase.api.security.controller;

import com.frontendbase.api.security.dto.PermissionPageResponse;
import com.frontendbase.api.security.dto.PermissionPayload;
import com.frontendbase.api.security.dto.PermissionResponse;
import com.frontendbase.api.security.service.PermissionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
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
@RequestMapping("/permissions")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PermissionController {
    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_VIEW')")
    public PermissionPageResponse listPermissions(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer pageSize,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        int resolvedPageSize = size != null ? size : pageSize != null ? pageSize : 10;
        return permissionService.findPermissions(page, resolvedPageSize, keyword, name, code, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_VIEW')")
    public PermissionResponse getPermission(@PathVariable UUID id) {
        return permissionService.getPermission(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionResponse createPermission(@Valid @RequestBody PermissionPayload payload) {
        return permissionService.createPermission(payload);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_UPDATE')")
    public PermissionResponse updatePermission(@PathVariable UUID id, @Valid @RequestBody PermissionPayload payload) {
        return permissionService.updatePermission(id, payload);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermission(@PathVariable UUID id) {
        permissionService.deletePermission(id);
    }
}