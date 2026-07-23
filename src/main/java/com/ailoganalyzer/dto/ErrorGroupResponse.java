package com.ailoganalyzer.dto;

import com.ailoganalyzer.domain.ErrorGroup;

import java.time.OffsetDateTime;

/**
 * Bir hata grubunun istemciye dönen görünümü (tekrarlanan hata).
 * occurrenceCount ile "aynı hata N kez" bilgisini; first/last seen ile zaman aralığını taşır.
 */
public record ErrorGroupResponse(
        String fingerprint,
        String exceptionType,
        String sampleMessage,
        int occurrenceCount,
        OffsetDateTime firstSeen,
        OffsetDateTime lastSeen,
        Integer sampleLineNumber   // Temsilci kaydın orijinal satır numarası (kanıta gitmek için)
) {

    // Entity → DTO dönüşümü
    public static ErrorGroupResponse from(ErrorGroup g) {
        return new ErrorGroupResponse(
                g.getFingerprint(),
                g.getExceptionType(),
                g.getSampleMessage(),
                g.getOccurrenceCount(),
                g.getFirstSeen(),
                g.getLastSeen(),
                g.getSampleEntry() == null ? null : g.getSampleEntry().getLineNumber()
        );
    }
}
