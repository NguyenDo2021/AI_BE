package com.frontendbase.api.stock.controller;
import java.util.*;
import java.time.*;
import jakarta.validation.constraints.*;
import com.frontendbase.api.stock.dto.StockDtos.*;
import com.frontendbase.api.stock.service.StockService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
@RestController @RequestMapping("/warehouses/{warehouseId}") @Validated @RequiredArgsConstructor
@SecurityRequirement(name="bearerAuth")
public class InventoryController {
    private final StockService service;
    @GetMapping("/inventory") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('INVENTORY_VIEW')")
    public PageResponse<InventoryResponse> inventory(@PathVariable UUID warehouseId,
        @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="10") @Min(1) @Max(100) int pageSize,
        @RequestParam(required=false) UUID productId) { return service.inventory(warehouseId,page,pageSize,productId); }
    @GetMapping("/inventory-movements") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('INVENTORY_MOVEMENT_VIEW')")
    public PageResponse<MovementResponse> history(@PathVariable UUID warehouseId,
        @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="10") @Min(1) @Max(100) int pageSize,
        @RequestParam(required=false) UUID productId,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to) {
        return service.history(warehouseId,page,pageSize,productId,from,to);
    }
}
