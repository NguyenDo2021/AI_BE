# Xuất Excel báo cáo

## API contract

Base URL `/api`, xác thực Bearer JWT như API báo cáo hiện có.

| GET endpoint | Query | Permission |
| --- | --- | --- |
| `/api/reports/inventory/export` | `warehouseId?`, `productId?`, `from`, `to` | `REPORT_INVENTORY_VIEW` |
| `/api/reports/sales/export` | `warehouseId?`, `from`, `to` | `REPORT_SALES_VIEW` |
| `/api/reports/payments/export` | `warehouseId?`, `from`, `to` | `REPORT_PAYMENT_VIEW` |
| `/api/reports/low-stock/export` | `warehouseId?`, `productId?` | `REPORT_LOW_STOCK_VIEW` |

UUID và ngày kiểm tra theo API báo cáo. `from/to` là `YYYY-MM-DD`, từ đầu ngày đến trước đầu ngày sau `to`, múi giờ `Asia/Ho_Chi_Minh`. Tồn thấp là hiện tại và từ chối `from/to`. Cả bốn endpoint từ chối `page/pageSize` với 400 `INVALID_REQUEST`.

Admin giữ ngoại lệ hiện có (kiểm tra role sống trong DB). Nhân viên cần permission tương ứng; tất cả truy vấn, count, summary và metadata kho lọc theo gán kho trong DB, cùng điều kiện `ReportAccess.Scope.where`. Yêu cầu kho cụ thể vẫn dùng kiểm tra kho hiện có. Không có export dashboard/công nợ.

### Thành công

HTTP 200, body XLSX nhị phân:

```http
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="ban-hang_2026-10-11_2026-10-11.xlsx"
Cache-Control: no-store
Content-Length: <kích thước file>
Access-Control-Expose-Headers: Location, Content-Disposition
```

Header CORS có trên request từ origin được cấu hình. Tên file chỉ gồm prefix cố định, ngày hợp lệ và timestamp định dạng an toàn, nên dùng filename ASCII; không cần `filename*`. Nếu sau này cho phép tên Unicode, dùng `ContentDisposition` với UTF-8.

Tên file: `nhap-xuat-ton_<from>_<to>.xlsx`, `ban-hang_<from>_<to>.xlsx`, `thu-tien_<from>_<to>.xlsx`, `ton-thap_<yyyy-MM-dd_HH-mm-ss>.xlsx`.

### Lỗi

Body JSON theo `ApiErrorResponse` hiện có, `Content-Type: application/json`, không có header attachment. Các lỗi nghiệp vụ giữ nguyên status/code/details: xác thực, quyền, phạm vi kho, UUID, `INVALID_DATE_RANGE`, `NUMERIC_OVERFLOW`, `INVENTORY_HISTORY_INCONSISTENT`.

| Mã bổ sung | HTTP | Ý nghĩa |
| --- | --- | --- |
| `EXPORT_TOO_LARGE` | 409 | Hơn 100.000 dòng chi tiết sau lọc/phạm vi; thu hẹp bộ lọc. Không cắt dữ liệu. |
| `REPORT_EXPORT_FAILED` | 500 | Lỗi dựng workbook/file không dự kiến. Log chi tiết ở backend; không đổi lỗi nghiệp vụ thành mã này. |

File được dựng xong và đóng workbook trước khi đặt header 200. Lỗi nghiệp vụ/dựng workbook xảy ra trước response. Lỗi mạng sau khi bắt đầu gửi không thể chuyển response thành JSON; file tạm vẫn được dọn khi client ngắt kết nối.

## Workbook và số liệu

- `Thông tin`: tên, bộ lọc, từng kho trong phạm vi, thời điểm tạo/múi giờ, VND/cây, cơ sở thời gian và quy tắc độ chính xác.
- `Chi tiết`: STT liên tục, tất cả field của DTO API theo thứ tự record, nhãn tiếng Việt; sales/payment trải phẳng metrics. UUID, mã, tên và đơn vị là STRING. Trạng thái sản phẩm giữ mã API. Header đậm, freeze hàng đầu, autofilter, chiều rộng UUID/tên lớn hơn, wrap. Rỗng vẫn có header và thông báo.
- `Tổng hợp` chỉ cho sales/payments: cả 9 field summary toàn bộ bộ lọc lấy từ repository hiện có; không cộng từng batch và không dùng công thức SUM Excel. Inventory không thêm tổng.

Inventory giữ `performedAt`, ledger lịch sử và đối soát balance; sản phẩm/kho ngừng hoạt động vẫn giữ lịch sử như API. Bán hàng theo `confirmedAt/cancelledAt`, chưa phải lợi nhuận. Thu tiền theo `createdAt/cancelledAt`, không theo `paymentDate`; hủy phiếu thu là đảo ghi nhận sai, không phải hoàn tiền. Tồn thấp theo balance hiện tại, chỉ kho/sản phẩm hoạt động có ngưỡng > 0; thiếu balance là 0.

Số nguyên trong `[-999999999999999, 999999999999999]` ghi numeric với phân cách hàng nghìn; số ngoài giới hạn ghi STRING chính xác, kể cả `Long.MIN_VALUE/MAX_VALUE`. Không dùng abs gây tràn, không chuyển số lớn qua double. Tất cả text ghi `setCellValue(String)`, không gọi `setCellFormula`; `=`, `+`, `-`, `@` không thành công thức.

## Triển khai và tài nguyên

Thư viện mới `org.apache.poi:poi-ooxml:5.4.1`. Dùng [POI SXSSF](https://poi.apache.org/components/spreadsheet/how-to.html#sxssf) vì repo chưa có thư viện Excel: cửa sổ 100 dòng, inline strings, XML tạm nén, style dùng chung. Chi tiết không giữ List toàn bộ trong RAM.

`ReportRepository` thêm overload nhận Consumer, dùng nguyên SQL/RowMapper/count/thứ tự của API hiện có. JDBC forward-only cursor có fetchSize 500; PostgreSQL fetch theo batch trong transaction, không gọi HTTP nội bộ và không phân trang OFFSET cho export. Driver H2 dùng để kiểm tra chức năng, không đại diện đặc tính bộ nhớ PostgreSQL.

`ReportExportService` readOnly `REPEATABLE_READ`: kiểm tra quyền/phạm vi và đối soát trước dựng workbook; summary, metadata, count, cursor cùng snapshot. Giới hạn áp dụng theo số dòng báo cáo đã tổng hợp (cặp kho/sản phẩm hoặc kho), không theo số event nguồn. File hoàn chỉnh nằm trong OS temp ngoài vùng public, workbook.close dọn SXSSF temp. Controller gửi đồng bộ và xóa staged XLSX trong try-with-resources; rollback transaction cũng xóa staged file. Export không ghi dữ liệu nghiệp vụ.

## Frontend Axios

```javascript
async function downloadReport(api, kind, params) {
  try {
    const response = await api.get(`/reports/${kind}/export`, {
      params, responseType: 'blob'
    });
    const type = response.headers['content-type'] || '';
    if (!type.includes('spreadsheetml.sheet')) {
      throw new Error('Response không phải file Excel');
    }
    const disposition = response.headers['content-disposition'] || '';
    const extended = disposition.match(/filename\*=UTF-8''([^;]+)/i);
    const ascii = disposition.match(/filename="([^"]+)"/i);
    const filename = extended ? decodeURIComponent(extended[1])
      : ascii ? ascii[1] : 'bao-cao.xlsx';
    const url = URL.createObjectURL(response.data);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename.replace(/[\\/]/g, '_');
    document.body.appendChild(link);
    try { link.click(); }
    finally {
      link.remove();
      // Cho trình duyệt bắt đầu download trước khi thu hồi URL.
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
  } catch (error) {
    const response = error.response;
    if (response?.data instanceof Blob &&
        (response.headers['content-type'] || '').includes('application/json')) {
      const apiError = JSON.parse(await response.data.text());
      // UI hiển thị message; EXPORT_TOO_LARGE hướng dẫn thu hẹp bộ lọc.
      throw Object.assign(new Error(apiError.message), {
        code: apiError.code, status: response.status, details: apiError.details
      });
    }
    throw error;
  }
}
// api đã cấu hình baseURL '/api' và Authorization.
// Không truyền page/pageSize; low-stock không truyền from/to.
```

## File thay đổi

- `pom.xml`: dependency POI.
- `ReportRepository.java`: tái dùng query/mapper cho cursor và metadata phạm vi.
- `ReportExportService.java`: snapshot, quyền, summary, staged file và cleanup rollback.
- `ReportWorkbook.java`: style, sheet, STRING/numeric chính xác và SXSSF lifecycle.
- `ReportExportController.java`: bốn GET endpoint, validation và binary response.
- `ReportApiIntegrationTest.java`: export đọc lại XLSX, filter/scope, summary, cursor, limit, không thay đổi nghiệp vụ và snapshot PostgreSQL.
- `ReportWorkbookTest.java`, `ReportExportLimitTest.java`, `ReportExportFailureTest.java`: biên số nguyên, giới hạn trước cursor, lỗi dựng file và client disconnect.
- Tài liệu này và `report-excel-verification.md`; mẫu tại `samples/report-exports/`.

Không cần migration. CORS và JSON exception handler hiện có đã đáp ứng headers yêu cầu.
