package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.ai.AiAnalysisOutcome;
import com.ailoganalyzer.ai.AnalysisAiClient;
import com.ailoganalyzer.ai.AnalysisPromptBuilder;
import com.ailoganalyzer.ai.AnalysisResult;
import com.ailoganalyzer.domain.Analysis;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.LogFileStatus;
import com.ailoganalyzer.domain.LogFormat;
import com.ailoganalyzer.domain.Priority;
import com.ailoganalyzer.dto.AnalysisResponse;
import com.ailoganalyzer.dto.StatsResponse;
import com.ailoganalyzer.repository.AnalysisRepository;
import com.ailoganalyzer.repository.ErrorGroupRepository;
import com.ailoganalyzer.repository.LogFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AnalysisServiceImpl orkestrasyon testi (Mockito ile, DB/AI olmadan).
 * Doğrular: bağlam kurulur → AI istemcisi çağrılır → sonuç kaydedilir → dosya durumu ANALYZED olur.
 * AI istemcisi mock'landığı için gerçek bir API çağrısı YAPILMAZ (deterministik, hızlı, ücretsiz).
 */
@ExtendWith(MockitoExtension.class)
class AnalysisServiceImplTest {

    @Mock private LogFileRepository logFileRepository;
    @Mock private ErrorGroupRepository errorGroupRepository;
    @Mock private AnalysisRepository analysisRepository;
    @Mock private com.ailoganalyzer.service.StatsService statsService;
    @Mock private AnalysisAiClient aiClient;

    private AnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        // promptBuilder gerçek (saf) kullanılır → bağlam→prompt entegrasyonu da test edilmiş olur
        service = new AnalysisServiceImpl(
                logFileRepository, errorGroupRepository, analysisRepository,
                statsService, new AnalysisPromptBuilder(), aiClient);
    }

    @Test
    @DisplayName("Analiz: AI çağrılır, sonuç kaydedilir ve dosya ANALYZED olur")
    void analyzePersistsResultAndMarksFileAnalyzed() {
        UUID fileId = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(fileId);
        file.setFilename("app.log");
        file.setDetectedFormat(LogFormat.SPRING_BOOT);
        file.setErrorCount(2);
        file.setWarnCount(1);
        file.setStatus(LogFileStatus.PARSED);

        when(logFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(statsService.computeStats(fileId)).thenReturn(new StatsResponse(
                fileId, "SPRING_BOOT", 5, Map.of("ERROR", 2L, "WARN", 1L),
                List.of(), List.of(), List.of(), null, null, null));
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(fileId)).thenReturn(List.of());

        AnalysisResult aiResult = new AnalysisResult(
                "Özet", "Kök neden", "Çözüm", Priority.HIGH, 0.8, List.of(1042, 1187));
        when(aiClient.analyze(any())).thenReturn(
                new AiAnalysisOutcome(aiResult, "gemini-2.5-flash", 120, 45, "{\"ok\":true}"));
        // save çağrısında entity'ye bir id atayıp geri döndür (gerçek DB davranışını taklit)
        when(analysisRepository.save(any(Analysis.class))).thenAnswer(inv -> {
            Analysis a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AnalysisResponse response = service.analyze(fileId);

        // Sonuç doğru map'lendi
        assertThat(response.summary()).isEqualTo("Özet");
        assertThat(response.priority()).isEqualTo("HIGH");
        assertThat(response.confidence()).isEqualByComparingTo("0.80");
        assertThat(response.evidenceLines()).containsExactly(1042, 1187);
        assertThat(response.model()).isEqualTo("gemini-2.5-flash");

        // Yan etkiler doğrulandı: kayıt yapıldı ve dosya durumu güncellendi
        verify(analysisRepository).save(any(Analysis.class));
        assertThat(file.getStatus()).isEqualTo(LogFileStatus.ANALYZED);
    }
}
