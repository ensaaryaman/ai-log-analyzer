package com.ailoganalyzer.ai;

/**
 * Yapay zeka sağlayıcısının soyutlaması (SOLID/DIP).
 * Üst katman (AnalysisService) bu arayüze bağlıdır; arkasında GERÇEK model (Gemini/OpenAI)
 * veya MOCK (sahte yanıt) olabilir. Sağlayıcı/mock seçimi Spring profilleriyle yapılır.
 */
public interface AnalysisAiClient {

    // Verilen prompt'u modele gönderir ve yapılandırılmış sonucu + meta veriyi döner
    AiAnalysisOutcome analyze(AnalysisPrompt prompt);
}
