# Thanh toán và công nợ khách hàng

Các route bên dưới có tiền tố `/api`, cần `Authorization: Bearer <token>`.
Response trả trực tiếp, không bọc `data`. Danh sách dùng `{items,total,page,pageSize}`;
`page` bắt đầu từ 1 (mặc định 1), `pageSize` từ 1 đến 100 (mặc định 10).
UUID dùng chuỗi; tiền VND là số nguyên trong khoảng 0..9223372036854775807.
FE nên giữ số tiền lớn bằng kiểu xử lý số nguyên chính xác: JavaScript Number chỉ chính xác đến 9007199254740991.

## Quy tắc nghiệp vụ

Chỉ thu cho đơn CONFIRMED; không tự tạo phiếu khi xác nhận đơn. Mỗi phiếu có amount > 0,
không vượt remainingAmount. Thu/hủy phiếu không thay đổi tồn hoặc biến động kho.
Khách và kho ngừng hoạt động vẫn có thể thanh toán đơn đã xác nhận nếu người gọi còn quyền/phạm vi.

`paidAmount` là tổng các phiếu ACTIVE. Với CONFIRMED:
`remainingAmount = totalAmount - paidAmount`; UNPAID khi paidAmount=0 và remainingAmount>0,
PARTIALLY_PAID khi đã thu nhưng còn nợ, PAID khi remainingAmount=0 (kể cả totalAmount=0).
Với DRAFT/CANCELLED: remainingAmount=0, paymentStatus không áp dụng và được bỏ khỏi JSON
theo cấu hình non_null hiện tại. paidAmount vẫn biểu thị phiếu hiệu lực; luồng bình thường
không cho hủy đơn có paidAmount>0.

Đây là công nợ **hiện tại**, không phải số dư lịch sử tại paymentDate hay một ngày quá khứ.
Lọc ngày chỉ chọn ngày thu thực tế của phiếu, không biến API thành báo cáo công nợ lịch sử.

Phiếu không có API sửa/xóa. Hủy phiếu cần lý do, lưu người/thời gian hủy.
Hủy là đảo ghi nhận sai, không phải hoàn tiền. Chưa hỗ trợ hoàn tiền, trả hàng,
công nợ nhà cung cấp, phân bổ một khoản thu cho nhiều đơn hay hóa đơn điện tử.
Đơn đã thu tiền bị chặn hủy với SALES_ORDER_HAS_PAYMENTS; FE giải thích chưa hỗ trợ
hủy đơn kèm hoàn tiền. Không hướng dẫn hủy khoản thu thật để vượt chặn này.

## Permission và phạm vi

| API | Permission |
|---|---|
| POST /sales-orders/{id}/payments | PAYMENT_CREATE |
| GET /sales-orders/{id}/payments | PAYMENT_VIEW |
| GET /payments, GET /payments/{id} | PAYMENT_VIEW |
| POST /payments/{id}/cancel | PAYMENT_CANCEL |
| GET /customers/{id}/receivables | RECEIVABLE_VIEW |
| GET /receivables, GET /receivables/walk-in-orders | RECEIVABLE_VIEW |

ADMIN dùng ngoại lệ role đang hoạt động của dự án. User khác cần permission đang hoạt động
trong role đang hoạt động và được gán kho liên quan. Các API mới kiểm tra RBAC trực tiếp
từ database, nên JWT cũ không giữ quyền đã bị thu hồi. Migration chỉ đăng ký permission,
không tự gán cho role/user.

PAYMENT_VIEW không đòi SALES_ORDER_VIEW. Chọn/xem đơn qua API /sales-orders vẫn kiểm tra
SALES_ORDER_VIEW riêng. RECEIVABLE_VIEW cho phép xem các đơn còn nợ trả kèm trong API công nợ.
Danh sách lọc phạm vi trước count/pagination. Kho chỉ định ngoài phạm vi trả 403;
danh sách không chỉ định kho chỉ chứa dữ liệu trong phạm vi.

## Schema phiếu và kết quả ghi nhận

`PaymentResponse`:

```json
{
  "id": "UUID",
  "code": "PT-<32 ký tự UUID>",
  "salesOrderId": "UUID",
  "warehouseId": "UUID",
  "customerId": "UUID",
  "amount": 600000,
  "paymentDate": "2026-10-09",
  "method": "CASH",
  "reference": "optional bank reference",
  "note": "Thu lần đầu",
  "status": "ACTIVE",
  "createdBy": "UUID",
  "createdAt": "2026-10-09T03:00:00Z"
}
```

Khi CANCELLED có thêm `cancelledBy`, `cancelledAt`, `cancellationReason`.
Các trường nullable bị bỏ khỏi JSON: customerId của khách lẻ; reference/note chưa nhập;
các trường hủy của phiếu ACTIVE. Code/id và người/kho/khách được backend sinh hoặc suy ra.

Kết quả create/cancel là `{payment: PaymentResponse, order: OrderPaymentSummary}`:

```json
{
  "payment": {
    "id": "UUID",
    "code": "PT-...",
    "salesOrderId": "UUID",
    "warehouseId": "UUID",
    "customerId": "UUID",
    "amount": 600000,
    "paymentDate": "2026-10-09",
    "method": "CASH",
    "note": "Thu lần đầu",
    "status": "ACTIVE",
    "createdBy": "UUID",
    "createdAt": "2026-10-09T03:00:00Z"
  },
  "order": {
    "salesOrderId": "UUID",
    "totalAmount": 1000000,
    "paidAmount": 600000,
    "remainingAmount": 400000,
    "paymentStatus": "PARTIALLY_PAID"
  }
}
```

## POST /api/sales-orders/{id}/payments

Header `Idempotency-Key` bắt buộc là UUID đủ dạng 8-4-4-4-12.
Body:

```json
{
  "amount": 600000,
  "paymentDate": "2026-10-09",
  "method": "CASH",
  "reference": null,
  "note": "Thu lần đầu"
}
```

amount/paymentDate/method bắt buộc. method là CASH hoặc BANK_TRANSFER.
reference tối đa 200 ký tự, note tối đa 2000; không tự trim hai trường này.
paymentDate là ngày ISO thực tế thu; không áp đặt giới hạn ngày tương lai.
amount chỉ nhận token JSON integer dương, không nhận string, số lẻ, float token (600000.0),
0/âm hoặc ngoài Long. Field khác, kể cả warehouseId/customerId/actor/createdBy, bị từ chối 400.

Success 201, gồm phiếu và tổng hiện tại như trên; retry thành công cũng trả 201.
Lỗi riêng: 400 INVALID_IDEMPOTENCY_KEY, INVALID_REQUEST, VALIDATION_ERROR;
404 SALES_ORDER_NOT_FOUND; 409 INVALID_SALES_ORDER_STATUS, PAYMENT_EXCEEDS_REMAINING,
IDEMPOTENCY_CONFLICT, NUMERIC_OVERFLOW.

### Idempotency và retry

FE sinh một UUID trước lần gửi đầu, giữ nguyên key và payload cho retry do timeout/mất mạng.
Key được định danh theo **user thực hiện**, không theo kho/đơn; database có UNIQUE(created_by,idempotency_key).
Payload so sánh gồm salesOrderId, amount, paymentDate, method, reference, note.
Không phân biệt thứ tự field; null và trường tùy chọn bị bỏ đều là null.
Thay đổi nội dung string (kể cả khoảng trắng), đơn, ngày, cách thu hoặc số tiền là payload khác, trả 409.
Một user khác có không gian key riêng.

```http
POST /api/sales-orders/<id>/payments
Authorization: Bearer <token>
Idempotency-Key: b4327560-bcca-4c1b-9209-2a1b3f7a0417
Content-Type: application/json

{"amount":600000,"paymentDate":"2026-10-09","method":"CASH","note":"Thu lần đầu"}
```

Gửi lại chính request trên: cùng payment.id, không có phiếu mới. Tổng order là tổng **hiện tại**,
không phải snapshot lần đầu. Nếu phiếu đã hủy, trả phiếu CANCELLED hiện tại và tổng mới;
kể cả đơn đã hủy sau khi không còn khoản thu hiệu lực. Không tạo phiếu thay thế.
Muốn ghi nhận lần thu mới phải dùng key mới. Retry luôn kiểm tra lại request, quyền và phạm vi hiện tại.
Hai request cùng key đồng thời được tuần tự hóa; payload giống nhau nhận cùng phiếu,
payload khác có một request thành công và một 409.

## GET /api/sales-orders/{id}/payments

Query: `status=ACTIVE|CANCELLED` tùy chọn, page/pageSize.
200: `{items: PaymentResponse[],total,page,pageSize}`.
Xem cả lịch sử đơn CANCELLED. Không cần SALES_ORDER_VIEW.
404 SALES_ORDER_NOT_FOUND; 400 INVALID_REQUEST/VALIDATION_ERROR/INVALID_PAGINATION.

## POST /api/payments/{id}/cancel

Body: `{"reason":"Ghi nhận nhầm khoản thu"}`. reason bắt buộc, không trắng,
tối đa 2000 ký tự; lưu sau khi trim. 200 trả MutationResponse với phiếu CANCELLED và tổng cập nhật.
Hủy lặp trả phiếu đã hủy, giữ người/thời gian/lý do hủy đầu tiên, không đảo tiền lần nữa.
Body vẫn phải hợp lệ và người gọi vẫn phải có PAYMENT_CANCEL/phạm vi, kể cả khi hủy lặp.
404 PAYMENT_NOT_FOUND; 400 INVALID_REQUEST/VALIDATION_ERROR.

## GET /api/payments

Query tùy chọn: warehouseId, salesOrderId, customerId (UUID),
status (ACTIVE/CANCELLED), method (CASH/BANK_TRANSFER), from/to (ngày paymentDate), page/pageSize.
from/to bao gồm cả hai đầu. Sắp xếp createdAt DESC, id DESC.
200: `{items: PaymentResponse[],total,page,pageSize}`.
Chỉ định salesOrderId không tồn tại: 404 SALES_ORDER_NOT_FOUND; ngoài phạm vi: 403.
400 INVALID_DATE_RANGE khi from>to, INVALID_REQUEST khi UUID/enum/ngày sai,
VALIDATION_ERROR/INVALID_PAGINATION khi page/pageSize sai.

## GET /api/payments/{id}

200 trả PaymentResponse trực tiếp. 404 PAYMENT_NOT_FOUND.
Cho xem phiếu ACTIVE/CANCELLED và lịch sử đơn đã hủy.

## GET /api/customers/{id}/receivables

Query: page/pageSize. 200:

```json
{
  "customerId": "UUID",
  "warehouseId": "UUID",
  "remainingAmount": 2400000,
  "orders": {
    "items": ["SalesResponse (schema bên dưới)"],
    "total": 2,
    "page": 1,
    "pageSize": 1
  }
}
```

orders.items chứa các object SalesResponse đầy đủ (chuỗi trên chỉ minh họa vị trí).
Chỉ đơn CONFIRMED, remainingAmount>0 của khách và kho tương ứng.
remainingAmount tính trên **toàn bộ** đơn khớp, không chỉ trang hiện tại.
Khách không còn nợ trả remainingAmount=0 và orders.total=0.
Khách ngừng hoạt động vẫn xem được.
404 CUSTOMER_NOT_FOUND; 400 INVALID_REQUEST/VALIDATION_ERROR/INVALID_PAGINATION;
409 NUMERIC_OVERFLOW nếu tổng khách vượt Long.MAX_VALUE.

## GET /api/receivables

Query tùy chọn warehouseId, customerId, keyword, page/pageSize.
keyword tìm không phân biệt hoa thường trên mã/tên/điện thoại khách hiện tại;
ký tự % và _ được tìm như ký tự thường.
200:

```json
{
  "items": [{
    "customerId": "UUID",
    "warehouseId": "UUID",
    "customerCode": "KH-...",
    "customerName": "Nguyễn Văn A",
    "outstandingOrderCount": 2,
    "totalAmount": 3000000,
    "paidAmount": 600000,
    "remainingAmount": 2400000
  }],
  "total": 1,
  "page": 1,
  "pageSize": 10
}
```

Mỗi item gộp theo khách/kho, chỉ những khách có nợ. Các amount/count của item được tính trên
toàn bộ **đơn còn nợ** của khách (không gồm đơn đã trả đủ), độc lập page/pageSize.
total là số nhóm khách/kho khớp bộ lọc, không phải tổng số tiền hoặc số đơn.
Không trả grand total toàn danh sách. Sắp xếp remainingAmount DESC, warehouseId, customerId.
Không có khách giả cho đơn khách lẻ. customerId không tồn tại trả 404 CUSTOMER_NOT_FOUND;
ngoài phạm vi trả 403. 409 NUMERIC_OVERFLOW nếu tổng tiền một nhóm vượt Long.MAX_VALUE.

## GET /api/receivables/walk-in-orders

FE đặt tên khu vực **“Đơn khách lẻ chưa thanh toán”**.
Query tùy chọn warehouseId, page/pageSize.
200: `{items: SalesResponse[],total,page,pageSize}`; chỉ đơn CONFIRMED,
customerId null và remainingAmount>0. Không yêu cầu SALES_ORDER_VIEW.
Các đơn này không xuất hiện trong /receivables hoặc công nợ của khách.
400 INVALID_REQUEST/VALIDATION_ERROR/INVALID_PAGINATION, 403 WAREHOUSE_ACCESS_DENIED.

## Schema đơn bán bổ sung

Các field hiện có trong [sales-api.md](sales-api.md) giữ nguyên. Tất cả response đơn bán
(create/update/confirm/cancel/detail/list và đơn trả trong API công nợ) có thêm:

```json
{
  "totalAmount": 1000000,
  "paidAmount": 600000,
  "remainingAmount": 400000,
  "paymentStatus": "PARTIALLY_PAID"
}
```

Đơn DRAFT/CANCELLED trả paidAmount=0, remainingAmount=0 và bỏ paymentStatus.
Đơn CONFIRMED giá 0 trả paidAmount=0, remainingAmount=0, paymentStatus=PAID, không có phiếu thu.
Thanh toán không đổi sales version; FE dùng payment summary để cập nhật số tiền,
và lấy version hiện tại riêng cho thao tác đơn bán. Hủy đơn đã thu trả
409 SALES_ORDER_HAS_PAYMENTS trước khi ghi biến động kho.

## Lỗi chung

Mọi API dùng `{timestamp,status,code,message,details}` của dự án.
401 UNAUTHORIZED cho phiên thiếu/sai hoặc tài khoản không hoạt động.
403 FORBIDDEN cho thiếu quyền; WAREHOUSE_ACCESS_DENIED cho ngoài phạm vi.
400 INVALID_REQUEST cho JSON/UUID/enum/ngày sai, VALIDATION_ERROR cho constraint đầu vào,
INVALID_PAGINATION cho giới hạn page/pageSize của service.
404 WAREHOUSE_NOT_FOUND khi kho không tồn tại (ADMIN); user thường có thể nhận 403 trước.
409 RESOURCE_CONFLICT cho vi phạm ràng buộc database (không tạo dữ liệu một phần).
PUT/DELETE phiếu không được hỗ trợ; gọi trên /payments/{id} trả 405 METHOD_NOT_ALLOWED.
500 INTERNAL_ERROR là lỗi không dự kiến, không dùng để báo lỗi nghiệp vụ.

## Luồng thử cho FE

1. Tạo khách trong kho A, nhập đủ tồn, tạo đơn 1.000.000 và xác nhận.
   Đơn UNPAID, remainingAmount=1.000.000; không có phiếu thu.
2. Thu 600.000 với key K1: PARTIALLY_PAID, còn 400.000.
   Gửi lại K1/payload cũ: cùng payment.id; đổi amount/note với K1: 409 IDEMPOTENCY_CONFLICT.
3. Thu 400.000 với K2: PAID, còn 0; thu tiếp bị 409 PAYMENT_EXCEEDS_REMAINING.
4. Thử hủy đơn: 409 SALES_ORDER_HAS_PAYMENTS, tồn không đổi.
5. Trong ca kiểm thử ghi nhận sai, hủy phiếu 400.000 bằng reason: còn nợ 400.000.
   Retry K2 trả phiếu CANCELLED, không thu lại; hủy lặp không tăng nợ lần nữa.
6. So sánh chi tiết khách và /receivables với pageSize=1: tổng vẫn gồm tất cả đơn còn nợ.
   Thử đơn khách lẻ trong tab riêng; không thấy khách giả trong công nợ theo khách.
7. Thu hồi permission/kho của user và thử lại bằng JWT/key cũ: bị chặn.
8. Hai request thu 600.000 vào đơn 1.000.000: một 201, một 409.
   Hai request cùng key/payload: cả hai 201 nhưng chỉ một phiếu.

CORS cho phép header Idempotency-Key từ các origin FE đã cấu hình. Kết quả kiểm thử và danh sách file: [payments-verification.md](payments-verification.md).
