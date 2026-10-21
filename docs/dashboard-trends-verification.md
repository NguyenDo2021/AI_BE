# Kiểm chứng API xu hướng dashboard

Ngày kiểm tra: 2026-10-21, Asia/Bangkok.
JDK 25.0.2, Maven wrapper 3.9.9, compile --release 21.

| Bộ kiểm tra cuối | Tổng | Đạt | Bỏ qua | Lỗi |
|---|---:|---:|---:|---:|
| Toàn bộ backend trên H2 PostgreSQL mode | 137 | 131 | 6 | 0 |
| PostgreSQL 18: trends, snapshot Excel, hai ca trả/hoàn | 10 | 10 | 0 | 0 |

H2 bỏ qua 6 ca cần PostgreSQL/PL/pgSQL theo điều kiện hiện có.
Không chạy toàn bộ suite trên PostgreSQL. Bộ PostgreSQL gồm 7 kiểm thử trends mới,
1 snapshot Excel hiện có và 2 ca trả/hoàn có báo cáo/Excel.
PostgreSQL chạy trên database test mới dashboard_trends_verify_20261021,
cluster riêng target/returns-pg-test, chỉ nghe 127.0.0.1:55439; đã dừng sau kiểm tra.
Các migration V1-V16 và trigger production được chạy thật trên database mới.
Không sửa migration hoặc cấp quyền role/user.

## Tình huống đã kiểm tra

- Đủ ngày, tăng dần, ngày trống bằng 0 và ngày nhuận 29/02.
- Khoảng 366 ngày được nhận, 367 ngày bị REPORT_PERIOD_TOO_LARGE.
- Thiếu ngày, sai ngày, đảo khoảng, sai UUID theo mã lỗi hiện có.
- Ranh giới 00:00 Việt Nam, sự kiện ngay trước đầu kỳ bị loại.
- Xác nhận/hủy bán, trả hàng, thu/đảo thu và hoàn/đảo hoàn đúng ngày sự kiện.
- Sự kiện ở kỳ sau không thay đổi chuỗi ngày của kỳ trước.
- Thu ròng, hoàn ròng và dòng tiền ròng âm chính xác.
- Tổng mọi field trends khớp summary/dashboard cùng filter.
- Transaction REPEATABLE_READ: có commit từ thread khác giữa trends và
  summary/dashboard mà kết quả trong transaction vẫn giữ nguyên.
- Admin ngoại lệ; user chỉ sales/chỉ payments không nhận series thiếu quyền;
  chỉ dashboard nhận date-only; thiếu dashboard trả FORBIDDEN.
- Scope áp dụng trước tổng hợp; bỏ gán kho có hiệu lực với cùng JWT;
  kho ngoài phạm vi bị WAREHOUSE_ACCESS_DENIED kể cả không có quyền series.
- JSON integer chính xác với 9007199254740993, lớn hơn giới hạn integer an toàn JavaScript.
- Overflow thành phần sales dù net=0; overflow thu ròng; overflow phép trừ
  dòng tiền dù hai net thành phần vẫn nằm trong Long.
- Series không có quyền không được đọc, kể cả dữ liệu có thể gây overflow.
- Cơ cấu thu CASH/BANK_TRANSFER vẫn gồm phiếu bị hủy kỳ sau hoặc trong cùng kỳ;
  kỳ chỉ đảo thu có cơ cấu 0 và net âm.
- Các API báo cáo, inventory, trả/hoàn, workbook và export hiện có qua hồi quy.
- git diff --check đạt.

## Sửa fixture kiểm thử cũ

Lần chạy toàn bộ đầu tiên có 2 lỗi: ReturnsApiIntegrationTest tạo sự kiện ở ngày
chạy nhưng lọc báo cáo cố định 2026-10-16. Đã đổi riêng bộ lọc báo cáo sang
confirmedAt của fixture đến ngày hiện tại theo ReportPeriod.ZONE.
Các bộ lọc refundDate nghiệp vụ giữ nguyên. Hai ca này đạt trên PostgreSQL
và toàn suite H2 cuối đạt. Lần thử đầu trends cũng sửa fixture JWT không thêm
permission rỗng vào token dashboard-only.

## Lệnh và bằng chứng

PowerShell tại repo:

~~~powershell
$env:JAVA_HOME='C:/Users/dont/AppData/Local/jdks/jdk-25.0.2'
.\mvnw.cmd -q test
~~~

PostgreSQL yêu cầu database kiểm thử riêng vì fixture dọn dữ liệu test:

~~~powershell
.\mvnw.cmd -q '-Dtest=ReportApiIntegrationTest#trends*+excelPostgresCursorAndSummaryShareSnapshotDuringConcurrentCommit,ReturnsApiIntegrationTest#reportsDashboardMovementsAndWorkbooksMatchNewTotals+refundValidationBankTransferReplaysPermissionsAndScopedLists' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55439/dashboard_trends_verify_20261021' '-Dspring.datasource.username=returns_test' '-Dspring.datasource.password=' '-Dspring.datasource.driver-class-name=org.postgresql.Driver' test
~~~

- target/dashboard-trends-all-h2-final.log: toàn bộ H2 cuối, exit 0.
- target/surefire-reports: XML kết quả H2 cuối.
- target/dashboard-trends-postgres.log: PostgreSQL, exit 0.
- target/dashboard-trends-postgres-reports: XML hai class PostgreSQL, 8 + 2 ca.
- target/dashboard-trends-all-h2.log: lần đầu, 2 lỗi fixture ngày nêu trên.
- target/dashboard-trends-h2.log: lần đầu kiểm thử báo cáo, lỗi fixture JWT nêu trên.

Hai PDF mẫu do test tự tạo được khôi phục đúng byte từ HEAD sau kiểm tra;
không đưa thay đổi mẫu vào bản giao.

## Phạm vi thay đổi

- ReportController: GET /dashboard/trends.
- ReportDtos: DashboardTrends, TrendItem, SalesTrend, PaymentTrend.
- ReportPeriod: giới hạn riêng trends 366 ngày.
- ReportService: quyền từng series, điền ngày trống trong snapshot.
- ReportRepository: SQL sự kiện và công thức dùng chung cho báo cáo/Excel/trends;
  GROUP BY ngày Việt Nam trong DB, tối đa 2 query tổng hợp.
- ReportApiIntegrationTest: 7 ca tích hợp mới.
- ReturnsApiIntegrationTest: sửa 2 bộ lọc ngày trong fixture.
- docs/dashboard-trends-api.md, docs/reports-api.md, tài liệu này.

Hai field recordedCashAmount/recordedBankTransferAmount đã tồn tại trong
PaymentSummary và dashboard; tái sử dụng nguyên trạng, kiểm thử và ghi rõ
contract cho donut. Không đổi nghĩa field cũ, không thêm dữ liệu giả.

[API contract](dashboard-trends-api.md).
