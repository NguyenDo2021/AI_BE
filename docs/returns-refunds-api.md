# Trả hàng và hoàn tiền — API và tích hợp FE

Áp dụng từ V15/V16. Prefix /api, Bearer JWT bắt buộc. Response trực tiếp, không bọc data.
Chỉ nhận hàng còn bán được, nhập lại kho gốc. Không triển khai tồn đầu kỳ.
Chưa hỗ trợ hàng hỏng, đổi hàng, phí trả, hoàn không gắn hàng trả hoặc điều chỉnh phiếu trả CONFIRMED.

## Endpoints và quyền

| Method / route (sau /api) | Permission | Response |
|---|---|---|
| GET /sales-returns | SALES_RETURN_VIEW | 200 Page<ReturnResponse> |
| GET /sales-returns/{id} | SALES_RETURN_VIEW | 200 ReturnResponse |
| POST /sales-returns | SALES_RETURN_CREATE | 201 ReturnResponse |
| PUT /sales-returns/{id} | SALES_RETURN_UPDATE | 200 ReturnResponse |
| POST /sales-returns/{id}/confirm | SALES_RETURN_CONFIRM | 200 ReturnResponse |
| POST /sales-returns/{id}/cancel | SALES_RETURN_CANCEL | 200 ReturnResponse |
| GET /sales-orders/returnable-orders | SALES_RETURN_VIEW | 200 Page<ReturnableOrder> |
| GET /sales-orders/{id}/returnable-lines | SALES_RETURN_VIEW | 200 ReturnableLine[] |
| GET /refunds | REFUND_VIEW | 200 Page<RefundResponse> |
| GET /refunds/{id} | REFUND_VIEW | 200 RefundResponse |
| POST /sales-orders/{id}/refunds | REFUND_CREATE | 201 RefundMutation |
| POST /refunds/{id}/cancel | REFUND_CANCEL | 200 RefundMutation |
| GET /refundables | REFUND_VIEW | 200 Page<OrderSettlementSummary> |

Seed tám permission, không tự cấp cho role/user. ADMIN giữ ngoại lệ role hiện tại.
Nhân viên cần permission đang hoạt động và được gán kho; retry cũng kiểm tra lại quyền/phạm vi.
Danh sách chỉ đếm/trả dữ liệu trong phạm vi. Kho/đơn chỉ định ngoài phạm vi trả 403.
returnable-lines/refundables không yêu cầu SALES_ORDER_VIEW hoặc INVENTORY_VIEW.
Kho/khách/sản phẩm inactive vẫn xử lý được đơn cũ còn CONFIRMED.

## Request

POST /sales-returns:

~~~json
{
  "salesOrderId": "11111111-1111-1111-1111-111111111111",
  "returnDate": "2026-10-16",
  "reason": "Khách trả phần hàng chưa sử dụng",
  "note": null,
  "lines": [{"productId":"22222222-2222-2222-2222-222222222222","quantity":2}]
}
~~~

PUT /sales-returns/{id} thay thế toàn bộ dữ liệu sửa được; không nhận salesOrderId:

~~~json
{
  "returnDate": "2026-10-16",
  "reason": "Khách trả hàng",
  "note": null,
  "lines": [{"productId":"22222222-2222-2222-2222-222222222222","quantity":2}],
  "version": 0
}
~~~

Confirm: {"version":0}. Cancel DRAFT: {"version":0,"reason":"Nhập sai phiếu"}.
Chỉ sửa/hủy DRAFT. Confirm lặp trả phiếu CONFIRMED hiện tại, kể cả version cũ, không nhập thêm.
CANCELLED không confirm được. Hủy nháp không tác động tồn/giá trị đơn.

POST /sales-orders/{id}/refunds, bắt buộc header Idempotency-Key: UUID:

~~~json
{
  "amount": 300000,
  "refundDate": "2026-10-16",
  "method": "CASH",
  "reference": null,
  "reason": "Hoàn tiền hàng đã trả",
  "note": null
}
~~~

Cancel refund: {"reason":"Ghi nhận nhầm chi tiền"}. Đây là đảo ghi nhận sai, không phải thu tiền thật.
Không có PUT/DELETE refund. Không tự sinh refund khi xác nhận hàng trả.
Cùng user/key/payload trả cùng phiếu và summary hiện tại, kể cả phiếu CANCELLED.
Đổi payload/đơn với cùng user/key trả 409. Unique (created_by,idempotency_key) ở DB.
Namespace key của phiếu thu và phiếu hoàn độc lập.

Validation:

- Ngày YYYY-MM-DD. reason không trắng, tối đa 2000; note nullable, tối đa 2000.
- quantity integer JSON dương trong Long; 1–1000 dòng, không trùng productId.
- amount integer JSON dương trong Long; method CASH/BANK_TRANSFER; reference nullable tối đa 200.
- version integer JSON không âm trong Long.
- Request mới từ chối mọi field không khai báo, kể cả field lạ trong dòng/confirm/cancel.
- Client không gửi kho/khách/actor/code/snapshot/status hoặc returnAmount.
- quantity/amount/version không nhận số dạng string, thập phân, exponent hoặc vượt Long.

## Response schemas đầy đủ

UUID, Date, Instant là chuỗi UUID, YYYY-MM-DD và ISO-8601 UTC.
Int64 là integer JSON exact trong giới hạn Long, không phải floating point.
Field nullable có thể được bỏ qua theo Jackson hiện tại.

~~~ts
type Page<T> = {items:T[]; total:Int64; page:number; pageSize:number};
type ReturnStatus = "DRAFT" | "CONFIRMED" | "CANCELLED";
type PaymentStatus = "UNPAID" | "PARTIALLY_PAID" | "PAID";
type CustomerSnapshot = {
  code:string|null; name:string; phone:string|null; address:string|null
};
type ReturnableLine = {
  productId:UUID; productCode:string; productName:string; unit:string;
  soldQuantity:Int64; returnedQuantity:Int64; returnableQuantity:Int64;
  lineTotal:Int64; allocatedDiscountAmount:Int64; netLineAmount:Int64
};
type ReturnLine = {
  productId:UUID; quantity:Int64; returnAmount:Int64; soldQuantity:Int64;
  allocatedDiscountAmount:Int64; netLineAmount:Int64;
  productCode:string; productName:string; unit:string
};
type ReturnResponse = {
  id:UUID; code:string; salesOrderId:UUID; warehouseId:UUID; customerId:UUID|null;
  returnDate:Date; reason:string; note:string|null; status:ReturnStatus;
  totalAmount:Int64; version:Int64;
  createdBy:UUID; createdAt:Instant; updatedBy:UUID; updatedAt:Instant;
  confirmedBy:UUID|null; confirmedAt:Instant|null;
  cancelledBy:UUID|null; cancelledAt:Instant|null; cancellationReason:string|null;
  lines:ReturnLine[]
};
type RefundResponse = {
  id:UUID; code:string; salesOrderId:UUID; warehouseId:UUID; customerId:UUID|null;
  amount:Int64; refundDate:Date; method:"CASH"|"BANK_TRANSFER";
  reference:string|null; reason:string; note:string|null; status:"ACTIVE"|"CANCELLED";
  createdBy:UUID; createdAt:Instant; cancelledBy:UUID|null; cancelledAt:Instant|null;
  cancellationReason:string|null
};
type SettlementFields = {
  totalAmount:Int64; returnedAmount:Int64; adjustedTotalAmount:Int64;
  paidAmount:Int64; refundedAmount:Int64; netPaidAmount:Int64;
  remainingAmount:Int64; refundableAmount:Int64; paymentStatus:PaymentStatus|null
};
type OrderSettlementSummary = SettlementFields & {
  salesOrderId:UUID; code:string; warehouseId:UUID; customerId:UUID|null;
  customerSnapshot:CustomerSnapshot|null; saleDate:Date; status:ReturnStatus
};
type RefundMutation = {refund:RefundResponse; order:OrderSettlementSummary};
~~~

Mã BE sinh TH-/HT- cộng UUID không dấu gạch, không suy ra ngày từ mã.
DRAFT lưu báo giá dự kiến tại lần save gần nhất; CONFIRMED lưu số tiền chính thức bất biến.
Không tự thay đổi bản nháp khi phiếu khác xác nhận.

Bộ lọc/sắp xếp:

- sales-returns: warehouseId, salesOrderId, status, from/to theo returnDate, hai đầu inclusive.
- refunds: warehouseId, salesOrderId, customerId, status, method, from/to theo refundDate, inclusive.
- refundables: warehouseId; đơn CONFIRMED có refundableAmount > 0, gồm khách có tên/khách lẻ.
- page mặc định 1, pageSize mặc định 10, từ 1 đến 100.
- sales-returns/refunds sắp createdAt DESC,id DESC; refundables sắp refundableAmount DESC,id ASC.
- from/to có thể bỏ một hoặc cả hai; from > to trả 400. refundables không có bộ lọc ngày.
- API biến động giữ bộ lọc productId/from/to theo performedAt hiện có; chưa có bộ lọc type.

## Phân bổ giảm giá và trả nhiều lần

Dữ liệu tái lập phân bổ là subtotal, discountAmount và toàn bộ dòng đơn gốc,
bất biến sau xác nhận (V9 bảo vệ). Phiếu trả lưu thêm soldQuantity,
allocatedDiscountAmount, netLineAmount và snapshot dòng gốc.

Mỗi dòng: chia nguyên exact lineTotal × discountAmount / subtotal.
Phần nguyên là giảm giá ban đầu; cộng 1 VND cho các dòng phần dư lớn nhất đến khi đủ discountAmount.
Bằng phần dư thì productId theo chuỗi UUID tăng dần. Java dùng BigInteger,
PostgreSQL dùng NUMERIC/div để tránh làm tròn phép chia. subtotal=0 thì mọi giá trị trả bằng 0.

Giá trị lần này:
floor(netLineAmount × (before+quantity) / soldQuantity)
− floor(netLineAmount × before / soldQuantity).

Ví dụ A < B, mỗi dòng bán 3 sản phẩm giá 1 VND: subtotal=6, discount=1.
Giảm giá A=1, B=0; net A=2, B=3.
Trả A từng sản phẩm: 0,1,1 VND; trả B: 1,1,1 VND. Trả hết đúng 5 VND.
Giá danh mục thay đổi không ảnh hưởng.

Hai nháp cùng trả 1 của dòng net=2,sold=3 cùng báo giá 0.
Sau nháp thứ nhất confirm, nháp thứ hai nhận:

~~~json
{
  "timestamp":"2026-10-16T03:00:00Z",
  "status":409,
  "code":"RETURN_QUOTE_CHANGED",
  "message":"Return quote changed; review and save the draft again",
  "details":{
    "version":0,
    "quote":{
      "totalAmount":1,
      "lines":[{
        "productId":"22222222-2222-2222-2222-222222222222",
        "quantity":1,"returnAmount":1,"soldQuantity":3,
        "allocatedDiscountAmount":1,"netLineAmount":2,
        "productCode":"P","productName":"Sản phẩm","unit":"cay"
      }]
    }
  }
}
~~~

FE hiển thị báo giá mới để người dùng xem xét, lưu PUT rồi confirm lại.
409 không thay đổi nháp/tồn. Hạn mức không còn đủ thì RETURN_EXCEEDS_QUANTITY.

## Settlement và schema API cũ

~~~text
returnedAmount = tổng sales_returns CONFIRMED.total_amount
adjustedTotalAmount = totalAmount - returnedAmount
paidAmount = tổng payments ACTIVE.amount
refundedAmount = tổng refunds ACTIVE.amount
netPaidAmount = paidAmount - refundedAmount
remainingAmount = max(adjustedTotalAmount - netPaidAmount, 0)
refundableAmount = max(netPaidAmount - adjustedTotalAmount, 0)
~~~

remaining/refundable bằng 0 cho DRAFT/CANCELLED; paymentStatus không áp dụng như trước.
CONFIRMED: remaining>0,netPaid=0 → UNPAID; remaining>0,netPaid>0 → PARTIALLY_PAID;
remaining=0 → PAID. PAID vẫn có thể cần hoàn.

- Giữ totalAmount đơn gốc và amount in trên biên lai thu.
- SalesResponse giữ field cũ, thêm returnedAmount,adjustedTotalAmount,refundedAmount,
  netPaidAmount,refundableAmount. remainingAmount/paymentStatus theo công thức mới.
- OrderPaymentSummary của tạo/hủy thu giữ salesOrderId,totalAmount,paidAmount,remainingAmount,
  paymentStatus và thêm năm field ở trên.
- Thu/hoàn/hủy thu/hủy hoàn không đổi version đơn. Phiếu trả chỉ đổi version của chính nó.
- ReceivableResponse giữ totalAmount/paidAmount nghĩa cũ; remainingAmount là nợ đã điều chỉnh,
  không tính lại bằng total-paid từ các field cũ.
- /customers/{id}/receivables và /receivables/walk-in-orders dùng nợ mới và SalesResponse mở rộng.
- /refundables trả đủ summary, không tạo khách giả hoặc buộc gọi API đơn.
- Hủy thu cho phép tăng nợ, nhưng paid mới >= refunded. Thu mới <= remaining, hoàn mới <= refundable.

Đơn 1.000.000, thu 600.000:

| Tổng trả | Tổng điều chỉnh | Đã hoàn | Net paid | Còn nợ | Cần hoàn |
|---:|---:|---:|---:|---:|---:|
| 200.000 | 800.000 | 0 | 600.000 | 200.000 | 0 |
| 700.000 | 300.000 | 0 | 600.000 | 0 | 300.000 |
| 700.000 | 300.000 | 300.000 | 300.000 | 0 | 0 |

Chưa thu mà trả chỉ giảm nợ. Hàng giá 0 vẫn nhập lại kho, không tự sinh hoàn.

## Report, dashboard, movement và Excel

- InventoryRow thêm saleReturnQuantity từ SALE_RETURN.
  closingQuantity = openingQuantity + receiptQuantity − receiptCancellationQuantity
  − saleQuantity + saleCancellationQuantity + saleReturnQuantity
  + adjustmentIncreaseQuantity − adjustmentDecreaseQuantity.
  openingQuantity báo cáo là tổng ledger trước kỳ; không nhập tồn đầu kỳ.
- SalesSummary/SalesRow thêm returnedSalesAmount theo sales_returns.confirmedAt.
  netSalesAmount = confirmedSalesAmount − cancelledSalesAmount − returnedSalesAmount.
- PaymentSummary/PaymentRow thêm createdRefundCount,cancelledRefundCount,
  recordedRefundCashAmount,recordedRefundBankTransferAmount,
  reversedRefundCashAmount,reversedRefundBankTransferAmount,
  netRefundedCashAmount,netRefundedBankTransferAmount,netRefundedAmount,netCashFlowAmount.
  Net từng method = recordedRefund − reversedRefund; netRefundedAmount = tổng hai method.
  netCashFlowAmount = netRecordedAmount − netRefundedAmount.
  netRecordedAmount/netCashAmount/netBankTransferAmount giữ nghĩa thu ròng.
- Hoàn tạo/hủy theo createdAt/cancelledAt, không theo refundDate.
  Report from/to từ đầu ngày from đến trước đầu ngày sau to, Asia/Ho_Chi_Minh.
- Sales dùng confirmedAt/cancelledAt đơn và confirmedAt trả;
  inventory dùng performedAt; công nợ/refundables là hiện tại.
- Dashboard.currentSections thêm refunds:
  {refundableOrderCount:Int64,refundableAmount:Int64,asOf:Instant}.
  Chỉ trả section này khi có REFUND_VIEW hiện tại và quyền vào dashboard.
  periodSections.payments thuộc REPORT_PAYMENT_VIEW, gồm các metric thu/hoàn theo kỳ.
- /reports/receivables-summary dùng nợ mới.
- Excel thêm cột cuối và summary theo thứ tự DTO, giữ vị trí cột cũ.
  Giá trị trên 15 chữ số vẫn ghi text chính xác theo cơ chế hiện có.
- MovementResponse thêm salesReturnId,salesReturnCode; type SALE_RETURN.
  Mã/tên/đơn vị từ dòng phiếu trả; salesOrderId trực tiếp của movement SALE_RETURN là null,
  liên kết đơn qua phiếu trả. SALE_CANCEL vẫn riêng.
- Đây là ghi nhận hệ thống, không khẳng định đối soát ngân hàng.

## Transaction và migration

Thứ tự khóa kho → đơn → chứng từ (hoặc actor với key) → sản phẩm theo UUID → balances.
Dùng JDBC cùng datasource/JPA transaction manager cũ.
Khóa kho serialize cả balance chưa tồn tại; khóa đơn chung với bán/thu/hủy.
Nháp không giữ chỗ số lượng.

Confirm kiểm tra version, mọi dòng, hạn mức, báo giá và tổng tiền trước khi nhập kho.
Snapshot/amount/status/audit/movement/balance/revision cùng commit hoặc rollback.
Mỗi product tăng quantity/revision một lần; kiểm kê cũ nhận STOCKTAKE_STALE.
Unique movement theo return/product/type và key theo actor bảo vệ retry.
Có phiếu trả CONFIRMED bất kỳ, kể cả 0 VND, chặn hủy đơn kiểu cũ.

- V15: sales_returns,sales_return_lines,refunds, source movement, indexes, permission,
  mở rộng sales_payment_totals.
- V16: trigger lịch sử, source/quantity/valuation/movement, cap hoàn/thu mới,
  chặn hủy thu dưới tổng hoàn và chặn hủy đơn có hàng trả.
- Không sửa V1–V14. Không tự cấp quyền cho role/user.

## Error contract

Giữ envelope {timestamp,status,code,message,details}.
details validation cũ vẫn field→string; RETURN_QUOTE_CHANGED có quote có cấu trúc và integer exact.

| HTTP | Code | Ý nghĩa / xử lý |
|---|---|---|
| 400 | INVALID_REQUEST | JSON/type/field lạ sai |
| 400 | VALIDATION_ERROR | quantity/amount/version/reason/size sai |
| 400 | INVALID_IDEMPOTENCY_KEY | header thiếu/sai UUID |
| 400 | INVALID_PAGINATION / INVALID_DATE_RANGE | pagination/khoảng ngày sai |
| 400 | DUPLICATE_PRODUCT / PRODUCT_NOT_IN_SALES_ORDER | dòng trùng/không thuộc đơn |
| 401 | UNAUTHORIZED | token hoặc tài khoản không hợp lệ |
| 403 | FORBIDDEN / WAREHOUSE_ACCESS_DENIED | thiếu quyền / ngoài kho |
| 404 | SALES_RETURN_NOT_FOUND / REFUND_NOT_FOUND | không có phiếu |
| 404 | SALES_ORDER_NOT_FOUND / WAREHOUSE_NOT_FOUND | nguồn không có |
| 409 | VERSION_CONFLICT | reload phiếu trả |
| 409 | INVALID_SALES_ORDER_STATUS | đơn không còn CONFIRMED |
| 409 | INVALID_SALES_RETURN_STATUS | phiếu không ở DRAFT |
| 409 | RETURN_ADJUSTMENT_NOT_SUPPORTED | chưa hỗ trợ điều chỉnh phiếu trả CONFIRMED |
| 409 | RETURN_EXCEEDS_QUANTITY / RETURN_EXCEEDS_TOTAL | quá lượng / giá trị đơn |
| 409 | RETURN_QUOTE_CHANGED | xem details.quote, lưu PUT rồi confirm |
| 409 | SALES_ORDER_HAS_RETURNS | không hủy đơn có trả CONFIRMED |
| 409 | SALES_ORDER_HAS_PAYMENTS | chặn hủy đơn có thu ACTIVE như cũ |
| 409 | REFUND_EXCEEDS_REFUNDABLE | reload summary |
| 409 | PAYMENT_EXCEEDS_REMAINING | thu vượt nợ điều chỉnh |
| 409 | PAYMENT_BELOW_REFUNDED | hủy thu sẽ làm paid < refunded |
| 409 | IDEMPOTENCY_CONFLICT | key đã dùng cho payload khác |
| 409 | NUMERIC_OVERFLOW | vượt quantity/amount/revision/version |
| 409 | RESOURCE_CONFLICT | ràng buộc DB bảo vệ dữ liệu |
| 405 | METHOD_NOT_ALLOWED | phương thức không hỗ trợ |

## FE cần tích hợp

1. Gán tám permission bằng quản trị hiện có; nút theo từng quyền, vẫn xử lý 403.
2. Tìm mã qua [returnable-orders](returnable-orders-api.md), chọn item.id (UUID) → returnable-lines; hiện lượng bán/đã trả/còn trả, snapshot và báo giá.
3. Gửi quantity, không gửi giá/amount/kho/khách; PUT đủ toàn bộ dữ liệu và version.
4. DRAFT là dự kiến; xử lý RETURN_QUOTE_CHANGED bằng xem xét/lưu lại.
5. Ẩn sửa/hủy/xóa phiếu trả CONFIRMED; thông báo chưa hỗ trợ điều chỉnh.
6. Tách trả hàng và chi hoàn, không tự refund khi confirm trả.
7. Thêm refundables/lịch sử refund; hiện refundableAmount riêng dù PAID.
8. Sinh UUID key một lần cho lần chi, giữ nguyên key/payload khi retry.
   Retry có thể trả CANCELLED; không tự đổi key/tạo phiếu thay thế.
9. Hủy thu/hoàn phải có lý do, mô tả là sửa ghi nhận sai.
10. Hiện nợ và giới hạn thu theo summary mới, không tự lấy total-paid.
11. Thêm SALE_RETURN/source link, cột report, dòng tiền và section dashboard theo quyền.
12. Parse/serialize JSON integer lớn bằng công cụ lossless; dùng BigInt/decimal exact,
    không đổi wire contract sang chuỗi. Biên lai thu giữ amount từng phiếu.

## File và kiểm chứng

Kết quả/lệnh chạy thực tế: [returns-refunds-verification.md](returns-refunds-verification.md).
Module mới: src/main/java/com/frontendbase/api/returns và refund.
Tích hợp: PermissionCodes, ApiException/ApiErrorResponse/GlobalExceptionHandler,
SalesDtos/SalesRepository/SalesService, PaymentDtos/PaymentService,
StockDtos/StockRepository/StockService/StockOperations,
ReportDtos/ReportRepository/ReportService/ReportController/ReportExportService.
Test mới: ReturnsApiIntegrationTest, ReturnValuationTest; H2 adapter/cleanup chỉ thuộc test.

Tìm đơn theo mã: [contract returnable-orders](returnable-orders-api.md), gồm request/response, phạm vi, pagination và lỗi.
