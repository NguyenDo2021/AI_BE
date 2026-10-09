# Customer and sales implementation / verification

Verified on 2026-10-09 with Microsoft JDK 21.0.10, Maven wrapper 3.9.9 and PostgreSQL 18.
No dependency changes. Full API specification: [sales-api.md](sales-api.md).

## New files

- `src/main/java/com/frontendbase/api/customer/controller/CustomerController.java`: four APIs, endpoint permissions.
- `src/main/java/com/frontendbase/api/customer/dto/CustomerDtos.java`: create/update/response and strict integer status.
- `src/main/java/com/frontendbase/api/customer/repository/CustomerRepository.java`: JDBC mapping and row locks.
- `src/main/java/com/frontendbase/api/customer/service/CustomerService.java`: scoped CRUD/search/pagination, immutable warehouse.
- `src/main/java/com/frontendbase/api/sales/controller/SalesOrderController.java`: six APIs, independent sales permissions.
- `src/main/java/com/frontendbase/api/sales/dto/SalesDtos.java`: state/version/payloads/totals/snapshots/audit.
- `src/main/java/com/frontendbase/api/sales/repository/SalesRepository.java`: JDBC header/line mapping, row locking.
- `src/main/java/com/frontendbase/api/sales/service/SalesService.java`: atomic state transitions, snapshots, discount and inventory.
- `src/main/java/com/frontendbase/api/stock/service/StockOperations.java`: shared receipt/sale exact arithmetic, locking and stock movement.
- `src/main/resources/db/migration/V8__add_customers_and_sales_orders.sql`: tables, indexes, warehouse/customer FK, movement source/sign and uniqueness, eight permissions seeded and assigned to ADMIN.
- `src/main/resources/db/migration/V9__protect_sales_audit_history.sql`: PostgreSQL triggers protect customer identity, final sales/lines; existing movement immutability remains in V7.
- `src/test/java/com/frontendbase/api/SalesApiIntegrationTest.java`: sixteen integration tests, actual concurrent requests and database failures.
- `docs/sales-api.md`, `docs/sales-verification.md`: API contract and evidence.

## Modified files

- `src/main/java/com/frontendbase/api/common/PermissionCodes.java`: eight constants in ALL.
- `src/main/java/com/frontendbase/api/stock/service/StockService.java`: delegate shared inventory logic; history joins both receipt/sale snapshots.
- `src/main/java/com/frontendbase/api/stock/dto/StockDtos.java`: append salesOrderId/salesOrderCode to movement response.
- `src/main/java/com/frontendbase/api/stock/repository/StockRepository.java`: map new source fields.
- `src/test/java/com/frontendbase/api/H2MigrationConfiguration.java`: test-only legacy check naming for forward migration, skip PL/pgSQL V9 on H2.
- `src/test/java/com/frontendbase/api/StockTestCleanup.java`: remove sales/customer fixture references; PostgreSQL isolated tests use TRUNCATE to bypass audit DELETE guards.
- `README.md`, `docs/stock-api.md`: links and movement/receipt compatibility notes.

Production V1-V7 SQL files/checksums are unchanged. JDBC participates in the same
JpaTransactionManager/datasource as existing modules. WarehouseAccess, PreAuthorize,
ApiException/GlobalExceptionHandler, StrictLongDeserializer and existing line/page
DTOs are reused.

## Actual test results

| Run | Total | Passed | Failures/errors | Skipped |
|---|---:|---:|---:|---:|
| All tests on H2 PostgreSQL mode | 42 | 40 | 0 | 2 |
| SalesApiIntegrationTest + StockApiIntegrationTest on PostgreSQL 18 | 28 | 28 | 0 | 0 |

Both final runs reported BUILD SUCCESS. The two H2 skips are PostgreSQL-specific
receipt/sales audit-trigger tests; both passed on PostgreSQL. Flyway applied V1-V9
successfully in a fresh PostgreSQL test database. All production and test Java
sources compiled with release 21.

Logs: `target/sales-h2-full-test.log`, `target/sales-postgres-test.log`.
Surefire XML/text reports: `target/surefire-reports/` (latest PostgreSQL run replaces
reports for the two selected classes; the full H2 log retains its results).

The PostgreSQL run used the isolated cluster in `target/stock-pg-data`, bound to
127.0.0.1:55439, database `sales_test_20261009`. No application database was used. The isolated PostgreSQL server was stopped after verification.

V8/V9 were also applied in one SQL transaction to `sales_upgrade_test_20261009`,
a disposable clone of the existing V7 test database. The before/after comparison
retained 2 receipts, 1 movement, stock sum 100 and the identical movement audit digest.
All eight new permission codes were present after migration. This upgrade check used
psql directly (it does not update the clone's Flyway history). Logs:
`target/sales-upgrade-test.log`, `target/sales-upgrade-before.log`,
`target/sales-upgrade-after.log`.

Commands (PowerShell):
```powershell
$env:JAVA_HOME='D:\project_gen_ai\jdk-21\jdk-21.0.10'
.\mvnw.cmd test
.\mvnw.cmd test '-Dtest=SalesApiIntegrationTest,StockApiIntegrationTest' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55439/sales_test_20261009' '-Dspring.datasource.username=stock_test' '-Dspring.datasource.password=' '-Dspring.datasource.driver-class-name=org.postgresql.Driver'
```
The PostgreSQL database must be a disposable test database: fixture cleanup deletes
its business records and identity/permission fixtures.

## Covered scenarios

1. Inventory 100 -> draft sale 10 leaves 100 -> confirm leaves 90 / one -10 movement -> retry leaves 90 -> cancel with goodsReturned=true leaves 100 / one +10 movement -> retry remains unchanged.
2. Receipt reversal after selling stock is rejected, then succeeds after sale cancellation restores goods.
3. Two sales for 70 against stock 100 run concurrently: one succeeds, one returns 409, stock remains 30.
4. Two confirmations/cancellations of one order apply only once. Confirm racing edit/cancel yields one valid transition and one conflict.
5. A missing stock line rejects the entire order. A deliberately injected constraint failure on the second movement rolls back all balances/movements/status/version/snapshot.
6. Customer CRUD, optional contact fields, phone duplicates, generated identity, length/status validation, literal keyword matching and scoped pagination.
7. Customer from another warehouse rejected; inactive customer/product/warehouse rechecked at create/edit/confirm; final snapshots remain unchanged after customer/catalog edits and deactivation; cancellation still works.
8. Every new endpoint checks permission/scope. Old JWT loses revoked warehouse access immediately. Employee with only SALES_ORDER_CONFIRM can confirm without receipt or sales-view permissions.
9. Stale version, immutable warehouse, final state edits/deletion, cancellation reason and full-goods acknowledgement, invalid/missing entities.
10. Inclusive saleDate filters, pagination/count before slicing, invalid date range/status.
11. Strict quantity/price/discount/version tokens, negative/fractional/string/out-of-Long rejection; 1..1000 line constraint, duplicate products, exact multiplication/subtotal overflow, discount > subtotal, backend-only amounts/actor.
12. Long.MAX_VALUE quantity with zero price can be sold; cancellation balance overflow rolls back and preserves confirmed state.
13. PostgreSQL audit guards reject final line/header mutations, referenced customer deletion, movement mutation and duplicate movement insertion.
14. Existing authentication/user/catalog/warehouse tests pass; all existing stock receipt tests pass on both engines. Normal flows reconcile balances with SUM(quantity_change).

## Design limits

Warehouse row locks serialize receipt and sales inventory mutations within each
warehouse, including absent balance creation. This trades throughput for correctness
across backend instances and reuses the existing mechanism. Future stock writers
must use the same lock order. Product/customer rows are locked when validating and
capturing snapshots. No partial shipment/return, payments, debt, warehouse transfer
or electronic invoice behavior was added.
