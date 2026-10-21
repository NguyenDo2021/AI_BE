package com.frontendbase.api.report.controller;

import java.util.*;
import com.frontendbase.api.report.dto.ReportDtos.*;
import com.frontendbase.api.report.service.ReportService;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports", description = "Period event reports and current snapshot metrics")
public class ReportController {
    private final ReportService service;

    @GetMapping("/inventory")
    @PreAuthorize("@reportAccess.allowed('REPORT_INVENTORY_VIEW')")
    @Operation(summary = "Historical inventory in pieces", description = "Movement performedAt in Asia/Ho_Chi_Minh; retained inactive history")
    public PageResponse<InventoryRow> inventory(@RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID productId, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return service.inventory(warehouseId, productId, from, to, page, pageSize);
    }

    @GetMapping("/sales")
    @PreAuthorize("@reportAccess.allowed('REPORT_SALES_VIEW')")
    @Operation(summary = "Event-based sales value", description = ReportService.SALES_DESCRIPTION)
    public SummaryPage<SalesRow, SalesSummary> sales(@RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return service.sales(warehouseId, from, to, page, pageSize);
    }

    @GetMapping("/payments")
    @PreAuthorize("@reportAccess.allowed('REPORT_PAYMENT_VIEW')")
    @Operation(summary = "Thu tiền ghi nhận theo thời gian hệ thống", description = ReportService.PAYMENT_DESCRIPTION)
    public SummaryPage<PaymentRow, PaymentSummary> payments(@RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return service.payments(warehouseId, from, to, page, pageSize);
    }

    private void currentOnly(Map<String, String> query) {
        if (query.containsKey("from") || query.containsKey("to"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Current reports do not accept from/to");
    }

    @GetMapping("/receivables-summary")
    @PreAuthorize("@reportAccess.allowed('REPORT_RECEIVABLE_VIEW')")
    @Operation(summary = "Current receivables", description = "Current adjusted order totals less net ACTIVE collections/refunds; no period filter")
    public ReceivablesSummary receivables(@RequestParam(required = false) UUID warehouseId,
            @RequestParam Map<String, String> query) {
        currentOnly(query);
        return service.receivables(warehouseId);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("@reportAccess.allowed('REPORT_LOW_STOCK_VIEW')")
    @Operation(summary = "Current low stock", description = "Active warehouses/products; absent balance is zero; threshold zero disables alerts")
    public PageResponse<LowStockRow> lowStock(@RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID productId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize, @RequestParam Map<String, String> query) {
        currentOnly(query);
        return service.lowStock(warehouseId, productId, page, pageSize);
    }

    @GetMapping("/dashboard/trends")
    @PreAuthorize("@reportAccess.allowed('REPORT_DASHBOARD_VIEW')")
    @Operation(summary = "Daily dashboard trends", description = "Inclusive range up to 366 Vietnam calendar days; sales/payments require their report permission and are omitted otherwise; no pagination")
    public DashboardTrends trends(@RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return service.trends(warehouseId, from, to);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("@reportAccess.allowed('REPORT_DASHBOARD_VIEW')")
    @Operation(summary = "Dashboard", description = "Each section requires its corresponding report permission; unauthorized sections are omitted")
    public Dashboard dashboard(@RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return service.dashboard(warehouseId, from, to);
    }
}
