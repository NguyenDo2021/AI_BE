package com.frontendbase.api.productGroup.controller;

import java.util.*;
import jakarta.validation.constraints.*;
import com.frontendbase.api.productGroup.dto.*;
import com.frontendbase.api.productGroup.service.ProductGroupService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
@RestController @RequestMapping("/product-groups") @Validated @RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ProductGroupController {
    private final ProductGroupService service;
    @GetMapping @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('PRODUCT_GROUP_VIEW')")
    public ProductGroupPageResponse list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        return service.search(page, pageSize, keyword, status);
    }
    @GetMapping("/{id}") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('PRODUCT_GROUP_VIEW')")
    public ProductGroupResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('PRODUCT_GROUP_CREATE')")
    public ProductGroupResponse create(@Valid @RequestBody ProductGroupPayload payload) { return service.create(payload); }
    @PutMapping("/{id}") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('PRODUCT_GROUP_UPDATE')")
    public ProductGroupResponse update(@PathVariable UUID id, @Valid @RequestBody ProductGroupPayload payload) {
        return service.update(id, payload);
    }
}
