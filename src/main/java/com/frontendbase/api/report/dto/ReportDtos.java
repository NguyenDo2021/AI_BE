package com.frontendbase.api.report.dto;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

public final class ReportDtos {
        private ReportDtos() {
        }

        public record Period(LocalDate from, LocalDate to, String timezone) {
        }

        public record InventoryRow(UUID warehouseId, String warehouseCode, String warehouseName,
                        UUID productId, String productCode, String productName, String unit, short productStatus,
                        long openingQuantity, long receiptQuantity, long receiptCancellationQuantity,
                        long saleQuantity, long saleCancellationQuantity, long adjustmentIncreaseQuantity,
                        long adjustmentDecreaseQuantity, long closingQuantity) {
        }

        public record SalesSummary(long confirmedOrderCount, long cancelledConfirmedOrderCount,
                        long confirmedQuantity, long cancelledQuantity, long grossSalesAmount, long discountAmount,
                        long confirmedSalesAmount, long cancelledSalesAmount, long netSalesAmount) {
        }

        public record SalesRow(UUID warehouseId, String warehouseCode, String warehouseName,
                        @JsonUnwrapped SalesSummary metrics) {
        }

        public record PaymentSummary(long createdPaymentCount, long cancelledPaymentCount,
                        long recordedCashAmount, long recordedBankTransferAmount, long reversedCashAmount,
                        long reversedBankTransferAmount, long netCashAmount, long netBankTransferAmount,
                        long netRecordedAmount) {
        }

        public record PaymentRow(UUID warehouseId, String warehouseCode, String warehouseName,
                        @JsonUnwrapped PaymentSummary metrics) {
        }

        public record SummaryPage<T, S>(List<T> items, long total, int page, int pageSize,
                        S summary, Period period, String description) {
        }

        public record ReceivablesSummary(long customerRemainingAmount, long customerCount,
                        long customerOutstandingOrderCount, long walkInRemainingAmount,
                        long walkInOutstandingOrderCount, long totalRemainingAmount, Instant asOf) {
        }

        public record LowStockRow(UUID warehouseId, String warehouseCode, String warehouseName,
                        UUID productId, String productCode, String productName, String unit,
                        long quantity, long lowStockThreshold, long shortageQuantity) {
        }

        public record LowStockSummary(long lowStockPairCount, Instant asOf) {
        }

        public record PeriodSections(SalesSummary sales, PaymentSummary payments, String timeBasis) {
        }

        public record CurrentSections(ReceivablesSummary receivables, LowStockSummary lowStock, String timeBasis) {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record Dashboard(Instant generatedAt, Period period,
                        PeriodSections periodSections, CurrentSections currentSections) {
        }
}
