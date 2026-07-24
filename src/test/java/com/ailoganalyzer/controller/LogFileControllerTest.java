package com.ailoganalyzer.controller;

import com.ailoganalyzer.dto.LogFileSummaryResponse;
import com.ailoganalyzer.service.LogFileService;
import com.ailoganalyzer.service.StatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LogFileController için web katmanı testi.
 * @WebMvcTest yalnızca web katmanını yükler (servis mock'lanır) → Docker/DB GEREKMEZ, hızlı çalışır.
 * Amaç: HTTP davranışını doğrulamak (multipart yükleme, durum kodları, hata eşlemesi).
 */
@WebMvcTest(LogFileController.class)   // Sadece bu controller + web altyapısı + istisna yakalayıcı yüklenir
class LogFileControllerTest {

    @Autowired
    private MockMvc mockMvc;            // Gerçek sunucu başlatmadan HTTP istekleri simüle eder

    @MockitoBean                        // LogFileService'in sahte (mock) örneğini bağlama koyar (gerçek DB'ye gitmez)
    private LogFileService logFileService;

    @MockitoBean                        // Controller artık StatsService'e de bağlı → onu da mock'la (bağlam kurulabilsin)
    private StatsService statsService;

    @Test
    @DisplayName("Geçerli dosya yüklenince 201 Created ve özet döner")
    void uploadReturnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        LogFileSummaryResponse summary = new LogFileSummaryResponse(
                id, "app.log", "SPRING_BOOT", 120L, 6, 2, 1, 0, null, null, "PARSED", OffsetDateTime.now());
        when(logFileService.ingest(eq("app.log"), any())).thenReturn(summary);

        MockMultipartFile file = new MockMultipartFile(
                "file", "app.log", "text/plain", "2026-07-20 ERROR test".getBytes());

        mockMvc.perform(multipart("/api/logs").file(file))
                .andExpect(status().isCreated())                       // 201
                .andExpect(jsonPath("$.filename").value("app.log"))
                .andExpect(jsonPath("$.detectedFormat").value("SPRING_BOOT"))
                .andExpect(jsonPath("$.errorCount").value(2));
    }

    @Test
    @DisplayName("Boş dosya yüklenince 400 ProblemDetail döner")
    void uploadEmptyFileReturnsBadRequest() throws Exception {
        // Boş içerik → controller InvalidFileException fırlatır → GlobalExceptionHandler 400'e çevirir
        MockMultipartFile empty = new MockMultipartFile(
                "file", "bos.log", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/logs").file(empty))
                .andExpect(status().isBadRequest())                   // 400
                .andExpect(jsonPath("$.title").value("Geçersiz dosya"));
    }

    @Test
    @DisplayName("DELETE /api/logs/{id} → 204 No Content ve servis çağrılır")
    void deleteReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/logs/{id}", id))
                .andExpect(status().isNoContent());          // 204
        verify(logFileService).delete(eq(id));               // silme servise devredildi
    }
}
