package com.frontendbase.api.payment.print;

import java.io.IOException;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

/** Direct PDF text, no HTML interpretation. All measurements are PDF points. */
final class ReceiptPdfTemplate implements AutoCloseable {
    private static final float WIDTH = PDRectangle.A5.getHeight(), HEIGHT = PDRectangle.A5.getWidth();
    private static final float MARGIN = 25, BODY = WIDTH - 2 * MARGIN, SIZE = 10, LINE = 16;
    private final PDDocument document;
    private final PDFont normal, bold;
    private final ReceiptData data;
    private PDPageContentStream stream;
    private float y;

    ReceiptPdfTemplate(PDDocument document, PDFont normal, PDFont bold, ReceiptData data) {
        this.document = document; this.normal = normal; this.bold = bold; this.data = data;
    }

    void draw() throws IOException {
        page();
        float rightWidth = 220, leftWidth = BODY - rightWidth - 18;
        var left = wrap("Đơn vị: " + clean(data.warehouseName()), leftWidth, bold, SIZE);
        left.addAll(wrap("Địa chỉ: " + clean(data.warehouseAddress()), leftWidth, normal, SIZE));
        var right = wrap("Mã biên lai: " + clean(data.code()), rightWidth, bold, SIZE);
        for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
            ensure(LINE);
            if (i < left.size()) text(left.get(i), MARGIN, y, i == 0 ? bold : normal, SIZE);
            if (i < right.size()) text(right.get(i), WIDTH - MARGIN - rightWidth, y, bold, SIZE);
            y -= LINE;
        }
        ensure(data.cancelled() ? 105 : 80);
        y -= 12;
        center("BIÊN LAI THU TIỀN", WIDTH / 2, y, bold, 16); y -= 22;
        var date = data.date();
        center("Ngày %02d tháng %02d năm %04d".formatted(date.getDayOfMonth(), date.getMonthValue(), date.getYear()),
                WIDTH / 2, y, normal, SIZE); y -= 25;
        if (data.cancelled()) {
            center("ĐÃ HỦY — BẢN LƯU", WIDTH / 2, y, bold, 15); y -= 22;
            field("Lý do hủy:", data.cancellationReason());
        }
        field("Họ và tên người nộp:", data.payerName());
        field("Địa chỉ:", data.payerAddress());
        field("Nội dung thu:", data.description());
        field("Số tiền thu:", NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(data.amount()) + " đồng");
        field("Viết bằng chữ:", VietnameseMoneyWords.of(data.amount()));
        var collector = wrap(clean(data.collectorName()), BODY / 2 - 35, normal, SIZE);
        float signatureHeight = 82 + collector.size() * LINE;
        ensure(signatureHeight);
        // On short receipts, align signatures near the bottom, as in the reference.
        y = Math.min(y - 12, 110 + collector.size() * LINE);
        float first = MARGIN + BODY / 4, second = MARGIN + BODY * 3 / 4;
        center("Người nộp tiền", first, y, bold, 11);
        center("Người thu tiền", second, y, bold, 11); y -= 17;
        center("(Ký, họ tên)", first, y, normal, SIZE);
        center("(Ký, họ tên)", second, y, normal, SIZE); y -= 48;
        for (String line : collector) {
            center(line, second, y, normal, SIZE); y -= LINE;
        }
    }

    private void page() throws IOException {
        if (stream != null) stream.close();
        var page = new PDPage(new PDRectangle(WIDTH, HEIGHT));
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        y = HEIGHT - MARGIN - 10;
        if (document.getNumberOfPages() > 1) {
            String header = (data.cancelled() ? "ĐÃ HỦY — BẢN LƯU | " : "") + "BIÊN LAI THU TIỀN (tiếp)";
            text(header, MARGIN, y, bold, 11); y -= LINE + 4;
            for (String line : wrap("Mã biên lai: " + clean(data.code()), BODY, normal, SIZE)) {
                text(line, MARGIN, y, normal, SIZE); y -= LINE;
            }
            y -= 10;
        }
    }

    private void ensure(float height) throws IOException {
        if (y - height < MARGIN) page();
    }

    private void field(String label, String value) throws IOException {
        String content = clean(value);
        float labelWidth = width(label, normal, SIZE) + 7;
        float available = BODY - labelWidth;
        var lines = wrap(content, available, normal, SIZE);
        for (int i = 0; i < lines.size(); i++) {
            ensure(LINE + 3);
            if (i == 0) text(label, MARGIN, y, normal, SIZE);
            float start = MARGIN + labelWidth;
            text(lines.get(i), start, y, normal, SIZE);
            float dots = start + width(lines.get(i), normal, SIZE) + 5;
            if (dots < WIDTH - MARGIN) {
                stream.setLineWidth(0.35f);
                stream.setLineDashPattern(new float[]{1, 2}, 0);
                stream.moveTo(dots, y - 2); stream.lineTo(WIDTH - MARGIN, y - 2); stream.stroke();
                stream.setLineDashPattern(new float[]{}, 0);
            }
            y -= LINE;
        }
        y -= 5;
    }

    private List<String> wrap(String value, float maxWidth, PDFont font, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : clean(value).split("\\n", -1)) {
            String line = "";
            for (String word : paragraph.split(" +")) {
                if (word.isEmpty()) continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (width(candidate, font, size) <= maxWidth) { line = candidate; continue; }
                if (!line.isEmpty()) { lines.add(line); line = ""; }
                // Break even unspaced input by code point to prevent horizontal overflow.
                for (int cp : word.codePoints().toArray()) {
                    String character = new String(Character.toChars(cp));
                    if (!line.isEmpty() && width(line + character, font, size) > maxWidth) {
                        lines.add(line); line = "";
                    }
                    line += character;
                }
            }
            lines.add(line);
        }
        return lines;
    }

    private String clean(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
        StringBuilder safe = new StringBuilder();
        for (int cp : normalized.codePoints().toArray()) {
            if (cp == '\n') { safe.append('\n'); continue; }
            if (Character.isISOControl(cp)) { safe.append(' '); continue; }
            String ch = new String(Character.toChars(cp));
            try { normal.getStringWidth(ch); safe.append(ch); }
            catch (IOException | IllegalArgumentException e) { safe.append('?'); }
        }
        return safe.toString();
    }

    private float width(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(text) / 1000 * size;
    }
    private void text(String text, float x, float baseline, PDFont font, float size) throws IOException {
        stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(x, baseline);
        stream.showText(text); stream.endText();
    }
    private void center(String text, float x, float baseline, PDFont font, float size) throws IOException {
        text(text, x - width(text, font, size) / 2, baseline, font, size);
    }
    @Override public void close() throws IOException { if (stream != null) stream.close(); }
}
