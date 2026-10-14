package com.frontendbase.api.report.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import jakarta.servlet.http.HttpServletResponse;
import com.frontendbase.api.common.exception.ApiException;
import com.frontendbase.api.report.service.ReportExportService;
import com.frontendbase.api.report.service.ReportExportService.Kind;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports")
public class ReportExportController {
    private final ReportExportService exports;

    private void validate(Map<String, String> query, boolean current) {
        if (query.containsKey("page") || query.containsKey("pageSize") ||
                (current && (query.containsKey("from") || query.containsKey("to"))))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Export does not accept pagination; current low stock does not accept from/to");
    }
    private void send(Kind kind, UUID warehouse, UUID product, String from, String to,
            Map<String, String> query, HttpServletResponse response) throws IOException {
        validate(query, kind == Kind.LOW_STOCK);
        try (var file = exports.export(kind, warehouse, product, from, to)) {
            // Finish generation and open the file before setting success headers.
            try (var input = Files.newInputStream(file.path())) {
                long length = Files.size(file.path());
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString());
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                response.setContentLengthLong(length);
                input.transferTo(response.getOutputStream());
            }
        }
    }
    @GetMapping("/inventory/export")
    @PreAuthorize("@reportAccess.allowed('REPORT_INVENTORY_VIEW')")
    public void inventory(@RequestParam(required=false) UUID warehouseId, @RequestParam(required=false) UUID productId,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to,
            @RequestParam Map<String, String> query, HttpServletResponse response) throws IOException {
        send(Kind.INVENTORY, warehouseId, productId, from, to, query, response);
    }
    @GetMapping("/sales/export")
    @PreAuthorize("@reportAccess.allowed('REPORT_SALES_VIEW')")
    public void sales(@RequestParam(required=false) UUID warehouseId,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to,
            @RequestParam Map<String, String> query, HttpServletResponse response) throws IOException {
        send(Kind.SALES, warehouseId, null, from, to, query, response);
    }
    @GetMapping("/payments/export")
    @PreAuthorize("@reportAccess.allowed('REPORT_PAYMENT_VIEW')")
    public void payments(@RequestParam(required=false) UUID warehouseId,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to,
            @RequestParam Map<String, String> query, HttpServletResponse response) throws IOException {
        send(Kind.PAYMENTS, warehouseId, null, from, to, query, response);
    }
    @GetMapping("/low-stock/export")
    @PreAuthorize("@reportAccess.allowed('REPORT_LOW_STOCK_VIEW')")
    public void lowStock(@RequestParam(required=false) UUID warehouseId, @RequestParam(required=false) UUID productId,
            @RequestParam Map<String, String> query, HttpServletResponse response) throws IOException {
        send(Kind.LOW_STOCK, warehouseId, productId, null, null, query, response);
    }
}
