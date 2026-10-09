package com.frontendbase.api.customer.controller;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.frontendbase.api.customer.dto.CustomerDtos.*;
import com.frontendbase.api.customer.service.CustomerService;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/customers")
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {
    private final CustomerService service;

    @GetMapping
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('CUSTOMER_VIEW')")
    public PageResponse<CustomerResponse> list(@RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status, @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return service.search(warehouseId, keyword, status, page, pageSize);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('CUSTOMER_VIEW')")
    public CustomerResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('CUSTOMER_CREATE')")
    public CustomerResponse create(@Valid @RequestBody CreatePayload p) {
        return service.create(p);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('CUSTOMER_UPDATE')")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePayload p) {
        return service.update(id, p);
    }
}
