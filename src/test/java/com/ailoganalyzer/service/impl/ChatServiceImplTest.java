package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.ai.ChatAiClient;
import com.ailoganalyzer.domain.Analysis;
import com.ailoganalyzer.domain.ChatMessage;
import com.ailoganalyzer.domain.ChatRole;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.dto.ChatMessageResponse;
import com.ailoganalyzer.repository.AnalysisRepository;
import com.ailoganalyzer.repository.ChatMessageRepository;
import com.ailoganalyzer.repository.ErrorGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private ChatAiClient chatAiClient;

    @Test
    @DisplayName("Soru sorulunca kullanıcı + asistan mesajı kaydedilir ve yanıt döner")
    void askPersistsBothMessagesAndReturnsReply() {
        ChatServiceImpl service = new ChatServiceImpl(
                analysisRepository, chatMessageRepository, errorGroupRepository, chatAiClient);

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
    }

    @Test
    @DisplayName("Kaydedilen kullanıcı mesajı doğru rol ve içerikle oluşturulur")
    void savesUserMessageWithCorrectRole() {
        ChatServiceImpl service = new ChatServiceImpl(
                analysisRepository, chatMessageRepository, errorGroupRepository, chatAiClient);

        UUID analysisId = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(UUID.randomUUID());
        Analysis analysis = new Analysis();
        analysis.setId(analysisId);
        analysis.setFile(file);

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(chatMessageRepository.findByAnalysisIdOrderByCreatedAtAsc(analysisId)).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(file.getId())).thenReturn(List.of());
        when(chatAiClient.chat(anyString(), anyList(), anyString())).thenReturn("yanıt");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        service.ask(analysisId, "soru");

        // İlk kaydedilen mesaj USER rolünde ve soru içeriğiyle olmalı
        org.mockito.ArgumentCaptor<ChatMessage> captor = org.mockito.ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        ChatMessage firstSaved = captor.getAllValues().get(0);
        assertThat(firstSaved.getRole()).isEqualTo(ChatRole.USER);
        assertThat(firstSaved.getContent()).isEqualTo("soru");
    }
}
