# Báo cáo và Dashboard

Module chỉ đọc; response trực tiếp. Context path /api và controller /reports tạo URL /api/reports/*.
Swagger: /api/swagger-ui.html. Migration V14__add_reports_and_dashboard.sql seed 6 permission và index, không gán quyền cho role/user.

## Quy ước và phân quyền

Authorization: Bearer <accessToken>. REPORT_* lấy từ authorities trong JWT, giống stock/sales/stocktake; thay đổi quyền có hiệu lực với JWT mới. Payment hiện dùng live database RBAC riêng, không bị thay đổi.
ADMIN lấy từ user/role đang hoạt động trong database qua WarehouseAccess.
user_warehouses đọc database mỗi request trước SUM/count/LIMIT; thay đổi gán kho có hiệu lực với cùng JWT.
Admin không chọn kho xem toàn bộ; nhân viên không chọn kho xem các kho được gán. Kho chỉ định ngoài phạm vi: 403 WAREHOUSE_ACCESS_DENIED.

warehouseId/productId là UUID tùy chọn. productId không tồn tại trả danh sách rỗng.
page mặc định 1, pageSize mặc định 10 (1–100); total là tổng dòng. Sắp theo warehouseId, rồi productId nếu có.
Trang vượt total trả items=[], summary không đổi.
from/to bắt buộc ở API theo kỳ, dạng YYYY-MM-DD, from<=to.
Khoảng: [from.atStartOfDay(Asia/Ho_Chi_Minh), to.plusDays(1).atStartOfDay(Asia/Ho_Chi_Minh)).
Ví dụ ngày 2026-10-11: [2026-10-10T17:00:00Z, 2026-10-11T17:00:00Z).
Không dùng saleDate/paymentDate làm thời gian sự kiện.
Mọi tổng dùng SQL NUMERIC và longValueExact; vượt signed Long trả 409 NUMERIC_OVERFLOW. Net hỗ trợ số âm.
Số lượng bằng cây (unit=cay), không quy đổi/gộp các chiều dài thành mét.
Transaction readOnly, REPEATABLE_READ giữ items/count/summary/dashboard cùng snapshot. Không cache giữa user.

## Endpoint

| GET URL | Permission | Query |
| --- | --- | --- |
| /api/reports/inventory | REPORT_INVENTORY_VIEW | warehouseId?, productId?, from, to, page?, pageSize? |
| /api/reports/sales | REPORT_SALES_VIEW | warehouseId?, from, to, page?, pageSize? |
| /api/reports/payments | REPORT_PAYMENT_VIEW | warehouseId?, from, to, page?, pageSize? |
| /api/reports/receivables-summary | REPORT_RECEIVABLE_VIEW | warehouseId? |
| /api/reports/low-stock | REPORT_LOW_STOCK_VIEW | warehouseId?, productId?, page?, pageSize? |
| /api/reports/dashboard | REPORT_DASHBOARD_VIEW + quyền section | warehouseId?, from, to |

receivables-summary/low-stock từ chối from/to (400 INVALID_REQUEST).
Dashboard: sales cần REPORT_SALES_VIEW, payments cần REPORT_PAYMENT_VIEW, receivables cần REPORT_RECEIVABLE_VIEW, lowStock cần REPORT_LOW_STOCK_VIEW.
Section thiếu quyền bị bỏ, không trả null hoặc 0. Container periodSections/currentSections không có section cũng bị bỏ.
Admin nhận mọi section dù JWT không có permission. REPORT_INVENTORY_VIEW không cần cho dashboard vì dashboard không chứa tồn lịch sử.

## Nhập–xuất–tồn

GET /api/reports/inventory?warehouseId=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa&from=2026-10-11&to=2026-10-11&page=1&pageSize=10

~~~json
{"items":[{"warehouseId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","warehouseCode":"A","warehouseName":"Kho A","productId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","productCode":"P","productName":"Sản phẩm P","unit":"cay","productStatus":1,"openingQuantity":0,"receiptQuantity":100,"receiptCancellationQuantity":0,"saleQuantity":10,"saleCancellationQuantity":0,"adjustmentIncreaseQuantity":0,"adjustmentDecreaseQuantity":3,"closingQuantity":87}],"total":1,"page":1,"pageSize":10}
~~~

openingQuantity = SUM(quantity_change) trước đầu kỳ.
receiptQuantity = RECEIPT_CONFIRM; receiptCancellationQuantity = ABS(RECEIPT_CANCEL).
saleQuantity = ABS(SALE_CONFIRM); saleCancellationQuantity = SALE_CANCEL.
adjustmentIncreaseQuantity = STOCKTAKE_ADJUSTMENT dương; adjustmentDecreaseQuantity = ABS(STOCKTAKE_ADJUSTMENT âm).
closing = opening + receipt - receiptCancellation - sale + saleCancellation + adjustmentIncrease - adjustmentDecrease.
Các thành phần kỳ lọc performedAt; closing là tổng movement trước cuối kỳ, không lấy balance hiện tại thay tồn cuối quá khứ.
Chỉ cặp có movement trước cuối kỳ; dòng 0 và kho/sản phẩm ngừng hoạt động có lịch sử vẫn giữ.
Hủy bán ngày 12 tăng tồn ngày 12, không thay closing ngày 11. Kỳ không phát sinh vẫn trả opening/closing nếu có lịch sử.

### Nguồn tồn và đối soát

Khảo sát V6/V8/V12, StockOperations và StocktakeService: balance mới bắt đầu 0; thay đổi nghiệp vụ ghi movement cùng transaction. Không tìm thấy luồng seed tồn dương độc lập trong production code/migration.
Không thể khẳng định dữ liệu import/sửa ngoài ứng dụng luôn đúng. API kiểm tra toàn bộ lịch sử từng cặp trong phạm vi (kể cả sau cuối kỳ) với balance hiện tại.
Nếu lệch: 409 INVENTORY_HISTORY_INCONSISTENT, không suy đoán opening=0 hoặc chỉnh lịch sử/tồn.
Đối soát bằng chứng từ nguồn, thời điểm import, stocktake và snapshot có căn cứ.
Nếu phát hiện tồn khởi tạo trước ledger, cần migration riêng được duyệt với số lượng/thời điểm/chứng cứ và mô hình movement khởi tạo thích hợp. Không tự backdate stocktake hay sửa movement bất biến.
Chỉ dùng database kiểm thử trong triển khai này, chưa kiểm toán dữ liệu production.

## Hoạt động bán hàng

GET /api/reports/sales?from=2026-10-11&to=2026-10-11&pageSize=10

~~~json
{"items":[{"warehouseId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","warehouseCode":"A","warehouseName":"Kho A","confirmedOrderCount":1,"cancelledConfirmedOrderCount":0,"confirmedQuantity":10,"cancelledQuantity":0,"grossSalesAmount":1000,"discountAmount":100,"confirmedSalesAmount":900,"cancelledSalesAmount":0,"netSalesAmount":900}],"total":1,"page":1,"pageSize":10,"summary":{"confirmedOrderCount":1,"cancelledConfirmedOrderCount":0,"confirmedQuantity":10,"cancelledQuantity":0,"grossSalesAmount":1000,"discountAmount":100,"confirmedSalesAmount":900,"cancelledSalesAmount":0,"netSalesAmount":900},"period":{"from":"2026-10-11","to":"2026-10-11","timezone":"Asia/Ho_Chi_Minh"},"description":"Event-based sales value by confirmedAt/cancelledAt; not profit, cost of goods or VAT"}
~~~

Một dòng cho kho có sự kiện trong kỳ; không sinh dòng cho kho không có sự kiện.
confirmedOrderCount/confirmedQuantity: số đơn/tổng lượng lines có confirmedAt trong kỳ, kể cả đơn hiện CANCELLED.
grossSalesAmount = subtotal, discountAmount = discount_amount, confirmedSalesAmount = total_amount của các sự kiện xác nhận.
cancelledConfirmedOrderCount/cancelledQuantity/cancelledSalesAmount: đơn từng xác nhận có cancelledAt trong kỳ. Hủy nháp không tính.
netSalesAmount = confirmedSalesAmount - cancelledSalesAmount, có thể âm.
Đây là giá trị bán theo sự kiện, chưa phải lợi nhuận, không tính giá vốn/VAT.
Ví dụ hủy đơn 900 kỳ sau: kỳ trước vẫn 900; kỳ sau net=-900.
summary trên toàn bộ kho khớp phạm vi/bộ lọc, không phụ thuộc page/pageSize.

## Thu tiền ghi nhận theo thời gian hệ thống

GET /api/reports/payments?from=2026-10-11&to=2026-10-11

~~~json
{"items":[{"warehouseId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","warehouseCode":"A","warehouseName":"Kho A","createdPaymentCount":2,"cancelledPaymentCount":0,"recordedCashAmount":300,"recordedBankTransferAmount":200,"reversedCashAmount":0,"reversedBankTransferAmount":0,"netCashAmount":300,"netBankTransferAmount":200,"netRecordedAmount":500}],"total":1,"page":1,"pageSize":10,"summary":{"createdPaymentCount":2,"cancelledPaymentCount":0,"recordedCashAmount":300,"recordedBankTransferAmount":200,"reversedCashAmount":0,"reversedBankTransferAmount":0,"netCashAmount":300,"netBankTransferAmount":200,"netRecordedAmount":500},"period":{"from":"2026-10-11","to":"2026-10-11","timezone":"Asia/Ho_Chi_Minh"},"description":"Thu tiền ghi nhận theo thời gian hệ thống (createdAt/cancelledAt); cancellation reverses an incorrect record, not a refund or reconciled bank cash flow"}
~~~

createdPaymentCount/recordedCashAmount/recordedBankTransferAmount: createdAt trong kỳ, bất kể status hiện tại.
cancelledPaymentCount/reversedCashAmount/reversedBankTransferAmount: cancelledAt trong kỳ.
netCashAmount = recordedCashAmount - reversedCashAmount; netBankTransferAmount tương tự; netRecordedAmount là tổng hai net.
Ví dụ hai phiếu 300+200 bị hủy kỳ sau: kỳ trước vẫn recorded=500; kỳ sau net=-500.
Hủy là đảo ghi nhận sai, không hoàn tiền, không phải dòng tiền ngân hàng đã đối soát.
paymentDate vẫn ở API chi tiết phiếu thu hiện có; không lọc báo cáo này.
summary toàn phạm vi, không phụ thuộc phân trang.

## Công nợ hiện tại

GET /api/reports/receivables-summary?warehouseId=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa

~~~json
{"customerRemainingAmount":600,"customerCount":1,"customerOutstandingOrderCount":1,"walkInRemainingAmount":300,"walkInOutstandingOrderCount":1,"totalRemainingAmount":900,"asOf":"2026-10-13T02:00:00Z"}
~~~

Dùng view sales_payment_totals hiện có: paidAmount = SUM(phiếu ACTIVE), remaining = totalAmount-paidAmount của CONFIRMED; chỉ đơn còn nợ >0.
Khách ngừng hoạt động vẫn tính.
customerRemainingAmount/customerOutstandingOrderCount: tiền/số đơn có customer_id.
customerCount = COUNT DISTINCT customer_id còn nợ trong phạm vi.
walkInRemainingAmount/walkInOutstandingOrderCount: tiền/số đơn customer_id NULL, không tạo khách giả.
totalRemainingAmount = customerRemainingAmount + walkInRemainingAmount.
asOf là thời điểm đọc snapshot hiện tại, không phải công nợ tại cuối kỳ dashboard.
API chi tiết công nợ giữ nguyên.

## Tồn thấp hiện tại

GET /api/reports/low-stock?warehouseId=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa

~~~json
{"items":[{"warehouseId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","warehouseCode":"A","warehouseName":"Kho A","productId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","productCode":"P","productName":"Sản phẩm P","unit":"cay","quantity":0,"lowStockThreshold":5,"shortageQuantity":5}],"total":1,"page":1,"pageSize":10}
~~~

Cặp warehouses × products lọc/count/phân trang trong DB; chỉ kho/sản phẩm status=1.
Balance chưa có quantity=0. Ngưỡng 0 tắt cảnh báo.
Cảnh báo threshold>0 và quantity<threshold; shortageQuantity=threshold-quantity.
Không tải toàn bộ cặp vào bộ nhớ; dashboard lowStockPairCount đếm toàn phạm vi.

## Dashboard

GET /api/reports/dashboard?from=2026-10-11&to=2026-10-11
Schema Admin (sales/payments cùng schema summary của API tương ứng):

~~~json
{
  "generatedAt":"2026-10-13T02:00:00Z",
  "period":{"from":"2026-10-11","to":"2026-10-11","timezone":"Asia/Ho_Chi_Minh"},
  "periodSections":{
    "sales":{"confirmedOrderCount":1,"cancelledConfirmedOrderCount":0,"confirmedQuantity":10,"cancelledQuantity":0,"grossSalesAmount":1000,"discountAmount":100,"confirmedSalesAmount":900,"cancelledSalesAmount":0,"netSalesAmount":900},
    "payments":{"createdPaymentCount":2,"cancelledPaymentCount":0,"recordedCashAmount":300,"recordedBankTransferAmount":200,"reversedCashAmount":0,"reversedBankTransferAmount":0,"netCashAmount":300,"netBankTransferAmount":200,"netRecordedAmount":500},
    "timeBasis":"Event-based sales value by confirmedAt/cancelledAt; not profit, cost of goods or VAT; Thu tiền ghi nhận theo thời gian hệ thống (createdAt/cancelledAt); cancellation reverses an incorrect record, not a refund or reconciled bank cash flow"
  },
  "currentSections":{
    "receivables":{"customerRemainingAmount":600,"customerCount":1,"customerOutstandingOrderCount":1,"walkInRemainingAmount":300,"walkInOutstandingOrderCount":1,"totalRemainingAmount":900,"asOf":"2026-10-13T02:00:00Z"},
    "lowStock":{"lowStockPairCount":1,"asOf":"2026-10-13T02:00:00Z"},
    "timeBasis":"Current data read in one repeatable-read snapshot; independent of the selected period"
  }
}
~~~

User chỉ có REPORT_DASHBOARD_VIEW + REPORT_SALES_VIEW:

~~~json
{"generatedAt":"2026-10-13T02:00:00Z","period":{"from":"2026-10-11","to":"2026-10-11","timezone":"Asia/Ho_Chi_Minh"},"periodSections":{"sales":{"confirmedOrderCount":1,"cancelledConfirmedOrderCount":0,"confirmedQuantity":10,"cancelledQuantity":0,"grossSalesAmount":1000,"discountAmount":100,"confirmedSalesAmount":900,"cancelledSalesAmount":0,"netSalesAmount":900},"timeBasis":"Event-based sales value by confirmedAt/cancelledAt; not profit, cost of goods or VAT; Thu tiền ghi nhận theo thời gian hệ thống (createdAt/cancelledAt); cancellation reverses an incorrect record, not a refund or reconciled bank cash flow"}}
~~~

Chỉ REPORT_DASHBOARD_VIEW: nhận generatedAt/period, không periodSections/currentSections.
Tổng dashboard không dùng trang báo cáo. Đổi from/to không thay công nợ/tồn thấp hiện tại.
generatedAt/asOf đánh dấu snapshot request, không phải snapshot cuối ngày quá khứ.

## HTTP status và lỗi

Thành công mọi GET: 200. Lỗi giữ schema hiện có {timestamp,status,code,message,details}.

| HTTP | Code | Trường hợp |
| --- | --- | --- |
| 400 | INVALID_DATE_RANGE | Thiếu from/to, ngày sai định dạng/không tồn tại, from>to |
| 400 | INVALID_PAGINATION | page<1, pageSize ngoài 1–100 |
| 400 | INVALID_REQUEST | UUID/số query sai kiểu; from/to ở API hiện tại |
| 401 | UNAUTHORIZED | Thiếu JWT, JWT sai/hết hạn, user không hoạt động |
| 403 | FORBIDDEN | Thiếu permission endpoint |
| 403 | WAREHOUSE_ACCESS_DENIED | Kho chỉ định ngoài phạm vi |
| 404 | WAREHOUSE_NOT_FOUND | Admin chọn kho không tồn tại |
| 409 | NUMERIC_OVERFLOW | Metric trả về vượt signed Long |
| 409 | INVENTORY_HISTORY_INCONSISTENT | Balance không khớp toàn bộ ledger trong phạm vi |

Ví dụ lỗi:
~~~json
{"timestamp":"2026-10-13T02:00:00Z","status":409,"code":"NUMERIC_OVERFLOW","message":"Amount limit exceeded","details":{}}
~~~

Chưa triển khai Excel/PDF, biểu đồ FE, giá vốn, lợi nhuận, VAT hay công nợ lịch sử.
File thay đổi/kết quả thực chạy: [reports-verification.md](reports-verification.md).
