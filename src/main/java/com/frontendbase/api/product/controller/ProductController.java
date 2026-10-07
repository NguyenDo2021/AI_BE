package com.frontendbase.api.product.controller;

import java.util.*;
import jakarta.validation.constraints.*;
import com.frontendbase.api.product.dto.*;
import com.frontendbase.api.product.service.ProductService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
@RestController @RequestMapping("/products") @Validated @RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ProductController {
    private final ProductService service;
    @GetMapping @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('PRODUCT_VIEW')")
    public ProductPageResponse list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status,
            @RequestParam(required = false) UUID groupId) {
        return service.search(page, pageSize, keyword, status, groupId);
    }
    @GetMapping("/{id}") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('PRODUCT_VIEW')")
    public ProductResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('PRODUCT_CREATE')")
    public ProductResponse create(@Valid @RequestBody ProductPayload payload) { return service.create(payload); }
    @PutMapping("/{id}") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('PRODUCT_UPDATE')")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductPayload payload) {
        return service.update(id, payload);
    }
}
