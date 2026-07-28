package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.ai.ChatAiClient;
import com.ailoganalyzer.domain.Analysis;
import com.ailoganalyzer.domain.ChatMessage;
import com.ailoganalyzer.domain.ChatRole;
import com.ailoganalyzer.domain.LogEntry;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.LogLevel;
import com.ailoganalyzer.dto.ChatMessageResponse;
import com.ailoganalyzer.repository.AnalysisRepository;
import com.ailoganalyzer.repository.ChatMessageRepository;
import com.ailoganalyzer.repository.ErrorGroupRepository;
import com.ailoganalyzer.repository.LogEntryRepository;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatServiceImpl orkestrasyon testi (Mockito, DB/AI olmadan).
 * Doğrular: soru sorulunca hem kullanıcı mesajı hem asistan yanıtı kaydedilir; yanıt döner.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock private AnalysisRepository analysisRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ErrorGroupRepository errorGroupRepository;
    @Mock private LogEntryRepository logEntryRepository;
    @Mock private ChatAiClient chatAiClient;
    @Mock private com.ailoganalyzer.security.AccessControlService accessControl;   // requireAccess no-op (mock)

    @Test
    @DisplayName("Soru sorulunca kullanıcı + asistan mesajı kaydedilir ve yanıt döner")
    void askPersistsBothMessagesAndReturnsReply() {
        ChatServiceImpl service = new ChatServiceImpl(
                analysisRepository, chatMessageRepository, errorGroupRepository, logEntryRepository, chatAiClient, accessControl);

        UUID analysisId = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(UUID.randomUUID());
        Analysis analysis = new Analysis();
        analysis.setId(analysisId);
        analysis.setFile(file);
        analysis.setSummary("Özet");

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(chatMessageRepository.findByAnalysisIdOrderByCreatedAtAsc(analysisId)).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(file.getId())).thenReturn(List.of());
        when(logEntryRepository.findByFileIdAndLevelInOrderByLineNumberAsc(eq(file.getId()), any())).thenReturn(List.of());
        when(chatAiClient.chat(anyString(), anyList(), eq("DB hatası neden?"))).thenReturn("Bağlantı havuzu tükenmiş.");
        // save: id ata ve geri döndür (gerçek DB davranışını taklit)
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            if (m.getId() == null) m.setId(1L);
            return m;
        });

        ChatMessageResponse response = service.ask(analysisId, "DB hatası neden?");

        assertThat(response.role()).isEqualTo("ASSISTANT");
        assertThat(response.content()).isEqualTo("Bağlantı havuzu tükenmiş.");
        // İki kayıt: önce kullanıcı sorusu, sonra asistan yanıtı
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
        verify(chatAiClient).chat(anyString(), anyList(), eq("DB hatası neden?"));
        verify(accessControl).requireAccess(file);   // sahiplik denetimi çağrıldı
    }

    @Test
    @DisplayName("Bulunamayan analiz → ResourceNotFoundException, AI hiç çağrılmaz")
    void askNotFound() {
        ChatServiceImpl service = new ChatServiceImpl(
                analysisRepository, chatMessageRepository, errorGroupRepository, logEntryRepository, chatAiClient, accessControl);
        UUID analysisId = UUID.randomUUID();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ask(analysisId, "soru")).isInstanceOf(ResourceNotFoundException.class);
        verify(chatAiClient, never()).chat(anyString(), anyList(), anyString());
    }

    @Test
    @DisplayName("Başka kullanıcının analizine sohbet denemesi → 403, mesaj kaydedilmez, AI çağrılmaz")
    void askDeniedAccessNeverCallsAi() {
        ChatServiceImpl service = new ChatServiceImpl(
                analysisRepository, chatMessageRepository, errorGroupRepository, logEntryRepository, chatAiClient, accessControl);
        UUID analysisId = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(UUID.randomUUID());
        Analysis analysis = new Analysis();
        analysis.setId(analysisId);
        analysis.setFile(file);
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        doThrow(new AccessDeniedException("yetkisiz")).when(accessControl).requireAccess(file);

        assertThatThrownBy(() -> service.ask(analysisId, "soru")).isInstanceOf(AccessDeniedException.class);
        verify(chatMessageRepository, never()).save(any());
        verify(chatAiClient, never()).chat(anyString(), anyList(), anyString());
    }

    @Test
    @DisplayName("Kaydedilen kullanıcı mesajı doğru rol ve içerikle oluşturulur")
    void savesUserMessageWithCorrectRole() {
        ChatServiceImpl service = new ChatServiceImpl(
                analysisRepository, chatMessageRepository, errorGroupRepository, logEntryRepository, chatAiClient, accessControl);

        UUID analysisId = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(UUID.randomUUID());
        Analysis analysis = new Analysis();
        analysis.setId(analysisId);
        analysis.setFile(file);

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(chatMessageRepository.findByAnalysisIdOrderByCreatedAtAsc(analysisId)).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(file.getId())).thenReturn(List.of());
        when(logEntryRepository.findByFileIdAndLevelInOrderByLineNumberAsc(eq(file.getId()), any())).thenReturn(List.of());
        when(chatAiClient.chat(anyString(), anyList(), anyString())).thenReturn("yanıt");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        service.ask(analysisId, "soru");

        // İlk kaydedilen mesaj USER rolünde ve soru içeriğiyle olmalı
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        ChatMessage firstSaved = captor.getAllValues().get(0);
        assertThat(firstSaved.getRole()).isEqualTo(ChatRole.USER);
        assertThat(firstSaved.getContent()).isEqualTo("soru");
    }

    @Test
    @DisplayName("WARN+ kayıtlar sistem promptuna satır no + thread ile eklenir")
    void includesWarnPlusEntriesInSystemPrompt() {
        ChatServiceImpl service = new ChatServiceImpl(
                analysisRepository, chatMessageRepository, errorGroupRepository, logEntryRepository, chatAiClient, accessControl);

        UUID analysisId = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(UUID.randomUUID());
        Analysis analysis = new Analysis();
        analysis.setId(analysisId);
        analysis.setFile(file);

        LogEntry entry = new LogEntry();
        entry.setLineNumber(42);
        entry.setLevel(LogLevel.ERROR);
        entry.setThread("pool-1-thread-3");
        entry.setMessage("Bağlantı havuzu tükendi");
        entry.setExceptionType("SQLTransientConnectionException");

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(chatMessageRepository.findByAnalysisIdOrderByCreatedAtAsc(analysisId)).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(file.getId())).thenReturn(List.of());
        when(logEntryRepository.findByFileIdAndLevelInOrderByLineNumberAsc(eq(file.getId()), any()))
                .thenReturn(List.of(entry));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatAiClient.chat(anyString(), anyList(), anyString())).thenReturn("yanıt");

        service.ask(analysisId, "42. satırda ne oldu?");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatAiClient).chat(systemPromptCaptor.capture(), anyList(), anyString());
        String systemPrompt = systemPromptCaptor.getValue();
        assertThat(systemPrompt).contains("satır 42", "pool-1-thread-3", "Bağlantı havuzu tükendi",
                "SQLTransientConnectionException");
    }

    @Test
    @DisplayName("history(): bulunamayan analiz → ResourceNotFoundException")
    void historyNotFound() {
        ChatServiceImpl service = new ChatServiceImpl(
                analysisRepository, chatMessageRepository, errorGroupRepository, logEntryRepository, chatAiClient, accessControl);
        UUID analysisId = UUID.randomUUID();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(analysisId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("history(): başka kullanıcının analizine erişim → 403")
    void historyDeniedAccess() {
        ChatServiceImpl service = new ChatServiceImpl(
                analysisRepository, chatMessageRepository, errorGroupRepository, logEntryRepository, chatAiClient, accessControl);
        UUID analysisId = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(UUID.randomUUID());
        Analysis analysis = new Analysis();
        analysis.setId(analysisId);
        analysis.setFile(file);
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        doThrow(new AccessDeniedException("yetkisiz")).when(accessControl).requireAccess(file);

        assertThatThrownBy(() -> service.history(analysisId)).isInstanceOf(AccessDeniedException.class);
        verify(chatMessageRepository, never()).findByAnalysisIdOrderByCreatedAtAsc(any());
    }
}
