package com.frontendbase.api.report.service;

import java.time.Instant;
import java.util.UUID;
import com.frontendbase.api.report.dto.ReportDtos.*;
import com.frontendbase.api.report.repository.ReportRepository;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReportService {
    private final ReportRepository repository;
    private final ReportAccess access;
    private final com.frontendbase.api.payment.service.PaymentAccess paymentAccess;
    public static final String SALES_DESCRIPTION = "Event-based sales and returns by confirmedAt/cancelledAt; not profit, cost of goods or VAT";
    public static final String PAYMENT_DESCRIPTION = "System collection/refund records by createdAt/cancelledAt, not business dates or bank reconciliation. netRecordedAmount is net collections; netRefundedAmount is net refunds; netCashFlowAmount is their difference. Cancellation reverses an incorrect record.";

    private void pagination(int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "page >= 1; pageSize between 1 and 100");
    }

    public PageResponse<InventoryRow> inventory(UUID warehouse, UUID product, String from, String to, int page,
            int size) {
        pagination(page, size);
        var period = ReportPeriod.parse(from, to);
        var scope = access.scope(warehouse);
        repository.verifyInventoryHistory(scope, product);
        return repository.inventory(scope, product, period, page, size);
    }

    public SummaryPage<SalesRow, SalesSummary> sales(UUID warehouse, String from, String to, int page, int size) {
        pagination(page, size);
        var period = ReportPeriod.parse(from, to);
        var scope = access.scope(warehouse);
        var summary = repository.salesSummary(scope, period);
        var rows = repository.sales(scope, period, page, size);
        return new SummaryPage<>(rows.items(), rows.total(), page, size, summary, period.response(), SALES_DESCRIPTION);
    }

    public SummaryPage<PaymentRow, PaymentSummary> payments(UUID warehouse, String from, String to, int page,
            int size) {
        pagination(page, size);
        var period = ReportPeriod.parse(from, to);
        var scope = access.scope(warehouse);
        var summary = repository.paymentsSummary(scope, period);
        var rows = repository.payments(scope, period, page, size);
        return new SummaryPage<>(rows.items(), rows.total(), page, size, summary, period.response(),
                PAYMENT_DESCRIPTION);
    }

    public ReceivablesSummary receivables(UUID warehouse) {
        var scope = access.scope(warehouse);
        return repository.receivables(scope, Instant.now());
    }

    public PageResponse<LowStockRow> lowStock(UUID warehouse, UUID product, int page, int size) {
        pagination(page, size);
        return repository.lowStock(access.scope(warehouse), product, page, size);
    }

    public Dashboard dashboard(UUID warehouse, String from, String to) {
        var period = ReportPeriod.parse(from, to);
        var scope = access.scope(warehouse);
        Instant generatedAt = Instant.now();
        var sales = access.allowed("REPORT_SALES_VIEW") ? repository.salesSummary(scope, period) : null;
        var payments = access.allowed("REPORT_PAYMENT_VIEW") ? repository.paymentsSummary(scope, period) : null;
        var receivables = access.allowed("REPORT_RECEIVABLE_VIEW") ? repository.receivables(scope, generatedAt) : null;
        var lowStock = access.allowed("REPORT_LOW_STOCK_VIEW")
                ? new LowStockSummary(repository.lowStockCount(scope), generatedAt)
                : null;
        var refunds = paymentAccess.allowed("REFUND_VIEW") ? repository.refundables(scope, generatedAt) : null;
        return new Dashboard(generatedAt, period.response(),
                sales == null && payments == null ? null
                        : new PeriodSections(sales, payments,
                                SALES_DESCRIPTION + "; " + PAYMENT_DESCRIPTION),
                receivables == null && lowStock == null && refunds == null ? null
                        : new CurrentSections(receivables, lowStock,
                                "Current data read in one repeatable-read snapshot; independent of the selected period", refunds));
    }
}
