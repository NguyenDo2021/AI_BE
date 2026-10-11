package com.frontendbase.api.payment.print;

import java.time.LocalDate;

/** Internal print model; never added to the payment API DTO. */
public record ReceiptData(String code, LocalDate date, String warehouseName, String warehouseAddress,
        String payerName, String payerAddress, String description, long amount,
        String collectorName, boolean cancelled, String cancellationReason) {}
