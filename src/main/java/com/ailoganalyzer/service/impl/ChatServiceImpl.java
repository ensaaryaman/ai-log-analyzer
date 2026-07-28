package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.ai.ChatAiClient;
import com.ailoganalyzer.ai.ChatTurn;
import com.ailoganalyzer.domain.Analysis;
import com.ailoganalyzer.domain.ChatMessage;
import com.ailoganalyzer.domain.ChatRole;
import com.ailoganalyzer.domain.ErrorGroup;
import com.ailoganalyzer.domain.LogEntry;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.LogLevel;
import com.ailoganalyzer.dto.ChatMessageResponse;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import com.ailoganalyzer.repository.AnalysisRepository;
import com.ailoganalyzer.repository.ChatMessageRepository;
import com.ailoganalyzer.repository.ErrorGroupRepository;
import com.ailoganalyzer.repository.LogEntryRepository;
import com.ailoganalyzer.security.AccessControlService;
import com.ailoganalyzer.service.ChatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * ChatService uygulaması. Her soruda: analizi + damıtılmış bağlamı sistem promptuna koyar,
 * önceki konuşmayı modele bağlam olarak verir, soruyu ve yanıtı veritabanına kaydeder.
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final int MAX_GROUPS = 5;    // Sistem promptuna en fazla 5 hata grubu koy (token bütçesi)
    private static final int MAX_ENTRIES = 40;  // WARN+ kayıtlardan en fazla 40 tanesi (token bütçesi)
    private static final int MAX_MESSAGE_LEN = 200;   // Tek bir kaydın mesajı bu uzunlukta kesilir
    private static final EnumSet<LogLevel> WARN_AND_ABOVE = EnumSet.of(LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL);

    private final AnalysisRepository analysisRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ErrorGroupRepository errorGroupRepository;
    private final LogEntryRepository logEntryRepository;   // WARN+ kayıtları sohbet bağlamına eklemek için
    private final ChatAiClient chatAiClient;   // Gerçek veya mock (profile göre)
    private final AccessControlService accessControl;   // Sahiplik (yetkilendirme) kontrolü

    public ChatServiceImpl(AnalysisRepository analysisRepository,
                           ChatMessageRepository chatMessageRepository,
                           ErrorGroupRepository errorGroupRepository,
                           LogEntryRepository logEntryRepository,
                           ChatAiClient chatAiClient,
                           AccessControlService accessControl) {
        this.analysisRepository = analysisRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.errorGroupRepository = errorGroupRepository;
        this.logEntryRepository = logEntryRepository;
        this.chatAiClient = chatAiClient;
        this.accessControl = accessControl;
    }

    // Soru sorma: DB'ye yazdığı için okuma-yazma transaction'ı
    @Override
    @Transactional
    public ChatMessageResponse ask(UUID analysisId, String question) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analiz", analysisId));
        accessControl.requireAccess(analysis.getFile());   // Yalnızca dosyanın sahibi (veya ADMIN) sohbet edebilir

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
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analiz", analysisId));
        accessControl.requireAccess(analysis.getFile());   // Sohbet geçmişi de sahiplik denetimine tabidir
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

    // Modelin dayanacağı sistem promptunu dosya meta verisi + analiz + damıtılmış hata gruplarından kurar
    private String buildSystemPrompt(Analysis analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                Sen bir log analiz asistanısın. Aşağıda bir log dosyasının DOSYA BİLGİSİ, YAPAY ZEKA
                ANALİZİ, damıtılmış HATA GRUPLARI ve WARN+ seviyesindeki gerçek KAYITLAR (satır no,
                thread, mesaj) var. Kullanıcı belirli bir satırı/thread'i sorarsa KAYITLAR bölümüne bak.
                Kullanıcının sorularını YALNIZCA bu bilgilere dayanarak, kısa ve Türkçe yanıtla.
                Bilmediğin bir şeyi uydurma; bağlamda yoksa belirt.

                """);
        sb.append(fileInfo(analysis.getFile()));
        sb.append("\nANALİZ:\n");
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

        sb.append(entriesSection(analysis.getFile().getId()));
        return sb.toString();
    }

    // WARN+ kayıtları (satır no, seviye, thread, mesaj) metne döker — modelin belirli satır/thread'lere
    // referans verebilmesi için. Ham log ASLA gönderilmez; INFO/DEBUG dışarıda bırakılır (distillation ilkesi).
    private String entriesSection(UUID fileId) {
        List<LogEntry> entries = logEntryRepository
                .findByFileIdAndLevelInOrderByLineNumberAsc(fileId, WARN_AND_ABOVE);
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\nWARN+ KAYITLAR (satır sırasına göre");
        if (entries.size() > MAX_ENTRIES) {
            sb.append(", ilk ").append(MAX_ENTRIES).append("/").append(entries.size()).append(" gösteriliyor");
        }
        sb.append("):\n");
        entries.stream().limit(MAX_ENTRIES).forEach(e -> {
            sb.append("- [satır ").append(e.getLineNumber()).append("] ")
              .append(e.getLevel() == null ? "?" : e.getLevel().name());
            if (e.getThread() != null) {
                sb.append(" (thread: ").append(e.getThread()).append(")");
            }
            sb.append(": ").append(truncate(e.getMessage()));
            if (e.getExceptionType() != null) {
                sb.append(" [").append(e.getExceptionType()).append("]");
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    // Uzun mesajları kısaltır (token bütçesi) — tam metin zaten Kayıtlar sekmesinde/kanıt satırlarında mevcut
    private String truncate(String s) {
        if (s == null) return "-";
        return s.length() > MAX_MESSAGE_LEN ? s.substring(0, MAX_MESSAGE_LEN) + "…" : s;
    }

    // Dosyanın parse/format meta verisini metne döker (ör. "neden format UNKNOWN oldu" gibi
    // sorulara cevap verebilmek için — bu bilgi eskiden yalnızca analiz sonucunda vardı, sohbete hiç girmiyordu).
    private String fileInfo(LogFile file) {
        StringBuilder sb = new StringBuilder("DOSYA BİLGİSİ:\n");
        sb.append("Dosya adı: ").append(nz(file.getFilename())).append("\n");
        sb.append("Algılanan format: ")
          .append(file.getDetectedFormat() == null ? "UNKNOWN" : file.getDetectedFormat().name())
          .append("\n");
        sb.append("Toplam satır: ").append(file.getLineCount())
          .append(" | ERROR: ").append(file.getErrorCount())
          .append(" | WARN: ").append(file.getWarnCount())
          .append(" | Parse edilemeyen satır: ").append(file.getParseErrorCount()).append("\n");
        sb.append("Dosya boyutu: ").append(file.getSizeBytes()).append(" bayt\n");
        if (file.getFirstTs() != null && file.getLastTs() != null) {
            sb.append("Zaman aralığı: ").append(file.getFirstTs()).append(" — ").append(file.getLastTs()).append("\n");
        }
        sb.append("Durum: ").append(file.getStatus() == null ? "-" : file.getStatus().name()).append("\n");
        return sb.toString();
    }

    private String nz(String s) {
        return s == null ? "-" : s;
    }
}
