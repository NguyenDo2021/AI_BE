# API thông tin tài khoản và trạng thái Admin

## Nguyên nhân và luồng xử lý

Response thông tin tài khoản trước đây thiếu trạng thái Admin. Frontend phải đọc thêm `/api/users/{id}` và `/api/users/{id}/roles`; cả hai được bảo vệ bởi `USER_VIEW`, nên nhân viên thiếu quyền này nhận 403.

URL chính xác: **`GET /api/auth/me`**, gồm servlet context `/api` trong `application.yml` và mapping `/auth/me` của `AuthController`.

Luồng: `JwtAuthenticationFilter` xác thực chữ ký/hạn JWT và tạo principal từ subject → `AuthController.currentUser` → `AuthService.getCurrentUser` → `AuthUserResponse`.

`AuthService` đọc tài khoản từ DB và yêu cầu `users.status = 1`. Field mới gọi trực tiếp `WarehouseAccess.isAdmin()` đang được API nghiệp vụ sử dụng. Hàm dùng principal hiện tại, kiểm tra lại user tồn tại/hoạt động và quan hệ `user_roles` đến role có code `ADMIN` (không phân biệt hoa thường), `roles.status = 1`. Model/bảng `user_roles` chỉ có `user_id`, `role_id`, không có status hoặc khoảng thời gian hiệu lực bổ sung. Không kiểm tra kho, username/email, tập permission, dữ liệu client hay role claim JWT để xác định Admin. Không lưu cache kết quả giữa request.

## File thay đổi

| File | Thay đổi |
|---|---|
| `src/main/java/com/frontendbase/api/auth/controller/AuthController.java` | Đặt `Cache-Control: no-store` cho response me, giữ JSON trực tiếp |
| `src/main/java/com/frontendbase/api/auth/service/AuthService.java` | Gọi `WarehouseAccess.isAdmin()` dùng chung; tách phép lấy permission hiện có để login/refresh không cần tính Admin |
| `src/main/java/com/frontendbase/api/auth/dto/AuthUserResponse.java` | Thêm primitive boolean `isAdmin`, cố định tên JSON và luôn serialize |
| `src/test/java/com/frontendbase/api/CurrentUserApiIntegrationTest.java` | Thêm kiểm thử tích hợp qua JWT và kiểm tra serialization |
| `README.md` | Cập nhật API matrix và mô tả field mới |
| `docs/auth-me-admin.md` | Contract, response đã kiểm tra và báo cáo bàn giao |

## Contract cho frontend

```ts
type CurrentUser = {
  id: string;
  username: string;
  fullName: string;
  email: string;
  permissions: string[];
  isAdmin: boolean;
};
```

Gửi `Authorization: Bearer <accessToken>`. API chỉ yêu cầu đăng nhập hợp lệ, không yêu cầu `USER_VIEW`, `ROLE_VIEW` hay gán kho. Body thành công là object trực tiếp, không có envelope `data`. `isAdmin` luôn có, kể cả `false`, không null và không phải chuỗi. DTO dùng primitive `boolean`, `@JsonProperty("isAdmin")` và `@JsonInclude(ALWAYS)`; cấu hình Jackson chung hiện là `non_null`.

Frontend đọc trực tiếp `response.isAdmin`, bỏ hai request quản trị dùng riêng để dò Admin. Những chỗ thực sự quản trị người dùng tiếp tục tuân theo quyền hiện có. Nếu nhận 401, xử lý phiên đăng nhập theo cơ chế hiện tại. `isAdmin` chỉ hỗ trợ giao diện; backend vẫn kiểm tra quyền và phạm vi kho cho từng API nghiệp vụ.

Response thành công đặt rõ `Cache-Control: no-store`. Response lỗi vẫn có cache header mặc định của Spring Security chứa `no-store`.

## Response được xác minh trong integration test

Đây là dữ liệu fixture của `CurrentUserApiIntegrationTest`, không phải dữ liệu tài khoản production. Cả hai tài khoản đều đăng nhập thật qua `/auth/login` và không được gán kho. Kiểm thử so sánh toàn bộ JSON để bảo đảm không lộ thêm trường nội bộ.

Nhân viên không có `USER_VIEW` — HTTP 200, `Cache-Control: no-store`:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "username": "test_nv_1101",
  "fullName": "test_nv_1101",
  "email": "test_nv_1101@example.test",
  "permissions": ["INVENTORY_VIEW", "PRODUCT_VIEW", "SALES_ORDER_VIEW", "STOCK_RECEIPT_VIEW", "WAREHOUSE_VIEW"],
  "isAdmin": false
}
```

Admin có role `ADMIN` hoạt động, không có permission — HTTP 200, `Cache-Control: no-store`:

```json
{
  "id": "22222222-2222-2222-2222-222222222222",
  "username": "test_admin",
  "fullName": "test_admin",
  "email": "test_admin@example.test",
  "permissions": [],
  "isAdmin": true
}
```

## Nguồn dữ liệu và tương thích

| Dữ liệu | Nguồn và ý nghĩa |
|---|---|
| Principal | Subject của access JWT đã xác thực |
| `id`, `username`, `fullName`, `email` | DB của chính principal |
| `permissions` trong response me | Giữ nguyên phép hợp permission từ các role trong DB, loại trùng và sắp xếp; giữ nguyên cả cách xử lý status role/permission hiện có |
| Quyền dùng để authorize request | Giữ nguyên authorities lấy từ claim `permissions` trong JWT; không chuyển cơ chế kiểm tra quyền hiện có sang DB |
| `isAdmin` | DB tại request hiện tại qua `WarehouseAccess.isAdmin()` dùng chung, độc lập với permission/role claim JWT |

Login/refresh giữ nguyên payload token và cách sinh JWT/permission. Access JWT hiện chỉ mang subject, username, permissions và các mốc thời gian, không phát hành role claim. Kiểm thử bổ sung JWT có claim `roles: ["ADMIN"]` để xác nhận claim cũ/khác nguồn không thể ghi đè trạng thái DB. Không migration, không cấp thêm permission, không thay đổi các API quản trị. Client cũ tiếp tục dùng các field cũ như trước.

## HTTP status và lỗi

| Tình huống | HTTP | `code` | `message` |
|---|---|---|---|
| Tài khoản hoạt động, token hợp lệ | 200 | Không có error envelope | Object `CurrentUser` |
| User bị xóa hoặc `status != 1` | 401 | `UNAUTHORIZED` | `Tài khoản không hoạt động` |
| Thiếu/sai chữ ký/sai định dạng/hết hạn token | 401 | `UNAUTHORIZED` | `Phiên đăng nhập hết hạn` |
| Nhân viên gọi `/api/users/{id}` hoặc `/api/users/{id}/roles` thiếu `USER_VIEW` | 403 | `FORBIDDEN` | `Bạn không có quyền truy cập` |

Lỗi giữ nguyên envelope `{ timestamp, status, code, message, details }`. `timestamp` là thời điểm lỗi dạng ISO-8601; `details` là object, thường `{}` trong các trường hợp trên. Không trả object tài khoản hoặc `isAdmin: false` thay cho lỗi 401.

## Kiểm thử và build

`CurrentUserApiIntegrationTest` kiểm tra nhân viên thiếu quyền/không gán kho, Admin không permission/không gán kho, đủ mọi permission nhưng không role ADMIN, role ngừng hoạt động, gỡ assignment với token cũ có claim ADMIN, dữ liệu client, quy ước code không phân biệt hoa thường, user ngừng hoạt động/bị xóa, token thiếu/sai/hết hạn, chặn API quản trị, header no-store, JSON boolean false, các field cũ và nguồn permission DB/JWT giữ nguyên. Thay đổi role/assignment được commit qua JDBC giữa các request để không bị che bởi transaction test.

Lệnh kiểm thử liên quan:

```powershell
mvn -o '-Dtest=CurrentUserApiIntegrationTest,AuthAndUserApiIntegrationTest,JwtServiceTest,InventoryApiIntegrationTest' test
```

Lượt chạy nhóm liên quan đầu tiên có 1 assertion sai cách so sánh danh sách Java immutable với JSON array; body API thực tế đã đúng. Assertion được sửa sang matcher so sánh các phần tử; lượt build toàn bộ sau đó chạy lại nhóm này thành công.

Kết quả thực tế ngày 05/11/2026:

- `mvn -o verify`: **BUILD SUCCESS**, thời gian **03:34 phút**.
- **154 test**, **0 failure**, **0 error**, **6 skipped**; 148 test pass.
- `CurrentUserApiIntegrationTest`: **17/17 pass**, không bỏ qua tình huống nào.
- Nhóm liên quan trong lượt verify: auth/user 6, current-user 17, inventory 6, JWT 2 — **31/31 pass**.
- 6 test cũ bị bỏ qua do yêu cầu PostgreSQL: report 1, returns 1, sales 2, stock 1, stocktake 1. Không chạy kiểm thử trigger/SQL riêng PostgreSQL trên DB thật trong lần bàn giao này.
- Môi trường: Maven **3.9.9**, JDK **25.0.2**, compiler target **Java 21** theo `pom.xml`, Spring profile **test**, database **H2** với bộ chuyển migration dành cho test hiện có.
- Artifact: `target/frontend-base-api-1.0.0.jar` (Spring Boot executable JAR).
- Log build: `target/current-user-build.log`; báo cáo test: `target/surefire-reports/`.
- `git diff --check`: không lỗi whitespace.

Không deploy.
