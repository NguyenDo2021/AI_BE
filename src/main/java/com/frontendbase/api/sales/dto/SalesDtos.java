package com.frontendbase.api.sales.dto;

import java.util.*;
import java.time.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.frontendbase.api.common.validation.StrictLongDeserializer;
import com.frontendbase.api.stock.dto.StockDtos.LinePayload;
import com.frontendbase.api.stock.dto.StockDtos.LineResponse;

public final class SalesDtos {
    private SalesDtos() {
    }

    public enum SalesStatus {
        DRAFT, CONFIRMED, CANCELLED
    }

    public record CreatePayload(@NotNull UUID warehouseId, UUID customerId, @NotNull LocalDate saleDate,
            @Size(max = 2000) String note,
            @PositiveOrZero @JsonDeserialize(using = StrictLongDeserializer.class) Long discountAmount,
            @NotEmpty @Size(max = 1000) List<@NotNull @Valid LinePayload> lines) {
    }

    public record UpdatePayload(UUID warehouseId, UUID customerId, @NotNull LocalDate saleDate,
            @Size(max = 2000) String note,
            @PositiveOrZero @JsonDeserialize(using = StrictLongDeserializer.class) Long discountAmount,
            @NotEmpty @Size(max = 1000) List<@NotNull @Valid LinePayload> lines,
            @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLongDeserializer.class) Long version) {
    }

    public record ConfirmPayload(
            @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLongDeserializer.class) Long version) {
    }

    public record CancelPayload(
            @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLongDeserializer.class) Long version,
            @NotBlank @Size(max = 2000) String reason, Boolean goodsReturned) {
    }

    public record CustomerSnapshot(String code, String name, String phone, String address) {
    }

    public record SalesResponse(UUID id, String code, UUID warehouseId, UUID customerId, LocalDate saleDate,
            String note,
            SalesStatus status, long subtotal, long discountAmount, long totalAmount, long version,
            UUID createdBy, Instant createdAt, Instant updatedAt, UUID confirmedBy, Instant confirmedAt,
            UUID cancelledBy, Instant cancelledAt, String cancellationReason, Boolean goodsReturned,
            CustomerSnapshot customerSnapshot, List<LineResponse> lines) {
    }
}
