package com.ailoganalyzer.dto;

import java.time.OffsetDateTime;

/**
 * Hata fırtınası (anomali) tespiti: dakikalık ERROR zaman serisinde, dosyanın kendi ortalamasının
 * ve standart sapmasının çok üzerine çıkan bir sıçrama olup olmadığını taşır. Basit bir z-score
 * yaklaşımıyla, AI olmadan ve tamamen deterministik hesaplanır (bkz. StatsServiceImpl.detectErrorStorm).
 * Yeterli veri noktası yoksa veya seyir düzse (varyans yoksa) null olur — "kanıt zayıfsa iddiada
 * bulunma" ilkesiyle tutarlı; küçük/kısa loglarda yanlış alarm vermez.
 *
 * @param stormStartMinute     anomalinin ilk görüldüğü dakika
 * @param stormEndMinute       anomalinin en son görüldüğü dakika
 * @param peakErrorCount       anomali penceresindeki en yüksek dakikalık ERROR sayısı
 * @param baselineAverage      dosyanın tüm zaman serisindeki ortalama dakikalık ERROR sayısı
 * @param peakToBaselineRatio  tepe noktanın ortalamaya oranı (ör. 6.0 → "6 kat"); ortalama 0 ise null
 * @param anomalousMinuteCount anomali eşiğini aşan dakika sayısı
 */
public record ErrorStormInsight(
        OffsetDateTime stormStartMinute,
        OffsetDateTime stormEndMinute,
        long peakErrorCount,
        double baselineAverage,
        Double peakToBaselineRatio,
        int anomalousMinuteCount
) {
}
