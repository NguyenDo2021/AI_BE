package com.frontendbase.api.returns.controller;
import java.time.LocalDate;
import java.util.*;
import jakarta.validation.Valid;
import com.frontendbase.api.returns.dto.ReturnDtos.*;
import com.frontendbase.api.returns.service.ReturnService;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController @RequiredArgsConstructor @SecurityRequirement(name="bearerAuth")
public class ReturnController {
    private final ReturnService service;
    @GetMapping("/sales-returns") @PreAuthorize("@paymentAccess.allowed('SALES_RETURN_VIEW')")
    public PageResponse<ReturnResponse> list(@RequestParam(required=false) UUID warehouseId,
        @RequestParam(required=false) UUID salesOrderId,@RequestParam(required=false) Status status,
        @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize) {
        return service.search(warehouseId,salesOrderId,status,from,to,page,pageSize);
    }
    @GetMapping("/sales-returns/{id}") @PreAuthorize("@paymentAccess.allowed('SALES_RETURN_VIEW')")
    public ReturnResponse get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/sales-orders/returnable-orders")
    @PreAuthorize("@paymentAccess.allowed('SALES_RETURN_VIEW')")
    public PageResponse<ReturnableOrder> returnableOrders(
            @io.swagger.v3.oas.annotations.Parameter(required=true, description="Nonblank literal substring of the sales order code")
            @RequestParam(required=false) String code,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int pageSize) {
        return service.returnableOrders(code, page, pageSize);
    }

    @GetMapping("/sales-orders/{id}/returnable-lines") @PreAuthorize("@paymentAccess.allowed('SALES_RETURN_VIEW')")
    public List<ReturnableLine> returnable(@PathVariable UUID id) { return service.returnable(id); }
    @PostMapping("/sales-returns") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@paymentAccess.allowed('SALES_RETURN_CREATE')")
    public ReturnResponse create(@Valid @RequestBody CreatePayload p) { return service.create(p); }
    @PutMapping("/sales-returns/{id}") @PreAuthorize("@paymentAccess.allowed('SALES_RETURN_UPDATE')")
    public ReturnResponse update(@PathVariable UUID id,@Valid @RequestBody UpdatePayload p) { return service.update(id,p); }
    @PostMapping("/sales-returns/{id}/confirm") @PreAuthorize("@paymentAccess.allowed('SALES_RETURN_CONFIRM')")
    public ReturnResponse confirm(@PathVariable UUID id,@Valid @RequestBody ConfirmPayload p) { return service.confirm(id,p); }
    @PostMapping("/sales-returns/{id}/cancel") @PreAuthorize("@paymentAccess.allowed('SALES_RETURN_CANCEL')")
    public ReturnResponse cancel(@PathVariable UUID id,@Valid @RequestBody CancelPayload p) { return service.cancel(id,p); }
}
