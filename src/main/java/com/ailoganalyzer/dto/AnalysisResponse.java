package com.ailoganalyzer.dto;

import com.ailoganalyzer.domain.Analysis;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bir analiz sonucunun istemciye dönen görünümü (DTO).
 * Token/süre gibi LLMOps meta verileri de gösterilir (şeffaflık).
 */
public record AnalysisResponse(
        UUID id,
        UUID fileId,
        String model,
        String promptVersion,
        String summary,
        String rootCause,
        String solution,
        String priority,
        BigDecimal confidence,
        List<Integer> evidenceLines,
        Integer promptTokens,
        Integer completionTokens,
        Integer durationMs,
        OffsetDateTime createdAt
) {

    // Entity → DTO dönüşümü
    public static AnalysisResponse from(Analysis a) {
        return new AnalysisResponse(
                a.getId(),
                a.getFile().getId(),
                a.getModel(),
                a.getPromptVersion(),
                a.getSummary(),
                a.getRootCause(),
                a.getSolution(),
                a.getPriority() == null ? null : a.getPriority().name(),
                a.getConfidence(),
                a.getEvidenceLines(),
                a.getPromptTokens(),
                a.getCompletionTokens(),
                a.getDurationMs(),
                a.getCreatedAt()
        );
    }
}
