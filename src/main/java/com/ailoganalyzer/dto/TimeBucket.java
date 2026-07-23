package com.ailoganalyzer.dto;

import java.time.OffsetDateTime;

/**
 * Zaman serisinde bir dakikalık kova (bucket): o dakikadaki WARN ve ERROR sayıları.
 * Gün 6'daki dashboard zaman grafiğinin ve WARN→ERROR geçiş analizinin veri kaynağıdır.
 */
public record TimeBucket(
        OffsetDateTime minute,
        long warnCount,
        long errorCount
) {
}
