package com.ailoganalyzer.ai;

import java.time.OffsetDateTime;

/**
 * Bir hata grubunun prompt'a girecek "damıtılmış" özeti.
 * Ham log yerine bu özet gönderilir → token tasarrufu + modele önemli olanı önden söyleme.
 *
 * @param exceptionType      istisna tipi
 * @param sampleMessage      temsilci mesaj
 * @param occurrenceCount    kaç kez tekrarlandığı
 * @param firstSeen/lastSeen zaman aralığı
 * @param sampleLineNumber   temsilci kaydın orijinal satır numarası (kanıt için)
 * @param stackTraceExcerpt  stack trace'in ilk birkaç satırı (kısaltılmış)
 */
public record ErrorGroupDigest(
        String exceptionType,
        String sampleMessage,
        int occurrenceCount,
        OffsetDateTime firstSeen,
        OffsetDateTime lastSeen,
        Integer sampleLineNumber,
        String stackTraceExcerpt
) {
}
