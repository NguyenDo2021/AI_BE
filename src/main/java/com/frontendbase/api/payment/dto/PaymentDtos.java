package com.frontendbase.api.payment.dto;

import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.frontendbase.api.common.validation.StrictLongDeserializer;
import com.frontendbase.api.sales.dto.SalesDtos.PaymentStatus;
import com.frontendbase.api.sales.dto.SalesDtos.SalesResponse;
import com.frontendbase.api.stock.dto.StockDtos.PageResponse;

public final class PaymentDtos {
        private PaymentDtos() {
        }

        public enum Method {
                CASH, BANK_TRANSFER
        }

        public enum Status {
                ACTIVE, CANCELLED
        }

        public record CreatePayload(
                        @NotNull @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long amount,
                        @NotNull LocalDate paymentDate, @NotNull Method method,
                        @Size(max = 200) String reference, @Size(max = 2000) String note) {
                @JsonAnySetter
                public void rejectUnknown(String field, JsonNode value) {
                        throw new IllegalArgumentException("Unknown payment field: " + field);
                }
        }

        public record CancelPayload(@NotBlank @Size(max = 2000) String reason) {
        }

        public record PaymentResponse(UUID id, String code, UUID salesOrderId, UUID warehouseId, UUID customerId,
                        long amount, LocalDate paymentDate, Method method, String reference, String note, Status status,
                        UUID createdBy, Instant createdAt, UUID cancelledBy, Instant cancelledAt,
                        String cancellationReason) {
        }

        public record OrderPaymentSummary(UUID salesOrderId, long totalAmount, long paidAmount,
                        long remainingAmount, PaymentStatus paymentStatus) {
        }

        public record MutationResponse(PaymentResponse payment, OrderPaymentSummary order) {
        }

        public record ReceivableResponse(UUID customerId, UUID warehouseId, String customerCode, String customerName,
                        long outstandingOrderCount, long totalAmount, long paidAmount, long remainingAmount) {
        }

        public record CustomerReceivables(UUID customerId, UUID warehouseId, long remainingAmount,
                        PageResponse<SalesResponse> orders) {
        }
}
