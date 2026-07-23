package com.ailoganalyzer.parse;

import com.ailoganalyzer.domain.LogLevel;

/**
 * Bir log satırının "başlık" kısmından (yani yeni bir kaydı başlatan satırdan) çıkarılan alanlar.
 * Zaman damgası burada ham metin olarak taşınır; OffsetDateTime'a çevirme işini
 * {@link TimestampParser} yapar (Tek Sorumluluk: desen eşleme ≠ tarih ayrıştırma).
 */
public record HeaderFields(
        String rawTimestamp,
        LogLevel level,
        String thread,
        String logger,
        String message
) {
}
