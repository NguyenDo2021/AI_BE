package com.frontendbase.api.report.service;

import java.sql.Timestamp;
import java.time.*;

import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public record ReportPeriod(LocalDate from, LocalDate to) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static ReportPeriod parse(String from, String to) {
        try {
            if (from == null || to == null || !from.matches("\\d{4}-\\d{2}-\\d{2}")
                    || !to.matches("\\d{4}-\\d{2}-\\d{2}"))
                throw new IllegalArgumentException();
            var period = new ReportPeriod(LocalDate.parse(from), LocalDate.parse(to));
            if (period.from.isAfter(period.to))
                throw new IllegalArgumentException();
            period.end();
            return period;
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "from/to must be YYYY-MM-DD and from must not exceed to");
        }
    }

    public Timestamp start() {
        return Timestamp.from(from.atStartOfDay(ZONE).toInstant());
    }

    public Timestamp end() {
        return Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant());
    }

    public com.frontendbase.api.report.dto.ReportDtos.Period response() {
        return new com.frontendbase.api.report.dto.ReportDtos.Period(from, to, ZONE.getId());
    }
}
