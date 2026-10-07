package com.frontendbase.api.warehouse.controller;

import java.util.*;
import com.frontendbase.api.warehouse.dto.WarehouseResponse;
import com.frontendbase.api.warehouse.service.UserWarehouseService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
@RestController @RequestMapping("/users") @RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserWarehouseController {
    private final UserWarehouseService service;
    @GetMapping("/me/warehouses")
    public List<WarehouseResponse> mine() { return service.mine(); }
    @GetMapping("/{id}/warehouses")
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('USER_WAREHOUSE_VIEW')")
    public List<WarehouseResponse> list(@PathVariable UUID id) { return service.list(id); }
    @PutMapping("/{id}/warehouses/{warehouseId}")
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('USER_WAREHOUSE_ASSIGN')")
    public WarehouseResponse assign(@PathVariable UUID id, @PathVariable UUID warehouseId) {
        return service.assign(id, warehouseId);
    }
    @DeleteMapping("/{id}/warehouses/{warehouseId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('USER_WAREHOUSE_ASSIGN')")
    public void remove(@PathVariable UUID id, @PathVariable UUID warehouseId) { service.remove(id, warehouseId); }
}
