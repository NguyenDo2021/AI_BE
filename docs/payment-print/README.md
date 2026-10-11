# API in biên lai thu tiền

GET /api/payments/{id}/print, xác thực Bearer token.

Yêu cầu PAYMENT_VIEW qua PaymentAccess (RBAC hiện tại, ADMIN được ngoại lệ).
Nhân viên phải được gán kho phiếu thu. Không cần SALES_ORDER_VIEW.
Quyền được kiểm tra trước khi đọc dữ liệu đơn/khách/kho/người tạo và trước khi render PDF.

## Phản hồi thành công

HTTP 200, body PDF nhị phân trực tiếp.

```http
Content-Type: application/pdf
Content-Disposition: inline; filename="bien-lai-<payment.code>.pdf"
Cache-Control: no-store
Access-Control-Expose-Headers: Location, Content-Disposition
```

Header CORS xuất hiện với Origin hợp lệ trong cấu hình hiện có.
Mã phiếu do hệ thống tạo (PT- + UUID không dấu gạch) được giữ nguyên.
Ký tự không an toàn trong mã dữ liệu cũ được thay bằng dấu gạch dưới trong filename.

## Lỗi JSON

Giữ format hiện có: timestamp, status, code, message, details.
Content-Type được đặt rõ application/json, kể cả client gửi Accept: application/pdf.

| HTTP | code | Trường hợp |
| --- | --- | --- |
| 400 | INVALID_REQUEST | id không phải UUID |
| 401 | UNAUTHORIZED | Thiếu/hết hạn token hoặc tài khoản không hoạt động |
| 403 | FORBIDDEN | Không có PAYMENT_VIEW |
| 403 | WAREHOUSE_ACCESS_DENIED | Không được gán kho phiếu |
| 404 | PAYMENT_NOT_FOUND | Không tìm thấy phiếu sau khi kiểm tra quyền |
| 500 | PAYMENT_PDF_FAILED | Font hoặc quá trình dựng PDF thất bại |
| 500 | INTERNAL_ERROR | Lỗi hệ thống khác theo handler hiện có |

## Dữ liệu và bố cục

- A5 ngang (210 × 148 mm), trắng/đen, Noto Sans Regular/Bold được nhúng.
- Đơn vị và địa chỉ kho lấy hiện tại.
- Người nộp/địa chỉ lấy sales_orders.customer_name/customer_address, là snapshot đã xác nhận; không đọc customer hiện tại.
- Khách lẻ dùng tên Khách lẻ, địa chỉ để trống khi thiếu.
- Ngày dùng payment.paymentDate; số tiền dùng payment.amount của lần thu.
- Nội dung: Thu tiền đơn bán [code], thêm note nếu có.
- Người thu dùng users.full_name của payment.createdBy; thiếu tên thì phần ký trống.
- Phiếu CANCELLED vẫn in bản lưu với ĐÃ HỦY — BẢN LƯU và lý do hủy.
- Trang tiếp theo giữ mã phiếu và nhãn hủy; chữ ký nằm sau toàn bộ nội dung.
- Dòng chấm, xuống dòng theo độ rộng font, chia cả chuỗi không có dấu cách.
- Dựng PDF trực tiếp, không dùng HTML nên dữ liệu không được thực thi/diễn giải thành markup.
- Chuẩn hóa Unicode NFC; ký tự ngoài bộ glyph của font thay bằng ? để vẫn in được tiếng Việt và toàn bộ phần chữ còn lại.
- Transaction chỉ đọc, REPEATABLE_READ, không khóa FOR UPDATE, không ghi DB.
- Không thêm field vào PaymentDtos, không thay migration/schema.

## File thay đổi

- pom.xml: thêm Apache PDFBox 3.0.6.
- src/main/java/com/frontendbase/api/payment/controller/PaymentController.java: endpoint/headers.
- src/main/java/com/frontendbase/api/payment/print/PaymentPrintService.java: kiểm tra quyền/phạm vi và đọc dữ liệu nội bộ.
- src/main/java/com/frontendbase/api/payment/print/ReceiptData.java: model nội bộ bản in.
- src/main/java/com/frontendbase/api/payment/print/ReceiptPdfService.java: nhúng font và tạo PDF.
- src/main/java/com/frontendbase/api/payment/print/ReceiptPdfTemplate.java: bố cục/phân trang.
- src/main/java/com/frontendbase/api/payment/print/VietnameseMoneyWords.java: chuyển số nguyên long bằng phép chia nguyên.
- src/main/java/com/frontendbase/api/security/SecurityConfig.java: expose Content-Disposition.
- src/main/java/com/frontendbase/api/common/exception/GlobalExceptionHandler.java: đặt rõ Content-Type JSON cho lỗi.
- src/main/resources/fonts/NotoSans-Regular.ttf, NotoSans-Bold.ttf, OFL.txt.
- src/test/java/com/frontendbase/api/PaymentPrintApiIntegrationTest.java.
- src/test/java/com/frontendbase/api/payment/print/ReceiptPdfTest.java.
- docs/payment-print/README.md và samples/*.pdf, samples/*.png.

## Kiểm tra và mẫu

Chạy kiểm tra tập trung:

```powershell
.\mvnw.cmd "-Dtest=ReceiptPdfTest,PaymentPrintApiIntegrationTest" test
```

5 tests đạt. Kiểm tra số 0, lăm/mốt, nghìn, triệu, tỷ, số 0 xen giữa, tỷ tỷ và Long.MAX_VALUE.
Kiểm tra PDF thực bằng đọc lại text, font nhúng, kích thước trang, tọa độ chữ trong lề và render PNG.
Kiểm tra API: 200 và headers/CORS, 400/401/403/404, ADMIN không cần permission/kho,
PAYMENT_VIEW không cần SALES_ORDER_VIEW, bản lưu phiếu hủy, khách lẻ, snapshot,
ngày thanh toán, số tiền lần thu và không thay dữ liệu khi in hai lần.
Kiểm tra tích hợp dùng H2 và adapter migration sẵn có; không kiểm thử trực tiếp PostgreSQL.

- [PDF mẫu một trang](samples/bien-lai-mau.pdf): 1.250.000 đồng, ngày 09/10/2026, dữ liệu thử.
- [PDF mẫu hủy nội dung dài](samples/bien-lai-mau-da-huy-noi-dung-dai.pdf): 4 trang, Long.MAX_VALUE, chuỗi không dấu cách và literal HTML.
- Các PNG cùng thư mục là bản render để kiểm tra trực quan.

Font Noto Sans từ [Noto Fonts](https://github.com/notofonts/noto-fonts), theo
[SIL Open Font License 1.1](https://raw.githubusercontent.com/notofonts/noto-fonts/main/LICENSE).
Giấy phép được lưu kèm font. Cách đọc/render dùng API trong
[tài liệu Apache PDFBox 3](https://pdfbox.apache.org/3.0/migration.html).
