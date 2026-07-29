package com.ailoganalyzer.ai;

import com.ailoganalyzer.dto.ErrorStormInsight;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Prompt kurmak için gereken tüm damıtılmış bağlam (tek pakette).
 * AnalysisService bunu doldurur, AnalysisPromptBuilder de bundan metni üretir.
 */
public record PromptContext(
        String filename,
        String format,
        long totalEntries,
        long errorCount,
        long warnCount,
        OffsetDateTime firstTs,
        OffsetDateTime lastTs,
        Map<String, Long> levelDistribution,
        List<ErrorGroupDigest> errorGroups,
        ErrorStormInsight errorStorm   // İstatistiksel hata sıçraması varsa (yoksa null); modele ek bağlam
) {
}
