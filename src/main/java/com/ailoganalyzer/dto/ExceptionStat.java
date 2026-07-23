package com.ailoganalyzer.dto;

/**
 * Bir istisna tipinin toplam görülme sayısı (exception istatistikleri için).
 * Örn. {"type": "SQLTransientConnectionException", "count": 31}
 */
public record ExceptionStat(
        String type,
        long count
) {
}
