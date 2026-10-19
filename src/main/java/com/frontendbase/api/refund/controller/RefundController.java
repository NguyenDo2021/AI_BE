package com.frontendbase.api.refund.controller;

import java.util.*;
import java.time.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.frontendbase.api.refund.dto.RefundDtos.*;
import com.frontendbase.api.payment.dto.PaymentDtos.Method;
import com.frontendbase.api.payment.dto.PaymentDtos.Status;
import com.frontendbase.api.refund.service.RefundService;
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
public class RefundController {
    private final RefundService service;


    @PostMapping("/sales-orders/{id}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@paymentAccess.allowed('REFUND_CREATE')")
    public MutationResponse create(@PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreatePayload p) {
        return service.create(id, key, p);
    }

    @PostMapping("/refunds/{id}/cancel")
    @PreAuthorize("@paymentAccess.allowed('REFUND_CANCEL')")
    public MutationResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelPayload p) {
        return service.cancel(id, p);
    }

    @GetMapping("/refunds/{id}")
    @PreAuthorize("@paymentAccess.allowed('REFUND_VIEW')")
    public RefundResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/refunds")
    @PreAuthorize("@paymentAccess.allowed('REFUND_VIEW')")
    public PageResponse<RefundResponse> list(
            @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID salesOrderId,
            @RequestParam(required = false) UUID customerId, @RequestParam(required = false) Status status,
            @RequestParam(required = false) Method method, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return service.search(warehouseId, salesOrderId, customerId, status, method, from, to, page, pageSize);
    }

    @GetMapping("/refundables")
    @PreAuthorize("@paymentAccess.allowed('REFUND_VIEW')")
    public PageResponse<OrderSettlementSummary> refundables(@RequestParam(required=false) UUID warehouseId,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize) {
        return service.refundables(warehouseId,page,pageSize);
    }
}
