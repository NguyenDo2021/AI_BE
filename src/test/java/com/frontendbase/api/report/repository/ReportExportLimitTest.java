package com.frontendbase.api.report.repository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import java.util.function.Consumer;
import com.frontendbase.api.common.exception.ApiException;
import com.frontendbase.api.payment.repository.PaymentRepository;
import com.frontendbase.api.report.dto.ReportDtos.LowStockRow;
import com.frontendbase.api.report.service.ReportAccess.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ReportExportLimitTest {
    @Test void rejectsTooManyRowsBeforeReadingCursor() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(100001L);
        var repository = new ReportRepository(jdbc, mock(PaymentRepository.class));
        Consumer<LowStockRow> sink = mock(Consumer.class);
        var e = assertThrows(ApiException.class, () -> repository.lowStock(new Scope(null, UUID.randomUUID()), null, 1, 500, sink));
        assertEquals("EXPORT_TOO_LARGE", e.getCode()); assertEquals(409, e.getStatus().value());
        verifyNoInteractions(sink); verify(jdbc).queryForObject(anyString(), eq(Long.class), any(Object[].class)); verifyNoMoreInteractions(jdbc);
    }
}
