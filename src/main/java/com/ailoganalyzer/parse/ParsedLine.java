package com.ailoganalyzer.parse;

import com.ailoganalyzer.domain.LogLevel;

import java.time.OffsetDateTime;

/**
 * Parse edilmiş tek bir log kaydı — henüz veritabanı entity'si DEĞİL, saf bir veri nesnesi.
 * Bu ayrım önemlidir: parser katmanı JPA/persistence'tan habersizdir (Tek Sorumluluk + test edilebilirlik).
 * Servis katmanı bu record'ları LogEntry entity'lerine dönüştürüp kaydeder.
 *
 * @param timestamp     satırın zaman damgası (ayrıştırılamazsa null)
 * @param level         log seviyesi (tespit edilemezse null)
 * @param loggerClass   kaydı üreten sınıf/logger
 * @param thread        thread adı
 * @param message       log mesajı (çok satırlı mesajlarda birleştirilmiş)
 * @param exceptionType stack trace'ten çıkarılan istisna tipi (örn. NullPointerException)
 * @param stackTrace    birleştirilmiş çok satırlı stack trace (yoksa null)
 * @param lineNumber    orijinal dosyadaki 1-tabanlı satır numarası (kanıt için)
 */
public record ParsedLine(
        OffsetDateTime timestamp,
        LogLevel level,
        String loggerClass,
        String thread,
        String message,
        String exceptionType,
        String stackTrace,
        int lineNumber
) {
}
