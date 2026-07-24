package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.ai.ChatAiClient;
import com.ailoganalyzer.ai.ChatTurn;
import com.ailoganalyzer.domain.Analysis;
import com.ailoganalyzer.domain.ChatMessage;
import com.ailoganalyzer.domain.ChatRole;
import com.ailoganalyzer.domain.ErrorGroup;
import com.ailoganalyzer.dto.ChatMessageResponse;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import com.ailoganalyzer.repository.AnalysisRepository;
import com.ailoganalyzer.repository.ChatMessageRepository;
import com.ailoganalyzer.repository.ErrorGroupRepository;
import com.ailoganalyzer.service.ChatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ChatService uygulaması. Her soruda: analizi + damıtılmış bağlamı sistem promptuna koyar,
 * önceki konuşmayı modele bağlam olarak verir, soruyu ve yanıtı veritabanına kaydeder.
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final int MAX_GROUPS = 5;   // Sistem promptuna en fazla 5 hata grubu koy (token bütçesi)

    private final AnalysisRepository analysisRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ErrorGroupRepository errorGroupRepository;
    private final ChatAiClient chatAiClient;   // Gerçek veya mock (profile göre)

    public ChatServiceImpl(AnalysisRepository analysisRepository,
                           ChatMessageRepository chatMessageRepository,
                           ErrorGroupRepository errorGroupRepository,
                           ChatAiClient chatAiClient) {
        this.analysisRepository = analysisRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.errorGroupRepository = errorGroupRepository;
        this.chatAiClient = chatAiClient;
    }

    // Soru sorma: DB'ye yazdığı için okuma-yazma transaction'ı
    @Override
    @Transactional
    public ChatMessageResponse ask(UUID analysisId, String question) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analiz", analysisId));

        String systemPrompt = buildSystemPrompt(analysis);

        // Önceki konuşmayı (bu sorudan ÖNCEKİ mesajlar) modele bağlam olarak hazırla
        List<ChatTurn> history = chatMessageRepository.findByAnalysisIdOrderByCreatedAtAsc(analysisId)
                .stream()
                .map(m -> new ChatTurn(m.getRole() == ChatRole.USER, m.getContent()))
                .toList();

        // Kullanıcı sorusunu kaydet
        save(analysis, ChatRole.USER, question);

        // Modeli çağır ve yanıtı kaydet
        String reply = chatAiClient.chat(systemPrompt, history, question);
        ChatMessage assistantMessage = save(analysis, ChatRole.ASSISTANT, reply);

        return ChatMessageResponse.from(assistantMessage);
    }

    // Sohbet geçmişi (salt okuma)
    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(UUID analysisId) {
        if (!analysisRepository.existsById(analysisId)) {
            throw new ResourceNotFoundException("Analiz", analysisId);
        }
        return chatMessageRepository.findByAnalysisIdOrderByCreatedAtAsc(analysisId)
                .stream().map(ChatMessageResponse::from).toList();
    }

    // --- Yardımcılar ---

    // Bir mesajı (rol + içerik) kaydeder ve kaydedilen entity'yi döner
    private ChatMessage save(Analysis analysis, ChatRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setAnalysis(analysis);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        return chatMessageRepository.save(message);
    }

    // Modelin dayanacağı sistem promptunu analiz + damıtılmış hata gruplarından kurar
    private String buildSystemPrompt(Analysis analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                Sen bir log analiz asistanısın. Aşağıda bir log dosyasının YAPAY ZEKA ANALİZİ ve
                damıtılmış hata bilgileri var. Kullanıcının sorularını YALNIZCA bu bilgilere dayanarak,
                kısa ve Türkçe yanıtla. Bilmediğin bir şeyi uydurma; bağlamda yoksa belirt.

                ANALİZ:
                """);
        sb.append("Özet: ").append(nz(analysis.getSummary())).append("\n");
        sb.append("Kök neden: ").append(nz(analysis.getRootCause())).append("\n");
        sb.append("Çözüm: ").append(nz(analysis.getSolution())).append("\n");
        if (analysis.getPriority() != null) {
            sb.append("Öncelik: ").append(analysis.getPriority().name()).append("\n");
        }

        List<ErrorGroup> groups = errorGroupRepository
                .findByFileIdOrderByOccurrenceCountDesc(analysis.getFile().getId());
        if (!groups.isEmpty()) {
            sb.append("\nHATA GRUPLARI:\n");
            groups.stream().limit(MAX_GROUPS).forEach(g -> sb.append("- ")
                    .append(g.getExceptionType() == null ? "(istisna yok)" : g.getExceptionType())
                    .append(" ×").append(g.getOccurrenceCount())
                    .append(": ").append(nz(g.getSampleMessage())).append("\n"));
        }
        return sb.toString();
    }

    private String nz(String s) {
        return s == null ? "-" : s;
    }
}
