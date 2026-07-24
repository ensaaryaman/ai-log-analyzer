package com.ailoganalyzer.service;

import com.ailoganalyzer.dto.ChatMessageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Bir analiz bağlamında log ile sohbeti yönetir: soruyu bağlamla birlikte modele gönderir,
 * hem soruyu hem yanıtı chat_message tablosunda saklar.
 */
public interface ChatService {

    // Verilen analiz için soru sorar; asistan yanıtını döner (soru ve yanıt kaydedilir)
    ChatMessageResponse ask(UUID analysisId, String question);

    // Bir analizin sohbet geçmişini (kronolojik) döner
    List<ChatMessageResponse> history(UUID analysisId);
}
