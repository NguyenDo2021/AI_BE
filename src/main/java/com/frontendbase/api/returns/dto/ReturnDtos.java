package com.frontendbase.api.returns.dto;
import java.time.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.frontendbase.api.common.validation.StrictLongDeserializer;

public final class ReturnDtos {
    private ReturnDtos() {}
    public enum Status { DRAFT, CONFIRMED, CANCELLED }
    public record LinePayload(@NotNull UUID productId,
            @NotNull @Positive @JsonDeserialize(using=StrictLongDeserializer.class) Long quantity) {
        @JsonAnySetter public void reject(String field, JsonNode value) { throw new IllegalArgumentException("Unknown field: "+field); }
    }
    public record CreatePayload(@NotNull UUID salesOrderId, @NotNull LocalDate returnDate,
            @NotBlank @Size(max=2000) String reason, @Size(max=2000) String note,
            @NotEmpty @Size(max=1000) List<@NotNull @Valid LinePayload> lines) {
        @JsonAnySetter public void reject(String field, JsonNode value) { throw new IllegalArgumentException("Unknown field: "+field); }
    }
    public record UpdatePayload(@NotNull LocalDate returnDate, @NotBlank @Size(max=2000) String reason,
            @Size(max=2000) String note, @NotEmpty @Size(max=1000) List<@NotNull @Valid LinePayload> lines,
            @NotNull @PositiveOrZero @JsonDeserialize(using=StrictLongDeserializer.class) Long version) {
        @JsonAnySetter public void reject(String field, JsonNode value) { throw new IllegalArgumentException("Unknown field: "+field); }
    }
    public record ConfirmPayload(@NotNull @PositiveOrZero @JsonDeserialize(using=StrictLongDeserializer.class) Long version) {
        @JsonAnySetter public void reject(String field, JsonNode value) { throw new IllegalArgumentException("Unknown field: "+field); }
    }
    public record CancelPayload(@NotNull @PositiveOrZero @JsonDeserialize(using=StrictLongDeserializer.class) Long version,
            @NotBlank @Size(max=2000) String reason) {
        @JsonAnySetter public void reject(String field, JsonNode value) { throw new IllegalArgumentException("Unknown field: "+field); }
    }
    public record ReturnableOrder(UUID id, String code, UUID warehouseId, String warehouseName,
            LocalDate saleDate, String customerName, long totalAmount) {}
    public record ReturnableLine(UUID productId, String productCode, String productName, String unit,
            long soldQuantity, long returnedQuantity, long returnableQuantity, long lineTotal,
            long allocatedDiscountAmount, long netLineAmount) {}
    public record LineResponse(UUID productId, long quantity, long returnAmount, long soldQuantity,
            long allocatedDiscountAmount, long netLineAmount, String productCode, String productName, String unit) {}
    public record Quote(long totalAmount, List<LineResponse> lines) {}
    public record ReturnResponse(UUID id, String code, UUID salesOrderId, UUID warehouseId, UUID customerId,
            LocalDate returnDate, String reason, String note, Status status, long totalAmount, long version,
            UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt,
            UUID confirmedBy, Instant confirmedAt, UUID cancelledBy, Instant cancelledAt,
            String cancellationReason, List<LineResponse> lines) {}
}
