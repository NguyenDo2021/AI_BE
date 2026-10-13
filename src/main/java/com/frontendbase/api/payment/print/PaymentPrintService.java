package com.frontendbase.api.payment.print;

import java.util.UUID;
import com.frontendbase.api.common.exception.ApiException;
import com.frontendbase.api.payment.dto.PaymentDtos.Status;
import com.frontendbase.api.payment.repository.PaymentRepository;
import com.frontendbase.api.payment.service.PaymentAccess;
import com.frontendbase.api.warehouse.service.WarehouseAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentPrintService {
    private final PaymentRepository payments;
    private final PaymentAccess permissions;
    private final WarehouseAccess access;
    private final ReceiptPdfService pdf;

    public record PrintedReceipt(String code, byte[] bytes) {
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PrintedReceipt print(UUID id) {
        // Also protect internal callers; fetch customer/warehouse/user only after
        // authorization.
        if (!permissions.allowed("PAYMENT_VIEW"))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền xem phiếu thu");
        var payment = payments.payment(id, false);
        access.requireWarehouse(payment.warehouseId());
        var data = payments.jdbc().queryForObject("""
                SELECT s.code AS order_code, s.customer_name, s.customer_address,
                       w.name AS warehouse_name, w.address AS warehouse_address, u.full_name
                FROM sales_orders s JOIN warehouses w ON w.id=?
                LEFT JOIN users u ON u.id=?
                WHERE s.id=?
                """, (r, n) -> new ReceiptData(payment.code(), payment.paymentDate(),
                r.getString("warehouse_name"), r.getString("warehouse_address"),
                r.getString("customer_name") == null || r.getString("customer_name").isBlank()
                        ? "Khách lẻ"
                        : r.getString("customer_name"),
                r.getString("customer_address"),
                "Thu tiền đơn bán " + r.getString("order_code")
                        + (payment.note() == null || payment.note().isBlank() ? "" : ". " + payment.note()),
                payment.amount(), r.getString("full_name"), payment.status() == Status.CANCELLED,
                payment.cancellationReason()), payment.warehouseId(), payment.createdBy(), payment.salesOrderId());
        return new PrintedReceipt(payment.code(), pdf.render(data));
    }
}
