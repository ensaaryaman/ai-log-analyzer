package com.ailoganalyzer.service;

import com.ailoganalyzer.domain.LogFile;

/**
 * Ham log içeriğini parse edip sonuçları (log_entry kayıtları + log_file özeti) veritabanına yazar.
 * Saf {@link com.ailoganalyzer.parse.LogParser} (algoritma) ile persistence'ı ayırır (Tek Sorumluluk):
 * parser test edilebilir kalır, bu servis ise DB entegrasyonundan sorumludur.
 */
public interface LogParsingService {

    /**
     * Verilen dosyanın içeriğini parse eder, kayıtları saklar ve dosyanın özet alanlarını
     * (format, hata/uyarı sayıları, zaman aralığı, durum=PARSED) günceller.
     *
     * @param file    daha önce kaydedilmiş (yönetilen) LogFile entity'si
     * @param content dosyanın ham metin içeriği
     */
    void parseAndPersist(LogFile file, String content);
}
