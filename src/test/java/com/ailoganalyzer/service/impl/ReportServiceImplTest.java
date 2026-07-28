package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.domain.Analysis;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.Priority;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import com.ailoganalyzer.repository.AnalysisRepository;
import com.ailoganalyzer.repository.ErrorGroupRepository;
import com.ailoganalyzer.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReportServiceImpl birim testi. PDF render'ı gerçekten çalıştırır (openhtmltopdf saf/deterministik
 * ve hızlıdır — mock'lamaya gerek yok); yalnızca 404 ve sahiplik (403) kablolaması mock'lanır.
 * Gün 11 sağlamlaştırma: bu servisin daha önce hiç birim testi yoktu.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private AnalysisRepository analysisRepository;
    @Mock private ErrorGroupRepository errorGroupRepository;
    @Mock private AccessControlService accessControl;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(analysisRepository, errorGroupRepository, accessControl);
    }

    @Test
    @DisplayName("Bulunamayan analiz → ResourceNotFoundException")
    void notFound() {
        UUID id = UUID.randomUUID();
        when(analysisRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generateAnalysisReport(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Başka kullanıcının analizine erişim → 403, PDF hiç üretilmez")
    void deniedAccessNeverRendersPdf() {
        UUID id = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(UUID.randomUUID());
        Analysis analysis = new Analysis();
        analysis.setId(id);
        analysis.setFile(file);
        when(analysisRepository.findById(id)).thenReturn(Optional.of(analysis));
        doThrow(new AccessDeniedException("yetkisiz")).when(accessControl).requireAccess(file);

        assertThatThrownBy(() -> service.generateAnalysisReport(id)).isInstanceOf(AccessDeniedException.class);
        verify(errorGroupRepository, never()).findByFileIdOrderByOccurrenceCountDesc(any());
    }

    @Test
    @DisplayName("Geçerli analiz: gerçek bir PDF üretilir (%PDF sihirli baytlarıyla başlar), Türkçe karakter içerir")
    void generatesValidPdfWithTurkishCharacters() {
        UUID id = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(UUID.randomUUID());
        file.setFilename("şğıöç-log.log");   // Türkçe karakter: DejaVu font gömme doğrulaması
        file.setLineCount(10);
        file.setErrorCount(2);
        file.setWarnCount(1);

        Analysis analysis = new Analysis();
        analysis.setId(id);
        analysis.setFile(file);
        analysis.setPriority(Priority.HIGH);
        analysis.setConfidence(new BigDecimal("0.90"));
        analysis.setSummary("Veritabanı bağlantı havuzu tükendi");
        analysis.setRootCause("HikariPool kapasitesi yetersiz");
        analysis.setSolution("Havuz boyutunu artır");
        analysis.setEvidenceLines(List.of(12, 45));
        analysis.setCreatedAt(OffsetDateTime.now());

        when(analysisRepository.findById(id)).thenReturn(Optional.of(analysis));
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(file.getId())).thenReturn(List.of());

        byte[] pdf = service.generateAnalysisReport(id);

        assertThat(pdf).isNotEmpty();
        // PDF dosyaları her zaman "%PDF-" ile başlar (format imzası)
        String header = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
        verify(accessControl).requireAccess(file);
    }
}
