package com.frontendbase.api.sales.controller;

import java.util.*;
import java.time.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.frontendbase.api.sales.dto.SalesDtos.*;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;
import com.frontendbase.api.sales.service.SalesService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/sales-orders")
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SalesOrderController {
    private final SalesService service;

    @GetMapping
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('SALES_ORDER_VIEW')")
    public PageResponse<SalesResponse> list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) SalesStatus status,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return service.search(warehouseId, customerId, status, from, to, page, pageSize);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('SALES_ORDER_VIEW')")
    public SalesResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('SALES_ORDER_CREATE')")
    public SalesResponse create(@Valid @RequestBody CreatePayload p) {
        return service.create(p);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('SALES_ORDER_UPDATE')")
    public SalesResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePayload p) {
        return service.update(id, p);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('SALES_ORDER_CONFIRM')")
    public SalesResponse confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmPayload p) {
        return service.confirm(id, p);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('SALES_ORDER_CANCEL')")
    public SalesResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelPayload p) {
        return service.cancel(id, p);
    }
}
