package com.frontendbase.api.report.service;

import java.io.*;
import java.lang.reflect.RecordComponent;
import java.nio.file.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/** Bounded row window; inline strings preserve user text without formula evaluation. */
public final class ReportWorkbook implements AutoCloseable {
    private final SXSSFWorkbook workbook = new SXSSFWorkbook(100);
    private final CellStyle text, number, heading;
    private final Sheet detail;
    private int rows;
    private final int columns;

    public ReportWorkbook(String[] headers) {
        workbook.setCompressTempFiles(true);
        text = workbook.createCellStyle();
        text.setWrapText(true);
        text.setDataFormat(workbook.createDataFormat().getFormat("@"));
        number = workbook.createCellStyle();
        number.setDataFormat(workbook.createDataFormat().getFormat("#,##0;[Red]-#,##0"));
        heading = workbook.createCellStyle();
        heading.setWrapText(true);
        heading.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        var font = workbook.createFont(); font.setBold(true); heading.setFont(font);
        workbook.createSheet("Thông tin");
        detail = workbook.createSheet("Chi tiết");
        columns = headers.length + 1;
        var row = detail.createRow(0); row.setHeightInPoints(45);
        cell(row.createCell(0), "STT"); row.getCell(0).setCellStyle(heading);
        detail.setColumnWidth(0, 8 * 256);
        for (int i = 0; i < headers.length; i++) {
            cell(row.createCell(i + 1), headers[i]); row.getCell(i + 1).setCellStyle(heading);
            detail.setColumnWidth(i + 1, (headers[i].contains("UUID") ? 38 : headers[i].startsWith("Tên") ? 40 : 23) * 256);
        }
        detail.createFreezePane(0, 1);
    }

    public void info(String key, Object value) {
        var sheet = workbook.getSheet("Thông tin");
        var row = sheet.createRow(sheet.getPhysicalNumberOfRows());
        cell(row.createCell(0), key); cell(row.createCell(1), value);
        sheet.setColumnWidth(0, 30 * 256); sheet.setColumnWidth(1, 100 * 256);
    }

    public void detail(Object record) {
        var values = values(record);
        if (values.size() + 1 != columns) throw new IllegalArgumentException("Report columns do not match DTO");
        var row = detail.createRow(++rows);
        cell(row.createCell(0), (long) rows);
        for (int i = 0; i < values.size(); i++) cell(row.createCell(i + 1), values.get(i));
    }

    public void summary(String[] labels, Object record) {
        var sheet = workbook.createSheet("Tổng hợp");
        sheet.setColumnWidth(0, 50 * 256); sheet.setColumnWidth(1, 30 * 256);
        var values = values(record);
        for (int i = 0; i < labels.length; i++) {
            var row = sheet.createRow(i);
            cell(row.createCell(0), labels[i]); cell(row.createCell(1), values.get(i));
        }
    }

    private List<Object> values(Object record) {
        var result = new ArrayList<Object>();
        try {
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                Object value = component.getAccessor().invoke(record);
                if (value != null && value.getClass().isRecord()) result.addAll(values(value));
                else result.add(value);
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return result;
    }

    // Compare directly, avoiding abs(Long.MIN_VALUE) and conversion of large longs to double.
    void cell(Cell cell, Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            long integer = ((Number) value).longValue();
            if (integer >= -999_999_999_999_999L && integer <= 999_999_999_999_999L) {
                cell.setCellValue((double) integer); cell.setCellStyle(number); return;
            }
        }
        cell.setCellValue(value == null ? "" : value.toString()); cell.setCellStyle(text);
    }

    public void write(Path path) throws IOException {
        detail.setAutoFilter(new CellRangeAddress(0, rows, 0, columns - 1));
        if (rows == 0) cell(detail.createRow(1).createCell(0), "Không có dữ liệu");
        try (var output = Files.newOutputStream(path)) { workbook.write(output); }
    }

    @Override public void close() throws IOException { workbook.close(); }
}
