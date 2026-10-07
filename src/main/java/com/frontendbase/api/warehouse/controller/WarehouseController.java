package com.frontendbase.api.warehouse.controller;

import java.util.*;
import jakarta.validation.constraints.*;
import com.frontendbase.api.warehouse.dto.*;
import com.frontendbase.api.warehouse.service.WarehouseService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
@RestController @RequestMapping("/warehouses") @Validated @RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class WarehouseController {
    private final WarehouseService service;
    @GetMapping @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('WAREHOUSE_VIEW')")
    public WarehousePageResponse list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        return service.search(page, pageSize, keyword, status);
    }
    @GetMapping("/{id}") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('WAREHOUSE_VIEW')")
    public WarehouseResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('WAREHOUSE_CREATE')")
    public WarehouseResponse create(@Valid @RequestBody WarehousePayload payload) { return service.create(payload); }
    @PutMapping("/{id}") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('WAREHOUSE_UPDATE')")
    public WarehouseResponse update(@PathVariable UUID id, @Valid @RequestBody WarehousePayload payload) {
        return service.update(id, payload);
    }
}
