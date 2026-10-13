# API Kiểm kê kho

Base URL: /api. Mọi API yêu cầu Authorization: Bearer <accessToken>.
Response trực tiếp, không có lớp data; các field null được bỏ theo cấu hình Jackson hiện tại.
UUID là string, thời gian audit là ISO-8601 UTC. Số lượng/revision/version là JSON integer 64-bit.
FE cần giữ độ chính xác 64-bit khi parse/serialize (tránh chuyển Long.MAX_VALUE sang JavaScript Number).
Backend từ chối string số, token float (kể cả 1.0/1e0), âm và tràn Long.

## Quyền và phạm vi

| API | Permission |
| --- | --- |
| GET danh sách/chi tiết | STOCKTAKE_VIEW |
| POST tạo | STOCKTAKE_CREATE |
| PUT sửa / POST refresh-snapshot | STOCKTAKE_UPDATE |
| POST confirm | STOCKTAKE_CONFIRM |
| POST cancel | STOCKTAKE_CANCEL |

Permission dùng authorities lấy từ claim permissions của JWT, theo JwtAuthenticationFilter hiện tại.
Thay đổi permission trong database cần cấp lại access token để có hiệu lực.
Ngoại lệ ADMIN kiểm tra user/role đang hoạt động trong database qua WarehouseAccess.isAdmin().
User khác phải có permission và được gán kho. Gán kho được đọc từ database mỗi request,
kể cả retry confirm/cancel. Danh sách áp dụng phạm vi trước COUNT/LIMIT/OFFSET.
Migration chỉ thêm danh mục permission, không gán cho role/user.

## Quy tắc nghiệp vụ

Kiểm kê tại thời điểm hiện tại, không hồi tố. Người dùng phải tạm ngừng nhập/xuất thực tế
với các sản phẩm đang đếm. Backend không tự suy ra số đếm từ giao dịch phát sinh.
Mỗi phiếu có 1–1000 sản phẩm không trùng, có thể kiểm kê một phần kho.
Sản phẩm active được chọn dù chưa có balance (tồn/revision = 0).
Sản phẩm inactive chỉ được chọn nếu có inventory_movements tại kho.
Kho phải active khi tạo/sửa/refresh/xác nhận DRAFT; xem lịch sử và hủy DRAFT được ở kho inactive.
Phiếu CONFIRMED/CANCELLED là cuối; không hỗ trợ DELETE hoặc hủy đảo CONFIRMED.
Sai kiểm kê thì tạo phiếu mới và đếm lại.

Số đếm là null/chưa gửi nếu chưa đếm; 0 hợp lệ; khoảng 0..9223372036854775807.
difference do BE tính: countedQuantity - systemQuantity. Lý do bắt buộc nếu difference khác 0,
kiểm tra ngay khi sửa và khi xác nhận. note/reason/cancellationReason tối đa 2000 ký tự.
Client chỉ được gửi đúng các field mô tả bên dưới. Field lạ hoặc snapshot/actor do client gửi bị 400.
Tạo không nhận countedQuantity; nhập số đếm qua PUT.

## Response phiếu chung R

Mọi API trả phiếu đều dùng đầy đủ schema R dưới đây (field null bị bỏ):

```json
{
  "id": "30000000-0000-0000-0000-000000000001",
  "code": "KK-30000000000000000000000000000001",
  "warehouseId": "40000000-0000-0000-0000-000000000001",
  "note": "Kiểm kê kho",
  "status": "CONFIRMED",
  "version": 2,
  "createdBy": "50000000-0000-0000-0000-000000000001",
  "createdAt": "2026-10-12T01:00:00Z",
  "updatedBy": "50000000-0000-0000-0000-000000000002",
  "updatedAt": "2026-10-12T02:00:00Z",
  "confirmedBy": "50000000-0000-0000-0000-000000000002",
  "confirmedAt": "2026-10-12T02:00:00Z",
  "lines": [{
    "productId": "60000000-0000-0000-0000-000000000001",
    "systemQuantity": 100,
    "inventoryRevision": 1,
    "countedQuantity": 97,
    "difference": -3,
    "reason": "Thiếu 3 cây khi kiểm đếm",
    "productCode": "P001",
    "productName": "Sản phẩm 1",
    "unit": "cay"
  }]
}
```

R còn có cancelledBy (UUID), cancelledAt (ISO instant), cancellationReason (string) khi CANCELLED.
DRAFT không có confirmed/cancelled audit. Khi tạo, updatedBy/updatedAt bằng createdBy/createdAt.
Mỗi sửa/refresh/confirm/cancel đầu tiên tăng version đúng 1 và ghi updatedBy/updatedAt.
Retry cuối trả nguyên phiếu và audit lần đầu, dù gửi version cũ.

## GET /api/stocktakes

Query tùy chọn warehouseId UUID, status DRAFT|CONFIRMED|CANCELLED, from/to YYYY-MM-DD;
page mặc định 1, pageSize mặc định 10, khoảng 1..100.
from/to lọc createdAt theo ngày Asia/Ho_Chi_Minh, bao gồm hai đầu
([from 00:00, ngày sau to 00:00)); from > to trả 400.
Sắp xếp createdAt DESC, id DESC.

Request: GET /api/stocktakes?warehouseId=40000000-0000-0000-0000-000000000001&status=DRAFT&from=2026-10-12&to=2026-10-12&page=1&pageSize=10

200: {"items":[R], "total":1, "page":1, "pageSize":10}.
items chứa các phiếu đầy đủ dòng/audit như schema R; trang rỗng trả items=[].

## GET /api/stocktakes/{id}

Không có body. Ví dụ GET /api/stocktakes/30000000-0000-0000-0000-000000000001.
200: R đầy đủ, cho phép xem cả phiếu cuối hoặc kho inactive.

## POST /api/stocktakes

```json
{
  "warehouseId": "40000000-0000-0000-0000-000000000001",
  "note": "Kiểm kê kho",
  "lines": [{"productId":"60000000-0000-0000-0000-000000000001"}]
}
```

201: R với code BE sinh dạng KK-<UUID bỏ dấu gạch>, status=DRAFT, version=0,
created/updated audit của user hiện tại; lines có systemQuantity/inventoryRevision và
snapshot productCode/productName/unit, chưa có countedQuantity/difference/reason.
Không thay đổi tồn hoặc sinh movement.

## PUT /api/stocktakes/{id}

```json
{
  "version": 0,
  "note": "Kiểm kê kho",
  "lines": [{
    "productId": "60000000-0000-0000-0000-000000000001",
    "countedQuantity": 97,
    "reason": "Thiếu 3 cây khi kiểm đếm"
  }]
}
```

Thay thế toàn bộ note/lines. Không gửi note thì note thành null; không gửi countedQuantity/reason
thì các field đó thành null. Giữ nguyên snapshot tồn/revision/mã/tên/đơn vị của dòng cũ;
dòng thêm mới lấy snapshot lúc sửa. Dòng bỏ khỏi request bị loại khỏi DRAFT.
Không nhận warehouseId hoặc systemQuantity/inventoryRevision/difference/actor.
200: R DRAFT, version=1, dòng ví dụ systemQuantity=100/count=97/difference=-3,
updated audit mới. Không đổi tồn.

## POST /api/stocktakes/{id}/refresh-snapshot

Request: {"version":1}.
200: R DRAFT, version=2; toàn bộ dòng lấy snapshot/revision mới, countedQuantity/difference/reason
bị xóa (field null không xuất hiện); updated audit mới.
Không đổi tồn. FE phải yêu cầu người dùng đếm lại.
Chỉ thực hiện chủ động; confirm thất bại không tự refresh.

## POST /api/stocktakes/{id}/confirm

Request: {"version":2}.
200: R CONFIRMED, version=3, confirmedBy/confirmedAt và updatedBy/updatedAt mới.
Tất cả dòng phải đã đếm, kể cả thực tế 0; dòng lệch phải có reason.
Kiểm tra revision tất cả dòng rồi mới cập nhật; bất kỳ stale nào trả 409, toàn bộ rollback.
Nếu hợp lệ tồn mới = countedQuantity; movement chỉ cho difference khác 0.
CONFIRMED retry trả R hiện tại, không tăng version/revision hoặc tạo movement thêm.
CANCELLED trả 409. Retry vẫn phải có quyền/phạm vi; retry cuối không bị chặn bởi kho inactive.

## POST /api/stocktakes/{id}/cancel

Request: {"version":2,"reason":"Lập nhầm phiếu"}.
200: R CANCELLED, version=3, cancelledBy/cancelledAt/cancellationReason và updated audit mới.
Chỉ hủy DRAFT; reason bắt buộc không blank. Không đổi tồn/movement.
CANCELLED retry trả R hiện tại, giữ nguyên lý do/audit lần đầu.
CONFIRMED trả 409; không hỗ trợ đảo kiểm kê.

## Mã lỗi và xử lý FE

Schema lỗi hiện tại: {timestamp,status,code,message,details}; details là map string -> string.

| HTTP | code | Ý nghĩa |
| --- | --- | --- |
| 400 | INVALID_REQUEST | JSON/token số/UUID/enum/field lạ sai |
| 400 | VALIDATION_ERROR | Thiếu field, âm, quá dài, lines rỗng/quá 1000 |
| 400 | DUPLICATE_PRODUCT | Trùng productId |
| 400 | STOCKTAKE_COUNT_REQUIRED | Có dòng chưa đếm; details.productId |
| 400 | STOCKTAKE_REASON_REQUIRED | Dòng lệch thiếu lý do; details.productId |
| 400 | INVALID_DATE_RANGE / INVALID_PAGINATION | Query không hợp lệ |
| 401 | UNAUTHORIZED | Không có JWT hợp lệ hoặc user inactive |
| 403 | FORBIDDEN | Thiếu permission (handler hiện tại) |
| 403 | WAREHOUSE_ACCESS_DENIED | Ngoài phạm vi hoặc đã thu hồi kho |
| 404 | STOCKTAKE_NOT_FOUND / WAREHOUSE_NOT_FOUND / PRODUCT_NOT_FOUND | Không tồn tại |
| 409 | INVALID_STOCKTAKE_STATUS | Trạng thái không hỗ trợ thao tác |
| 409 | WAREHOUSE_INACTIVE / PRODUCT_INACTIVE | Danh mục không hợp lệ |
| 409 | VERSION_CONFLICT | Người khác đã sửa; tải lại phiếu |
| 409 | STOCKTAKE_STALE | Tồn có biến động; người dùng chủ động refresh và đếm lại |
| 409 | NUMERIC_OVERFLOW | Version/revision đã tới giới hạn; rollback |
| 409 | RESOURCE_CONFLICT | Ràng buộc dữ liệu thất bại |
| 405 | METHOD_NOT_ALLOWED | DELETE không hỗ trợ |

Ví dụ details VERSION_CONFLICT:
{"stocktakeId":"30000000-0000-0000-0000-000000000001","expectedVersion":"1","currentVersion":"2"}.

Ví dụ details STOCKTAKE_STALE, trả tất cả dòng stale:
```json
{
  "lines.60000000-0000-0000-0000-000000000001.snapshotRevision": "1",
  "lines.60000000-0000-0000-0000-000000000001.currentRevision": "3",
  "lines.60000000-0000-0000-0000-000000000001.systemQuantity": "100",
  "lines.60000000-0000-0000-0000-000000000001.currentQuantity": "100"
}
```

FE không tự confirm lại/đổi baseline sau stale. Hiển thị các dòng đã biến động; cho người dùng
chọn lấy lại tồn hệ thống (gửi version phiếu hiện tại), sau đó nhập lại số thực tế và lý do.

## Lịch sử biến động

GET /api/warehouses/{warehouseId}/inventory-movements giữ schema phân trang hiện tại.
Một item sau điều chỉnh 100 -> 97:

```json
{
  "id": "70000000-0000-0000-0000-000000000001",
  "warehouseId": "40000000-0000-0000-0000-000000000001",
  "productId": "60000000-0000-0000-0000-000000000001",
  "quantityChange": -3,
  "type": "STOCKTAKE_ADJUSTMENT",
  "stocktakeId": "30000000-0000-0000-0000-000000000001",
  "stocktakeCode": "KK-30000000000000000000000000000001",
  "productCode": "P001",
  "productName": "Sản phẩm 1",
  "unit": "cay",
  "performedBy": "50000000-0000-0000-0000-000000000002",
  "performedAt": "2026-10-12T02:00:00Z"
}
```

type còn có RECEIPT_CONFIRM (+), RECEIPT_CANCEL (-), SALE_CONFIRM (-), SALE_CANCEL (+).
Nguồn nhập dùng receiptId/receiptCode, nguồn bán dùng salesOrderId/salesOrderCode.
Nguồn kiểm kê chỉ dùng stocktakeId/stocktakeCode; nguồn khác có các field mới null/bị bỏ.
Product snapshot lấy từ dòng chứng từ nguồn, không từ danh mục đã đổi tên.

DB inventory_movements thêm stocktake_id UUID nullable, FK(stocktake_id,product_id) tới stocktake_lines,
UNIQUE(stocktake_id,product_id,type); CHECK bảo đảm chỉ đúng một nguồn và đúng dấu.
quantity_change BIGINT có dấu, không bằng 0. Giữ trigger append-only hiện tại.
stocktakeCode tra từ stocktakes (code bất biến), không lưu lặp trong movement.

## Transaction, revision, migration

V12 thêm stocktakes/stocktake_lines/revision/nguồn movement/permission/index và CHECK/FK.
V13 thêm trigger PostgreSQL: cấm xóa phiếu, đổi identity/kho, sửa phiếu/dòng cuối,
version phải tăng 1, confirm phải có 1–1000 dòng đã đếm.
Không sửa migration V1–V11.

Revision lưu trên inventory_balances, tăng chính xác 1 mỗi thay đổi tồn từ
StockOperations (nhập/bán/hủy nhập/hủy bán) hoặc xác nhận kiểm kê có chênh lệch.
Balance mới được insert revision=1; balance về 0 vẫn giữ hàng/revision, không DELETE.
V12 khởi tạo revision=0 cho balance cũ; snapshot chỉ có sau khi migration.
Dòng kiểm kê không lệch không đổi revision/movement.
Không cập nhật số dư đơn bán, payment, customer hay receivable.

Khóa database: warehouse FOR UPDATE -> stocktake FOR UPDATE -> products theo UUID -> balances.
Khóa kho chung với nhập/bán xử lý cả balance chưa tồn tại và nhiều instance.
Kiểm tra stale tất cả dòng trước khi ghi; tồn/revision/movement/trạng thái/audit nằm trong một transaction.
Đọc danh sách/chi tiết dùng REPEATABLE_READ để nhất quán header/lines/count.
Đánh đổi hiện tại: thao tác tồn trong cùng kho được tuần tự hóa.
Không có cơ chế hồi tố hay bù giao dịch vật lý trong lúc người dùng đếm.
