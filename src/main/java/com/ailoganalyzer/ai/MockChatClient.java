package com.ailoganalyzer.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SAHTE sohbet istemcisi. Gerçek API çağrısı yapmaz; deterministik bir yanıt döner.
 * API anahtarı olmadan demo/test için; @Profile("mock") aktifken devreye girer.
 */
@Component
@Profile("mock")
public class MockChatClient implements ChatAiClient {

    @Override
    public String chat(String systemPrompt, List<ChatTurn> history, String question) {
        return "[MOCK] Sorunuz alındı: \"" + question + "\". Bu sahte bir yanıttır; "
                + "gerçek sohbet için 'default' profille (API anahtarıyla) çalıştırın. "
                + "(Geçmişte " + history.size() + " mesaj var.)";
    }
}
