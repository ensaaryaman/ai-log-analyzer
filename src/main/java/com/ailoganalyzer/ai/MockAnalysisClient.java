package com.ailoganalyzer.ai;

import com.ailoganalyzer.domain.Priority;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SAHTE yapay zeka istemcisi. Gerçek bir API çağrısı YAPMAZ; deterministik, mantıklı bir yanıt döner.
 *
 * @Profile("mock"): yalnızca 'mock' profili aktifken devreye girer.
 * Faydası: API anahtarı olmadan demo/geliştirme; testlerde belirlilik; kota bitince hayat kurtarır.
 */
@Component
@Profile("mock")
public class MockAnalysisClient implements AnalysisAiClient {

    // Gerçek çağrı yerine, prompt içeriğine göre makul bir sahte analiz üretir
    @Override
    public AiAnalysisOutcome analyze(AnalysisPrompt prompt) {
        boolean hasError = prompt.user().contains("ERROR")
                && !prompt.user().contains("0 ERROR");   // Kaba bir sinyal: logda hata var mı?

        AnalysisResult result = new AnalysisResult(
                "[MOCK] Log dosyasında " + (hasError ? "hata kayıtları tespit edildi." : "belirgin hata görünmüyor."),
                "[MOCK] Bu sahte bir analizdir; gerçek kök neden için 'default' profille (API anahtarıyla) çalıştırın.",
                "[MOCK] Örnek çözüm: ilgili hata gruplarını inceleyin, en sık tekrarlayandan başlayın.",
                hasError ? Priority.HIGH : Priority.LOW,
                0.5,                                     // Sahte olduğu için orta güven
                List.of());                              // Kanıt satırı yok

        // Ham yanıt alanı için geçerli bir JSON dizesi (mock olduğunu belli eder)
        String raw = "{\"mock\":true,\"note\":\"MockAnalysisClient tarafindan uretildi\"}";
        return new AiAnalysisOutcome(result, "mock", 0, 0, raw);
    }
}
