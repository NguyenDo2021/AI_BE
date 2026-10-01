package com.frontendbase.api.user.controller;

import com.frontendbase.api.role.dto.RoleResponse;
import com.frontendbase.api.user.dto.UserPageResponse;
import com.frontendbase.api.user.dto.UserPayload;
import com.frontendbase.api.user.dto.UserResponse;
import com.frontendbase.api.user.dto.UserRolesPayload;
import com.frontendbase.api.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/users")
@Validated
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public UserPageResponse listUsers(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        return userService.findUsers(page, pageSize, keyword, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public UserResponse getUser(@PathVariable UUID id) {
        return userService.getUser(id);
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public List<RoleResponse> getUserRoles(@PathVariable UUID id) {
        return userService.getUserRoles(id);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public List<RoleResponse> updateUserRoles(
            @PathVariable UUID id,
            @Valid @RequestBody UserRolesPayload payload) {
        return userService.updateUserRoles(id, payload);
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUserRole(@PathVariable UUID id, @PathVariable UUID roleId) {
        userService.removeUserRole(id, roleId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserPayload payload) {
        return userService.createUser(payload);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public UserResponse updateUser(@PathVariable UUID id, @Valid @RequestBody UserPayload payload) {
        return userService.updateUser(id, payload);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
    }
}
