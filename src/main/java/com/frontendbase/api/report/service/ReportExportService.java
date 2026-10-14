package com.frontendbase.api.report.service;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import com.frontendbase.api.report.repository.ReportRepository;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReportExportService {
    private static final Logger LOG = LoggerFactory.getLogger(ReportExportService.class);
    private final ReportRepository repository;
    private final ReportAccess access;
    public enum Kind { INVENTORY, SALES, PAYMENTS, LOW_STOCK }
    public record ExportFile(Path path, String filename) implements AutoCloseable {
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }
    static final String[] PRODUCT = {"UUID kho", "Mã kho", "Tên kho", "UUID sản phẩm", "Mã sản phẩm", "Tên sản phẩm", "Đơn vị"};
    static final String[] SALES = {"Số đơn xác nhận", "Số đơn đã xác nhận bị hủy", "Số lượng xác nhận", "Số lượng hủy", "Giá trị bán trước giảm giá", "Giảm giá", "Giá trị bán xác nhận", "Giá trị bán hủy", "Giá trị bán thuần"};
    static final String[] PAYMENTS = {"Số phiếu thu tạo", "Số phiếu thu hủy", "Tiền mặt ghi nhận", "Chuyển khoản ghi nhận", "Tiền mặt đảo ghi nhận", "Chuyển khoản đảo ghi nhận", "Tiền mặt thuần", "Chuyển khoản thuần", "Tổng thu ghi nhận thuần"};
    private static String[] concat(String[] a, String... b) {
        return java.util.stream.Stream.concat(java.util.Arrays.stream(a), java.util.Arrays.stream(b)).toArray(String[]::new);
    }

    public ExportFile export(Kind kind, UUID warehouse, UUID product, String from, String to) {
        String permission = switch (kind) {
            case INVENTORY -> "REPORT_INVENTORY_VIEW";
            case SALES -> "REPORT_SALES_VIEW";
            case PAYMENTS -> "REPORT_PAYMENT_VIEW";
            case LOW_STOCK -> "REPORT_LOW_STOCK_VIEW";
        };
        if (!access.allowed(permission)) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền truy cập");
        var period = kind == Kind.LOW_STOCK ? null : ReportPeriod.parse(from, to);
        var scope = access.scope(warehouse);
        if (kind == Kind.INVENTORY) repository.verifyInventoryHistory(scope, product);
        var generatedAt = ZonedDateTime.now(ReportPeriod.ZONE);
        String title = switch (kind) {
            case INVENTORY -> "Nhập xuất tồn"; case SALES -> "Bán hàng";
            case PAYMENTS -> "Thu tiền"; case LOW_STOCK -> "Tồn thấp";
        };
        String prefix = switch (kind) {
            case INVENTORY -> "nhap-xuat-ton"; case SALES -> "ban-hang";
            case PAYMENTS -> "thu-tien"; case LOW_STOCK -> "ton-thap";
        };
        String filename = prefix + "_" + (period == null ? generatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) : period.from() + "_" + period.to()) + ".xlsx";
        String[] headers = switch (kind) {
            case INVENTORY -> concat(PRODUCT, "Trạng thái sản phẩm", "Tồn đầu kỳ", "Nhập", "Hủy nhập", "Xuất bán", "Hủy bán", "Điều chỉnh tăng", "Điều chỉnh giảm", "Tồn cuối kỳ");
            case SALES -> concat(new String[]{"UUID kho", "Mã kho", "Tên kho"}, SALES);
            case PAYMENTS -> concat(new String[]{"UUID kho", "Mã kho", "Tên kho"}, PAYMENTS);
            case LOW_STOCK -> concat(PRODUCT, "Tồn hiện tại", "Ngưỡng tồn thấp", "Số lượng thiếu");
        };
        Path path = null;
        try (var workbook = new ReportWorkbook(headers)) {
            workbook.info("Tên báo cáo", title);
            workbook.info("Bộ lọc kho", warehouse == null ? "Tất cả kho trong phạm vi được phép" : warehouse);
            workbook.info("Bộ lọc sản phẩm", product == null ? "Tất cả sản phẩm phù hợp" : product);
            if (period != null) { workbook.info("Từ ngày", period.from()); workbook.info("Đến ngày", period.to()); }
            for (String label : repository.warehouseLabels(scope)) workbook.info("Kho trong phạm vi", label);
            workbook.info("Thời điểm tạo", generatedAt.toString());
            workbook.info("Múi giờ", ReportPeriod.ZONE.getId());
            workbook.info("Đơn vị", "Tiền: VND; số lượng: cây. Cột Đơn vị giữ giá trị API.");
            workbook.info("Độ chính xác", "Số nguyên trong [-999999999999999, 999999999999999] ghi numeric; ngoài giới hạn ghi text chính xác. Tổng chính thức tính exact ở backend; không dùng SUM Excel.");
            workbook.info("Cơ sở thời gian/chỉ số", switch (kind) {
                case INVENTORY -> "Theo performedAt, từ đầu ngày đến trước đầu ngày sau cuối kỳ. Tồn cuối kỳ lịch sử từ ledger, không thay bằng balance hiện tại. Giữ lịch sử sản phẩm ngừng hoạt động; trạng thái sản phẩm theo API: 1 hoạt động, 0 ngừng hoạt động.";
                case SALES -> "Bán hàng theo confirmedAt/cancelledAt, chưa phải lợi nhuận. " + ReportService.SALES_DESCRIPTION;
                case PAYMENTS -> "Thu tiền theo createdAt/cancelledAt, không theo paymentDate. Hủy phiếu thu là đảo ghi nhận sai, không phải hoàn tiền. " + ReportService.PAYMENT_DESCRIPTION;
                case LOW_STOCK -> "Tồn thấp là hiện tại trong cùng snapshot; chỉ kho/sản phẩm hoạt động, ngưỡng > 0, balance chưa có là 0.";
            });
            switch (kind) {
                case INVENTORY -> repository.inventory(scope, product, period, 1, 500, workbook::detail);
                case SALES -> {
                    var summary = repository.salesSummary(scope, period);
                    repository.sales(scope, period, 1, 500, workbook::detail);
                    workbook.summary(SALES, summary);
                }
                case PAYMENTS -> {
                    var summary = repository.paymentsSummary(scope, period);
                    repository.payments(scope, period, 1, 500, workbook::detail);
                    workbook.summary(PAYMENTS, summary);
                }
                case LOW_STOCK -> repository.lowStock(scope, product, 1, 500, workbook::detail);
            }
            // OS private temp location, never an application public directory.
            path = Files.createTempFile("report-export-", ".xlsx");
            workbook.write(path);
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                Path completedFile = path;
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) {
                                try { Files.deleteIfExists(completedFile); }
                                catch (IOException e) { LOG.error("Failed to clean rolled back export", e); }
                            }
                        }
                    });
            }
        } catch (Exception e) {
            if (path != null) try { Files.deleteIfExists(path); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            if (e instanceof ApiException business) throw business;
            LOG.error("Failed to build {} report export", kind, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_EXPORT_FAILED", "Không thể tạo file báo cáo");
        }
        return new ExportFile(path, filename);
    }
}
