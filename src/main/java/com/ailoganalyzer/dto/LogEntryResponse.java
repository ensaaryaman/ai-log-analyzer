package com.ailoganalyzer.dto;

import com.ailoganalyzer.domain.LogEntry;

import java.time.OffsetDateTime;

/**
 * Parse edilmiş tek bir log kaydının istemciye dönen görünümü (DTO).
 * lineNumber, kullanıcı arayüzünde "kanıt" satırına gitmeyi mümkün kılar.
 */
public record LogEntryResponse(
        Long id,
        OffsetDateTime ts,
        String level,
        String loggerClass,
        String thread,
        String message,
        String exceptionType,
        boolean hasStackTrace,     // Liste hafif kalsın diye tam stack trace yerine sadece "var mı?" bilgisi
        int lineNumber
) {

    // Entity → DTO dönüşümü
    public static LogEntryResponse from(LogEntry e) {
        return new LogEntryResponse(
                e.getId(),
                e.getTs(),
                e.getLevel() == null ? null : e.getLevel().name(),
                e.getLoggerClass(),
                e.getThread(),
                e.getMessage(),
                e.getExceptionType(),
                e.getStackTrace() != null && !e.getStackTrace().isBlank(),
                e.getLineNumber() == null ? 0 : e.getLineNumber()
        );
    }
}
