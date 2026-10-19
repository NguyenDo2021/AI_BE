# Tìm đơn bán theo mã để lập phiếu trả

## API thực tế

GET /api/sales-orders/returnable-orders

Authorization: Bearer JWT. Permission: SALES_RETURN_VIEW.
Không yêu cầu SALES_ORDER_VIEW hoặc INVENTORY_VIEW. ADMIN giữ ngoại lệ role hiện tại.

| Query | Bắt buộc | Mặc định | Quy tắc |
|---|---|---|---|
| code | Có | — | Trim đầu/cuối; không trắng; tìm chứa không phân biệt hoa/thường |
| page | Không | 1 | Integer >= 1 |
| pageSize | Không | 10 | Integer từ 1 đến 100 |

Ví dụ: GET /api/sales-orders/returnable-orders?code=bh-12ab&page=1&pageSize=10

code là chuỗi tìm kiếm literal; %, _ và ! không được hiểu là wildcard.
FE nên URL-encode query bằng URLSearchParams hoặc chức năng params của HTTP client.

## Response 200

~~~json
{
  "items": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "code": "BH-12ab0000000000000000000000000000",
      "warehouseId": "22222222-2222-2222-2222-222222222222",
      "warehouseName": "Kho A",
      "saleDate": "2026-10-16",
      "customerName": "Khách lẻ",
      "totalAmount": 1000000
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 10
}
~~~

| Field item | Kiểu | Nguồn / ý nghĩa |
|---|---|---|
| id | UUID string | ID đơn, dùng làm salesOrderId |
| code | string | Mã đơn gốc do BE sinh |
| warehouseId | UUID string | Kho đơn gốc |
| warehouseName | string | Tên hiện tại của kho, kể cả kho inactive |
| saleDate | YYYY-MM-DD string | Ngày bán của đơn |
| customerName | string | Snapshot customer_name lúc xác nhận đơn; khách lẻ giữ “Khách lẻ” |
| totalAmount | integer JSON Long exact | Tổng tiền đơn gốc, không trừ giá trị hàng đã trả |

total là số đơn phù hợp trong phạm vi, không phải số item của trang.
Sắp xếp createdAt DESC, id DESC để phân trang ổn định.
Không kết quả: {"items":[],"total":0,"page":1,"pageSize":10}.
Trang vượt cuối: items rỗng nhưng total vẫn là tổng số đơn phù hợp.

## Điều kiện và bảo mật

- Chỉ đơn CONFIRMED có confirmedAt và còn ít nhất một dòng chưa trả hết số lượng.
- Đã trả = tổng quantity của các phiếu trả CONFIRMED cùng đơn/sản phẩm.
- Nháp/hủy trả không trừ hạn mức. Đơn có một dòng trả hết nhưng còn dòng khác vẫn được tìm thấy.
- Hàng 0 VND vẫn xét theo lượng; không suy ra còn được trả từ returnedAmount/totalAmount.
- Nhân viên chỉ thấy đơn thuộc kho được gán. Cùng predicate phạm vi/điều kiện được áp dụng trước count và LIMIT/OFFSET.
- Mã chỉ tồn tại ở kho ngoài phạm vi trả danh sách rỗng/total 0, không lộ việc đơn tồn tại.
- Không lọc trạng thái kho, khách hoặc sản phẩm; không lấy tên khách từ danh mục hiện tại.
- Count và items nằm trong cùng transaction read-only REPEATABLE_READ.
- Kết quả tìm không giữ chỗ số lượng; lúc tạo/xác nhận phiếu trả vẫn kiểm tra lại.

## Lỗi

Giữ envelope {timestamp,status,code,message,details} hiện có.

| HTTP | Code | Trường hợp |
|---|---|---|
| 400 | INVALID_REQUEST | code thiếu/rỗng/chỉ whitespace; page/pageSize không parse được |
| 400 | INVALID_PAGINATION | page < 1 hoặc pageSize ngoài 1–100 |
| 401 | UNAUTHORIZED | Thiếu/sai token, tài khoản không hợp lệ |
| 403 | FORBIDDEN | Không có SALES_RETURN_VIEW và không phải ADMIN |

## Luồng FE

1. Người dùng nhập mã, gọi endpoint này bằng code đã nhập.
2. Hiển thị code, kho, ngày bán, tên khách và totalAmount; chọn một item.
3. Dùng item.id gọi GET /api/sales-orders/{id}/returnable-lines.
4. Gửi POST /api/sales-returns với salesOrderId = item.id (UUID), không gửi code thay UUID.
5. Xử lý danh sách rỗng khi không có kết quả/ngoài phạm vi/đã trả hết; phân trang theo total.
6. Nếu phiếu khác xác nhận sau tìm kiếm, xử lý hạn mức/báo giá thay đổi theo contract trả hàng.

POST /sales-returns và GET /sales-orders/{id}/returnable-lines giữ nguyên contract UUID.
API GET /sales-orders cũ giữ nguyên quyền/bộ lọc; không tái sử dụng vì thiếu tìm code và yêu cầu SALES_ORDER_VIEW.
Không thêm migration hoặc permission.

## Kết quả kiểm tra thực tế

Bốn kiểm tra mới đạt trên PostgreSQL 18. Hồi quy toàn ReturnsApiIntegrationTest trên H2: 19 đạt, 1 guard PostgreSQL bỏ qua, 0 lỗi. [Chi tiết và log](returns-refunds-verification.md).
