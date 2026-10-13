package com.frontendbase.api.stocktake.dto;

import java.time.Instant;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.frontendbase.api.common.validation.StrictLongDeserializer;

public final class StocktakeDtos {
    private StocktakeDtos() {}
    public enum Status { DRAFT, CONFIRMED, CANCELLED }
    public record CreateLine(@NotNull UUID productId) {
        @JsonAnySetter public void unknown(String key, Object value) { reject(key); }
    }
    public record LinePayload(@NotNull UUID productId,
        @PositiveOrZero @JsonDeserialize(using=StrictLongDeserializer.class) Long countedQuantity,
        @Size(max=2000) String reason) {
        @JsonAnySetter public void unknown(String key, Object value) { reject(key); }
    }
    public record CreatePayload(@NotNull UUID warehouseId, @Size(max=2000) String note,
        @NotEmpty @Size(max=1000) List<@NotNull @Valid CreateLine> lines) {
        @JsonAnySetter public void unknown(String key, Object value) { reject(key); }
    }
    public record UpdatePayload(
        @NotNull @PositiveOrZero @JsonDeserialize(using=StrictLongDeserializer.class) Long version,
        @Size(max=2000) String note, @NotEmpty @Size(max=1000) List<@NotNull @Valid LinePayload> lines) {
        @JsonAnySetter public void unknown(String key, Object value) { reject(key); }
    }
    public record VersionPayload(
        @NotNull @PositiveOrZero @JsonDeserialize(using=StrictLongDeserializer.class) Long version) {
        @JsonAnySetter public void unknown(String key, Object value) { reject(key); }
    }
    public record CancelPayload(
        @NotNull @PositiveOrZero @JsonDeserialize(using=StrictLongDeserializer.class) Long version,
        @NotBlank @Size(max=2000) String reason) {
        @JsonAnySetter public void unknown(String key, Object value) { reject(key); }
    }
    private static void reject(String key) { throw new IllegalArgumentException("Unsupported field: " + key); }
    public record LineResponse(UUID productId, long systemQuantity, long inventoryRevision,
        Long countedQuantity, Long difference, String reason, String productCode, String productName, String unit) {}
    public record Response(UUID id, String code, UUID warehouseId, String note, Status status, long version,
        UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt,
        UUID confirmedBy, Instant confirmedAt, UUID cancelledBy, Instant cancelledAt,
        String cancellationReason, List<LineResponse> lines) {}
}
