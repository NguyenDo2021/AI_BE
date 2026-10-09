package com.frontendbase.api.payment.controller;

import java.util.*;
import java.time.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.frontendbase.api.payment.dto.PaymentDtos.*;
import com.frontendbase.api.payment.service.PaymentService;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {
    private final PaymentService service;

    @PostMapping("/sales-orders/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@paymentAccess.allowed('PAYMENT_CREATE')")
    public MutationResponse create(@PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreatePayload p) {
        return service.create(id, key, p);
    }

    @GetMapping("/sales-orders/{id}/payments")
    @PreAuthorize("@paymentAccess.allowed('PAYMENT_VIEW')")
    public PageResponse<PaymentResponse> orderPayments(@PathVariable UUID id,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return service.orderPayments(id, status, page, pageSize);
    }

    @PostMapping("/payments/{id}/cancel")
    @PreAuthorize("@paymentAccess.allowed('PAYMENT_CANCEL')")
    public MutationResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelPayload p) {
        return service.cancel(id, p);
    }

    @GetMapping("/payments/{id}")
    @PreAuthorize("@paymentAccess.allowed('PAYMENT_VIEW')")
    public PaymentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/payments")
    @PreAuthorize("@paymentAccess.allowed('PAYMENT_VIEW')")
    public PageResponse<PaymentResponse> list(
            @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID salesOrderId,
            @RequestParam(required = false) UUID customerId, @RequestParam(required = false) Status status,
            @RequestParam(required = false) Method method, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return service.search(warehouseId, salesOrderId, customerId, status, method, from, to, page, pageSize);
    }
}
