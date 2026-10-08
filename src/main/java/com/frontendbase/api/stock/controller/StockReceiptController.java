package com.frontendbase.api.stock.controller;
import java.util.*;
import java.time.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.frontendbase.api.stock.dto.StockDtos.*;
import com.frontendbase.api.stock.service.StockService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
@RestController @RequestMapping("/stock-receipts") @Validated @RequiredArgsConstructor
@SecurityRequirement(name="bearerAuth")
public class StockReceiptController {
    private final StockService service;
    @GetMapping @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCK_RECEIPT_VIEW')")
    public PageResponse<ReceiptResponse> list(@RequestParam(defaultValue="1") @Min(1) int page,
        @RequestParam(defaultValue="10") @Min(1) @Max(100) int pageSize,
        @RequestParam(required=false) UUID warehouseId, @RequestParam(required=false) ReceiptStatus status,
        @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate to) {
        return service.search(page,pageSize,warehouseId,status,from,to);
    }
    @GetMapping("/{id}") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCK_RECEIPT_VIEW')")
    public ReceiptResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCK_RECEIPT_CREATE')")
    public ReceiptResponse create(@Valid @RequestBody CreatePayload p) { return service.create(p); }
    @PutMapping("/{id}") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCK_RECEIPT_UPDATE')")
    public ReceiptResponse update(@PathVariable UUID id,@Valid @RequestBody UpdatePayload p) { return service.update(id,p); }
    @PostMapping("/{id}/confirm") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCK_RECEIPT_CONFIRM')")
    public ReceiptResponse confirm(@PathVariable UUID id,@Valid @RequestBody ConfirmPayload p) { return service.confirm(id,p); }
    @PostMapping("/{id}/cancel") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCK_RECEIPT_CANCEL')")
    public ReceiptResponse cancel(@PathVariable UUID id,@Valid @RequestBody CancelPayload p) { return service.cancel(id,p); }
}
