package com.ailoganalyzer.ai;

import com.ailoganalyzer.exception.AiAnalysisException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * GERÇEK yapay zeka istemcisi. Spring AI ChatClient ile modeli (Gemini/OpenAI) çağırır ve
 * yanıtı doğrudan AnalysisResult record'una bağlar (structured output).
 *
 * @Profile("!mock"): 'mock' profili AKTİF DEĞİLKEN kullanılır (yani gerçek AI çağrısı yapılır).
 */
@Component
@Profile("!mock")                    // mock profili kapalıyken (varsayılan) bu gerçek istemci devreye girer
public class SpringAiAnalysisClient implements AnalysisAiClient {

    private final ChatClient chatClient;

    // Spring AI, otomatik yapılandırmayla bir ChatClient.Builder sağlar (model + API anahtarı config'ten)
    public SpringAiAnalysisClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    // Prompt'u gönderir; .responseEntity ile hem yapılandırılmış sonucu hem ham yanıtı/token'ları alır
    @Override
    public AiAnalysisOutcome analyze(AnalysisPrompt prompt) {
        try {
            var responseEntity = chatClient.prompt()
                    .system(prompt.system())
                    .user(prompt.user())
                    .call()
                    .responseEntity(AnalysisResult.class);   // Modele JSON şeması verir, yanıtı record'a bağlar

            AnalysisResult result = responseEntity.entity();
            ChatResponse response = responseEntity.response();

            return new AiAnalysisOutcome(
                    result,
                    modelName(response),
                    promptTokens(response),
                    completionTokens(response),
                    rawText(response));
        } catch (Exception e) {
            // API/parse hataları tek yerde sarmalanır → controller anlamlı bir 502 döner
            throw new AiAnalysisException("Yapay zeka analizi başarısız: " + e.getMessage(), e);
        }
    }

    // --- Meta veriyi güvenli (null-korumalı) biçimde okuyan yardımcılar ---

    private String modelName(ChatResponse r) {
        return r.getMetadata() != null ? r.getMetadata().getModel() : null;
    }

    private Integer promptTokens(ChatResponse r) {
        return hasUsage(r) ? r.getMetadata().getUsage().getPromptTokens() : null;
    }

    private Integer completionTokens(ChatResponse r) {
        return hasUsage(r) ? r.getMetadata().getUsage().getCompletionTokens() : null;
    }

    private boolean hasUsage(ChatResponse r) {
        return r.getMetadata() != null && r.getMetadata().getUsage() != null;
    }

    // Modelin döndürdüğü ham metin (debug için saklanır)
    private String rawText(ChatResponse r) {
        return (r.getResult() != null && r.getResult().getOutput() != null)
                ? r.getResult().getOutput().getText() : null;
    }
}
