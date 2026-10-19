# Hướng dẫn sử dụng Hệ thống bán hàng và quản lý kho

**Dành cho:** người mới sử dụng app, nhân viên kho, bán hàng, thu tiền và quản trị viên.  
**Ngày đối chiếu mã nguồn:** 19/10/2026.  
**Ngôn ngữ giao diện trong tài liệu:** Tiếng Việt.

Tài liệu hướng dẫn thao tác trên app đã được triển khai. Tên menu và nút bấm được đối chiếu với mã giao diện AI_FE; điều kiện xử lý, tồn kho và công nợ được đối chiếu với backend AI_BE hiện tại. Tài khoản của bạn có thể chỉ nhìn thấy một phần chức năng do được phân quyền khác nhau.

## Mục lục

1. [Bắt đầu nhanh và luồng làm việc](#bat-dau)
2. [Đăng nhập, chọn kho và làm quen giao diện](#dang-nhap)
3. [Hiểu trạng thái và quy tắc nhập dữ liệu](#quy-tac)
4. [Chuẩn bị kho, nhóm và sản phẩm](#danh-muc)
5. [Quản lý khách hàng](#khach-hang)
6. [Nhập hàng vào kho](#nhap-kho)
7. [Xem tồn kho và lịch sử biến động](#ton-kho)
8. [Tạo và xác nhận đơn bán hàng](#ban-hang)
9. [Thu tiền và in biên lai](#thu-tien)
10. [Theo dõi công nợ](#cong-no)
11. [Nhận hàng khách trả](#tra-hang)
12. [Hoàn tiền cho khách](#hoan-tien)
13. [Kiểm kê kho](#kiem-ke)
14. [Xem báo cáo và xuất Excel](#bao-cao)
15. [Thiết lập người dùng và phân quyền](#quan-tri)
16. [Bài thực hành xuyên suốt](#thuc-hanh)
17. [Xử lý lỗi và câu hỏi thường gặp](#xu-ly-loi)
18. [Công việc đầu ca, cuối ca và phạm vi hiện có](#van-hanh)
19. [Nguồn đối chiếu tài liệu](#nguon)

<a id="bat-dau"></a>

## 1. Bắt đầu nhanh và luồng làm việc

App dùng để quản lý danh mục, nhập kho, bán hàng, ghi nhận thu tiền, theo dõi công nợ, nhận trả hàng, ghi nhận hoàn tiền, kiểm kê và xem báo cáo.

### 1.1. Trước khi sử dụng lần đầu

Nhờ quản trị viên cung cấp:

- Địa chỉ truy cập app.
- Tên đăng nhập và mật khẩu.
- Kho bạn được làm việc.
- Các thao tác được phép: xem, tạo, sửa, xác nhận hoặc hủy.
- Người liên hệ khi có lỗi hoặc số liệu cần đối chiếu.

Người dùng thông thường không cần cài Java, Maven, Docker hoặc phần mềm cơ sở dữ liệu để sử dụng app qua trình duyệt.

**Nếu chưa thấy kho hoặc menu cần dùng, hãy kiểm tra quyền và kho được giao với quản trị viên trước khi nhập dữ liệu.**

### 1.2. Luồng nghiệp vụ tổng quát

~~~mermaid
flowchart TD
    A["Chuẩn bị kho, nhóm sản phẩm, sản phẩm và người dùng"] --> B["Tạo phiếu nhập nháp"]
    B --> C["Xác nhận nhập: tăng tồn kho"]
    C --> D["Tạo đơn bán nháp"]
    D --> E["Xác nhận bán: giảm tồn kho"]
    E --> F["Ghi nhận thu tiền: giảm công nợ"]
    E --> G["Khách trả hàng còn bán được"]
    G --> H["Xác nhận phiếu trả: tăng tồn, giảm giá trị phải thanh toán"]
    H --> I{"Đã thu nhiều hơn tổng sau trả?"}
    I -->|Có| J["Chi tiền cho khách và ghi nhận hoàn tiền"]
    I -->|Không| K["Theo dõi hoặc thu phần công nợ còn lại"]
~~~

Kiểm kê là luồng đối chiếu riêng: đếm thực tế, lưu số đếm, rồi xác nhận để điều chỉnh tồn về số thực tế.

### 1.3. Người mới nên đọc phần nào?

| Công việc | Phần cần đọc trước |
| --- | --- |
| Nhân viên kho | Đăng nhập → Danh mục → Nhập kho → Tồn kho → Kiểm kê |
| Nhân viên bán hàng | Đăng nhập → Khách hàng → Bán hàng → Trả hàng |
| Nhân viên thu tiền | Đăng nhập → Thu tiền → Công nợ → Hoàn tiền → Báo cáo |
| Quản lý | Luồng tổng quát → Báo cáo → Phân quyền → Xử lý lỗi |
| Quản trị thiết lập app lần đầu | Phân quyền → Kho → Nhóm sản phẩm → Sản phẩm → Bài thực hành |

Đây là gợi ý cách sử dụng; app không tự cấp quyền theo các tên công việc trong bảng.

<a id="dang-nhap"></a>

## 2. Đăng nhập, chọn kho và làm quen giao diện

### 2.1. Đăng nhập

1. Mở địa chỉ app do quản trị viên cung cấp.
2. Nhập **Tên đăng nhập** và **Mật khẩu**.
3. Bấm **Đăng nhập** và chờ app mở màn hình bạn được phép sử dụng.
4. Nếu giao diện đang dùng tiếng Anh, chọn **Ngôn ngữ** trên thanh đầu trang và chuyển sang tiếng Việt.

Nếu đăng nhập thất bại, kiểm tra chữ hoa/chữ thường, khoảng trắng khi sao chép và trạng thái tài khoản với quản trị viên. Giao diện hiện tại chưa có chức năng tự đăng ký hoặc tự đặt lại mật khẩu.

### 2.2. Chọn kho đang làm việc

1. Tìm bộ chọn **Kho đang làm việc** trên thanh đầu trang.
2. Chọn đúng kho sẽ nhập, bán hoặc kiểm tra tồn.
3. Mở màn hình nghiệp vụ.
4. Trước khi lưu bất kỳ phiếu nào, kiểm tra lại trường **Kho** ngay trong phiếu.

Kho đang làm việc giúp chọn ngữ cảnh sử dụng. Một số danh sách có bộ lọc kho riêng; báo cáo cũng có bộ lọc độc lập. Vì vậy, hãy kiểm tra cả kho trên phiếu và kho trong bộ lọc.

Lựa chọn kho được giữ khi tải lại trang trong cùng phiên sử dụng. Khi đăng xuất, lựa chọn này được xóa. Kho ngừng hoạt động không được chọn làm kho đang làm việc để lập nghiệp vụ mới; dữ liệu lịch sử vẫn có thể xem theo quyền ở các màn hình hỗ trợ.

Nếu thấy thông báo chưa có kho trong phạm vi:

1. Bấm **Tải lại** tại khu vực chọn kho.
2. Nếu vẫn không có, nhờ quản trị viên gán kho.
3. Không chọn một kho khác chỉ để tiếp tục nhập liệu khi nghiệp vụ thực tế thuộc kho chưa được giao.

### 2.3. Các vùng chính trên màn hình

| Vùng | Cách sử dụng |
| --- | --- |
| Menu bên trái | Chọn chức năng. Menu chỉ hiện khi tài khoản đáp ứng điều kiện truy cập. |
| Bộ lọc | Chọn kho, trạng thái, ngày hoặc nhập từ khóa tùy màn hình. |
| Bảng dữ liệu | Xem các bản ghi phù hợp bộ lọc; cuộn ngang nếu chưa thấy hết cột. |
| Cột Thao tác | Mở chi tiết, sửa, xác nhận, hủy hoặc các chức năng được cấp. |
| Phân trang | Chuyển trang hoặc đổi số dòng; trang hiện tại chỉ là một phần danh sách. |
| Hộp nhập liệu | Điền thông tin; đọc thông báo ngay dưới trường bị lỗi trước khi lưu lại. |

**Tìm kiếm/Đặt lại** dùng cho bộ lọc ở những màn hình có các nút này. **Tải lại** lấy dữ liệu mới. Riêng tải lại dữ liệu trong form đang sửa có thể bỏ thay đổi chưa lưu; đọc hộp xác nhận trước khi tiếp tục.

### 2.4. Đăng xuất

Mở menu tài khoản trên thanh đầu trang và chọn **Đăng xuất**.

Nếu đang có **Lần thu cần xác định kết quả** hoặc **Lần hoàn cần xác định kết quả**, hãy đối chiếu trước khi đăng xuất. Dữ liệu hỗ trợ thử lại các lần này được lưu theo phiên/tab và bị xóa khi đăng xuất; đóng phiên trình duyệt cũng có thể làm mất dữ liệu đó.

<a id="quy-tac"></a>

## 3. Hiểu trạng thái và quy tắc nhập dữ liệu

### 3.1. Trạng thái chứng từ

| Trạng thái | Ý nghĩa | Bạn có thể làm gì? |
| --- | --- | --- |
| Nháp — DRAFT | Chứng từ đang chuẩn bị; chưa làm đổi tồn kho. | Sửa, xác nhận hoặc hủy nếu có quyền. |
| Đã xác nhận — CONFIRMED | Chứng từ đã được ghi nhận và có tác động nghiệp vụ. | Xem lịch sử; khả năng hủy phụ thuộc loại chứng từ. |
| Đã hủy — CANCELLED | Chứng từ đã kết thúc theo thao tác hủy. | Xem để đối chiếu; không khôi phục thành nháp. |
| ACTIVE trên phiếu thu/hoàn | Khoản tiền đang có hiệu lực trong tính toán. | Xem; hủy ghi nhận sai nếu có quyền và đủ điều kiện. |

Phiếu nhập và đơn bán đã xác nhận không sửa trực tiếp. Phiếu trả và phiếu kiểm kê đã xác nhận không hỗ trợ sửa hoặc hủy đảo.

**Nút Hủy/Đóng ở chân một số hộp thoại chỉ đóng cửa sổ. Hủy chứng từ là thao tác riêng trên phiếu, có bước xác nhận và điều kiện nghiệp vụ.**

### 3.2. Khi nào tồn kho thay đổi?

| Thao tác thành công | Tác động tồn kho |
| --- | --- |
| Lưu hoặc sửa nháp | Chưa thay đổi |
| Xác nhận phiếu nhập | Tăng theo số lượng nhập |
| Hủy phiếu nhập đã xác nhận | Giảm lại số lượng đã nhập, nếu đủ tồn |
| Xác nhận đơn bán | Giảm toàn bộ số lượng trong đơn |
| Hủy đơn bán đã xác nhận hợp lệ | Cộng lại toàn bộ số lượng đã bán |
| Xác nhận phiếu trả | Tăng số hàng khách trả vào kho gốc |
| Xác nhận kiểm kê | Đưa tồn của sản phẩm được kiểm kê về số thực tế đã lưu |
| Thu tiền, hoàn tiền, hủy ghi nhận thu/hoàn | Không thay đổi |

### 3.3. Quy tắc nhập số và mã

- Đơn vị sản phẩm hiện tại là **Cây**. Số lượng nhập, bán hoặc trả là số nguyên dương; ví dụ nhập 10 cây, không nhập 1,5 cây.
- Số kiểm kê là số nguyên từ 0 trở lên. **Ô trống nghĩa là chưa đếm; số 0 nghĩa là đã đếm và không có hàng.**
- Giá dùng VND nguyên, không âm. Khoản thu hoặc khoản hoàn phải lớn hơn 0. Giá sản phẩm/đơn giá 0 vẫn hợp lệ.
- Khi ô yêu cầu nhập số nguyên, nên gõ 100000; app sẽ định dạng khi hiển thị. Không gõ đơn vị tiền vào ô số.
- Chiều dài sản phẩm tính bằng mét và cho phép phần thập phân, ví dụ 2.4.
- Mã kho, nhóm và sản phẩm dùng chữ không dấu, số, dấu gạch dưới, dấu gạch ngang hoặc dấu chấm; ví dụ KHO-HN, PHAO-PU, PU-001.
- Mã được chuẩn hóa thành chữ hoa; không được dùng lại mã đã tồn tại, kể cả bản ghi đã ngừng hoạt động.
- Các mã phiếu nhập, đơn bán, phiếu trả, thu và hoàn được hệ thống sinh; không cần tự đặt.
- Một phiếu nhập, đơn bán hoặc phiếu trả có từ 1 đến 1.000 dòng. Mỗi sản phẩm chỉ xuất hiện một dòng; tăng số lượng tại dòng đã có nếu cần.

### 3.4. Phân biệt ngày nghiệp vụ và thời điểm thực hiện

**Ngày nhập/ngày bán/ngày thu/ngày trả/ngày hoàn** là ngày bạn chọn trên form. **Thời điểm xác nhận/tạo/hủy** là lúc hệ thống thực sự ghi nhận thao tác.

Ví dụ: hôm nay mới xác nhận một đơn có ngày bán hôm qua. Danh sách đơn lọc theo ngày bán có thể hiển thị ở hôm qua, trong khi báo cáo hoạt động bán hàng ghi nhận lần xác nhận vào hôm nay. Xem phần Báo cáo để chọn đúng kỳ đối chiếu.

<a id="danh-muc"></a>

## 4. Chuẩn bị kho, nhóm và sản phẩm

Phần này dành cho người được cấp quyền quản lý danh mục. Nếu dữ liệu đã có, chỉ cần kiểm tra trước khi lập chứng từ.

### 4.1. Tạo kho

Vào **Quản lý kho** → **Thêm mới**.

| Trường | Cách điền | Ví dụ |
| --- | --- | --- |
| Mã | Bắt buộc, duy nhất; tối đa 80 ký tự | KHO-HN |
| Tên | Bắt buộc; tối đa 160 ký tự | Kho Hà Nội |
| Địa chỉ | Điền địa chỉ kho nếu có | 12 Đường A, Hà Nội |
| Số điện thoại | Điện thoại liên hệ kho | 0901234567 |
| Ghi chú | Thông tin hỗ trợ vận hành | Kho chính |
| Trạng thái | Chọn Hoạt động để dùng cho nghiệp vụ mới | Hoạt động |

Bấm **Lưu**, tìm lại kho vừa tạo và mở **Chi tiết** để kiểm tra.

Tạo kho chưa tự tạo hàng tồn. Việc gán kho cho nhân viên thực hiện tại **Hệ thống → Người dùng → Kho được giao**.

### 4.2. Tạo nhóm sản phẩm

1. Vào **Nhóm sản phẩm** → **Thêm mới**.
2. Nhập mã, ví dụ PHAO-PU.
3. Nhập tên, ví dụ Phào PU.
4. Điền mô tả nếu cần; chọn **Hoạt động**.
5. Bấm **Lưu**.

Cần có nhóm hoạt động trước khi tạo sản phẩm.

### 4.3. Tạo sản phẩm

Vào **Sản phẩm** → **Thêm mới**.

| Trường | Cách điền | Ví dụ |
| --- | --- | --- |
| Mã / Tên | Bắt buộc | PU-001 / Phào PU trắng 2.4m |
| Nhóm sản phẩm | Chọn nhóm hoạt động | Phào PU |
| Chất liệu / Màu / Kích thước | Thông tin nhận diện, nếu có | PU / Trắng / 20 × 30 mm |
| Chiều dài cây (m) | Lớn hơn 0 nếu nhập | 2.4 |
| Đơn vị | Đơn vị hiện có | Cây |
| Giá nhập tham khảo | Giá để tham khảo, không phải số tiền đã thanh toán | 50000 |
| Giá bán mặc định | Giá gợi ý khi lập đơn, vẫn có thể sửa trên đơn | 100000 |
| Ngưỡng tồn thấp | Số cây dùng để cảnh báo theo từng kho | 10 |
| Mô tả / Trạng thái | Bổ sung mô tả; chọn Hoạt động | Hoạt động |

Bấm **Lưu** và kiểm tra thông tin.

**Tạo sản phẩm không làm tăng tồn kho.** Muốn đưa hàng thực tế vào kho, lập và xác nhận phiếu nhập theo phần 6.

Giá nhập trên phiếu nhập không tự cập nhật giá nhập tham khảo trong danh mục. Giá bán trên đơn cũng không thay đổi giá bán mặc định của sản phẩm.

### 4.4. Sửa hoặc ngừng sử dụng danh mục

1. Tìm bản ghi theo mã/tên.
2. Bấm **Chỉnh sửa**, thay thông tin và lưu.
3. Khi cần dừng dùng cho nghiệp vụ mới, chọn **Ngừng hoạt động** và xác nhận.
4. Chọn **Kích hoạt** khi cần sử dụng lại.

Kho, nhóm và sản phẩm được quản lý bằng trạng thái, không có thao tác xóa danh mục. Ngừng hoạt động vẫn giữ dữ liệu lịch sử. Không chọn nhóm ngừng hoạt động cho sản phẩm mới hoặc khi chuyển nhóm.

<a id="khach-hang"></a>

## 5. Quản lý khách hàng

### 5.1. Thêm khách

1. Vào **Khách hàng** và chọn thao tác thêm mới.
2. Chọn **Kho**.
3. Nhập **Tên**; đây là thông tin bắt buộc.
4. Điền **Điện thoại**, **Địa chỉ**, **Ghi chú** nếu có.
5. Chọn trạng thái **Hoạt động**, rồi bấm **Lưu**.
6. Kiểm tra khách vừa tạo; mã khách được hệ thống sinh.

Khách hàng thuộc một kho. Khi lập đơn, bạn chọn khách thuộc đúng kho của đơn; màn hình sửa khách không cho đổi kho.

### 5.2. Bán cho khách lẻ

Trong form đơn bán, có thể để khách hàng trống để bán **Khách lẻ**. Không cần tạo một khách hàng giả dùng chung.

Đơn khách lẻ chưa thanh toán vẫn được theo dõi riêng tại **Công nợ → Đơn khách lẻ chưa thanh toán**.

### 5.3. Tìm và cập nhật thông tin

Tìm khách theo mã, tên hoặc điện thoại; lọc kho và trạng thái khi cần. Mở chi tiết để kiểm tra trước khi sửa hoặc ngừng hoạt động.

Thông tin khách trên đơn đã xác nhận được lưu tại thời điểm xác nhận. Sửa tên hoặc địa chỉ khách trong danh mục sau này không làm thay đổi thông tin lịch sử của đơn đã xác nhận.

<a id="nhap-kho"></a>

## 6. Nhập hàng vào kho

### 6.1. Tạo phiếu nhập nháp

1. Chọn đúng kho đang làm việc.
2. Vào **Phiếu nhập kho** → **Tạo phiếu nhập**.
3. Kiểm tra trường **Kho**.
4. Chọn **Ngày nhập**.
5. Nhập **Nhà cung cấp** và **Ghi chú** nếu cần.
6. Chọn sản phẩm ở từng dòng.
7. Nhập **Số lượng** và **Đơn giá**.
8. Bấm **Thêm sản phẩm** nếu có nhiều mặt hàng; bỏ dòng nhập thừa.
9. Kiểm tra thành tiền và tổng dự tính.
10. Bấm **Lưu**.

**Ví dụ:** nhập PU-001, 100 cây, đơn giá 50.000 VND/cây → tổng phiếu 5.000.000 VND.

**Kết quả:** app tạo phiếu có mã PN-… và trạng thái **Nháp**. Tồn kho vẫn chưa tăng.

### 6.2. Kiểm tra và xác nhận nhập

1. Tìm phiếu nháp vừa tạo; có thể lọc kho, trạng thái và ngày nhập.
2. Mở **Chi tiết phiếu nhập**.
3. Đối chiếu hàng thực nhận với kho, sản phẩm, số lượng và giá.
4. Nếu sai, sửa khi phiếu còn nháp và lưu.
5. Chọn **Xác nhận phiếu**, đọc thông báo tăng tồn và xác nhận.

**Kết quả:** phiếu chuyển **Đã xác nhận**, tồn tăng theo từng dòng và có lịch sử **Xác nhận nhập kho**.

Trong ví dụ, nếu trước đó tồn bằng 0 thì sau xác nhận tồn là 100 cây. Có quyền tạo nháp chưa đồng nghĩa với có quyền xác nhận; nếu không thấy nút, chuyển mã phiếu cho người được cấp quyền xác nhận.

### 6.3. Hủy phiếu nhập sai

- **Phiếu nháp:** chọn **Hủy phiếu** và xác nhận; tồn không đổi.
- **Phiếu đã xác nhận:** chọn **Hủy phiếu**, nhập lý do và xác nhận; hệ thống giảm lại số lượng đã nhập.
- Nếu tồn hiện tại không đủ để trừ lại toàn bộ, app từ chối hủy và giữ nguyên dữ liệu.

Ví dụ: phiếu đã nhập 100 cây nhưng hiện kho chỉ còn 90 cây thì không thể hủy toàn bộ phiếu đó. Cần nhờ quản lý đối chiếu chứng từ và hàng thực tế, không tạo giao dịch giả để làm đủ tồn.

Phiếu đã xác nhận không sửa trực tiếp; phiếu đã hủy không khôi phục.

<a id="ton-kho"></a>

## 7. Xem tồn kho và lịch sử biến động

### 7.1. Xem tồn hiện tại

1. Chọn đúng **Kho đang làm việc**.
2. Mở **Tồn kho**.
3. Chọn sản phẩm cần lọc nếu có quyền tra cứu.
4. Bấm **Tải lại** khi cần cập nhật.
5. Đọc cột số lượng; chuyển trang hoặc cuộn ngang để xem đủ dữ liệu.

Sản phẩm hoạt động chưa nhập hàng vẫn có thể xuất hiện với tồn 0. Tồn ở danh mục sản phẩm không phải chỗ nhập số lượng; tồn được hình thành từ chứng từ và kiểm kê.

### 7.2. Tra cứu vì sao tồn tăng hoặc giảm

1. Vào **Lịch sử biến động** với đúng kho.
2. Lọc sản phẩm và khoảng thời gian thực hiện nếu cần.
3. Đọc số lượng có dấu: **+** là tăng, **−** là giảm.
4. Đọc loại biến động và **Chứng từ nguồn**.
5. Mở chứng từ nguồn khi có quyền xem loại chứng từ đó.

| Loại biến động hiển thị | Ý nghĩa |
| --- | --- |
| Xác nhận nhập kho | Cộng hàng từ phiếu nhập |
| Hủy nhập kho | Trừ lại hàng của phiếu nhập đã hủy |
| Xác nhận bán hàng | Trừ hàng đã xuất theo đơn bán |
| Hủy đơn bán hàng | Cộng lại hàng của đơn bị hủy |
| Khách trả hàng | Cộng hàng đã nhận trả |
| Điều chỉnh kiểm kê | Tăng hoặc giảm để khớp số đếm thực tế |

Bộ lọc lịch sử dùng **từ thời điểm bao gồm, đến thời điểm không bao gồm**. Ví dụ muốn xem trọn ngày 19/10/2026 theo giờ địa phương, chọn từ 19/10/2026 00:00 đến 20/10/2026 00:00.

Một số cột người thực hiện hoặc bộ lọc hiển thị ID/UUID — chuỗi định danh dài do hệ thống tạo. Đây không phải mã phiếu; không đoán hoặc tự tạo ID khi cần tra cứu.

<a id="ban-hang"></a>

## 8. Tạo và xác nhận đơn bán hàng

### 8.1. Tạo đơn nháp

1. Vào **Đơn bán hàng**, chọn thao tác tạo mới.
2. Chọn kho xuất hàng.
3. Chọn khách thuộc kho đó; để trống nếu là khách lẻ.
4. Chọn **Ngày bán** và ghi chú nếu có.
5. Thêm sản phẩm và nhập số lượng.
6. Kiểm tra **Đơn giá**; giá mặc định chỉ là gợi ý và có thể chỉnh cho đơn này.
7. Nhập **Giảm giá toàn đơn** nếu có.
8. Kiểm tra tạm tính, giảm giá và tổng tiền.
9. Bấm **Lưu nháp**.

Cách tính: **Tổng tiền = tổng của từng số lượng × đơn giá − giảm giá toàn đơn**. Giảm giá không được lớn hơn giá trị trước giảm giá.

**Ví dụ:** 10 cây × 100.000 VND, giảm giá 0 → tổng 1.000.000 VND.

**Kết quả:** có đơn nháp, tồn chưa giảm, chưa phát sinh khoản thu. Nếu tạo nháp ở kho A, hãy kiểm tra kỹ trước khi lưu; không chuyển kho của đơn đã lưu.

### 8.2. Xác nhận xuất hàng

1. Mở lại đơn nháp và đối chiếu hàng cần giao.
2. Kiểm tra kho, khách, số lượng, đơn giá và tổng tiền.
3. Chọn thao tác xác nhận.
4. Đọc cảnh báo xuất toàn bộ các dòng.
5. Bấm **Xác nhận xuất hàng**.

**Kết quả:** trạng thái chuyển **Đã xác nhận / Đã xuất hàng**; toàn bộ số lượng được trừ kho. Nếu có dòng không đủ tồn, hệ thống từ chối toàn bộ lần xác nhận.

Ví dụ tồn đang 100 cây, xác nhận bán 10 cây → tồn còn 90 cây.

**Xác nhận bán hàng chưa ghi nhận tiền khách trả.** Sau bước này, mở **Thu tiền** để ghi nhận khoản đã nhận.

### 8.3. Hủy đơn khi nhập sai

- Đơn nháp: nhập lý do và hủy; tồn không đổi.
- Đơn đã xác nhận: chỉ hủy khi đáp ứng điều kiện, đã thu hồi toàn bộ hàng hoặc hàng chưa được giao.
- Khi hủy đơn đã xác nhận, phải nhập lý do và tích **Tôi xác nhận đã thu hồi toàn bộ hàng hoặc hàng chưa được giao**.
- Hủy thành công cộng lại toàn bộ hàng trong đơn.
- Đơn có khoản thu còn hiệu lực hoặc có phiếu trả đã xác nhận sẽ bị chặn hủy.

Khi khách đã trả một phần hoặc toàn bộ hàng theo quy trình trả hàng, dùng **Trả hàng** và xử lý **Hoàn tiền** nếu có. Không hủy khoản thu đúng chỉ để vượt điều kiện chặn hủy đơn.

<a id="thu-tien"></a>

## 9. Thu tiền và in biên lai

### 9.1. Ghi nhận khoản đã thu

1. Mở đơn đã xác nhận còn phải trả tại **Đơn bán hàng**, hoặc mở đơn từ **Công nợ**.
2. Bấm **Thu tiền**.
3. Kiểm tra đúng mã đơn và số còn phải trả.
4. Nhập **Số tiền (VND)** thực nhận; có thể dùng nút điền số còn lại.
5. Chọn **Ngày thu**.
6. Chọn **Tiền mặt** hoặc **Chuyển khoản**.
7. Điền **Tham chiếu** nếu có, ví dụ mã giao dịch ngân hàng.
8. Điền **Ghi chú** và xác nhận ghi nhận.

Số tiền phải lớn hơn 0 và không vượt khoản còn phải thu hiện tại.

**Ví dụ:** đơn 1.000.000 VND, đã nhận 600.000 VND → ghi nhận thu 600.000 VND; đơn còn phải thu 400.000 VND.

Có thể thu nhiều lần cho một đơn. Nếu người khác vừa thu thêm và hạn mức đã thay đổi, hãy tải lại số liệu rồi kiểm tra trước khi ghi nhận tiếp.

**Chọn Chuyển khoản là ghi nhận thủ công. App không tự xác minh tiền đã vào ngân hàng.**

### 9.2. Hiểu trạng thái thanh toán

| Trạng thái | Cách hiểu |
| --- | --- |
| UNPAID | Chưa thu, vẫn còn khoản phải trả |
| PARTIALLY_PAID | Đã thu một phần, vẫn còn khoản phải trả |
| PAID | Không còn khoản phải thu |

Đơn có giá trị phải thanh toán bằng 0 có thể ở trạng thái PAID dù chưa từng thu tiền. Sau trả hàng, đơn PAID vẫn có thể có **Cần hoàn tiền**; luôn đọc thêm mục **Còn cần hoàn**.

### 9.3. Xem và in biên lai

1. Mở **Phiếu thu**.
2. Lọc kho, trạng thái, hình thức hoặc ngày thu để tìm phiếu.
3. Bấm **Chi tiết** và kiểm tra số tiền, ngày, hình thức, đơn nguồn.
4. Chọn **In biên lai**.
5. Trong cửa sổ xem trước, chọn **Tải PDF**, **Mở PDF riêng** hoặc **In**.
6. Nếu hộp thoại in không mở, dùng **Mở PDF riêng** rồi in trong trình duyệt. Khổ PDF là **A5 ngang**.

Biên lai thể hiện số tiền của **lần thu đó**, không phải tổng tất cả các lần thu. In lại không ghi nhận thêm tiền. Phiếu thu đã hủy vẫn có thể in bản lưu, có nhãn **ĐÃ HỦY — BẢN LƯU**.

### 9.4. Hủy phiếu thu ghi nhận sai

1. Tìm đúng phiếu đang có hiệu lực tại **Phiếu thu**.
2. Mở chi tiết, chọn hủy.
3. Nhập lý do rõ ràng và xác nhận.
4. Kiểm tra lại công nợ của đơn.

Phiếu thu không có thao tác sửa/xóa. Hủy loại khoản đó khỏi tổng thu hiệu lực và có thể làm công nợ tăng trở lại.

**Hủy phiếu thu chỉ sửa ghi nhận sai; không ghi nhận việc đã chi trả lại tiền cho khách.** Nếu khách đã trả hàng và bạn đã chi tiền lại, dùng **Hoàn tiền**. App chặn hủy thu nếu làm tổng thu hiệu lực thấp hơn tổng đã hoàn.

<a id="cong-no"></a>

## 10. Theo dõi công nợ

### 10.1. Công nợ khách hàng

1. Mở **Công nợ** → tab **Công nợ khách hàng**.
2. Chọn kho hoặc giữ toàn bộ kho được giao.
3. Tìm theo mã, tên hoặc điện thoại khách.
4. Bấm **Chi tiết công nợ**.
5. Xem tổng công nợ và từng đơn còn phải trả.
6. Chọn **Thu tiền** ở đơn phù hợp nếu được cấp quyền.

Mỗi dòng danh sách tổng hợp theo **khách/kho**. Chi tiết khách có tổng cho toàn phạm vi bạn được phép xem; kiểm tra thông tin kho trên từng đơn. Không cộng vài dòng đang nhìn thấy rồi coi đó là toàn bộ công nợ của khách.

Khách hết nợ không còn nằm trong danh sách còn nợ. Nếu cần kiểm tra theo ID khách đã biết, dùng **Xem công nợ theo UUID khách**.

### 10.2. Đơn khách lẻ còn nợ

1. Chuyển sang tab **Đơn khách lẻ chưa thanh toán**.
2. Chọn đúng kho.
3. Tìm đúng mã đơn và đối chiếu người mua/hồ sơ giao dịch thực tế.
4. Thu tiền trên đúng đơn.

### 10.3. Đọc số tiền sau khi có trả hàng hoặc hoàn tiền

| Nhãn trên chi tiết quyết toán | Ý nghĩa |
| --- | --- |
| Tổng đơn gốc | Giá trị đơn lúc bán, sau giảm giá ban đầu |
| Giá trị hàng đã trả | Tổng giá trị các phiếu trả đã xác nhận |
| Tổng sau trả | Tổng đơn gốc trừ giá trị hàng đã trả |
| Đã thu (hiệu lực) | Tổng khoản thu chưa bị hủy |
| Đã hoàn (hiệu lực) | Tổng khoản hoàn chưa bị hủy |
| Thu ròng (thu trừ hoàn) | Đã thu hiệu lực trừ đã hoàn hiệu lực |
| Còn phải thu | Phần khách còn phải thanh toán |
| Còn cần hoàn | Phần tiền còn cần chi trả lại khách |

Cách đọc:

- **Còn phải thu = phần dương của Tổng sau trả − Thu ròng.**
- **Còn cần hoàn = phần dương của Thu ròng − Tổng sau trả.**

Dùng số app trả về. Sau khi có trả hàng/hoàn tiền, không lấy riêng Tổng đơn gốc trừ Đã thu để kết luận công nợ.

<a id="tra-hang"></a>

## 11. Nhận hàng khách trả

Luồng hiện tại nhận **hàng còn bán được**, nhập lại **kho gốc của đơn bán**. Chưa hỗ trợ hàng hỏng, đổi hàng hoặc phí trả.

### 11.1. Tạo phiếu trả nháp

1. Mở đơn đã xác nhận → **Trả hàng**; hoặc vào menu **Trả hàng** → **Tạo phiếu trả nháp**.
2. Nếu tạo từ menu, nhập mã BH-… vào ô **Đơn bán hàng**, rồi chọn đúng kết quả.
3. Đối chiếu mã đơn, khách, kho, ngày bán và tổng đơn gốc.
4. Chọn ngày trả, nhập lý do; thêm ghi chú nếu cần.
5. Xem từng dòng: số đã bán, đã trả và còn được trả.
6. Nhập số cây muốn nhận trả; để trống các dòng không trả.
7. Kiểm tra giá trị trả dự kiến và lưu nháp.

Số lượng trả phải nguyên dương, không vượt số còn được trả. Không thêm sản phẩm ngoài đơn gốc.

Lưu nháp chưa tăng tồn và chưa giảm giá trị phải thanh toán. Phiếu nháp đã lưu giữ nguyên đơn nguồn; cần chọn kỹ trước khi lưu.

### 11.2. Xác nhận đã nhận hàng

1. Mở phiếu nháp.
2. Kiểm tra hàng thực nhận và tình trạng còn bán được.
3. Đối chiếu số lượng và giá trị trả dự kiến.
4. Tích **Tôi xác nhận đã nhận đủ số hàng còn bán được vào kho.**
5. Xác nhận phiếu.

**Kết quả:** tồn kho gốc tăng; tổng phải thanh toán của đơn giảm theo giá trị hàng trả.

**Xác nhận trả hàng không tự sinh phiếu hoàn tiền.** Nếu khách chưa trả tiền, việc nhận trả có thể chỉ làm giảm nợ. Nếu đã thu dư so với Tổng sau trả, đơn xuất hiện khoản **Còn cần hoàn**.

### 11.3. Giá trị trả được xác định thế nào?

App tính dựa trên đơn gốc và phần giảm giá được phân bổ, không theo giá bán hiện tại trong danh mục. Với đơn có giảm giá hoặc chia lẻ giá trị, các lần trả cùng số lượng có thể chênh nhau một vài đồng; khi trả hết, tổng được giới hạn đúng theo giá trị hàng của đơn.

Nếu app báo **RETURN_QUOTE_CHANGED**:

1. Đọc giá trị cũ/mới và các dòng thay đổi.
2. Chọn **Xem xét và sửa / lưu lại nháp**.
3. Kiểm tra lại, lưu nháp.
4. Tích xác nhận đã nhận hàng và xác nhận lại.

Giá trị có thể đổi vì một phiếu trả khác vừa được xác nhận. Không bấm xác nhận liên tục để bỏ qua thông báo.

### 11.4. Hủy hoặc sửa phiếu trả

Chỉ sửa/hủy khi còn nháp; hủy nháp cần lý do và không ảnh hưởng tồn. Phiếu đã xác nhận không được sửa hoặc hủy đảo trong phiên bản hiện tại. Nếu xác nhận sai, báo quản lý để đối chiếu và xử lý; không lập phiếu trả giả hoặc tự sửa bằng giao dịch khác.

<a id="hoan-tien"></a>

## 12. Hoàn tiền cho khách

### 12.1. Tìm đơn cần hoàn

Vào **Đơn cần hoàn tiền**, hoặc mở đơn bán và chọn **Hoàn tiền** khi được phép.

Đối chiếu mã đơn, khách, kho, **Tổng sau trả**, **Thu ròng** và **Còn cần hoàn**. Danh sách bao gồm cả khách lẻ và khách có tên, trong phạm vi kho được giao.

### 12.2. Ghi nhận khoản đã chi

1. Xác định chính xác khoản cần trả lại khách.
2. Thực hiện chi tiền theo quy trình cửa hàng.
3. Trong app, nhập **Số tiền hoàn (VND)**.
4. Chọn **Ngày hoàn**, **Hình thức**; nhập **Tham chiếu** nếu có.
5. Nhập **Lý do** và ghi chú khi cần.
6. Tích **Tôi xác nhận khoản tiền này đã thực tế chi cho khách.**
7. Ghi nhận và kiểm tra lại **Còn cần hoàn**.

Số tiền phải lớn hơn 0 và không vượt khoản còn cần hoàn. Có thể ghi nhận nhiều lần đến khi hết khoản cần hoàn.

**App ghi nhận việc chi tiền thủ công; bấm ghi nhận không tự thực hiện chuyển khoản.** Hoàn tiền không làm thay đổi tồn kho.

### 12.3. Hủy phiếu hoàn ghi nhận sai

1. Vào **Phiếu hoàn tiền**.
2. Tìm đúng phiếu và mở chi tiết.
3. Chọn hủy, nhập lý do và xác nhận.
4. Kiểm tra lại quyết toán đơn.

Phiếu hoàn không có sửa/xóa hoặc in phiếu trong giao diện hiện tại. Hủy chỉ đảo ghi nhận sai, không có nghĩa đã thu lại tiền thật từ khách.

### 12.4. Mất mạng khi thu hoặc hoàn tiền

Nếu app chưa xác định được kết quả:

1. Không thu thêm hoặc chi thêm tiền chỉ vì chưa thấy thông báo thành công.
2. Giữ nguyên nội dung lần thu/hoàn đang chờ.
3. Dùng **Thử lại đúng request cũ** hoặc thao tác thử lại ở bảng **Lần thu cần xác định kết quả / Lần hoàn cần xác định kết quả**.
4. Đối chiếu phiếu và quyết toán sau khi app xác định kết quả.
5. Nếu vẫn xung đột, chuyển mã đơn và thông báo lỗi cho người phụ trách.

“Request cũ” là đúng yêu cầu đã gửi, gồm cùng đơn và số tiền. App giữ dấu nhận diện để lần thử lại không thành một giao dịch mới.

Nếu lần thử lại cho biết phiếu cũ đã hủy, đó không phải khoản thu/chi mới. Nếu báo **IDEMPOTENCY_CONFLICT**, cần đối chiếu trước; không tạo lần mới để bỏ qua lỗi.

<a id="kiem-ke"></a>

## 13. Kiểm kê kho

### 13.1. Chuẩn bị

Chọn thời điểm phù hợp và tạm ngừng nhập/xuất thực tế các sản phẩm đang đếm. App không tự khóa toàn bộ kho chỉ vì bạn mở phiếu kiểm kê.

Cần quyền xem tồn để chọn sản phẩm hợp lệ theo kho, cùng quyền tạo/nhập số đếm/xác nhận phù hợp.

### 13.2. Tạo và nhập số đếm

1. Vào **Kiểm kê kho** → **Tạo phiếu kiểm kê**.
2. Chọn kho hoạt động.
3. Chọn từ 1 đến 1.000 sản phẩm trong danh sách theo kho; nhập ghi chú nếu cần.
4. Lưu nháp để app ghi nhận tồn đối chiếu ban đầu.
5. Mở **Nhập số đếm**.
6. Đếm thực tế và nhập ở cột **Thực tế**.
7. Kiểm tra cột **Tồn snapshot** và **Chênh lệch**.
8. Với dòng chênh lệch, nhập **Lý do** trước khi lưu.
9. Bấm **Lưu**.

**Tồn snapshot** là số tồn hệ thống được chụp làm mốc cho phiếu. Chênh lệch bằng Thực tế trừ Tồn snapshot.

Ví dụ riêng: hệ thống 100 cây, đếm thực tế 97 cây → chênh lệch −3. Số 0 hợp lệ; để trống là chưa đếm. Nếu thêm sản phẩm vào nháp, lưu dòng mới để app lấy mốc tồn rồi mới nhập số đếm.

### 13.3. Xác nhận kiểm kê

1. Kiểm tra tất cả dòng đã có số đếm.
2. Kiểm tra đủ lý do cho các dòng chênh lệch.
3. Lưu mọi thay đổi.
4. Chọn **Xác nhận kiểm kê**, đọc cảnh báo và xác nhận.

**Kết quả:** tồn được đưa về số thực tế đã lưu. Ví dụ 100 → 97 sinh điều chỉnh −3; 100 → 105 sinh +5. Dòng không chênh lệch không sinh biến động số lượng.

### 13.4. Khi tồn đã thay đổi trong lúc đếm

Nếu báo **STOCKTAKE_STALE** hoặc tồn đã có biến động:

1. Phối hợp tạm ngừng giao dịch thực tế của các sản phẩm liên quan.
2. Chọn **Lấy lại tồn hệ thống**.
3. Đọc cảnh báo: thao tác xóa toàn bộ số đếm và lý do đã lưu.
4. Xác nhận lấy mốc mới.
5. Đếm lại tất cả sản phẩm, nhập số đếm/lý do và lưu.
6. Xác nhận kiểm kê sau khi kiểm tra.

Kể cả tồn tăng rồi giảm về số cũ, app vẫn có thể yêu cầu đếm lại vì đã có giao dịch xen vào.

Chỉ hủy được nháp. Phiếu kiểm kê đã xác nhận không sửa/hủy đảo; nếu kiểm kê sai, lập phiếu kiểm kê mới và đếm lại.

<a id="bao-cao"></a>

## 14. Xem báo cáo và xuất Excel

### 14.1. Chọn đúng màn hình

| Menu trong nhóm Báo cáo | Mục đích | Có chọn kỳ ngày? | Xuất Excel? |
| --- | --- | --- | --- |
| Tổng quan | Xem hoạt động trong kỳ và các chỉ số hiện tại theo quyền | Có cho hoạt động trong kỳ | Không |
| Nhập–xuất–tồn | Xem tồn đầu, các biến động và tồn cuối kỳ | Có | Có |
| Hoạt động bán hàng | Xem bán xác nhận, hủy bán, hàng trả và bán ròng | Có | Có |
| Thu / hoàn theo thời gian hệ thống | Xem ghi nhận/đảo thu, hoàn và dòng tiền ròng | Có | Có |
| Tổng công nợ hiện tại | Xem khoản còn phải thu của khách và khách lẻ | Không | Không |
| Hàng tồn thấp | Xem các cặp kho/sản phẩm đang dưới ngưỡng | Không | Có |

**Tổng quan** ở menu chính là trang chào; số liệu nghiệp vụ nằm tại **Báo cáo → Tổng quan**.

### 14.2. Cách xem

1. Mở báo cáo phù hợp.
2. Chọn kho; **Tất cả kho được phép** chỉ gồm phạm vi tài khoản được giao.
3. Nếu có kỳ ngày, chọn Từ ngày/Đến ngày hoặc dùng **Hôm nay**, **Tháng này**, **Tháng trước**.
4. Chọn sản phẩm nếu báo cáo có bộ lọc và bạn có quyền tra cứu.
5. Bấm **Tải lại** để áp dụng.
6. Đọc thẻ tổng, sau đó xem bảng; cuộn ngang và chuyển trang khi cần.

Bộ lọc kho của báo cáo độc lập với Kho đang làm việc trên thanh đầu trang. Báo cáo có thể giữ dữ liệu lịch sử của kho ngừng hoạt động theo quyền; báo cáo tồn thấp chỉ xét kho/sản phẩm hoạt động.

Nếu một chỉ số trên Tổng quan không xuất hiện, có thể bạn chưa có quyền xem chỉ số đó. Dấu “—” hoặc phần không được trả về không có nghĩa là số 0.

### 14.3. Cơ sở thời gian của số liệu

Kỳ báo cáo tính theo giờ Việt Nam, bao gồm từ đầu ngày bắt đầu đến hết ngày kết thúc.

| Số liệu | Cơ sở ghi nhận |
| --- | --- |
| Nhập–xuất–tồn | Thời điểm thực hiện biến động kho |
| Hoạt động bán hàng | Thời điểm xác nhận/hủy đơn, xác nhận phiếu trả |
| Thu / hoàn | Thời điểm hệ thống tạo/hủy phiếu thu hoặc hoàn |
| Công nợ, khoản cần hoàn, tồn thấp | Số liệu hiện tại tại thời điểm xem |

Ví dụ: chọn Ngày thu là 18/10 nhưng đến 19/10 mới ghi nhận phiếu. Danh sách phiếu thu lọc ngày thu có thể nằm ở 18/10; báo cáo thu/hoàn ghi nhận phiếu vào 19/10.

Công nợ hiện tại và tồn thấp không chuyển thành số liệu lịch sử khi bạn chọn kỳ khác trên Tổng quan.

### 14.4. Đọc các chỉ số dễ nhầm

- **Tồn cuối kỳ** khác tồn hiện tại nếu sau ngày cuối kỳ còn phát sinh giao dịch.
- **Giá trị bán ròng** đã xét hủy bán/hàng trả; chưa phải lợi nhuận.
- **Thu ròng** là tiền thu ghi nhận trừ các khoản thu bị hủy.
- **Hoàn ròng** là tiền hoàn ghi nhận trừ các khoản hoàn bị hủy.
- **Dòng tiền ghi nhận ròng** là thu ròng trừ hoàn ròng.
- Giá trị ròng trong một kỳ có thể âm, ví dụ kỳ này đảo ghi nhận một phiếu tạo từ kỳ trước.
- Thẻ tổng bán/thu là tổng toàn bộ bộ lọc, không chỉ các dòng của trang đang mở.
- Hàng tồn thấp xuất hiện khi tồn nhỏ hơn ngưỡng lớn hơn 0. Tồn bằng ngưỡng không bị cảnh báo; ngưỡng 0 là không bật cảnh báo này.

### 14.5. Xuất Excel

1. Mở một trong bốn báo cáo có hỗ trợ xuất.
2. Chọn bộ lọc và bấm **Tải lại**.
3. Kiểm tra bảng đã đúng phạm vi.
4. Bấm **Xuất Excel** và chờ tải xong.
5. Mở file .xlsx vừa tải.

Nếu vừa thay bộ lọc nhưng chưa tải lại, hãy áp dụng trước khi xuất. File chứa toàn bộ dữ liệu phù hợp bộ lọc, không chỉ trang hiện tại.

File có trang **Thông tin**, **Chi tiết**; báo cáo bán hàng và thu/hoàn có thêm **Tổng hợp**. Số nguyên trên 15 chữ số có thể được lưu dạng chữ để giữ chính xác; không tự đổi sang số nếu chưa kiểm tra.

Nếu báo **EXPORT_TOO_LARGE**, thu hẹp kỳ hoặc chọn từng kho/sản phẩm rồi xuất lại. Giới hạn hiện tại là 100.000 dòng chi tiết; hệ thống không tự cắt bớt dữ liệu để xuất.

<a id="quan-tri"></a>

## 15. Thiết lập người dùng và phân quyền

Phần này dành cho người được cấp quyền quản trị tương ứng.

### 15.1. Hiểu hai lớp truy cập

- **Vai trò/quyền:** quyết định được làm gì, ví dụ xem đơn, tạo đơn, xác nhận hoặc thu tiền.
- **Kho được giao:** quyết định được làm ở kho nào.

Nhân viên có quyền bán hàng nhưng chưa được gán kho vẫn không thao tác được trên kho đó. Gán kho cũng không tự cấp quyền bán hàng.

### 15.2. Tạo vai trò và gán quyền

1. Vào **Hệ thống → Vai trò** → **Thêm vai trò**.
2. Nhập tên, mã, mô tả nếu cần; chọn trạng thái hoạt động.
3. Ví dụ: tên Nhân viên bán hàng, mã SALES_STAFF.
4. Lưu vai trò.
5. Tại vai trò vừa tạo, mở **Phân quyền**.
6. Chọn các quyền đã được hệ thống định nghĩa và cần cho công việc.
7. Kiểm tra toàn bộ danh sách đang chọn rồi lưu.

Khi chỉnh lại danh sách quyền, giữ các quyền cũ vẫn cần dùng. Bỏ chọn quyền và lưu có nghĩa là gỡ quyền đó khỏi vai trò.

**Màn hình Quyền** dùng quản lý các định nghĩa quyền. Với sử dụng thông thường, chọn quyền đã có để gán vai trò. Tạo một mã quyền mới không tự tạo thêm chức năng trong app.

### 15.3. Tạo người dùng

1. Vào **Hệ thống → Người dùng** → **Thêm người dùng**.
2. Nhập tên đăng nhập, họ tên, email và điện thoại nếu có.
3. Chọn **Hoạt động**, bấm lưu.
4. Mở **Gán vai trò** tại người dùng đó.
5. Chọn vai trò phù hợp và lưu.
6. Kiểm tra lại danh sách vai trò; bỏ chọn rồi lưu sẽ gỡ vai trò, trừ các trường hợp hệ thống bảo vệ.

Tên đăng nhập và email không được trùng. Form người dùng hiện không có trường đặt mật khẩu; quản trị viên cần bàn giao thông tin đăng nhập theo cơ chế cấp tài khoản của bản triển khai. Giao diện hiện chưa có đổi/quên mật khẩu.

### 15.4. Gán kho

1. Tại người dùng, bấm **Kho được giao**.
2. Xem các kho đã gán.
3. Chọn kho hoạt động trong danh sách có thể gán.
4. Bấm **Gán kho**.
5. Muốn gỡ, bấm **Gỡ kho** ở đúng kho và xác nhận.

Mỗi thao tác chỉ thay đổi một kho. Nếu người thực hiện không phải Admin, cửa sổ chỉ hiển thị các kho trong phạm vi của họ; danh sách rỗng không chứng minh người dùng chưa có kho ở nơi khác.

Gỡ kho có hiệu lực ở lần truy cập tiếp theo. Sau đổi vai trò/quyền, yêu cầu người dùng đăng nhập lại để nhận bộ quyền mới; trước khi đăng xuất cần xử lý xong các lần thu/hoàn chưa rõ kết quả.

### 15.5. Gợi ý quyền theo công việc

| Công việc | Nhóm quyền nên kiểm tra |
| --- | --- |
| Nhập kho | Xem/tạo/sửa/xác nhận phiếu nhập, tra cứu sản phẩm, xem tồn/lịch sử khi cần |
| Bán hàng | Xem/tạo/sửa/xác nhận đơn, xem khách/sản phẩm; thêm quyền tạo khách nếu cần |
| Thu tiền | Xem đơn hoặc công nợ để mở luồng thu; tạo phiếu thu; xem phiếu để tra cứu/in |
| Trả hàng | Xem/tạo/sửa/xác nhận phiếu trả |
| Hoàn tiền | Xem đơn cần hoàn/phiếu hoàn và tạo phiếu hoàn |
| Kiểm kê | Xem tồn để chọn hàng; xem/tạo/cập nhật/xác nhận kiểm kê |
| Báo cáo | Cấp riêng từng quyền REPORT_* cần sử dụng |

Quyền hủy được cấp riêng theo trách nhiệm. Không phải ai được tạo cũng được xác nhận hoặc hủy.

Các quyền trả hàng/hoàn tiền mới không tự được cấp cho vai trò nhân viên. Khi đã gán kho và đăng nhập lại nhưng vẫn thiếu menu, kiểm tra nhóm quyền này.

Vai trò ADMIN, quyền của ADMIN và việc gỡ ADMIN khỏi người dùng được hệ thống bảo vệ. Không đổi mã/xóa vai trò ADMIN hoặc cố gỡ để xử lý lỗi quyền. Vai trò đang được gán cho người dùng không xóa được.

<a id="thuc-hanh"></a>

## 16. Bài thực hành xuyên suốt

**Chỉ thực hành trên môi trường thử nghiệm hoặc bộ dữ liệu đã được quản lý cho phép.** Các bước xác nhận/thu/hoàn làm thay đổi dữ liệu thật của môi trường đang đăng nhập.

Chuẩn bị một kho thử đang hoạt động, sản phẩm PU-001 ban đầu tồn 0, giá bán 100.000 VND/cây, khách thử thuộc kho. Đơn trong bài không có giảm giá.

| Bước | Thao tác | Tồn kho sau bước | Còn phải thu | Còn cần hoàn |
| --- | --- | ---: | ---: | ---: |
| 1 | Tạo nháp nhập 100 cây × 50.000 | 0 | Chưa có đơn | Chưa có đơn |
| 2 | Xác nhận phiếu nhập | 100 | Chưa có đơn | Chưa có đơn |
| 3 | Tạo nháp bán 10 cây × 100.000 | 100 | Chưa ghi nhận công nợ của nháp | Không áp dụng |
| 4 | Xác nhận đơn 1.000.000 | 90 | 1.000.000 | 0 |
| 5 | Ghi nhận đã thu 600.000 | 90 | 400.000 | 0 |
| 6 | Tạo nháp trả 2 cây | 90 | 400.000 | 0 |
| 7 | Xác nhận nhận trả 2 cây, giá trị 200.000 | 92 | 200.000 | 0 |
| 8 | Tạo và xác nhận trả thêm 5 cây, giá trị 500.000 | 97 | 0 | 300.000 |
| 9 | Chi 300.000 và ghi nhận hoàn | 97 | 0 | 0 |

Sau bước 9, mở lại đơn và đối chiếu:

- Tổng đơn gốc: **1.000.000 VND**.
- Giá trị hàng đã trả: **700.000 VND**.
- Tổng sau trả: **300.000 VND**.
- Đã thu hiệu lực: **600.000 VND**.
- Đã hoàn hiệu lực: **300.000 VND**.
- Thu ròng: **300.000 VND**.
- Còn phải thu và Còn cần hoàn: **0 VND**.
- Hàng khách giữ lại: **3 cây**; tồn kho thử: **97 cây**.

Tiếp tục kiểm tra **Lịch sử biến động**: có nhập +100, bán −10, trả +2 và trả +5. Thu/hoàn không tạo biến động kho.

Nếu có sẵn tồn hoặc có người khác giao dịch cùng lúc, số tồn sẽ khác bảng. Đối chiếu chứng từ phát sinh trước khi kết luận app sai.

<a id="xu-ly-loi"></a>

## 17. Xử lý lỗi và câu hỏi thường gặp

### 17.1. Bảng xử lý nhanh

| Tình huống/thông báo | Cách xử lý |
| --- | --- |
| Không thấy menu/nút | Kiểm tra quyền xem và quyền thao tác; trạng thái chứng từ cũng có thể làm ẩn nút. |
| Không có kho | Tải lại kho; nhờ quản trị viên kiểm tra gán kho và trạng thái kho. |
| Không thấy sản phẩm trong bộ chọn | Kiểm tra từ khóa, trạng thái sản phẩm/nhóm và quyền tra cứu. Bộ chọn kiểm kê lấy hàng theo kho. |
| Sản phẩm hiện bằng ID, không tìm được tên | Kiểm tra quyền tra cứu danh mục; nhờ người có quyền cung cấp dữ liệu đúng. |
| Phiên hết hạn / 401 | Đăng nhập lại khi app yêu cầu; kiểm tra chứng từ trước khi gửi lại thao tác chưa rõ kết quả. |
| Không có quyền / 403 | Nhờ quản trị viên kiểm tra quyền; không tạo phiếu khác để vượt giới hạn. |
| WAREHOUSE_ACCESS_DENIED | Kho ngoài phạm vi hoặc vừa bị thu hồi; tải lại kho và liên hệ quản trị. |
| Trùng mã/tên đăng nhập/email | Tìm bản ghi đã có; đổi giá trị nếu thực sự cần tạo mới. Bản ghi ngừng hoạt động vẫn giữ mã. |
| Không đủ tồn khi xác nhận bán | Kiểm tra đúng kho, hàng thực tế và phiếu nhập đã xác nhận; sửa nháp khi cần. |
| Không đủ tồn khi hủy nhập | Phiếu hủy cần trừ lại hàng đã nhập; nhờ quản lý đối chiếu các giao dịch sau nhập. |
| VERSION_CONFLICT / dữ liệu bị xung đột | Có người hoặc tab khác cập nhật; giữ thông tin đang nhập để đối chiếu, rồi tải lại chi tiết trước khi tiếp tục. |
| STOCKTAKE_STALE | Tồn đổi sau mốc kiểm kê; lấy lại tồn hệ thống và đếm lại toàn bộ. |
| RETURN_QUOTE_CHANGED | Xem báo giá mới, sửa/lưu nháp rồi xác nhận lại sau khi kiểm tra. |
| RETURN_EXCEEDS_QUANTITY | Số còn được trả đã thay đổi; cập nhật hạn mức và kiểm tra lại số lượng. |
| SALES_ORDER_HAS_PAYMENTS / SALES_ORDER_HAS_RETURNS | Đơn có thu hiệu lực hoặc trả đã xác nhận; xử lý theo luồng trả/hoàn và đối chiếu thực tế. |
| PAYMENT_EXCEEDS_REMAINING | Khoản thu vượt công nợ hiện tại; tải lại và kiểm tra số còn phải thu. |
| REFUND_EXCEEDS_REFUNDABLE | Khoản hoàn vượt số còn cần hoàn; tải lại quyết toán. |
| PAYMENT_BELOW_REFUNDED | Hủy thu làm tổng thu thấp hơn tổng đã hoàn; nhờ người phụ trách đối chiếu. |
| IDEMPOTENCY_CONFLICT | Yêu cầu đang chờ xung đột với dữ liệu đã ghi; giữ lần cũ và đối chiếu phiếu, không tự tạo lần thay thế. |
| EXPORT_TOO_LARGE | Thu hẹp bộ lọc; chia theo kho/kỳ/sản phẩm rồi xuất lại. |
| INVENTORY_HISTORY_INCONSISTENT | Cần đối soát tồn và lịch sử; gửi thông tin cho quản trị/kỹ thuật, không tự điều chỉnh để che sai lệch. |
| Mất mạng / lỗi máy chủ / chưa rõ kết quả | Kiểm tra trạng thái đã lưu trước khi tạo lại; thu/hoàn dùng thử lại đúng yêu cầu cũ. |

### 17.2. Nếu bấm lưu/xác nhận rồi app đứng hoặc báo lỗi mạng

**Với tạo nháp:** tìm lại danh sách theo kho, ngày và nội dung vừa nhập trước khi tạo thêm phiếu. Lần tạo trước có thể đã thành công dù trình duyệt chưa nhận phản hồi.

**Với xác nhận/hủy:** mở hoặc tải lại chi tiết để kiểm tra trạng thái. Nếu chưa xác định được, dừng thao tác đó và nhờ người có quyền xem kiểm tra.

**Với thu/hoàn:** dùng cơ chế thử lại yêu cầu cũ như phần 12.4. Không đổi số tiền hoặc tạo một lần mới khi chưa đối chiếu xong.

### 17.3. Vì sao đã lưu phiếu nhập mà tồn vẫn bằng 0?

Phiếu có thể đang **Nháp**. Kiểm tra trạng thái và xác nhận nếu hàng đã được nhận đủ. Sau đó kiểm tra đúng kho/sản phẩm ở Tồn kho.

### 17.4. Vì sao đơn đã xác nhận mà vẫn còn nợ?

Xác nhận đơn ghi nhận xuất hàng. Phải ghi nhận thêm khoản thu thực tế qua **Thu tiền**.

### 17.5. Vì sao trả hàng xong vẫn chưa có phiếu hoàn?

Nhận hàng trả và chi tiền là hai bước riêng. Xem **Còn cần hoàn**; chỉ ghi nhận hoàn sau khi thực tế đã chi tiền.

### 17.6. Vì sao báo cáo khác danh sách chứng từ?

Kiểm tra phạm vi kho, thời điểm tải và cơ sở ngày lọc. Danh sách có thể dùng ngày nghiệp vụ trên form; báo cáo dùng thời điểm xác nhận/tạo/hủy. Công nợ và tồn thấp là hiện tại.

### 17.7. Khi báo lỗi cho người hỗ trợ, cần gửi gì?

Gửi tên màn hình, kho, mã chứng từ, thời điểm xảy ra, thao tác vừa làm, nguyên văn thông báo/mã lỗi, kết quả mong đợi và ảnh màn hình nếu có. Với thu/hoàn, nêu rõ tiền thực tế đã nhận/chi hay chưa và đã thử lại lần cũ chưa. Không gửi mật khẩu hoặc thông tin phiên đăng nhập.

<a id="van-hanh"></a>

## 18. Công việc đầu ca, cuối ca và phạm vi hiện có

### 18.1. Đầu ca

1. Đăng nhập bằng tài khoản được cấp.
2. Kiểm tra Kho đang làm việc.
3. Tải lại tồn hoặc danh sách cần sử dụng.
4. Kiểm tra phiếu nháp, công nợ đến lượt xử lý và các lần thu/hoàn chưa xác định kết quả.
5. Xác nhận người chịu trách nhiệm duyệt/xác nhận/hủy nếu bạn chưa có các quyền đó.

### 18.2. Cuối ca

1. Kiểm tra phiếu nháp còn treo; chưa xác nhận khi chưa đủ căn cứ thực tế.
2. Đối chiếu phiếu thu/hoàn với tiền mặt và giao dịch chuyển khoản thực tế.
3. Kiểm tra đơn còn nợ, đơn cần hoàn và các giao dịch chưa rõ kết quả.
4. Xem báo cáo đúng kho, đúng kỳ; xuất file khi cần bàn giao.
5. Lưu mã các chứng từ cần người khác tiếp tục xử lý.
6. Đăng xuất sau khi đã đối chiếu các lần thu/hoàn đang chờ.

### 18.3. Các giới hạn người sử dụng cần biết

- Hiện bán theo số cây nguyên; chưa có quy trình bán lẻ theo mét hoặc phần cây.
- Chưa có giao diện tự đổi/quên mật khẩu.
- Chưa có quy trình chuyển kho hoặc nhập tồn đầu kỳ riêng. “Tồn đầu kỳ” trên báo cáo được tính từ lịch sử, không phải ô để nhập.
- Trả hàng hiện chỉ nhận hàng còn bán được về kho gốc; chưa có hàng hỏng, đổi hàng hoặc phí trả.
- Phiếu trả đã xác nhận chưa hỗ trợ điều chỉnh/hủy đảo.
- Thu/hoàn chuyển khoản là ghi nhận thủ công, chưa tự đối soát ngân hàng.
- Biên lai thu là chứng từ ghi nhận lần thu; app chưa có quy trình hóa đơn điện tử/VAT.
- Báo cáo bán ròng chưa tính giá vốn hoặc lợi nhuận; công nợ lịch sử chưa được hỗ trợ.
- Tài liệu này được viết từ mã nguồn và tài liệu hiện có; chưa thực hiện giao dịch thật hoặc nghiệm thu toàn bộ luồng trên trình duyệt trong lần biên soạn này.

<a id="nguon"></a>

## 19. Nguồn đối chiếu tài liệu

Các liên kết dưới đây dành cho quản trị viên/kỹ thuật khi cần kiểm tra chi tiết. Người mới có thể sử dụng các phần hướng dẫn ở trên mà không cần đọc API.

| Nội dung | Tài liệu backend |
| --- | --- |
| Kho, danh mục và gán kho | [Quản lý kho và danh mục](inventory-api.md) |
| Phiếu nhập, tồn và lịch sử | [Nhập kho và tồn kho](stock-api.md) |
| Khách hàng và đơn bán | [Khách hàng và bán hàng](sales-api.md) |
| Thu tiền và công nợ | [Thanh toán và công nợ](payments-api.md) |
| Trả hàng và hoàn tiền, công thức quyết toán hiện hành | [Trả hàng và hoàn tiền](returns-refunds-api.md) |
| Tìm đơn nguồn để trả hàng | [Đơn còn được trả](returnable-orders-api.md) |
| Kiểm kê | [Kiểm kê kho](stocktake-api.md) |
| Báo cáo | [Báo cáo](reports-api.md) |
| Xuất Excel | [Xuất Excel báo cáo](report-excel-export.md) |
| In biên lai | [Biên lai PDF](payment-print/README.md) |

Giao diện được đối chiếu tại dự án AI_FE cùng cấp: cấu hình menu, bản dịch tiếng Việt, các trang danh mục/kho/bán hàng/báo cáo/hệ thống và các hộp thoại nhập liệu liên quan. Khi tài liệu cũ mô tả chưa có thu/hoàn/trả hàng, phần mở rộng hiện hành và mã nguồn mới được ưu tiên. Cơ chế tạo tài khoản cũng được kiểm tra trực tiếp trong UserService thay vì dựa riêng vào README cũ.
