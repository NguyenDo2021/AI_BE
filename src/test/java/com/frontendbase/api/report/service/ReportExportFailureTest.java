package com.frontendbase.api.report.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.frontendbase.api.common.exception.ApiException;
import com.frontendbase.api.report.controller.ReportExportController;
import com.frontendbase.api.report.dto.ReportDtos.LowStockRow;
import com.frontendbase.api.report.repository.ReportRepository;
import com.frontendbase.api.report.service.ReportExportService.*;

class ReportExportFailureTest {
    private Set<String> temporaryFiles() throws IOException {
        var result = new HashSet<String>();
        var temp = Path.of(System.getProperty("java.io.tmpdir"));
        for (Path directory : List.of(temp, temp.resolve("poifiles"))) {
            if (!Files.isDirectory(directory)) continue;
            try (var paths = Files.list(directory)) {
                paths.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().startsWith("report-export-")
                        || p.getFileName().toString().startsWith("poi-sxssf-sheet"))
                        .map(Object::toString).forEach(result::add);
            }
        }
        return result;
    }

    @Test void workbookFailureClosesTempResourcesAndPreservesBusinessErrors() throws Exception {
        var access = mock(ReportAccess.class); var repository = mock(ReportRepository.class);
        when(access.allowed(anyString())).thenReturn(true);
        when(access.scope(null)).thenReturn(new ReportAccess.Scope(null, null));
        when(repository.warehouseLabels(any())).thenReturn(List.of());
        var exports = new ReportExportService(repository, access);
        for (RuntimeException failure : List.of(new IllegalStateException("Unexpected construction failure"),
                new ApiException(HttpStatus.CONFLICT, "NUMERIC_OVERFLOW", "Overflow"))) {
            doAnswer(invocation -> {
                Consumer<LowStockRow> sink = invocation.getArgument(4);
                sink.accept(new LowStockRow(UUID.randomUUID(), "W", "Warehouse", UUID.randomUUID(), "P", "Product", "cay", 0, 1, 1));
                throw failure;
            }).when(repository).lowStock(any(), isNull(), eq(1), eq(500), any());
            var before = temporaryFiles();
            var result = assertThrows(ApiException.class, () -> exports.export(Kind.LOW_STOCK, null, null, null, null));
            if (failure instanceof ApiException) assertSame(failure, result);
            else { assertEquals("REPORT_EXPORT_FAILED", result.getCode()); assertEquals(500, result.getStatus().value()); }
            assertEquals(before, temporaryFiles());
        }
    }
    @Test void disconnectedClientStillDeletesCompletedFile() throws Exception {
        Path path = Files.createTempFile("report-export-", ".xlsx");
        Files.write(path, new byte[]{1, 2, 3});
        try {
            var service = mock(ReportExportService.class);
            when(service.export(Kind.LOW_STOCK, null, null, null, null)).thenReturn(new ExportFile(path, "ton-thap.xlsx"));
            var response = mock(HttpServletResponse.class);
            when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(WriteListener listener) {}
                @Override public void write(int b) throws IOException { throw new IOException("Client disconnected"); }
            });
            var controller = new ReportExportController(service);
            assertThrows(IOException.class, () -> controller.lowStock(null, null, Map.of(), response));
            assertFalse(Files.exists(path));
        } finally { Files.deleteIfExists(path); }
    }
}
