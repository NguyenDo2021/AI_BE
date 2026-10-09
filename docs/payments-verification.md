# Thanh toán / Công nợ — triển khai và kiểm chứng

Kiểm tra ngày 2026-10-09 với Microsoft JDK 21.0.10, Maven Wrapper 3.9.9,
Spring Boot 3.5.6 và PostgreSQL 18. Không thêm dependency.
Contract: [payments-api.md](payments-api.md).

## Migration

- V10__add_payments_and_receivables.sql: bảng payments, ràng buộc amount/status/audit,
  FK, code duy nhất, UNIQUE(created_by,idempotency_key), indexes, view sales_payment_totals,
  bốn permission PAYMENT_VIEW/CREATE/CANCEL và RECEIVABLE_VIEW. Không gán role/user.
- V11__protect_payment_history.sql: PostgreSQL trigger cấm xóa/sửa phiếu,
  chỉ cho ACTIVE -> CANCELLED; kiểm tra nguồn đơn/kho/khách, CONFIRMED và giới hạn thu;
  trigger chặn hủy đơn có phiếu ACTIVE.
- V1–V9 giữ nguyên checksum. Flyway là cơ chế quản lý schema production.

## File mới

- src/main/java/com/frontendbase/api/payment/dto/PaymentDtos.java:
  payload với strict integer/validation; response phiếu, tổng đơn, công nợ.
  Field ngoài schema tạo phiếu bị từ chối.
- src/main/java/com/frontendbase/api/payment/controller/PaymentController.java:
  tạo/chi tiết/danh sách/hủy phiếu và lịch sử theo đơn.
- src/main/java/com/frontendbase/api/payment/controller/ReceivableController.java:
  công nợ theo khách/kho, chi tiết khách và đơn khách lẻ còn nợ.
- src/main/java/com/frontendbase/api/payment/repository/PaymentRepository.java:
  JDBC mapper, khóa phiếu, tìm key theo actor, chuyển tổng numeric sang Long chính xác.
- src/main/java/com/frontendbase/api/payment/service/PaymentAccess.java:
  kiểm tra RBAC live, không giữ permission bị thu hồi trong JWT cũ.
- src/main/java/com/frontendbase/api/payment/service/PaymentService.java:
  transaction thu/hủy phiếu, khóa và idempotency, phạm vi, lịch sử/phân trang.
- src/main/java/com/frontendbase/api/payment/service/ReceivableService.java:
  công nợ hiện tại, nhóm khách/kho, tổng khách trên toàn bộ đơn khớp,
  lọc phạm vi trước count/pagination, tách khách lẻ.
- Hai migration trên; docs/payments-api.md; docs/payments-verification.md.

## File sửa

- sales/dto/SalesDtos.java: PaymentStatus và paidAmount/remainingAmount/paymentStatus.
- sales/repository/SalesRepository.java: đọc tổng từ view; khóa hàng sales_orders
  bằng truy vấn riêng trước khi đọc tổng. Không FOR UPDATE trực tiếp view tổng hợp.
- sales/service/SalesService.java: SALES_ORDER_HAS_PAYMENTS trước khi ghi SALE_CANCEL.
- common/PermissionCodes.java: đăng ký bốn mã quyền.
- security/SecurityConfig.java: CORS cho phép Idempotency-Key.
- src/test/java/com/frontendbase/api/SalesApiIntegrationTest.java:
  thêm 9 ca nghiệp vụ/đồng thời/DB và 1 ca CORS, tái sử dụng fixture đơn bán.
- H2MigrationConfiguration.java: chỉ bỏ PL/pgSQL V11 trên H2, giữ SQL production nguyên vẹn.
- StockTestCleanup.java: xóa fixture payments trước sales/identity; PostgreSQL test dùng TRUNCATE.
- README.md và docs/sales-api.md: liên kết và cập nhật contract.

Các đường dẫn Java rút gọn phía trên nằm dưới src/main/java/com/frontendbase/api.

## Transaction và khóa

Tái sử dụng JpaTransactionManager/JdbcTemplate, WarehouseAccess, ApiException,
GlobalExceptionHandler, StrictLongDeserializer và PageResponse.

Thu tiền: kho -> đơn -> user thực hiện (để tuần tự hóa key qua nhiều kho) -> insert phiếu.
Hủy phiếu: kho -> đơn -> phiếu. Hủy đơn dùng kho -> đơn như cũ, kiểm tra paidAmount
trước khi hoàn tồn. Không có thao tác thu/hủy phiếu nào ghi bảng inventory.
Các phiếu cùng user được tuần tự hóa; khóa kho cũng giữ cơ chế tuần tự hóa hiện có.

UNIQUE(created_by,idempotency_key) bảo vệ ở database. Giao dịch lỗi không lưu phiếu
và không giữ key. Phiếu hủy vẫn giữ key và lịch sử, nên retry không tạo phiếu thay thế.
Kiểm tra phạm vi lại sau khi lấy khóa user. Permission được kiểm tra live ở mỗi API call.

Tổng được tính từ phiếu ACTIVE bằng view, không lưu một cache paidAmount có thể bị lệch.
Phiếu và tổng trả về được đọc trong cùng transaction. API đọc dùng REPEATABLE_READ để
count/items/tổng nhất quán. Công nợ khách tính trên toàn bộ đơn CONFIRMED còn nợ của khách;
không lấy tổng từ trang. SUM numeric và longValueExact/addExact bảo đảm số nguyên;
trả NUMERIC_OVERFLOW khi tổng trả về vượt Long.MAX_VALUE.

## Kết quả thực tế

| Lần kiểm tra | Tổng | Qua | Lỗi | Bỏ qua |
|---|---:|---:|---:|---:|
| Toàn bộ suite H2 PostgreSQL mode | 51 | 48 | 0 | 3 |
| SalesApiIntegrationTest + StockApiIntegrationTest, PostgreSQL 18 | 37 | 37 | 0 | 0 |
| CORS preflight sau thay đổi header, H2 | 1 | 1 | 0 | 0 |

Cả ba lần cuối đều BUILD SUCCESS. Suite toàn bộ chạy trước thay đổi CORS cuối;
thay đổi CORS được biên dịch và kiểm tra riêng sau đó.
Ba ca H2 bỏ qua là audit trigger PostgreSQL của nhập kho, đơn bán, phiếu thu;
cả ba chạy thành công trên PostgreSQL. Sau bổ sung ca CORS, source suite có 52 ca.

Log:
- target/payments-h2-full-test.log
- target/payments-postgres-test.log
- target/payments-cors-test.log
- target/surefire-reports/ (lần chạy riêng cuối thay báo cáo Sales; log giữ kết quả đầy đủ)

PostgreSQL dùng cluster kiểm thử có sẵn target/stock-pg-data tại 127.0.0.1:55439,
database riêng payments_test_20261009; không dùng database ứng dụng.
Flyway áp dụng V1–V11 trên database mới thành công.
Server kiểm thử đã được dừng sau kiểm chứng.

Nâng cấp: tạo payments_upgrade_20261009 từ database test V9 sales_test_20261009,
áp dụng V10/V11 bằng psql trong một transaction. Trước/sau giữ nguyên:
2 phiếu nhập, 1 biến động, tổng tồn 100 và digest lịch sử
d4264974ba78a963c1a7335da2de12dd.
Có đủ 4 permission mới và 0 role grant mới.
Thao tác psql này không cập nhật Flyway history của bản sao kiểm thử.
Log: target/payments-upgrade-before.log, payments-upgrade-test.log, payments-upgrade-after.log.

## Ca kiểm tra mới

1. Đơn 1.000.000: UNPAID -> thu 600.000/còn 400.000 -> thu 400.000/PAID.
   Thu vượt nợ bị chặn; hủy phiếu 400.000 phục hồi nợ; tồn/biến động giữ nguyên.
2. Retry cùng key/payload giữ cùng phiếu; payload khác conflict; retry phiếu hủy
   và retry sau khi đơn hủy trả lịch sử hiện tại, không tạo phiếu thay thế.
3. Hai khoản 600.000 đồng thời: chỉ một thành công. Hai request cùng key:
   chỉ một phiếu; key cùng user trên hai đơn/kho cũng chỉ một phiếu.
   User khác được dùng cùng UUID.
4. Thu và hủy đơn đồng thời: một kết quả nghiệp vụ hợp lệ, không thu vào đơn đã hủy.
   Đơn đang có khoản thu ACTIVE bị SALES_ORDER_HAS_PAYMENTS.
5. Hai lần hủy phiếu đồng thời chỉ đảo một lần; giữ metadata/lý do hủy đầu tiên.
6. DRAFT/CANCELLED không thu được; đơn giá 0 là PAID; strict integer từ chối
   float/string/âm/0/ngoài Long. Thu đủ Long.MAX_VALUE không mất chính xác.
   Constraint lỗi khi insert rollback cả phiếu/key; retry key sau rollback thành công.
7. Công nợ khách gộp đầy đủ độc lập pageSize; DRAFT/giá 0/khách lẻ không bị gộp.
   Phạm vi khách/kho, keyword literal, page ngoài dữ liệu và tổng vượt Long được kiểm tra.
8. PAYMENT_VIEW độc lập SALES_ORDER_VIEW; user thiếu quyền bị chặn.
   JWT/key cũ sau thu hồi permission hoặc kho bị chặn; hủy lặp vẫn kiểm tra phạm vi.
   Khách/kho ngừng hoạt động vẫn thu/hủy được; filter ngày bao gồm hai đầu.
9. PostgreSQL bảo vệ lịch sử: sửa amount/xóa phiếu/sửa phiếu đã hủy/hủy đơn đã thu đều bị chặn.
10. OPTIONS từ origin FE được phép gửi authorization/content-type/idempotency-key.

Kiểm thử API dùng MockMvc và request đồng thời qua thread riêng với transaction/database thật.
Không thực hiện browser E2E; preflight CORS được kiểm tra qua MockMvc.
Luồng thử FE và ví dụ request/retry có trong payments-api.md.

## Lệnh kiểm tra

PowerShell (cần JDK 21):

```powershell
$env:JAVA_HOME='D:\project_gen_ai\jdk-21\jdk-21.0.10'
.\mvnw.cmd test
.\mvnw.cmd test '-Dtest=SalesApiIntegrationTest,StockApiIntegrationTest' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55439/payments_test_20261009' '-Dspring.datasource.username=stock_test' '-Dspring.datasource.password=' '-Dspring.datasource.driver-class-name=org.postgresql.Driver'
.\mvnw.cmd test '-Dtest=SalesApiIntegrationTest#paymentCorsPreflightAllowsIdempotencyHeader'
```

Các lệnh PostgreSQL phải trỏ database dùng riêng cho test: fixture cleanup xóa dữ liệu test.
Lần chạy thực tế thêm logging flags để giảm log, không đổi hành vi nghiệp vụ.
