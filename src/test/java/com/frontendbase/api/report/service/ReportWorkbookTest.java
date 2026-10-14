package com.frontendbase.api.report.service;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ReportWorkbookTest {
    public record Value(long value) {}
    @Test void integerBoundariesAndFormulaTextRoundTrip() throws Exception {
        var path = Files.createTempFile("excel-test-", ".xlsx");
        long[] values = {0, -1, 999999999999999L, -999999999999999L, 1000000000000000L, -1000000000000000L, Long.MAX_VALUE, Long.MIN_VALUE};
        try {
            try (var wb = new ReportWorkbook(new String[]{"Giá trị"})) {
                for (long value : values) wb.detail(new Value(value));
                wb.info("Chuỗi", "=SUM(A1:A2)"); wb.write(path);
            }
            try (var input = Files.newInputStream(path); var wb = new XSSFWorkbook(input)) {
                for (int i = 0; i < values.length; i++) {
                    var cell = wb.getSheet("Chi tiết").getRow(i + 1).getCell(1);
                    if (i < 4) { assertEquals(CellType.NUMERIC, cell.getCellType()); assertEquals(values[i], (long)cell.getNumericCellValue()); }
                    else { assertEquals(CellType.STRING, cell.getCellType()); assertEquals(Long.toString(values[i]), cell.getStringCellValue()); }
                }
                var cell = wb.getSheet("Thông tin").getRow(0).getCell(1);
                assertEquals(CellType.STRING, cell.getCellType()); assertEquals("=SUM(A1:A2)", cell.getStringCellValue());
            }
        } finally { Files.deleteIfExists(path); }
    }
}
