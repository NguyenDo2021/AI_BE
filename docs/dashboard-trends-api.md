# API xu hướng dashboard

GET /api/reports/dashboard/trends?from=2026-10-01&to=2026-10-21&warehouseId=<UUID>

- from/to bắt buộc, YYYY-MM-DD hợp lệ, from <= to.
- Tối đa 366 ngày tính cả hai đầu, chỉ áp dụng cho trends.
- warehouseId: UUID tùy chọn. Không phân trang.
- HTTP 200 trả object trực tiếp như dưới đây; ví dụ rút gọn items, thực tế có đủ mọi ngày tăng dần.

~~~json
{
  "generatedAt": "2026-10-21T03:00:00Z",
  "period": {"from":"2026-10-01","to":"2026-10-21","timezone":"Asia/Ho_Chi_Minh"},
  "items": [{
    "date": "2026-10-01",
    "sales": {"confirmedSalesAmount":1000000,"cancelledSalesAmount":0,"returnedSalesAmount":200000,"netSalesAmount":800000},
    "payments": {"netRecordedAmount":600000,"netRefundedAmount":100000,"netCashFlowAmount":500000}
  }]
}
~~~

Mọi số nghiệp vụ là JSON integer, Java signed long; net được phép âm.
Ngày trống trả 0 cho các series được cấp quyền.

## Phân quyền và kho

REPORT_DASHBOARD_VIEW bắt buộc. Trong từng item:
sales cần REPORT_SALES_VIEW; payments cần REPORT_PAYMENT_VIEW.
Series thiếu quyền bị bỏ hoàn toàn, không trả null hoặc 0 giả.
Chỉ có quyền dashboard vẫn nhận generatedAt, period và items chỉ chứa date.
Admin giữ ngoại lệ ADMIN kiểm tra live. User khác chỉ đọc kho đang được gán,
kể cả không truyền warehouseId. Scope áp dụng trước SUM/GROUP BY.
Không cache dữ liệu giữa user hoặc phạm vi.

## Thời gian và công thức

Khoảng [from 00:00 Việt Nam, ngày sau to 00:00 Việt Nam).
Ngày 2026-10-11 là [2026-10-10T17:00:00Z, 2026-10-11T17:00:00Z).

- Xác nhận bán: sales_orders.confirmed_at, kể cả đơn bị hủy về sau.
- Hủy bán: sales_orders.cancelled_at, chỉ đơn có confirmed_at.
- Trả hàng: sales_returns.confirmed_at.
- netSalesAmount = xác nhận - hủy - trả.
- Thu và hoàn theo created_at, đảo tương ứng theo cancelled_at.
- netRecordedAmount = thu được tạo - đảo thu; giữ nguyên nghĩa thu ròng.
- netRefundedAmount = hoàn được tạo - đảo hoàn.
- netCashFlowAmount = netRecordedAmount - netRefundedAmount.
- Không lọc saleDate/paymentDate/refundDate/returnDate.
- Không tính lợi nhuận hay số dư tiền mặt.

Sự kiện kỳ sau không sửa kỳ trước. SQL sự kiện, scope và công thức dùng chung
với summary/dashboard/Excel. Tối đa hai query tổng hợp cho hai series được cấp quyền;
không query theo từng ngày hoặc từng kho. Service điền ngày trống sau tổng hợp.
SQL NUMERIC và longValueExact bảo đảm phép tính exact, phát hiện overflow.

Transaction read-only REPEATABLE_READ bao gồm scope và các series.
Tổng ngày khớp báo cáo/dashboard cùng filter trên cùng snapshot.
Các HTTP request độc lập có thể đọc các dữ liệu commit khác nhau.

## Cơ cấu thu trên dashboard

GET /api/reports/dashboard hiện đã có:
periodSections.payments.recordedCashAmount và recordedBankTransferAmount.
Tái sử dụng trực tiếp PaymentSummary, không tạo field trùng hoặc đổi nghĩa.

Hai số là tổng thu ĐƯỢC TẠO trong kỳ theo hình thức, kể cả phiếu bị hủy
sau đó hoặc trong cùng kỳ. Không trừ đảo thu/hoàn; không âm, dùng cho donut.
Không dùng netCashAmount/netBankTransferAmount vì có thể âm.
Giữ nguyên mọi field thu ròng/hoàn ròng hiện có.
Section payments vẫn yêu cầu REPORT_PAYMENT_VIEW.

## Mã lỗi

Giữ schema lỗi {timestamp,status,code,message,details}.

| HTTP | Code | Trường hợp |
|---|---|---|
| 400 | INVALID_DATE_RANGE | Thiếu/ngày sai định dạng/không tồn tại, from > to |
| 400 | REPORT_PERIOD_TOO_LARGE | Vượt 366 ngày tính cả hai đầu |
| 400 | INVALID_REQUEST | UUID query sai kiểu |
| 401 | UNAUTHORIZED | Thiếu/sai xác thực |
| 403 | FORBIDDEN | Thiếu quyền dashboard |
| 403 | WAREHOUSE_ACCESS_DENIED | Kho được chọn ngoài phạm vi |
| 404 | WAREHOUSE_NOT_FOUND | Admin chọn kho không tồn tại |
| 409 | NUMERIC_OVERFLOW | Metric trả về vượt signed Long |

[Kết quả kiểm tra](dashboard-trends-verification.md).
