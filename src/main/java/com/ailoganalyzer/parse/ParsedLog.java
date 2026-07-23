package com.ailoganalyzer.parse;

import com.ailoganalyzer.domain.LogFormat;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bir log dosyasının tümünün parse sonucu (saf veri nesnesi).
 * Sayaçlar ve zaman aralığı, parser tarafından bir kez hesaplanıp burada taşınır;
 * böylece servis/DTO katmanları bunları tekrar hesaplamak zorunda kalmaz.
 *
 * @param format          tespit edilen format
 * @param lines           parse edilmiş kayıtlar
 * @param errorCount      ERROR + FATAL sayısı
 * @param warnCount       WARN sayısı
 * @param parseErrorCount hiçbir kayda bağlanamayan (ayrıştırılamayan) satır sayısı — dürüst raporlama için
 * @param firstTimestamp  logdaki en erken zaman damgası (null olabilir)
 * @param lastTimestamp   logdaki en geç zaman damgası (null olabilir)
 */
public record ParsedLog(
        LogFormat format,
        List<ParsedLine> lines,
        int errorCount,
        int warnCount,
        int parseErrorCount,
        OffsetDateTime firstTimestamp,
        OffsetDateTime lastTimestamp
) {
}
