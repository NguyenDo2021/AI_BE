package com.frontendbase.api.stock.dto;

import java.util.*;
import java.time.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.frontendbase.api.common.validation.StrictLongDeserializer;

public final class StockDtos {
    private StockDtos() {
    }

    public enum ReceiptStatus {
        DRAFT, CONFIRMED, CANCELLED
    }

    public record LinePayload(@NotNull UUID productId,
            @NotNull @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long quantity,
            @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLongDeserializer.class) Long unitPrice) {
    }

    public record CreatePayload(@NotNull UUID warehouseId, @NotNull LocalDate receiptDate,
            @Size(max = 160) String supplierName, @Size(max = 2000) String note,
            @NotEmpty @Size(max = 1000) List<@NotNull @Valid LinePayload> lines) {
    }

    // warehouseId is optional on update for clients that echo it; a different value
    // is rejected.
    public record UpdatePayload(UUID warehouseId, @NotNull LocalDate receiptDate,
            @Size(max = 160) String supplierName, @Size(max = 2000) String note,
            @NotEmpty @Size(max = 1000) List<@NotNull @Valid LinePayload> lines,
            @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLongDeserializer.class) Long version) {
    }

    public record ConfirmPayload(
            @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLongDeserializer.class) Long version) {
    }

    public record CancelPayload(
            @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLongDeserializer.class) Long version,
            @Size(max = 2000) String reason) {
    }

    public record LineResponse(UUID productId, long quantity, long unitPrice, long lineTotal,
            String productCode, String productName, String unit) {
    }

    public record ReceiptResponse(UUID id, String code, UUID warehouseId, LocalDate receiptDate,
            String supplierName, String note, ReceiptStatus status, long totalAmount, long version,
            UUID createdBy, Instant createdAt, UUID confirmedBy, Instant confirmedAt,
            UUID cancelledBy, Instant cancelledAt, String cancellationReason, List<LineResponse> lines) {
    }

    public record InventoryResponse(UUID warehouseId, UUID productId, String productCode,
            String productName, String unit, short productStatus, long quantity) {
    }

    public record MovementResponse(UUID id, UUID warehouseId, UUID productId, long quantityChange,
            String type, UUID receiptId, String receiptCode, String productCode, String productName,
            String unit, UUID performedBy, Instant performedAt, UUID salesOrderId, String salesOrderCode) {
    }

    public record PageResponse<T>(List<T> items, long total, int page, int pageSize) {
    }
}
