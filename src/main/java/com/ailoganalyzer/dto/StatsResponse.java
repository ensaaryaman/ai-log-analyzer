package com.ailoganalyzer.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Bir log dosyasının damıtılmış istatistikleri (dashboard ve AI prompt'u için tek pakette).
 *
 * @param fileId            dosya kimliği
 * @param detectedFormat    tespit edilen format
 * @param totalEntries      toplam parse edilmiş kayıt sayısı
 * @param levelDistribution seviye → adet (ör. {"ERROR":2, "WARN":1, "INFO":2})
 * @param topExceptions     en sık görülen istisna tipleri
 * @param errorGroups       tekrarlanan hata grupları (en çok tekrarlanandan aza)
 * @param problemTimeline   dakikalık WARN/ERROR zaman serisi
 * @param firstTs           logdaki en erken zaman
 * @param lastTs            logdaki en geç zaman
 */
public record StatsResponse(
        java.util.UUID fileId,
        String detectedFormat,
        long totalEntries,
        Map<String, Long> levelDistribution,
        List<ExceptionStat> topExceptions,
        List<ErrorGroupResponse> errorGroups,
        List<TimeBucket> problemTimeline,
        OffsetDateTime firstTs,
        OffsetDateTime lastTs
) {
}
