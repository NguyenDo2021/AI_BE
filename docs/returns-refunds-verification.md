# Kiểm chứng module Trả hàng và Hoàn tiền

Ngày kiểm tra: 2026-10-16, Asia/Bangkok. Maven wrapper 3.9.9; JDK 25.0.2,
biên dịch --release 21 theo pom.xml. PostgreSQL 18, cluster test riêng trong target,
chỉ nghe 127.0.0.1:55439; không dùng database nghiệp vụ hiện có.

## Kết quả thực tế

| Bộ kiểm tra | Tổng | Đạt | Bỏ qua | Lỗi |
|---|---:|---:|---:|---:|
| Toàn backend, H2 PostgreSQL mode | 126 | 120 | 6 | 0 |
| ReturnsApiIntegrationTest + ReturnValuationTest, PostgreSQL 18 | 18 | 18 | 0 | 0 |

Các kiểm tra bỏ qua trên H2 yêu cầu PL/pgSQL guard của PostgreSQL.
Bộ PostgreSQL mới chạy guard V16 thật, gồm kiểm tra lịch sử bất biến.
Bộ H2 bao gồm API auth/quyền/kho, bán/thu/công nợ, biên lai PDF,
kiểm kê, báo cáo và workbook/export.

Log thực tế (target bị git ignore):

- target/returns-postgres-verified.log: bộ PostgreSQL cuối, exit 0.
- target/returns-all-h2.log: toàn bộ H2 cuối, exit 0.
- target/surefire-reports: XML/text kết quả lần chạy H2 cuối.
- git diff --check: đạt.

## Tình huống đã kiểm tra

- Đơn 1.000.000 thu 600.000, trả 200.000 → điều chỉnh 800.000, nợ 200.000.
- Tổng trả 700.000 → điều chỉnh 300.000, nợ 0, cần hoàn 300.000.
- Hoàn 300.000 → netPaid 300.000, cần hoàn 0.
- Chưa thu mà trả chỉ giảm nợ; hàng 0 VND vẫn nhập tồn, không sinh hoàn.
- Trả hết nhận đủ net; 0/1/1 VND với dòng sold=3/net=2.
- Phân bổ phần dư lớn nhất trên nhiều sản phẩm, tie theo UUID.
- Trả với soldQuantity=Long.MAX_VALUE, discount=1, số tiền nguyên exact.
- Snapshot gốc giữ nguyên khi danh mục đổi tên hoặc kho/khách/sản phẩm inactive.
- RETURN_QUOTE_CHANGED có quote mới; PUT xem xét/lưu rồi confirm lại.
- Quá lượng/quá tiền/thu vượt nợ/field hệ thống lạ/type số sai bị từ chối.
- Hủy thu dưới tổng hoàn bị từ chối; sau hủy hoàn có thể hủy thu và tăng nợ.
- Retry confirm không thêm movement; key hoàn giữ đúng phiếu, kể cả CANCELLED.
- Hai request cùng key chỉ tạo một refund.
- Quyền live, thiếu quyền, ngoài phạm vi, retry sau bỏ gán kho.
- Refundables đủ summary cho operator không có SALES_ORDER_VIEW.
- Nợ khách có tên/khách lẻ, dashboard, report/Excel bán/tồn/thu-hoàn khớp.
- Hoàn CASH/BANK_TRANSFER và đảo hoàn tách đúng method.
- Revision tăng khi nhập hàng trả làm kiểm kê cũ STOCKTAKE_STALE.
- Overflow balance rollback toàn bộ phiếu, không thêm movement/nhập một phần.
- PostgreSQL chặn xóa/sửa phiếu trả confirmed, dòng confirmed và refund lịch sử.
- Hủy đơn có trả confirmed bị chặn kể cả số tiền trả 0.
- Đơn đã hủy khiến nháp trả không thể confirm, vẫn hủy nháp được.

Hai thread/transaction và barrier đồng thời trên PostgreSQL:

1. Hai phiếu trả cùng dòng: một thành công, một 409, không vượt lượng.
2. Trả với hủy đơn: chỉ một thành công, không cộng hàng hai lần.
3. Hai lần hoàn: không vượt tiền; cùng key cùng phiếu.
4. Hoàn với hủy thu: không để paid < refunded.
5. Thu với confirm trả: theo thứ tự khóa, không thu vượt nợ hiện tại hoặc mất tiền cần hoàn.

Không chạy toàn bộ suite cũ trên PostgreSQL; phần PostgreSQL thực tế là hai class mới nêu trên.
Không triển khai lên môi trường nghiệp vụ hoặc cấp quyền role/user.

## Lệnh tái lập

PowerShell tại repository, JDK phù hợp có sẵn:

~~~powershell
$env:JAVA_HOME='C:\Users\dont\AppData\Local\jdks\jdk-25.0.2'
.\mvnw.cmd -q test
~~~

Tạo database PostgreSQL test rỗng riêng. Fixture truncate dữ liệu test;
chỉ dùng database dành riêng cho kiểm thử.

~~~powershell
$env:JAVA_HOME='C:\Users\dont\AppData\Local\jdks\jdk-25.0.2'
.\mvnw.cmd -q '-Dtest=ReturnsApiIntegrationTest,ReturnValuationTest' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55439/returns_verify' '-Dspring.datasource.username=returns_test' '-Dspring.datasource.password=' '-Dspring.datasource.driver-class-name=org.postgresql.Driver' test
~~~

Cluster test riêng được dừng sau kiểm chứng. Không sửa migration V1–V14.
V15/V16 chạy từ đầu trên PostgreSQL rỗng với trigger đầy đủ.
H2MigrationConfiguration chỉ loại trigger PostgreSQL cho H2, không sửa SQL production.

## Danh sách file bàn giao

API/schema/phân bổ và danh sách tích hợp FE:
[returns-refunds-api.md](returns-refunds-api.md).

- README.md
- docs/payments-api.md
- docs/report-excel-export.md
- docs/reports-api.md
- docs/returns-refunds-api.md
- docs/returns-refunds-verification.md
- docs/sales-api.md
- src/main/java/com/frontendbase/api/common/PermissionCodes.java
- src/main/java/com/frontendbase/api/common/exception/ApiErrorResponse.java
- src/main/java/com/frontendbase/api/common/exception/ApiException.java
- src/main/java/com/frontendbase/api/common/exception/GlobalExceptionHandler.java
- src/main/java/com/frontendbase/api/payment/dto/PaymentDtos.java
- src/main/java/com/frontendbase/api/payment/service/PaymentService.java
- src/main/java/com/frontendbase/api/refund/controller/RefundController.java
- src/main/java/com/frontendbase/api/refund/dto/RefundDtos.java
- src/main/java/com/frontendbase/api/refund/repository/RefundRepository.java
- src/main/java/com/frontendbase/api/refund/service/RefundService.java
- src/main/java/com/frontendbase/api/report/controller/ReportController.java
- src/main/java/com/frontendbase/api/report/dto/ReportDtos.java
- src/main/java/com/frontendbase/api/report/repository/ReportRepository.java
- src/main/java/com/frontendbase/api/report/service/ReportExportService.java
- src/main/java/com/frontendbase/api/report/service/ReportService.java
- src/main/java/com/frontendbase/api/returns/controller/ReturnController.java
- src/main/java/com/frontendbase/api/returns/dto/ReturnDtos.java
- src/main/java/com/frontendbase/api/returns/repository/ReturnRepository.java
- src/main/java/com/frontendbase/api/returns/service/ReturnService.java
- src/main/java/com/frontendbase/api/returns/service/ReturnValuation.java
- src/main/java/com/frontendbase/api/sales/dto/SalesDtos.java
- src/main/java/com/frontendbase/api/sales/repository/SalesRepository.java
- src/main/java/com/frontendbase/api/sales/service/SalesService.java
- src/main/java/com/frontendbase/api/stock/dto/StockDtos.java
- src/main/java/com/frontendbase/api/stock/repository/StockRepository.java
- src/main/java/com/frontendbase/api/stock/service/StockOperations.java
- src/main/java/com/frontendbase/api/stock/service/StockService.java
- src/main/resources/db/migration/V15__add_sales_returns_and_refunds.sql
- src/main/resources/db/migration/V16__protect_return_and_refund_history.sql
- src/test/java/com/frontendbase/api/H2MigrationConfiguration.java
- src/test/java/com/frontendbase/api/ReportApiIntegrationTest.java
- src/test/java/com/frontendbase/api/ReturnsApiIntegrationTest.java
- src/test/java/com/frontendbase/api/StockTestCleanup.java
- src/test/java/com/frontendbase/api/returns/service/ReturnValuationTest.java


## Kiểm chứng bổ sung: tìm đơn trả hàng theo mã

- Khảo sát GET /sales-orders: chưa nhận code, yêu cầu SALES_ORDER_VIEW, nên thêm endpoint riêng.
- H2: chạy toàn ReturnsApiIntegrationTest, 20 kiểm tra, 19 đạt, 1 guard PostgreSQL bỏ qua, 0 lỗi.
- PostgreSQL 18: bốn kiểm tra returnableOrder* đều đạt, không bỏ qua.
- Log: target/returnable-orders-h2.log và target/returnable-orders-postgres.log; cả hai lệnh cuối exit 0.
- Đã kiểm tra trim/case/substring; wildcard literal; mã không có; code thiếu/trắng;
  pagination sai/trang vượt cuối; DRAFT/CANCELLED không hiện; return DRAFT/CANCELLED không giữ chỗ;
  trả hết hàng 0 VND không hiện; còn một dòng được trả vẫn hiện.
- Đã kiểm tra count/phân trang theo kho, admin thấy mọi kho, mã ngoài phạm vi total 0,
  chỉ SALES_RETURN_VIEW vẫn tìm được, thiếu quyền trả 403, bỏ gán kho không còn thấy đơn.
- Kho/khách/sản phẩm inactive vẫn hiện; tên khách từ snapshot, tên khách lẻ đúng.
- Kiểm tra thủ công git diff --check đạt; không thêm migration/permission.
- Contract FE đầy đủ: [returnable-orders-api.md](returnable-orders-api.md).
- File lần bổ sung này: returns/controller/ReturnController.java, returns/dto/ReturnDtos.java,
  returns/service/ReturnService.java, ReturnsApiIntegrationTest.java,
  docs/returnable-orders-api.md, docs/returns-refunds-api.md và tài liệu kiểm chứng này.
- POST /sales-returns và GET /sales-orders/{id}/returnable-lines giữ UUID, không đổi contract.
