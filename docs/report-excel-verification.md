# Bàn giao xuất Excel — 2026-10-14

Tính năng và API contract/headers/mã lỗi/FE: [report-excel-export.md](report-excel-export.md). Danh sách file thay đổi và lựa chọn Apache POI SXSSF nằm trong tài liệu đó. Bốn [workbook mẫu từ fixture](samples/report-exports/README.md) được giữ trong repo.

## Kết quả bản cuối thực chạy

| Phạm vi | Số bài | Qua | Lỗi | Bỏ qua |
| --- | ---: | ---: | ---: | ---: |
| Toàn bộ suite — H2 | 108 | 103 | 0 | 5 |
| Toàn bộ suite — PostgreSQL 18.6 | 108 | 108 | 0 | 0 |
| Nhóm báo cáo/Excel trong suite H2 | 32 | 31 | 0 | 1 |
| Nhóm báo cáo/Excel trong suite PostgreSQL | 32 | 32 | 0 | 0 |

Cả hai lượt cuối Maven exit code 0. Nhóm báo cáo/Excel gồm ReportApiIntegrationTest 28 bài (18 cũ, 10 thêm hoặc mở rộng), ReportWorkbookTest 1, ReportExportLimitTest 1, ReportExportFailureTest 2. H2 bỏ qua test snapshot commit đồng thời của export và 4 test guard/concurrency cũ cần PostgreSQL; tất cả chạy và qua trên PostgreSQL.

Các lượt ban đầu phát hiện lỗi UTF-8 khi ghi source qua pipe PowerShell và fixture thiếu tồn/hủy đơn trước phiếu thu. Đã sửa source/fixture, không nới kiểm tra nghiệp vụ hoặc trigger. Kết quả bảng trên là toàn bộ suite sau các sửa cuối, bao gồm dọn file khi rollback.

## Đã xác minh

- Đọc lại XLSX bằng XSSFWorkbook: sheet/header, freeze/autofilter, kiểu ô và giá trị; không chỉ kiểm tra HTTP 200.
- 521 dòng low-stock trong kho staff: hơn pageSize và fetchSize 500, STT liên tục, so sánh từng UUID/tên/thứ tự với toàn bộ trang API, không trùng/mất dòng.
- Inventory lọc kho/sản phẩm/kỳ: lịch sử closing=90 dù balance hiện tại=110; không có sheet tổng tự thêm.
- Sales/payments: cả 9 trường summary khớp API toàn bộ bộ lọc; kỳ hủy cho net âm -20; thu theo thời gian hệ thống.
- Long.MAX_VALUE tiền trong detail và summary đọc lại STRING chính xác. Test biên riêng gồm 0, -1, ±999999999999999, ±1000000000000000, Long.MAX_VALUE/MIN_VALUE; không làm tròn.
- Chuỗi tiếng Việt/tên dài bắt đầu `=` là STRING, không phải công thức; UUID và mã cũng STRING.
- Bốn loại workbook rỗng hợp lệ, thông báo không có dữ liệu; metadata không lộ kho ngoài phạm vi.
- Permission và scope kho cho cả bốn endpoint; Admin bypass cũ. Ngày/UUID/pagination/from-to low-stock sai trả JSON; giữ lỗi đối soát và overflow.
- Dữ liệu thực 318 kho × 316 sản phẩm phù hợp = 100.488 dòng: admin bị 409 EXPORT_TOO_LARGE, Content-Type JSON và không có attachment; staff cùng fixture vẫn xuất 316 dòng trong kho được gán. Test unit cũng xác nhận count 100.001 bị chặn trước cursor.
- PostgreSQL REPEATABLE_READ: trong lúc cursor sales đang đọc, connection khác commit một sale mới; rows/summary của transaction giữ snapshot cũ, transaction sau thấy tổng mới.
- Không thay đổi 10 bảng nghiệp vụ trước/sau tất cả báo cáo và bốn export.
- Dọn staged XLSX sau request thành công/client disconnect, dọn SXSSF temp khi lỗi bất ngờ hoặc lỗi nghiệp vụ giữa quá trình dựng; bảo toàn ApiException, lỗi bất ngờ thành REPORT_EXPORT_FAILED. Transaction rollback xóa file hoàn chỉnh.
- Header XLSX/attachment/no-store/Content-Length và expose Content-Disposition qua origin CORS được cấu hình.
- `git diff --check` không có lỗi whitespace. Hai PDF mẫu do test cũ sinh lại được khôi phục, không nằm trong thay đổi export.

## Môi trường và tái hiện

JDK 21.0.10 tại `D:project_gen_aijdk-21jdk-21.0.10`, Maven wrapper, POI 5.4.1. H2 dùng profile test và H2MigrationConfiguration hiện có. PostgreSQL 18.6: cluster kiểm thử có sẵn trong `target/stocktake-pg-data`, 127.0.0.1:55449, database mới riêng `reports_export_test_20261014`, user `stocktake_test`. Flyway có 14 migration versioned thành công. Không chạy fixture vào database ứng dụng/production. Cluster đã dừng sau kiểm tra.

```powershell
$env:JAVA_HOME='D:project_gen_aijdk-21jdk-21.0.10'
.\mvnw.cmd -q test
.\mvnw.cmd -q test '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55449/reports_export_test_20261014' '-Dspring.datasource.username=stocktake_test' '-Dspring.datasource.password=' '-Dspring.datasource.driver-class-name=org.postgresql.Driver'
```

Khởi động cluster test trước lệnh PostgreSQL; test có cleanup/truncate fixture, chỉ dùng DB test riêng. Lệnh thực chạy thêm logging flags và redirect log:

- `target/excel-h2-full-test.log`, `target/excel-h2-summary.txt`.
- `target/excel-postgres-full-test.log`, `target/excel-postgres-summary.txt`.
- `target/surefire-reports`: XML/text bản cuối PostgreSQL.
- `target/excel-pg.log`: log cluster.
- `target/report-export-samples`: workbook từ test; bản bàn giao tại `docs/samples/report-exports`.

Không benchmark throughput/RAM ở mức tối đa 100.000 dòng hoặc thử tải nhiều export đồng thời. Đã kiểm tra cursor vượt một batch và chặn dữ liệu >100.000 dòng trên PostgreSQL thực. Chưa triển khai job export bất đồng bộ, PDF báo cáo, dashboard/công nợ Excel, hoặc FE thực tế; tài liệu cung cấp đoạn Axios xử lý blob/JSON.
