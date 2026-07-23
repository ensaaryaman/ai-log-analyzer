package com.ailoganalyzer.ai;

/**
 * AI çağrısının sonucu: yapılandırılmış analiz + meta veriler (model, token, ham yanıt).
 * Token/ham yanıt LLMOps (maliyet takibi, hata ayıklama) için saklanır.
 */
public record AiAnalysisOutcome(
        AnalysisResult result,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        String rawResponse
) {
}
