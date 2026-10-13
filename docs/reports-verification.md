# Bàn giao Báo cáo và Dashboard — 2026-10-13

## File thay đổi

- src/main/java/com/frontendbase/api/report/controller/ReportController.java: 6 GET API, permission và Swagger.
- src/main/java/com/frontendbase/api/report/dto/ReportDtos.java: rows, summary, period, dashboard; section không được phép bị bỏ.
- src/main/java/com/frontendbase/api/report/service/ReportAccess.java: JWT authorities, live ADMIN và scope kho database.
- src/main/java/com/frontendbase/api/report/service/ReportPeriod.java: ngày Việt Nam, đầu kỳ/đầu ngày sau cuối kỳ.
- src/main/java/com/frontendbase/api/report/service/ReportService.java: snapshot REPEATABLE_READ, tổng hợp độc lập phân trang, quyền section.
- src/main/java/com/frontendbase/api/report/repository/ReportRepository.java: JDBC tổng hợp exact, scope/count/pagination trong DB, kiểm tra ledger/balance.
- src/main/java/com/frontendbase/api/common/PermissionCodes.java: 6 mã REPORT_*.
- src/main/resources/db/migration/V14__add_reports_and_dashboard.sql: seed 6 quyền, index sự kiện bán/thu và công nợ/tồn thấp; không tự gán quyền.
- src/test/java/com/frontendbase/api/ReportApiIntegrationTest.java: 18 test mới.
- src/test/java/com/frontendbase/api/PaymentPrintApiIntegrationTest.java: sửa fixture khách lẻ tạo snapshot đúng ngay lúc tạo đơn; không sửa đơn CONFIRMED.
- docs/reports-api.md: từng endpoint, query/permission/schema/ví dụ/metric/thời gian/mã lỗi.
- README.md: liên kết tài liệu mới.
- docs/reports-verification.md: tài liệu này.

Không sửa migration V1–V13 hoặc logic nhập/bán/thu/công nợ/kiểm kê hiện có.
Các PDF mẫu được test cũ sinh lại đã khôi phục, không thuộc thay đổi bàn giao.

## Môi trường thực chạy

JDK 21.0.10 tại D:\project_gen_ai\jdk-21\jdk-21.0.10, Maven wrapper hiện có.
Java mặc định trên PATH là 11, vì vậy cần đặt JAVA_HOME khi chạy.
H2 dùng application-test.yml và H2MigrationConfiguration hiện có; adapter không áp dụng trigger PL/pgSQL.
PostgreSQL 18.6 dùng binaries tại C:\Program Files\PostgreSQL\18\bin.
Khởi động cluster kiểm thử có sẵn target/stocktake-pg-data trên 127.0.0.1:55449,
tạo database reports_test_20261013 riêng với user stocktake_test, không mật khẩu trong cluster test.
Không dùng database ứng dụng/production. Cluster đã dừng sau kiểm tra; database/artifact kiểm thử vẫn giữ để tái hiện.

PostgreSQL áp dụng migration V1–V14, mọi flyway_schema_history.success=true.
Trước bộ hồi quy (khi fixture các test cũ chưa xóa/tạo lại permissions), kiểm tra SQL xác nhận:
6 permissions REPORT_*, 0 role_permissions liên quan. Không có grant tự động trong migration.
Có index event confirmed_at/cancelled_at trên sales_orders và created_at/cancelled_at trên payments.

## Kết quả thực tế

| Lượt kiểm tra | Số test | Qua | Lỗi | Bỏ qua |
| --- | ---: | ---: | ---: | ---: |
| ReportApiIntegrationTest — H2, bản cuối | 18 | 18 | 0 | 0 |
| ReportApiIntegrationTest — PostgreSQL, bản cuối | 18 | 18 | 0 | 0 |
| Toàn bộ suite — H2 | 94 | 90 | 0 | 4 |
| Toàn bộ suite — PostgreSQL, lượt đầu | 94 | 93 | 1 | 0 |
| PaymentPrintApiIntegrationTest — H2 sau sửa fixture | 3 | 3 | 0 | 0 |
| PaymentPrintApiIntegrationTest — PostgreSQL sau sửa fixture | 3 | 3 | 0 | 0 |

Lỗi duy nhất lượt toàn bộ PostgreSQL nằm ở fixture cũ adminBypassAndCancelledRetailReceipt:
UPDATE thông tin khách trên đơn CONFIRMED bị protect_sales_order_state (V9) chặn.
Đã sửa fixture để tạo đơn/phiếu thu mới với snapshot khách lẻ, không bỏ assertion, không vô hiệu hóa trigger hoặc sửa production.
Chạy lại đầy đủ 3 test của class trên cả hai DB, đều qua. Không chạy lại toàn bộ suite sau thay đổi fixture này.
4 bài bỏ qua trên H2 là guard/concurrency chỉ có ý nghĩa trên PostgreSQL; tất cả đã chạy và qua trên PostgreSQL.

Chi tiết lượt toàn bộ PostgreSQL, ngoài class print gặp lỗi fixture:
AuthAndUser 6/6, Inventory 6/6, JwtService 2/2, ReceiptPdf 2/2,
Reports 18/18, Sales 26/26, Stock 12/12, Stocktake 19/19.
Maven compile và các lần test thành công có exit code 0; lượt toàn bộ PostgreSQL ban đầu exit 1 vì lỗi fixture nêu trên.
git diff --check không có lỗi whitespace.

## 18 bài kiểm tra module mới

- Ledger nhập 100/bán 10/kiểm kê giảm 3 trả closing=87; hủy kỳ sau không sửa kỳ trước; kỳ yên lặng vẫn có opening/closing; giữ history ngừng hoạt động.
- Hủy nhập phân loại riêng, giữ dòng số lượng 0; kiểm kê tăng.
- Bán/hủy theo confirmedAt/cancelledAt, bỏ hủy nháp, không lọc saleDate.
- Thu/hủy theo createdAt/cancelledAt, cả CASH và BANK_TRANSFER; net kỳ hủy âm, không lọc paymentDate.
- Ranh giới 00:00 Việt Nam và upper bound ngày sau exclusive.
- Tổng sales/payments không phụ thuộc pageSize hoặc trang rỗng, scope nhân viên lọc trước count/SUM.
- Công nợ hiện tại gồm khách ngừng hoạt động, khách lẻ riêng, chỉ CONFIRMED còn nợ và ACTIVE payments; không phụ thuộc kỳ dashboard.
- Tồn thấp gồm balance chưa có, loại ngưỡng 0/kho/sản phẩm ngừng hoạt động.
- Quyền endpoint, ngoại lệ Admin, scope kho thay đổi với cùng JWT, omission section dashboard.
- Lỗi ngày/pagination/UUID/kho/unauthenticated giữ response error chuẩn.
- Tồn import không có ledger bị từ chối, không tự sửa dữ liệu.
- Tiền tổng vượt Long trong sales/payments/receivables, phạm vi một kho vẫn trả Long.MAX_VALUE chính xác.
- Tổng receiptQuantity vượt Long dù closing nhỏ vẫn trả NUMERIC_OVERFLOW.
- Đơn nhiều lines không nhân order count/doanh số; discount riêng; hủy đúng tổng lượng và tiền.
- Dashboard tổng đủ kho, low-stock count trong DB, metric rỗng, section chỉ công nợ.
- Đối soát ledger và phân trang inventory đúng scope kho/product; lệch kho khác không làm hỏng report nhân viên.
- Tổng quantity bán vượt Long dù tiền bằng 0 vẫn bị từ chối.
- So sánh toàn bộ dữ liệu các bảng nghiệp vụ trước/sau mọi report: không thay đổi.

Không benchmark tải lớn hoặc kiểm toán dữ liệu production.
SQL và hành vi tổng hợp exact đã chạy trên PostgreSQL thực, không suy diễn chỉ từ H2.

## Lệnh tái hiện

Các test có cleanup/truncate fixture: chỉ chạy trên database test riêng.

~~~powershell
$env:JAVA_HOME='D:\project_gen_ai\jdk-21\jdk-21.0.10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -q test
.\mvnw.cmd -q test '-Dtest=ReportApiIntegrationTest'
.\mvnw.cmd -q test '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55449/reports_test_20261013' '-Dspring.datasource.username=stocktake_test' '-Dspring.datasource.password=' '-Dspring.datasource.driver-class-name=org.postgresql.Driver'
.\mvnw.cmd -q test '-Dtest=PaymentPrintApiIntegrationTest' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55449/reports_test_20261013' '-Dspring.datasource.username=stocktake_test' '-Dspring.datasource.password=' '-Dspring.datasource.driver-class-name=org.postgresql.Driver'
~~~

Cluster test cần khởi động trước lệnh PostgreSQL; không chỉ định URL production.
Log các lần thực chạy có thêm logging flags và redirect vào target:
- target/reports-h2-test.log, target/reports-postgres-test.log: lượt 14 test ban đầu.
- target/reports-h2-full-test.log, target/reports-h2-summary.txt: suite H2 cuối (18 test reports).
- target/reports-postgres-full-test.log, target/reports-postgres-summary.txt: suite PostgreSQL (18 test reports + lỗi fixture cũ).
- target/reports-print-h2-fix.log, target/reports-print-postgres-fix.log: kiểm thử sau sửa fixture.
- target/reports-pg.log: log cluster PostgreSQL.
- target/surefire-reports: XML/text từng class; class print là kết quả chạy lại mới nhất.

## Vận hành

Flyway V14 chạy khi backend khởi động trên database mục tiêu. Sau khi triển khai, quản trị viên tự cấp REPORT_* cần thiết cho nhân viên; cấp JWT mới để cập nhật authorities.
Gán kho lấy từ database mỗi request. Dashboard luôn cần quyền chính và quyền từng section.
Inventory phát hiện lệch ledger/balance trả INVENTORY_HISTORY_INCONSISTENT. Hướng đối soát có căn cứ nằm trong reports-api.md; không có migration tự bịa số dư lịch sử.
Không có Excel/PDF báo cáo, biểu đồ FE, giá vốn, lợi nhuận, VAT hoặc công nợ lịch sử.
