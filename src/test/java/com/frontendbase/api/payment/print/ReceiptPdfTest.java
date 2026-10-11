package com.frontendbase.api.payment.print;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

class ReceiptPdfTest {
    @Test void moneyWords() {
        Map<Long, String> cases = new LinkedHashMap<>();
        cases.put(0L, "Không đồng");
        cases.put(15L, "Mười lăm đồng");
        cases.put(21L, "Hai mươi mốt đồng");
        cases.put(105L, "Một trăm lẻ năm đồng");
        cases.put(1000L, "Một nghìn đồng");
        cases.put(1001L, "Một nghìn không trăm lẻ một đồng");
        cases.put(1000001L, "Một triệu không trăm lẻ một đồng");
        cases.put(1001005L, "Một triệu không trăm lẻ một nghìn không trăm lẻ năm đồng");
        cases.put(1000000000L, "Một tỷ đồng");
        cases.put(1000000000000000000L, "Một tỷ tỷ đồng");
        cases.put(Long.MAX_VALUE, "Chín tỷ tỷ hai trăm hai mươi ba triệu tỷ ba trăm bảy mươi hai nghìn tỷ "
                + "không trăm ba mươi sáu tỷ tám trăm năm mươi bốn triệu bảy trăm bảy mươi lăm nghìn tám trăm lẻ bảy đồng");
        cases.forEach((amount, expected) -> assertEquals(expected, VietnameseMoneyWords.of(amount)));
        assertThrows(IllegalArgumentException.class, () -> VietnameseMoneyWords.of(-1));
    }

    @Test void samplesAndLongContentStayInsidePages() throws Exception {
        Path output = Path.of("docs/payment-print/samples");
        Files.createDirectories(output);
        var regular = new ReceiptData("PT-DEMO-001", LocalDate.of(2026, 10, 9),
                "Cửa hàng Minh Anh — Kho Quận 1", "123 Nguyễn Thị Minh Khai, phường Bến Thành, TP. Hồ Chí Minh",
                "Nguyễn Thị Hồng Ánh", "45 Lê Thánh Tôn, TP. Hồ Chí Minh",
                "Thu tiền đơn bán DH-DEMO-001. Thanh toán đợt 2", 1250000L, "Trần Quốc Bình", false, null);
        verify(new ReceiptPdfService().render(regular), output, "bien-lai-mau", 1, false);
        var longData = new ReceiptData("PT-DEMO-LONG", regular.date(), regular.warehouseName(),
                "Địa chỉ kho có nhiều thông tin ".repeat(18), "Nguyễn Thị Hồng Ánh ".repeat(8),
                "Địa chỉ người nộp có nhiều thông tin ".repeat(12),
                "Thu tiền đơn bán DH-DEMO-002. " + "Ghi chú dài để kiểm tra đầy đủ nội dung ".repeat(48)
                    + " <script>& literal " + "Z".repeat(200) + " KẾT THÚC GHI CHÚ",
                Long.MAX_VALUE, "Trần Quốc Bình ".repeat(8), true,
                "Điều chỉnh phiếu thu bị ghi nhầm, lưu lại phục vụ đối chiếu. ".repeat(25));
        byte[] longPdf = new ReceiptPdfService().render(longData);
        verify(longPdf, output, "bien-lai-mau-da-huy-noi-dung-dai", 2, true);
        try (var document = Loader.loadPDF(longPdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("KẾT THÚC GHI CHÚ"));
            assertTrue(text.contains("<script>& literal"));
            assertTrue(text.contains("9.223.372.036.854.775.807"));
        }
        var noCollector = new ReceiptData("PT-RETAIL", regular.date(), "Kho", "", "Khách lẻ", "",
                "Thu tiền đơn bán DH-RETAIL", 1, null, false, null);
        try (var document = Loader.loadPDF(new ReceiptPdfService().render(noCollector))) {
            assertTrue(new PDFTextStripper().getText(document).contains("Khách lẻ"));
        }
    }

    private void verify(byte[] bytes, Path output, String name, int minPages, boolean cancelled) throws Exception {
        Files.write(output.resolve(name + ".pdf"), bytes);
        try (var document = Loader.loadPDF(bytes)) {
            assertTrue(document.getNumberOfPages() >= minPages);
            if (minPages == 1) assertEquals(1, document.getNumberOfPages());
            for (var page : document.getPages()) {
                assertEquals(595.276f, page.getMediaBox().getWidth(), 0.1);
                assertEquals(419.528f, page.getMediaBox().getHeight(), 0.1);
                for (var fontName : page.getResources().getFontNames())
                    assertTrue(page.getResources().getFont(fontName).isEmbedded());
            }
            var bounds = new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition position) {
                    assertTrue(position.getXDirAdj() >= 24, "Text crosses left edge");
                    assertTrue(position.getXDirAdj() + position.getWidthDirAdj() <= 572, "Text crosses right edge");
                    assertTrue(position.getYDirAdj() <= 395, "Text crosses bottom edge");
                    super.processTextPosition(position);
                }
            };
            String text = bounds.getText(document);
            assertTrue(text.contains("BIÊN LAI THU TIỀN"));
            assertTrue(text.contains("Người thu tiền"));
            assertFalse(text.contains("Mẫu số 06"));
            if (cancelled) {
                for (int i = 1; i <= document.getNumberOfPages(); i++) {
                    bounds.setStartPage(i); bounds.setEndPage(i);
                    assertTrue(bounds.getText(document).contains("ĐÃ HỦY"));
                }
            }
            var renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++)
                ImageIO.write(renderer.renderImageWithDPI(i, 120), "png", output.resolve(name + "-" + (i + 1) + ".png").toFile());
        }
    }
}
