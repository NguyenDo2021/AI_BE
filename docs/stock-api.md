# API nhập kho và tồn kho

Base URL: `http://localhost:8080/api`. Tất cả API cần header `Authorization: Bearer <accessToken>` và request có body cần `Content-Type: application/json`. Swagger: `/api/swagger-ui.html`; OpenAPI: `/api/v3/api-docs`.

## Phân quyền và phạm vi

ADMIN dùng ngoại lệ `WarehouseAccess.isAdmin()` kiểm tra role/account đang hoạt động trong database. User khác cần permission chức năng dưới đây và được gán kho. Gán kho kiểm tra trong database mỗi request, không lấy từ JWT; thu hồi có hiệu lực ở request tiếp theo kể cả JWT chưa hết hạn. Permission tiếp tục dùng authority trong JWT theo cơ chế project hiện có.

`warehouseId` khi tạo là kho yêu cầu, backend xác minh phạm vi và trạng thái. Khi sửa, backend dùng kho đã lưu trên phiếu; `warehouseId` có thể bỏ hoặc gửi cùng kho, gửi khác trả 409. Không có trường `userId` để quyết định người thực hiện; backend lấy người tạo/xác nhận/hủy từ phiên đăng nhập. Các trường FE tự gửi như `totalAmount`, `lineTotal`, snapshot, trạng thái hay người thực hiện không được dùng để ghi dữ liệu.

Danh sách phiếu không truyền kho chỉ trả các kho được giao. Lọc phạm vi trong SQL **trước** LIMIT/OFFSET/count. Nếu truyền một kho ngoài phạm vi, trả 403. Chi tiết và mọi thao tác ghi kiểm tra kho của phiếu trong database. Không có API xóa phiếu hoặc sửa trực tiếp tồn/lịch sử.

## Endpoint

| Method | URL (sau /api) | Permission | Thành công |
|---|---|---|---|
| GET | `/stock-receipts` | STOCK_RECEIPT_VIEW | 200 Page<Receipt> |
| GET | `/stock-receipts/{id}` | STOCK_RECEIPT_VIEW | 200 Receipt |
| POST | `/stock-receipts` | STOCK_RECEIPT_CREATE | 201 Receipt |
| PUT | `/stock-receipts/{id}` | STOCK_RECEIPT_UPDATE | 200 Receipt |
| POST | `/stock-receipts/{id}/confirm` | STOCK_RECEIPT_CONFIRM | 200 Receipt |
| POST | `/stock-receipts/{id}/cancel` | STOCK_RECEIPT_CANCEL | 200 Receipt |
| GET | `/warehouses/{warehouseId}/inventory` | INVENTORY_VIEW | 200 Page<Inventory> |
| GET | `/warehouses/{warehouseId}/inventory-movements` | INVENTORY_MOVEMENT_VIEW | 200 Page<Movement> |

ADMIN không cần được gán kho hoặc có authority tương ứng. Nhân viên có STOCK_RECEIPT_CONFIRM tự xác nhận, không có bước chờ admin duyệt. Các permission mới được seed bằng V6; role khác ADMIN cần được cấp theo API role hiện có.

### Danh sách phiếu

Query: `page=1`, `pageSize=10` (1–100), `warehouseId` UUID tùy chọn, `status=DRAFT|CONFIRMED|CANCELLED` tùy chọn, `from=2026-10-01`, `to=2026-10-31` tùy chọn. Khoảng ngày lọc **receiptDate**, bao gồm hai đầu. `from > to` trả 400. Sắp xếp `createdAt DESC, id DESC`. Chi tiết GET không có body.

Ví dụ: `GET /api/stock-receipts?warehouseId=<A>&status=CONFIRMED&page=1&pageSize=20&from=2026-10-01&to=2026-10-31`.

### Tạo phiếu

```json
{
  "warehouseId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "receiptDate": "2026-10-08",
  "supplierName": "Nhà cung cấp A",
  "note": "Nhập phào nguyên cây",
  "lines": [
    {"productId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "quantity": 100, "unitPrice": 25000}
  ]
}
```

`warehouseId`, `receiptDate`, `lines`, `productId`, `quantity`, `unitPrice` bắt buộc. `supplierName` tùy chọn, tối đa 160 ký tự; `note` tùy chọn, tối đa 2000 ký tự. Phiếu có 1–1000 dòng; không được trùng productId. Cả kho và sản phẩm phải hoạt động. Quantity là số nguyên JSON dương; unitPrice là số VND nguyên JSON không âm, giá 0 hợp lệ. Không nhận số lẻ, số thực dạng `1.0`, chuỗi `"1"`, null hoặc giá trị vượt Long. Giới hạn mỗi quantity/unitPrice/lineTotal/totalAmount/tồn: **9,223,372,036,854,775,807**; dùng bigint và phép cộng/nhân exact để chặn tràn trước ghi.

Phiếu mới có `status=DRAFT`, `version=0`; chưa ảnh hưởng tồn. Mã `PN-` + UUID bỏ dấu gạch, sinh tại backend; unique constraint bảo vệ mã. Giá nhập không cập nhật giá tham khảo của sản phẩm.

### Sửa phiếu

PUT thay thế toàn bộ ngày/nhà cung cấp/ghi chú/dòng của phiếu DRAFT. Các field tùy chọn không gửi sẽ thành null. Body như tạo, thêm `version` hiện tại; `warehouseId` tùy chọn và phải khớp nếu gửi.

```json
{
  "receiptDate": "2026-10-08",
  "supplierName": "Nhà cung cấp A",
  "note": "Sửa số lượng",
  "version": 0,
  "lines": [{"productId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "quantity": 120, "unitPrice": 25000}]
}
```

Response tăng version 1; không đổi tồn. Sản phẩm và kho phải hoạt động. CONFIRMED/CANCELLED không sửa được. Không có endpoint DELETE; trả 405 theo handler hiện có.

### Xác nhận

POST `/stock-receipts/{id}/confirm`:

```json
{"version": 0}
```

DRAFT: kiểm tra version; kiểm tra lại kho/sản phẩm hoạt động; chụp mã/tên/đơn vị sản phẩm tại thời điểm xác nhận; tăng tồn và sinh RECEIPT_CONFIRM cho từng dòng; lưu confirmedBy/confirmedAt; chuyển CONFIRMED và tăng version. Các dữ liệu nằm trong một transaction.

Đã CONFIRMED: trả 200 phiếu hiện tại, **không kiểm tra lại version cũ, không tăng version/tồn, không sinh thêm biến động**. Cho retry cả khi danh mục đã ngừng hoạt động. Vẫn cần permission và phạm vi kho. Phiếu CANCELLED trả 409 INVALID_RECEIPT_STATUS, không khôi phục.

### Hủy

POST `/stock-receipts/{id}/cancel`:

```json
{"version": 1, "reason": "Nhập nhầm phiếu"}
```

DRAFT: version phải khớp; reason tùy chọn, không ảnh hưởng tồn và không sinh biến động. CONFIRMED: version phải khớp và reason bắt buộc không blank (tối đa 2000 ký tự). Kiểm tra tồn tất cả sản phẩm trước khi ghi; bất kỳ dòng thiếu tồn đều từ chối toàn bộ với 409 INSUFFICIENT_STOCK. Khi đủ tồn, giảm đúng quantity đã nhập, sinh RECEIPT_CANCEL âm, chuyển CANCELLED và tăng version. Giữ thông tin xác nhận trước đó; thêm cancelledBy/cancelledAt/cancellationReason.

Cho phép hủy phiếu đã xác nhận dù kho/sản phẩm ngừng hoạt động. Không cần quyền admin ngoài STOCK_RECEIPT_CANCEL và phạm vi kho. Đã CANCELLED: trả 200 phiếu hiện tại, bỏ kiểm tra version cũ/reason nghiệp vụ; không tăng version, không giảm tồn hoặc tạo biến động nữa. Body vẫn phải hợp lệ về cấu trúc: version số nguyên không âm, reason nếu có không quá giới hạn.

### Response phiếu

Tạo/sửa/chi tiết/xác nhận/hủy cùng schema:

```json
{
  "id": "cccccccc-cccc-cccc-cccc-cccccccccccc",
  "code": "PN-cccccccccccccccccccccccccccccccc",
  "warehouseId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "receiptDate": "2026-10-08",
  "supplierName": "Nhà cung cấp A",
  "note": "Nhập phào nguyên cây",
  "status": "CONFIRMED",
  "totalAmount": 2500000,
  "version": 1,
  "createdBy": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "createdAt": "2026-10-08T03:00:00Z",
  "confirmedBy": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "confirmedAt": "2026-10-08T03:05:00Z",
  "lines": [{
    "productId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "quantity": 100,
    "unitPrice": 25000,
    "lineTotal": 2500000,
    "productCode": "PHAO-01",
    "productName": "Phào 01",
    "unit": "cay"
  }]
}
```

Các field null được bỏ theo cấu hình Jackson hiện có. Phiếu đã hủy thêm `cancelledBy` UUID, `cancelledAt` Instant UTC và `cancellationReason` nếu có. DRAFT chưa có confirmed/cancelled audit. Snapshot trên DRAFT là dữ liệu danh mục lúc tạo/sửa, chỉ được đóng băng lúc xác nhận. Lines trả theo productId, không bảo đảm thứ tự FE gửi. `lineTotal=quantity*unitPrice`; `totalAmount=sum(lineTotal)` đều backend tính.

### Tồn kho

GET `/warehouses/{warehouseId}/inventory?page=1&pageSize=10&productId=<optional UUID>`.

Hiển thị mọi sản phẩm hoạt động, kể cả chưa có tồn (0). Sản phẩm ngừng hoạt động xuất hiện nếu có biến động tại kho đang xem, kể cả tồn đã về 0. Sản phẩm ngừng hoạt động chưa có biến động tại kho này không nằm trong danh sách; về nghiệp vụ vẫn được hiểu là tồn 0. Có thể xem kho ngừng hoạt động. Lọc productId trước phân trang/count; sort code ASC, id ASC. Dữ liệu danh mục ở đây là hiện tại, lịch sử dùng snapshot.

```json
{
  "items": [{
    "warehouseId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "productId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "productCode": "PHAO-01", "productName": "Phào 01", "unit": "cay",
    "productStatus": 1, "quantity": 100
  }],
  "total": 1, "page": 1, "pageSize": 10
}
```

### Lịch sử biến động

GET `/warehouses/{warehouseId}/inventory-movements?page=1&pageSize=10&productId=<UUID>&from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z`.

productId/from/to tùy chọn. Thời gian ISO-8601 có timezone, lọc performedAt: **from bao gồm, to không bao gồm**. Có thể dùng offset được URL encode (vd `%2B07:00`) hoặc `Z`. `from > to` trả 400. Sort performedAt DESC, id DESC. Xem được khi danh mục ngừng hoạt động.

```json
{
  "items": [{
    "id": "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
    "warehouseId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "productId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "quantityChange": 100, "type": "RECEIPT_CONFIRM",
    "receiptId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
    "receiptCode": "PN-cccccccccccccccccccccccccccccccc",
    "productCode": "PHAO-01", "productName": "Phào 01", "unit": "cay",
    "performedBy": "dddddddd-dddd-dddd-dddd-dddddddddddd",
    "performedAt": "2026-10-08T03:05:00Z"
  }],
  "total": 1, "page": 1, "pageSize": 10
}
```

RECEIPT_CANCEL có quantityChange âm. Một dòng phiếu chỉ có tối đa một biến động mỗi loại, bảo vệ bằng unique(receipt_id, product_id, type). PostgreSQL V7 chặn UPDATE/DELETE biến động, sửa dòng phiếu đã final, xóa phiếu, đổi identity/kho phiếu và sửa nội dung phiếu đã xác nhận. Tài khoản database có quyền quản trị vẫn có thể can thiệp schema; không có API can thiệp dữ liệu này.

## Version, transaction và concurrency

Version bắt đầu 0, tăng 1 mỗi sửa/xác nhận/hủy thực sự. Mọi PUT/confirm/cancel yêu cầu version số nguyên không âm. Với thao tác thay đổi trạng thái/dữ liệu, version cũ trả 409 VERSION_CONFLICT; FE tải lại chi tiết rồi để người dùng quyết định. Không tự gửi lại thao tác thay đổi với version mới. Retry confirm trên CONFIRMED/cancel trên CANCELLED là ngoại lệ idempotent đã mô tả.

Thứ tự khóa: hàng warehouse FOR UPDATE → receipt FOR UPDATE → sản phẩm theo UUID (khi tạo/sửa/xác nhận) → balances. Khóa hàng kho xử lý cả insert balance chưa tồn tại, các phiếu cùng kho không mất cập nhật. Khóa nằm trong PostgreSQL, có hiệu lực qua nhiều instance backend. Đánh đổi: các thao tác nhập cùng kho được tuần tự hóa; kho khác có thể chạy đồng thời, nhưng sản phẩm chung có thể chờ khóa danh mục. Mọi module xuất/kiểm kê tương lai cần tuân thủ cùng quy tắc khóa hoặc triển khai cơ chế nguyên tử tương đương.

Read dùng REPEATABLE_READ để header/dòng/count/items cùng snapshot. Ghi dùng transaction mặc định READ_COMMITTED của project. Nếu một dòng thất bại, rollback trạng thái, snapshot, tồn và toàn bộ biến động.

## Lỗi

Response lỗi theo GlobalExceptionHandler hiện có:

```json
{"timestamp":"2026-10-08T03:05:00Z","status":409,"code":"VERSION_CONFLICT","message":"Receipt has changed; reload before retrying","details":{}}
```

| HTTP | Code | Trường hợp |
|---|---|---|
| 400 | INVALID_REQUEST | JSON không đọc được, UUID/date/enum sai, số thực/chuỗi/vượt Long |
| 400 | VALIDATION_ERROR | Thiếu field, quantity <= 0, giá âm, version âm, kích thước/length/page không hợp lệ |
| 400 | INVALID_PAGINATION | Service nhận page/pageSize ngoài giới hạn |
| 400 | DUPLICATE_PRODUCT | Trùng productId |
| 400 | INVALID_DATE_RANGE | from lớn hơn to |
| 400 | CANCELLATION_REASON_REQUIRED | Hủy CONFIRMED thiếu lý do không blank |
| 401 | UNAUTHORIZED | Chưa đăng nhập/JWT lỗi hoặc account không hoạt động |
| 403 | FORBIDDEN | Thiếu permission (cơ chế project) |
| 403 | WAREHOUSE_ACCESS_DENIED | Kho ngoài phạm vi |
| 404 | STOCK_RECEIPT_NOT_FOUND | ID phiếu không tồn tại |
| 404 | WAREHOUSE_NOT_FOUND | Kho không tồn tại (sau kiểm tra phạm vi) |
| 404 | PRODUCT_NOT_FOUND | Sản phẩm không tồn tại |
| 409 | VERSION_CONFLICT | Version đã cũ |
| 409 | INVALID_RECEIPT_STATUS | Sửa phiếu final/xác nhận CANCELLED |
| 409 | WAREHOUSE_IMMUTABLE | PUT đổi kho |
| 409 | WAREHOUSE_INACTIVE / PRODUCT_INACTIVE | Danh mục ngừng hoạt động lúc tạo/sửa/xác nhận |
| 409 | INSUFFICIENT_STOCK | Không đủ tồn đảo toàn bộ phiếu |
| 409 | NUMERIC_OVERFLOW | Tràn phép cộng/nhân tồn, tiền hoặc version |
| 409 | RESOURCE_CONFLICT | Vi phạm ràng buộc database |
| 405 | METHOD_NOT_ALLOWED | Method không được hỗ trợ, ví dụ DELETE phiếu |
| 500 | INTERNAL_ERROR | Lỗi ngoài dự kiến; transaction ghi rollback |

Validation errors có details theo tên field (ví dụ `lines[0].quantity`). Message có thể khác tùy validation; FE nên dùng HTTP/code. Với ID phiếu không tồn tại, trả 404; với phiếu có tồn tại nhưng kho ngoài phạm vi, trả 403. Kho không tồn tại và chưa gán có thể trả 403 trước 404.

## Kiểm tra bằng API (PowerShell)

Chuẩn bị hai kho hoạt động A/B, sản phẩm P hoạt động từ các API danh mục hiện có. Gán A cho employee và cấp các permission STOCK_RECEIPT_*/INVENTORY_* cần thiết. Đăng nhập lại sau cấp permission để JWT có authority mới.

```powershell
$base = 'http://localhost:8080/api'
$token = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body (@{username='employee'; password='<password>'} | ConvertTo-Json)).accessToken
$headers = @{Authorization="Bearer $token"}
$warehouseA = '<warehouse A UUID>'
$warehouseB = '<warehouse B UUID>'
$product = '<product UUID>'
$body = @{warehouseId=$warehouseA; receiptDate='2026-10-08'; lines=@(@{productId=$product;quantity=100;unitPrice=25000})} | ConvertTo-Json -Depth 5
$r = Invoke-RestMethod -Method Post -Uri "$base/stock-receipts" -Headers $headers -ContentType 'application/json' -Body $body
Invoke-RestMethod "$base/warehouses/$warehouseA/inventory?productId=$product" -Headers $headers
$c = Invoke-RestMethod -Method Post -Uri "$base/stock-receipts/$($r.id)/confirm" -Headers $headers -ContentType 'application/json' -Body (@{version=$r.version} | ConvertTo-Json)
# Retry bằng version cũ: vẫn 200, tồn vẫn 100.
Invoke-RestMethod -Method Post -Uri "$base/stock-receipts/$($r.id)/confirm" -Headers $headers -ContentType 'application/json' -Body (@{version=$r.version} | ConvertTo-Json)
Invoke-RestMethod "$base/warehouses/$warehouseA/inventory?productId=$product" -Headers $headers
Invoke-RestMethod "$base/warehouses/$warehouseA/inventory-movements?productId=$product" -Headers $headers
$cancelBody = @{version=$c.version;reason='Kiểm tra đảo phiếu'} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/stock-receipts/$($r.id)/cancel" -Headers $headers -ContentType 'application/json' -Body $cancelBody
# Retry hủy: tồn vẫn 0, lịch sử chỉ 2 dòng.
Invoke-RestMethod -Method Post -Uri "$base/stock-receipts/$($r.id)/cancel" -Headers $headers -ContentType 'application/json' -Body $cancelBody
```

1. Trước xác nhận, tồn A và B đều 0; sau xác nhận A=100, B=0 (xem B bằng admin hoặc user được gán B).
2. Tạo hai phiếu 100 và 70 của P tại A. Gửi confirm đồng thời bằng hai tab Postman: cả hai 200, tồn tăng tổng 170. Gửi hai confirm cùng phiếu/version: cả hai 200, chỉ một lần tăng.
3. PUT DRAFT với version cũ trả 409. Confirm chạy đồng thời với PUT/cancel cùng version: chỉ một thao tác chuyển/sửa thực sự thành công; thao tác còn lại 409. Tải lại để xem kết quả.
4. Confirm rồi hủy: đảo đúng tồn, hủy lặp không đảo nữa. Xác nhận lại phiếu CANCELLED trả 409. Hủy CONFIRMED thiếu reason trả 400.
5. Admin thu hồi gán A bằng DELETE `/api/users/{employeeId}/warehouses/{warehouseA}`. Dùng nguyên JWT employee: danh sách phiếu không còn A; chi tiết/confirm/cancel/tồn/history của A trả 403.
6. User được gán A nhưng thiếu permission: endpoint tương ứng trả 403. Admin không cần được gán kho.
7. Gửi quantity 0/-1/1.5 hoặc unitPrice -1/1.5: 400; gửi trùng sản phẩm/rỗng: 400. Gửi tích/tổng vượt Long: 409 NUMERIC_OVERFLOW; số JSON vượt Long: 400.
8. Ngừng sản phẩm/kho sau khi tạo DRAFT: confirm bị từ chối. Bật lại, confirm rồi ngừng: vẫn xem lịch sử và hủy được. Đổi tên/mã sau xác nhận không đổi snapshot phiếu/history.
9. Thiếu tồn và lỗi giữa transaction được kiểm tra tự động bằng fixture/injected constraint trong database test; hiện không có API tiêu thụ tồn để tạo trường hợp thiếu tồn. Không sửa trực tiếp database sản xuất để thử.

Đối soát database (chỉ đọc; kết quả phải không có dòng):

```sql
SELECT b.warehouse_id, b.product_id, b.quantity,
       COALESCE(SUM(m.quantity_change), 0) AS movement_quantity
FROM inventory_balances b
LEFT JOIN inventory_movements m
  ON m.warehouse_id=b.warehouse_id AND m.product_id=b.product_id
GROUP BY b.warehouse_id,b.product_id,b.quantity
HAVING b.quantity <> COALESCE(SUM(m.quantity_change),0);
```

## Migration và chạy test

V6 tạo stock_receipts, stock_receipt_lines, inventory_balances, inventory_movements, ràng buộc/index, bảy permission và cấp chúng cho ADMIN. V7 thêm trigger PostgreSQL bảo vệ audit. Flyway chạy tự động lúc startup; không sửa V1–V5. Không tạo số dư tồn đầu kỳ. Chưa có bán hàng/xuất/công nợ/chuyển kho/kiểm kê/tính giá vốn.

Yêu cầu JDK 21. H2: `./mvnw.cmd test`. PostgreSQL: dùng **database test riêng** vì test xóa/truncate fixture; chạy:

```powershell
./mvnw.cmd test '-Dtest=StockApiIntegrationTest' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55439/stock_test' '-Dspring.datasource.username=stock_test' '-Dspring.datasource.password=' '-Dspring.datasource.driver-class-name=org.postgresql.Driver'
```

H2 adapter hiện có áp dụng V1–V6 và thay V7 bằng SELECT 1 do không hỗ trợ PL/pgSQL. Test audit trigger chỉ chạy trên PostgreSQL; các test nghiệp vụ/concurrency/rollback chạy trên cả hai. Test thiếu tồn cố ý giảm balance bằng fixture và chỉ kiểm tra không thay đổi thêm sau từ chối; các test bình thường kiểm tra balance bằng tổng movement.
