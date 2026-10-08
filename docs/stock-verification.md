# Kết quả triển khai và kiểm tra

Thực hiện ngày 2026-10-08, dùng Microsoft JDK 21.0.10 và Maven wrapper 3.9.9.

## File mới

- `src/main/java/com/frontendbase/api/stock/controller/StockReceiptController.java`: sáu endpoint phiếu nhập, kiểm tra permission bằng cơ chế hiện có.
- `src/main/java/com/frontendbase/api/stock/controller/InventoryController.java`: danh sách tồn và biến động theo kho.
- `src/main/java/com/frontendbase/api/stock/dto/StockDtos.java`: request/response, enum trạng thái và validation.
- `src/main/java/com/frontendbase/api/stock/repository/StockRepository.java`: JDBC repository, khóa hàng và mapping response.
- `src/main/java/com/frontendbase/api/stock/service/StockService.java`: transaction, trạng thái/version, snapshot, tính tiền, tồn và lịch sử.
- `src/main/java/com/frontendbase/api/common/validation/StrictLongDeserializer.java`: từ chối JSON số thực/chuỗi và tràn Long.
- `src/main/resources/db/migration/V6__add_stock_receipts_and_inventory.sql`: schema, ràng buộc, index, bảy permission, cấp ADMIN.
- `src/main/resources/db/migration/V7__protect_stock_audit_history.sql`: trigger PostgreSQL bảo vệ lịch sử và phiếu final.
- `src/test/java/com/frontendbase/api/StockApiIntegrationTest.java`: 12 integration test mới, gồm concurrency bằng hai thread/request và database thật.
- `src/test/java/com/frontendbase/api/StockTestCleanup.java`: xóa fixture theo FK; PostgreSQL test dùng TRUNCATE để không vi phạm audit guard.
- `docs/stock-api.md`: đầy đủ API, lỗi/version/retry và hướng dẫn PowerShell/Postman/SQL.
- `docs/stock-verification.md`: danh sách file và kết quả thực tế.

## File cập nhật

- `src/main/java/com/frontendbase/api/common/PermissionCodes.java`: thêm bảy constant vào ALL.
- `src/test/java/com/frontendbase/api/InventoryApiIntegrationTest.java`: cleanup stock trước fixture danh mục.
- `src/test/java/com/frontendbase/api/AuthAndUserApiIntegrationTest.java`: cleanup stock trước fixture user.
- `src/test/java/com/frontendbase/api/H2MigrationConfiguration.java`: V7 PostgreSQL được thay bằng SELECT 1 trong H2 test; production SQL/checksum không đổi.

JDBC dùng cùng datasource và tham gia JpaTransactionManager của project. Không thêm dependency. Tái sử dụng WarehouseAccess, JWT/PreAuthorize, ApiException/GlobalExceptionHandler, Flyway, page từ 1 và response items/total/page/pageSize. Không đổi V1–V5.

## Kết quả thực tế

| Lần kiểm tra cuối | Tổng | Qua | Lỗi | Bỏ qua |
|---|---:|---:|---:|---:|
| Toàn bộ test trên H2 PostgreSQL mode | 26 | 25 | 0 | 1 |
| StockApiIntegrationTest trên PostgreSQL 18 | 12 | 12 | 0 | 0 |

Cả hai lần BUILD SUCCESS. Test bỏ qua trên H2 là postgresAuditGuardsRejectDirectMutation vì H2 không hỗ trợ PL/pgSQL; test này đã chạy và qua trên PostgreSQL. Flyway V1–V7 áp dụng thành công trên database PostgreSQL test sạch. Đã biên dịch toàn bộ source/test với release 21.

Log lần chạy nằm tại `target/stock-h2-test.log` và `target/stock-postgres-test.log`; target được gitignore. PostgreSQL được dựng riêng ở `target/stock-pg-data`, cổng 55439, database stock_test; không dùng database ứng dụng hiện tại, đã dừng sau kiểm tra.

Các test bao phủ:

1. Nháp không tăng tồn; xác nhận 100 tại A thì B vẫn 0; tổng tiền tính tại backend và không đổi giá tham khảo.
2. Hai phiếu cùng sản phẩm xác nhận đồng thời cộng đủ; hai confirm cùng phiếu chỉ cộng một lần.
3. Hai cancel cùng phiếu chỉ đảo một lần; hủy DRAFT không sinh biến động.
4. Confirm chạy đồng thời PUT/cancel cùng version cho kết quả nhất quán; stale version, đổi kho và sửa final bị từ chối.
5. Hủy thiếu tồn một trong hai dòng không thay đổi bất kỳ dòng, trạng thái hoặc lịch sử nào. Fixture mô phỏng tiêu thụ tồn bằng SQL trong database test vì chưa triển khai xuất kho.
6. Constraint cố ý gây lỗi trên dòng biến động thứ hai: rollback cả balance, movement, trạng thái/version, rồi confirm hợp lệ thành công khi bỏ constraint.
7. Kiểm tra thiếu permission trên mọi endpoint, phạm vi đọc/ghi, lọc trước total/pagination, thu hồi gán kho có hiệu lực với JWT cũ.
8. Quantity 0/âm/lẻ/chuỗi, giá âm/lẻ/chuỗi, empty/duplicate lines, tràn tiền dòng/tổng và tràn tồn bị từ chối.
9. Recheck kho/sản phẩm hoạt động khi confirm; lịch sử/snapshot còn nguyên sau đổi danh mục; hủy được khi danh mục ngừng hoạt động.
10. Khoảng thời gian/phân trang, yêu cầu lý do hủy, idempotent retry và tính bất biến ở trigger PostgreSQL.
11. Các luồng bình thường đối soát balance bằng SUM(quantity_change).

## Giới hạn thiết kế

Khóa hàng kho tuần tự hóa các thao tác nhập trong cùng kho, kể cả khi balance chưa tồn tại. Đây là đánh đổi throughput để đảm bảo tính nhất quán và hoạt động qua nhiều instance backend. Permission chức năng vẫn lấy từ JWT theo project; phạm vi gán kho và ngoại lệ ADMIN/account hoạt động kiểm tra database. Các module xuất/kiểm kê tương lai phải phối hợp cùng cơ chế khóa.

Phạm vi chỉ gồm nhập kho, tồn và lịch sử. Không có tồn đầu kỳ, bán hàng, xuất kho, chuyển kho, công nợ, kiểm kê hoặc tính giá vốn.
