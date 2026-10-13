package com.frontendbase.api.payment.print;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.frontendbase.api.common.exception.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReceiptPdfService {
    public byte[] render(ReceiptData data) {
        try (var document = new PDDocument();
                var regular = new ClassPathResource("fonts/NotoSans-Regular.ttf").getInputStream();
                var bold = new ClassPathResource("fonts/NotoSans-Bold.ttf").getInputStream();
                var output = new ByteArrayOutputStream()) {
            var normalFont = PDType0Font.load(document, regular);
            var boldFont = PDType0Font.load(document, bold);
            try (var template = new ReceiptPdfTemplate(document, normalFont, boldFont, data)) {
                template.draw();
            }
            document.getDocumentInformation().setTitle("BIÊN LAI THU TIỀN " + data.code());
            document.save(output);
            return output.toByteArray();
        } catch (IOException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT_PDF_FAILED",
                    "Không thể tạo PDF biên lai thu tiền");
        }
    }
}
