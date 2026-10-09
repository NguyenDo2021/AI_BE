package com.frontendbase.api.payment.controller;

import java.util.*;
import jakarta.validation.constraints.*;
import com.frontendbase.api.payment.dto.PaymentDtos.*;
import com.frontendbase.api.payment.service.ReceivableService;
import com.frontendbase.api.sales.dto.SalesDtos.SalesResponse;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@paymentAccess.allowed('RECEIVABLE_VIEW')")
public class ReceivableController {
    private final ReceivableService service;

    @GetMapping("/receivables")
    public PageResponse<ReceivableResponse> list(@RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID customerId, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return service.search(warehouseId, customerId, keyword, page, pageSize);
    }

    @GetMapping("/customers/{id}/receivables")
    public CustomerReceivables customer(@PathVariable UUID id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return service.customer(id, page, pageSize);
    }

    @GetMapping("/receivables/walk-in-orders")
    public PageResponse<SalesResponse> walkIn(@RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return service.walkIn(warehouseId, page, pageSize);
    }
}
