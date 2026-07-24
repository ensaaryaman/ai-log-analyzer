package com.ailoganalyzer.controller;

import com.ailoganalyzer.dto.ChatMessageResponse;
import com.ailoganalyzer.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ChatController web katmanı testi (@WebMvcTest → Docker/DB/AI gerekmez).
 * Sohbet ucunun HTTP davranışını ve boş-soru doğrulamasını (400) test eder.
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    @DisplayName("Geçerli soru → 200 ve asistan yanıtı döner")
    void validQuestionReturnsReply() throws Exception {
        UUID analysisId = UUID.randomUUID();
        when(chatService.ask(eq(analysisId), eq("Neden hata var?")))
                .thenReturn(new ChatMessageResponse(1L, "ASSISTANT", "Bağlantı sorunu.", OffsetDateTime.now()));

        mockMvc.perform(post("/api/analyses/{id}/chat", analysisId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Neden hata var?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.content").value("Bağlantı sorunu."));
    }

    @Test
    @DisplayName("Boş soru → 400 ProblemDetail (doğrulama)")
    void blankQuestionReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/analyses/{id}/chat", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Geçersiz istek"));
    }
}
