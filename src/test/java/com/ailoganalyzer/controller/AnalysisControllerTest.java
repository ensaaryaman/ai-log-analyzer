package com.ailoganalyzer.controller;

import com.ailoganalyzer.dto.AnalysisResponse;
import com.ailoganalyzer.service.AnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AnalysisController web katmanı testi (@WebMvcTest → Docker/DB/AI gerekmez).
 * Analiz uç noktasının HTTP davranışını doğrular; servis mock'lanır.
 */
@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    @Test
    @DisplayName("POST /api/logs/{id}/analyze → 200 ve analiz özeti döner")
    void analyzeReturnsResult() throws Exception {
        UUID fileId = UUID.randomUUID();
        AnalysisResponse resp = new AnalysisResponse(
                UUID.randomUUID(), fileId, "gemini-2.5-flash", "v1",
                "Özet", "Kök neden", "Çözüm", "HIGH", new BigDecimal("0.85"),
                List.of(1042), 120, 45, 900, OffsetDateTime.now());
        when(analysisService.analyze(eq(fileId))).thenReturn(resp);

        mockMvc.perform(post("/api/logs/{id}/analyze", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.summary").value("Özet"))
                .andExpect(jsonPath("$.model").value("gemini-2.5-flash"));
    }
}
