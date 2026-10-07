# API quản lý kho và danh mục vật tư

## Chạy và migration

Yêu cầu JDK 21. Maven Wrapper trong project dùng Maven 3.9.9. PostgreSQL theo `compose.yaml` dùng phiên bản 16; cấu hình mặc định dùng database/user `frontend_base`.

```powershell
# Chỉ cần đặt JAVA_HOME nếu Java mặc định chưa phải 21; thay bằng đường dẫn JDK của bạn.
$env:JAVA_HOME = 'C:\Users\dont\.jdk\jdk-21\jdk-21.0.10'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

Nếu đã có PostgreSQL chạy ở port 5432, không chạy thêm container trên cùng port. Cấu hình kết nối bằng `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` qua biến môi trường. Spring không tự đọc `.env.example`.

Flyway tự áp dụng V5 sau V1–V4 khi khởi động. Không chạy lại migration bằng tay, không sửa checksum migration cũ. V5 thêm bảng `warehouses`, `product_groups`, `products`, `user_warehouses`, index/ràng buộc và 11 permission mới; cấp chúng cho role `ADMIN` hiện có. Không tạo dữ liệu kho/sản phẩm mẫu hoặc tồn kho. Sao lưu database trước khi triển khai lên môi trường có dữ liệu; nếu cần quay lại phiên bản ứng dụng cũ, giữ nguyên các bảng mới và lịch sử Flyway.

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
# Chạy riêng kiểm tra tính năng mới
.\mvnw.cmd '-Dtest=InventoryApiIntegrationTest' test
```

Test mặc định dùng H2 trong bộ nhớ. `H2MigrationConfiguration` chỉ dùng trong test, đọc SQL gốc và tách lệnh ALTER TABLE ADD COLUMN nhiều cột để H2 chấp nhận. Production vẫn dùng nguyên SQL/checksum V1–V5; không có bản sao migration để phải đồng bộ.

Base URL: `http://localhost:8080/api`. Swagger: `http://localhost:8080/api/swagger-ui.html`. Mọi API mới cần `Authorization: Bearer <accessToken>`.

## Quyền và phạm vi

Admin được xác định từ user đang hoạt động có role `ADMIN` đang hoạt động trong database; Admin được gọi mọi API mới mà không cần gán kho. User khác cần permission tương ứng trong JWT. Quyền chức năng không mở rộng phạm vi kho.

| Method | URL | Quyền cho người không phải Admin | Thành công |
|---|---|---|---|
| GET | `/api/warehouses` | `WAREHOUSE_VIEW` | 200, trang kho trong phạm vi |
| GET | `/api/warehouses/{id}` | `WAREHOUSE_VIEW` | 200, Warehouse |
| POST | `/api/warehouses` | `WAREHOUSE_CREATE` | 201, Warehouse |
| PUT | `/api/warehouses/{id}` | `WAREHOUSE_UPDATE` | 200, Warehouse |
| GET | `/api/product-groups` | `PRODUCT_GROUP_VIEW` | 200, trang nhóm |
| GET | `/api/product-groups/{id}` | `PRODUCT_GROUP_VIEW` | 200, ProductGroup |
| POST | `/api/product-groups` | `PRODUCT_GROUP_CREATE` | 201, ProductGroup |
| PUT | `/api/product-groups/{id}` | `PRODUCT_GROUP_UPDATE` | 200, ProductGroup |
| GET | `/api/products` | `PRODUCT_VIEW` | 200, trang sản phẩm |
| GET | `/api/products/{id}` | `PRODUCT_VIEW` | 200, Product |
| POST | `/api/products` | `PRODUCT_CREATE` | 201, Product |
| PUT | `/api/products/{id}` | `PRODUCT_UPDATE` | 200, Product |
| GET | `/api/users/me/warehouses` | Chỉ cần đăng nhập | 200, Warehouse[] trong phạm vi bản thân; Admin nhận mọi kho |
| GET | `/api/users/{id}/warehouses` | `USER_WAREHOUSE_VIEW` | 200, Warehouse[] đã gán cho user, giao với phạm vi người gọi |
| PUT | `/api/users/{id}/warehouses/{warehouseId}` | `USER_WAREHOUSE_ASSIGN` | 200, Warehouse; không có request body |
| DELETE | `/api/users/{id}/warehouses/{warehouseId}` | `USER_WAREHOUSE_ASSIGN` | 204, không có body |

Người không phải Admin chỉ xem/sửa/gán/gỡ các kho được giao. Truy vấn lọc phạm vi trước khi phân trang và tính total. Danh mục sản phẩm và nhóm dùng chung toàn hệ thống, không cần gán kho để đọc/sửa khi có quyền tương ứng. GET kho của user khác không trả các kho ngoài phạm vi người gọi. Đây không phải danh sách đầy đủ nếu người gọi không phải Admin.

Người không phải Admin tạo kho được tự gán kho mới trong cùng transaction; không được cấp thêm permission. Không có API thay toàn bộ danh sách kho của user. PUT gán lại liên kết đã tồn tại trả 200, kể cả liên kết cũ đến kho ngừng hoạt động; chỉ việc gán mới kho ngừng hoạt động bị chặn. DELETE gỡ một kho không ảnh hưởng kho khác. Gỡ kho có hiệu lực ở request tiếp theo với JWT cũ.

Role/permission trong JWT giữ cơ chế hiện tại: sau khi thay đổi permission cần đăng nhập lại hoặc refresh để nhận quyền mới. Nhận diện Admin và phạm vi kho đọc database mỗi request. Các endpoint user/role/permission cũ và API đăng nhập không thay đổi.

## Request và response

POST tạo và PUT cập nhật dùng cùng payload. PUT là cập nhật đầy đủ: trường tùy chọn bị bỏ trống được xóa hoặc trở về mặc định 0; không phải PATCH. Không gửi `id`, `createdAt`, `updatedAt` trong payload. Response không bọc trong `{data: ...}`. Trường null bị bỏ khỏi JSON theo cấu hình Jackson hiện có.

Quy ước chung: `code` bắt buộc, tối đa 80 ký tự, cho phép chữ ASCII/số/`_`/`-`/`.`; khoảng trắng đầu/cuối được trim, mã lưu chữ hoa. Mã duy nhất trong từng danh mục, mã sản phẩm duy nhất toàn hệ thống; không tái sử dụng mã khi bản ghi ngừng hoạt động. `name` bắt buộc, tối đa 160 ký tự. `status` bắt buộc, số nguyên 0 (ngừng hoạt động) hoặc 1 (hoạt động).

### Kho

```json
{
  "code": "KHO-HN",
  "name": "Kho Hà Nội",
  "address": "Hà Nội",
  "phone": "0901234567",
  "note": "Kho chính",
  "status": 1
}
```

`address` tối đa 500 ký tự, `phone` 20, `note` 2000; tất cả tùy chọn. Response Warehouse có cùng các trường trên và `id` UUID, `createdAt` ISO-8601 UTC, `updatedAt` nếu đã cập nhật. Không chứa thông tin tồn hoặc danh sách user.

### Nhóm sản phẩm

```json
{
  "code": "PHAO-PU",
  "name": "Phào PU",
  "description": "Phào nội thất chất liệu PU",
  "status": 1
}
```

`description` tùy chọn, tối đa 2000 ký tự. Response ProductGroup có các trường trên và `id`, `createdAt`, `updatedAt` nếu có.

### Sản phẩm

```json
{
  "code": "PU-001",
  "name": "Phào PU trắng 2.4m",
  "groupId": "11111111-1111-1111-1111-111111111111",
  "material": "PU",
  "color": "Trắng",
  "dimensions": "20 × 30 mm",
  "lengthMeters": 2.4,
  "unit": "cay",
  "referencePurchasePrice": 50000,
  "defaultSalePrice": 75000,
  "lowStockThreshold": 10,
  "description": "Bán nguyên cây",
  "status": 1
}
```

- `groupId` bắt buộc, là UUID của nhóm tồn tại. Chọn nhóm đang hoạt động khi tạo/chuyển nhóm; có thể giữ nhóm cũ ngừng hoạt động khi sửa.
- `material`, `color` tối đa 100 ký tự; `dimensions` 255; `description` 2000; đều tùy chọn.
- `lengthMeters` tùy chọn; nếu có phải > 0, tối đa 9 chữ số nguyên và 3 chữ số thập phân, đơn vị mét.
- `unit` tùy chọn, nếu gửi chỉ nhận `cay`; response luôn trả `cay`.
- `referencePurchasePrice`, `defaultSalePrice`: số VND nguyên không âm, tối đa 19 chữ số nguyên; mặc định 0 nếu thiếu/null. Giá lẻ bị từ chối, không làm tròn.
- `lowStockThreshold`: số nguyên không âm, tối đa 2147483647, mặc định 0 nếu thiếu/null; không nhận số lẻ/chuỗi số.
- Response Product có cùng các trường trên và `id`, `createdAt`, `updatedAt` nếu có. Không lưu hay cập nhật số lượng tồn trong API này.

Ví dụ response Product tối thiểu:

```json
{
  "id": "22222222-2222-2222-2222-222222222222",
  "code": "PU-001",
  "name": "Phào PU trắng 2.4m",
  "groupId": "11111111-1111-1111-1111-111111111111",
  "unit": "cay",
  "referencePurchasePrice": 0,
  "defaultSalePrice": 0,
  "lowStockThreshold": 0,
  "status": 1,
  "createdAt": "2026-10-07T09:00:00Z"
}
```

### Tìm kiếm và phân trang

Cả ba API danh sách nhận `page` mặc định 1, `pageSize` mặc định 10 (1–100), `keyword` tùy chọn tìm chứa trong mã/tên không phân biệt hoa/thường, `status` tùy chọn 0/1. Sản phẩm nhận thêm `groupId` UUID. Không gửi status sẽ nhận cả hoạt động và ngừng hoạt động trong phạm vi được phép.

```http
GET /api/products?page=1&pageSize=10&keyword=PU&status=1&groupId=11111111-1111-1111-1111-111111111111
```

```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "pageSize": 10
}
```

`items` chứa Warehouse/ProductGroup/Product tương ứng. Sắp xếp `createdAt` giảm dần, sau đó ID giảm dần. Trang vượt số trang trả items rỗng, giữ total thực tế. `%` và `_` trong keyword được tìm như ký tự thông thường.

### Gán kho

```http
PUT /api/users/33333333-3333-3333-3333-333333333333/warehouses/44444444-4444-4444-4444-444444444444
Authorization: Bearer <accessToken>
```

Không có body; trả Warehouse vừa gán. GET danh sách gán trả array Warehouse trực tiếp, không phân trang. Tài khoản chung dùng user thường, được gán kho và role qua API hiện có; không có loại tài khoản mới.

## Lỗi

| HTTP | Code | Trường hợp |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu trường bắt buộc, sai giới hạn, giá âm/lẻ, status sai, mã sai |
| 400 | `INVALID_REQUEST` | JSON/UUID sai hoặc số lẻ được gửi vào status/ngưỡng nguyên |
| 400 | `INVALID_PAGINATION` | Phân trang không hợp lệ tại service |
| 401 | `UNAUTHORIZED` | Thiếu/invalid/expired token; user không hoạt động ở API mới |
| 403 | `FORBIDDEN` | Thiếu permission và không phải Admin |
| 403 | `WAREHOUSE_ACCESS_DENIED` | Kho ngoài phạm vi người gọi; kiểm tra trước tra cứu kho |
| 404 | `WAREHOUSE_NOT_FOUND` / `PRODUCT_GROUP_NOT_FOUND` / `PRODUCT_NOT_FOUND` | Không tìm thấy đối tượng |
| 404 | `USER_NOT_FOUND` | Không tìm thấy user cần gán/xem |
| 404 | `USER_WAREHOUSE_NOT_FOUND` | Gỡ liên kết không tồn tại |
| 409 | `WAREHOUSE_CODE_EXISTS` / `PRODUCT_GROUP_CODE_EXISTS` / `PRODUCT_CODE_EXISTS` | Trùng mã được phát hiện ở service |
| 409 | `WAREHOUSE_INACTIVE` | Gán mới kho ngừng hoạt động |
| 409 | `PRODUCT_GROUP_INACTIVE` | Tạo/chuyển sản phẩm sang nhóm ngừng hoạt động |
| 409 | `RESOURCE_CONFLICT` | Ràng buộc database, bao gồm trùng mã từ request đồng thời |
| 405 | `METHOD_NOT_ALLOWED` | Gọi DELETE danh mục hoặc method không hỗ trợ |

```json
{
  "timestamp": "2026-10-07T09:00:00Z",
  "status": 403,
  "code": "WAREHOUSE_ACCESS_DENIED",
  "message": "Kho không thuộc phạm vi được giao",
  "details": {}
}
```

```json
{
  "timestamp": "2026-10-07T09:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Yêu cầu không hợp lệ",
  "details": {"defaultSalePrice": "must be greater than or equal to 0"}
}
```

Nội dung field message của Bean Validation phụ thuộc locale; client nên dựa vào HTTP status, code và tên field trong details.

## Kiểm tra truy cập kho bằng API thật

1. Admin đăng nhập qua `POST /api/auth/login`, tạo kho A/B, tạo nhân viên qua API user hiện có và gán role có `WAREHOUSE_VIEW`, `WAREHOUSE_UPDATE`, `USER_WAREHOUSE_VIEW`, `USER_WAREHOUSE_ASSIGN`. Lấy permission ID từ API permission hiện có để gán role.
2. Chỉ gán A cho nhân viên. Nhân viên đăng nhập để JWT chứa các quyền mới. `GET /api/warehouses` chỉ có A và total=1.
3. Dùng JWT nhân viên gọi GET/PUT kho B hoặc PUT/DELETE gán B: phải trả 403; danh sách gán của user khác không được lộ B.
4. Admin gỡ A khỏi nhân viên. Dùng lại chính JWT cũ: GET A phải trả 403 và danh sách kho rỗng.
5. Ngừng hoạt động A: liên kết cũ vẫn xem được; gán mới cho user khác trả 409. Không có API xóa danh mục.

## Giả định và phần chưa triển khai

Giá VND nguyên, chiều dài mét, kích thước dạng chữ; sản phẩm bắt buộc có nhóm. Tài khoản chung là user hiện có; giữ nguyên cơ chế mật khẩu mặc định `Abc@12345` trong UserService và không sửa API đăng nhập. Các liên kết cũ đến kho/nhóm ngừng hoạt động được giữ. Chưa có tồn kho, nhập/xuất, bán hàng, khách nợ, hóa đơn thường/điện tử, báo cáo hoặc frontend.

## Kết quả kiểm tra triển khai

- JDK 21.0.10, Maven Wrapper 3.9.9; chạy offline bằng dependencies đã có, không cài thêm thư viện.
- `./mvnw.cmd -o test`: 14 test pass (6 hồi quy auth/user/role/permission, 6 kịch bản inventory, 2 JWT).
- Chạy cùng 14 test trên PostgreSQL 18.6: tất cả pass. Flyway áp dụng nguyên SQL V1–V5 thành công trong schema tạm riêng; schema tạm đã được xóa. Không ghi dữ liệu vào schema ứng dụng hiện tại. Compose cấu hình PostgreSQL 16; chưa chạy test riêng trên phiên bản 16.
- `./mvnw.cmd -o -DskipTests package`: BUILD SUCCESS, tạo `target/frontend-base-api-1.0.0.jar`. Bỏ chạy lại test ở bước đóng gói vì đã pass trên hai database.
- Các kịch bản inventory kiểm tra quyền thiếu/token thiếu, phạm vi kho ở list/detail/update/assign/remove, total không rò kho, thu hồi với JWT cũ, Admin theo role đang hoạt động, user ngừng hoạt động, tạo kho không tăng permission, trạng thái và liên kết cũ, CRUD/lọc/phân trang, mã trùng, giá âm/lẻ, ngưỡng lẻ và response timestamp.
- Các fixture test cũ được điều chỉnh để khớp API hiện tại: mã role/permission hợp lệ trước Bean Validation, tải collection role trước khi sửa, kiểm tra createdAt sau commit bằng GET, và dùng payload hợp lệ trong test thiếu quyền. Không thay đổi code nghiệp vụ hoặc hợp đồng API auth/user/role/permission.

Để chạy test trên PostgreSQL, **luôn dùng schema/database test riêng** vì fixture sẽ xóa dữ liệu user/role/permission trong schema đang dùng. Chuẩn bị schema test và cung cấp cấu hình qua các property sau (thay giá trị tương ứng):

```powershell
.\mvnw.cmd test `
  '-Dspring.datasource.url=jdbc:postgresql://localhost:5432/frontend_base?currentSchema=inventory_test' `
  '-Dspring.datasource.username=frontend_base' `
  "-Dspring.datasource.password=$env:DATABASE_PASSWORD" `
  '-Dspring.datasource.driver-class-name=org.postgresql.Driver' `
  '-Dspring.flyway.default-schema=inventory_test'
```

Lớp tương thích H2 tự bỏ qua trên PostgreSQL. Với schema test mới, Flyway tạo schema và chạy V1–V5. Chỉ dọn schema test do bạn tạo sau khi kiểm tra. Danh sách file bổ sung/sửa tại [inventory-changes.md](inventory-changes.md).
