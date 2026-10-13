# Bàn giao Kiểm kê kho — 2026-10-12

## File triển khai

- src/main/java/com/frontendbase/api/stocktake/dto/StocktakeDtos.java: payload strict integer/field, validation, response/audit.
- src/main/java/com/frontendbase/api/stocktake/controller/StocktakeController.java: 7 API, quyền riêng từng thao tác.
- src/main/java/com/frontendbase/api/stocktake/repository/StocktakeRepository.java: JDBC cùng datasource/transaction hiện tại.
- src/main/java/com/frontendbase/api/stocktake/service/StocktakeService.java: snapshot, thay thế nháp, refresh, stale, xác nhận/hủy/idempotency/phạm vi.
- src/main/resources/db/migration/V12__add_stocktakes.sql: bảng, revision, nguồn movement, index/FK/CHECK và 5 permission; không tự cấp quyền.
- src/main/resources/db/migration/V13__protect_stocktake_history.sql: PostgreSQL guard cho phiếu/dòng cuối.
- src/main/java/com/frontendbase/api/common/PermissionCodes.java: bổ sung 5 mã.
- src/main/java/com/frontendbase/api/stock/service/StockOperations.java: tăng revision cho nhập/bán/hủy nhập/hủy bán; kiểm tra tràn bằng Math.addExact.
- src/main/java/com/frontendbase/api/stock/service/StockService.java: join nguồn kiểm kê khi đọc lịch sử.
- src/main/java/com/frontendbase/api/stock/repository/StockRepository.java và stock/dto/StockDtos.java: stocktakeId/stocktakeCode.
- src/test/java/com/frontendbase/api/StocktakeApiIntegrationTest.java: 19 bài kiểm thử.
- src/test/java/com/frontendbase/api/StockTestCleanup.java: cleanup thêm bảng theo FK trên database test.
- src/test/java/com/frontendbase/api/H2MigrationConfiguration.java: chỉ bỏ trigger V13 trên H2, production SQL giữ nguyên.
- docs/stocktake-api.md: đầy đủ contract request/response, mã lỗi và details, schema movement, revision/khóa/quyền.

Không sửa migration V1–V11. API tài chính giữ nguyên; có bài kiểm thử so sánh toàn bộ response
đơn bán/thanh toán/công nợ trước và sau xác nhận kiểm kê.

## Môi trường và lệnh

Maven mặc định của máy dùng JDK 8; đã chạy bằng JDK 21 sẵn có:
D:\project_gen_ai\jdk-21\jdk-21.0.10.
PostgreSQL 18.6 chạy bằng binaries sẵn có, cluster riêng
target/stocktake-pg-data, host 127.0.0.1, port 55449, database stocktake_test_20261012,
user stocktake_test. Không dùng database ứng dụng hoặc cluster test cũ.
Migration V1–V13 đã áp dụng thành công trên PostgreSQL; kết quả flyway_schema_history success=true.

```powershell
$env:JAVA_HOME='D:\project_gen_ai\jdk-21\jdk-21.0.10'
mvn -q test
mvn -q test '-Dtest=StocktakeApiIntegrationTest,StockApiIntegrationTest,SalesApiIntegrationTest' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55449/stocktake_test_20261012' '-Dspring.datasource.username=stocktake_test' '-Dspring.datasource.password=' '-Dspring.datasource.driver-class-name=org.postgresql.Driver'
```

Các test cleanup/truncate fixture: chỉ chạy với database test riêng.
Lần chạy thực tế thêm logging flags và redirect log vào target (gitignored).
H2 không thay thế kiểm tra khóa/trigger PostgreSQL.

## Kết quả thực tế

| Bộ kiểm tra | Tổng | Qua | Lỗi | Bỏ qua |
| --- | ---: | ---: | ---: | ---: |
| Toàn project H2, lượt cuối | 76 | 72 | 0 | 4 |
| StocktakeApiIntegrationTest H2 (trong lượt trên) | 19 | 18 | 0 | 1 |
| StocktakeApiIntegrationTest PostgreSQL, lượt cuối | 19 | 19 | 0 | 0 |
| StockApiIntegrationTest PostgreSQL | 12 | 12 | 0 | 0 |
| SalesApiIntegrationTest PostgreSQL | 26 | 26 | 0 | 0 |

Lượt cuối H2 và PostgreSQL đều có Maven exit code 0. H2 bỏ qua 4 bài audit/trigger PostgreSQL.
Các bài trigger nhập/bán/thanh toán/kiểm kê đều đã chạy thành công trên PostgreSQL.
Kiểm tra git diff --check không phát hiện lỗi whitespace.
PostgreSQL test riêng đã dừng sau kiểm tra; giữ cluster/log trong target để kiểm tra lại.

Log: target/stocktake-h2-final.log, target/stocktake-postgres-test.log,
target/stocktake-postgres-final.log. Báo cáo chi tiết: target/surefire-reports.

## Phạm vi đã kiểm tra

- 100 -> 97 (-3), tăng lên 105 (+5), đếm 0, chưa đếm, thiếu lý do, không lệch không sinh movement/revision.
- Nháp không đổi tồn; edit giữ snapshot, dòng mới lấy snapshot, refresh xóa count/reason.
- Stale sau nhập/bán/hủy chứng từ; tăng/giảm về số cũ và balance chưa tồn tại vẫn stale.
- Hai kiểm kê cạnh tranh; retry confirm đồng thời chỉ điều chỉnh một lần.
- Xác nhận kiểm kê và nhập đồng thời không mất cập nhật; tất cả balance khớp tổng movement.
- Lỗi constraint một dòng rollback toàn bộ tồn/revision/movement/trạng thái/audit.
- Tràn revision không thay đổi dữ liệu, kể cả nhập kho hiện có.
- Version conflict trả expectedVersion/currentVersion; stale trả details cho toàn bộ dòng stale.
- Permission, scope, list count trước phân trang, thu hồi kho với JWT cũ, retry vẫn bị chặn.
- Kho inactive: chặn tạo/sửa/refresh/confirm DRAFT, cho xem/hủy nháp/retry cuối.
- Sản phẩm inactive có lịch sử được kiểm kê; inactive chưa có lịch sử bị chặn.
- Token string/float/âm/tràn, Long.MAX_VALUE, duplicate/empty/>1000 lines, note dài và field do client tự gửi.
- Lọc ngày createdAt theo Asia/Ho_Chi_Minh tại biên 00:00, hai đầu bao gồm.
- PostgreSQL chặn sửa/xóa phiếu/dòng đã xác nhận và giữ append-only movement.
- Hồi quy nhập/bán/hủy/thanh toán/in phiếu; kiểm kê giữ nguyên response đơn/thu tiền/công nợ.

## Vận hành

Phiên bản này kiểm kê hiện tại. Tạm ngừng nhập/xuất thực tế khi đếm.
STALE cần người dùng chủ động refresh rồi đếm lại; backend không tự đổi baseline.
Permission nhân viên lấy từ JWT; phạm vi kho và ngoại lệ ADMIN đọc database mỗi request.
Cấp quyền mới qua quản trị hiện có rồi cấp lại access token; migration không gán role/user.
Phiếu cuối không đảo/xóa; sai kiểm kê thì lập phiếu mới.
