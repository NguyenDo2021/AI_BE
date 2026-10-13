package com.frontendbase.api.stocktake.controller;

import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.frontendbase.api.stocktake.dto.StocktakeDtos.*;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;
import com.frontendbase.api.stocktake.service.StocktakeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController @RequestMapping("/stocktakes") @Validated @RequiredArgsConstructor
@SecurityRequirement(name="bearerAuth")
public class StocktakeController {
    private final StocktakeService service;
    @GetMapping @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCKTAKE_VIEW')")
    public PageResponse<Response> list(@RequestParam(defaultValue="1") @Min(1) int page,
        @RequestParam(defaultValue="10") @Min(1) @Max(100) int pageSize,
        @RequestParam(required=false) UUID warehouseId, @RequestParam(required=false) Status status,
        @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate to) {
        return service.search(page,pageSize,warehouseId,status,from,to);
    }
    @GetMapping("/{id}") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCKTAKE_VIEW')")
    public Response get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCKTAKE_CREATE')")
    public Response create(@Valid @RequestBody CreatePayload p) { return service.create(p); }
    @PutMapping("/{id}") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCKTAKE_UPDATE')")
    public Response update(@PathVariable UUID id,@Valid @RequestBody UpdatePayload p) { return service.update(id,p); }
    @PostMapping("/{id}/refresh-snapshot") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCKTAKE_UPDATE')")
    public Response refresh(@PathVariable UUID id,@Valid @RequestBody VersionPayload p) { return service.refresh(id,p); }
    @PostMapping("/{id}/confirm") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCKTAKE_CONFIRM')")
    public Response confirm(@PathVariable UUID id,@Valid @RequestBody VersionPayload p) { return service.confirm(id,p); }
    @PostMapping("/{id}/cancel") @PreAuthorize("@warehouseAccess.isAdmin() or hasAuthority('STOCKTAKE_CANCEL')")
    public Response cancel(@PathVariable UUID id,@Valid @RequestBody CancelPayload p) { return service.cancel(id,p); }
}
