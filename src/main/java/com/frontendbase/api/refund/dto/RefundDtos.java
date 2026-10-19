package com.frontendbase.api.refund.dto;
import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.frontendbase.api.common.validation.StrictLongDeserializer;
import com.frontendbase.api.payment.dto.PaymentDtos.Method;
import com.frontendbase.api.payment.dto.PaymentDtos.Status;
import com.frontendbase.api.sales.dto.SalesDtos.*;

public final class RefundDtos {
    private RefundDtos() {}
    public record CreatePayload(@NotNull @Positive @JsonDeserialize(using=StrictLongDeserializer.class) Long amount,
            @NotNull LocalDate refundDate, @NotNull Method method, @Size(max=200) String reference,
            @NotBlank @Size(max=2000) String reason, @Size(max=2000) String note) {
        @JsonAnySetter public void reject(String field, JsonNode value) { throw new IllegalArgumentException("Unknown field: "+field); }
    }
    public record CancelPayload(@NotBlank @Size(max=2000) String reason) {
        @JsonAnySetter public void reject(String field, JsonNode value) { throw new IllegalArgumentException("Unknown field: "+field); }
    }
    public record RefundResponse(UUID id, String code, UUID salesOrderId, UUID warehouseId, UUID customerId,
            long amount, LocalDate refundDate, Method method, String reference, String reason, String note, Status status,
            UUID createdBy, Instant createdAt, UUID cancelledBy, Instant cancelledAt, String cancellationReason) {}
    public record OrderSettlementSummary(UUID salesOrderId, String code, UUID warehouseId, UUID customerId,
            CustomerSnapshot customerSnapshot, LocalDate saleDate, SalesStatus status,
            long totalAmount, long returnedAmount, long adjustedTotalAmount, long paidAmount,
            long refundedAmount, long netPaidAmount, long remainingAmount, long refundableAmount, PaymentStatus paymentStatus) {
        public static OrderSettlementSummary from(SalesResponse s) {
            return new OrderSettlementSummary(s.id(),s.code(),s.warehouseId(),s.customerId(),s.customerSnapshot(),
                s.saleDate(),s.status(),s.totalAmount(),s.returnedAmount(),s.adjustedTotalAmount(),s.paidAmount(),
                s.refundedAmount(),s.netPaidAmount(),s.remainingAmount(),s.refundableAmount(),s.paymentStatus());
        }
    }
    public record MutationResponse(RefundResponse refund, OrderSettlementSummary order) {}
}
