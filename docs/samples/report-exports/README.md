# Workbook mẫu từ fixture kiểm thử

Các file được xuất qua MockMvc bằng API thật, trên PostgreSQL test riêng; đây là dữ liệu fixture A/P, không phải dữ liệu cửa hàng. Tên `test-fixture` giúp phân biệt mẫu kiểm thử với file nghiệp vụ.

- `inventory_test-fixture.xlsx`: nhập 3 cây, bán 1, tồn cuối kỳ 2.
- `sales_test-fixture.xlsx`: 1 đơn xác nhận, 1 cây, giá trị 100 VND.
- `payments_test-fixture.xlsx`: 1 phiếu thu CASH 50 VND.
- `ton-thap_test-fixture.xlsx`: tồn hiện tại 2, ngưỡng 5, thiếu 3.

Kỳ mẫu 2026-10-11, múi giờ Asia/Ho_Chi_Minh. Metadata thời điểm tạo/UUID là giá trị thực của lần chạy test. Các kiểm thử khác đọc lại workbook với 521 dòng, số âm, Long.MAX_VALUE, chuỗi tiếng Việt và ký tự =; mẫu bàn giao giữ dữ liệu nhỏ để xem nhanh.
