package com.ailoganalyzer.ai;

import java.util.List;

/**
 * Log ile sohbet için yapay zeka soyutlaması (SOLID/DIP).
 * Analiz istemcisinden (AnalysisAiClient) ayrıdır çünkü sohbet SERBEST METİN yanıt üretir,
 * analiz ise yapılandırılmış (JSON) çıktı. Gerçek/mock seçimi Spring profilleriyle yapılır.
 */
public interface ChatAiClient {

    /**
     * Sistem talimatı + önceki konuşma + yeni soruyu modele gönderir, asistan yanıtını döner.
     *
     * @param systemPrompt rol + log bağlamı (modelin neye dayanacağı)
     * @param history      önceki mesajlar (kronolojik)
     * @param question     kullanıcının yeni sorusu
     * @return asistanın metin yanıtı
     */
    String chat(String systemPrompt, List<ChatTurn> history, String question);
}
