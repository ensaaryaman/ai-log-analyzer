package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.domain.LogEntry;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.LogLevel;
import com.ailoganalyzer.dto.StatsResponse;
import com.ailoganalyzer.dto.WarnToErrorTransition;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import com.ailoganalyzer.repository.ErrorGroupRepository;
import com.ailoganalyzer.repository.LogEntryRepository;
import com.ailoganalyzer.repository.LogFileRepository;
import com.ailoganalyzer.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StatsServiceImpl birim testi (Mockito, DB olmadan).
 * Gün 11 sağlamlaştırma: bu servisin (WARN→ERROR geçiş hesabı dahil) daha önce hiç
 * birim testi yoktu — yalnızca çalışan uygulama + dashboard ekran görüntüleriyle
 * dolaylı doğrulanmıştı. Burada: 404, requireAccess kablolaması ve geçiş hesabının
 * üç senaryosu (geçiş var / hatalar önce geldi → geçiş yok / tek seviye → geçiş yok).
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceImplTest {

    @Mock private LogFileRepository logFileRepository;
    @Mock private LogEntryRepository logEntryRepository;
    @Mock private ErrorGroupRepository errorGroupRepository;
    @Mock private AccessControlService accessControl;

    private StatsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StatsServiceImpl(logFileRepository, logEntryRepository, errorGroupRepository, accessControl);
    }

    private LogFile fileWithId(UUID id) {
        LogFile file = new LogFile();
        file.setId(id);
        return file;
    }

    private LogEntry entryAt(LogLevel level, String isoTs) {
        LogEntry e = new LogEntry();
        e.setLevel(level);
        e.setTs(OffsetDateTime.parse(isoTs));
        return e;
    }

    @Test
    @DisplayName("Bulunamayan dosya → ResourceNotFoundException")
    void notFound() {
        UUID id = UUID.randomUUID();
        when(logFileRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.computeStats(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Başka kullanıcının dosyası → requireAccess 403 fırlatır, hesaplama hiç yapılmaz")
    void deniedAccessStopsBeforeComputing() {
        UUID id = UUID.randomUUID();
        LogFile file = fileWithId(id);
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));
        doThrow(new AccessDeniedException("yetkisiz")).when(accessControl).requireAccess(file);

        assertThatThrownBy(() -> service.computeStats(id)).isInstanceOf(AccessDeniedException.class);
        verify(errorGroupRepository, never()).findByFileIdOrderByOccurrenceCountDesc(any());
    }

    @Test
    @DisplayName("WARN önce, ERROR sonra geldiyse geçiş hesaplanır ve dakika farkı doğru olur")
    void detectsWarnToErrorTransitionWithGap() {
        UUID id = UUID.randomUUID();
        LogFile file = fileWithId(id);
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));
        when(logEntryRepository.countByLevel(id)).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(id)).thenReturn(List.of());
        when(logEntryRepository.findByFileAndLevels(eq(id), any())).thenReturn(List.of(
                entryAt(LogLevel.WARN, "2026-01-01T10:00:00Z"),
                entryAt(LogLevel.ERROR, "2026-01-01T10:07:00Z")));

        StatsResponse stats = service.computeStats(id);

        WarnToErrorTransition tr = stats.warnToErrorTransition();
        assertThat(tr).isNotNull();
        assertThat(tr.gapMinutes()).isEqualTo(7);
    }

    @Test
    @DisplayName("ERROR, WARN'dan ÖNCE geldiyse geçiş YOK sayılır (uyarı hatayı önceleyen bir sinyal değil)")
    void noTransitionWhenErrorPrecedesWarn() {
        UUID id = UUID.randomUUID();
        LogFile file = fileWithId(id);
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));
        when(logEntryRepository.countByLevel(id)).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(id)).thenReturn(List.of());
        when(logEntryRepository.findByFileAndLevels(eq(id), any())).thenReturn(List.of(
                entryAt(LogLevel.ERROR, "2026-01-01T10:00:00Z"),
                entryAt(LogLevel.WARN, "2026-01-01T10:07:00Z")));

        StatsResponse stats = service.computeStats(id);

        assertThat(stats.warnToErrorTransition()).isNull();
    }

    @Test
    @DisplayName("Yalnızca WARN varsa (hiç ERROR yoksa) geçiş YOK sayılır")
    void noTransitionWhenOnlyWarnPresent() {
        UUID id = UUID.randomUUID();
        LogFile file = fileWithId(id);
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));
        when(logEntryRepository.countByLevel(id)).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(id)).thenReturn(List.of());
        when(logEntryRepository.findByFileAndLevels(eq(id), any())).thenReturn(List.of(
                entryAt(LogLevel.WARN, "2026-01-01T10:00:00Z")));

        StatsResponse stats = service.computeStats(id);

        assertThat(stats.warnToErrorTransition()).isNull();
    }

    @Test
    @DisplayName("Seviye dağılımı TRACE→FATAL sırasıyla döner (UNKNOWN en sonda)")
    void levelDistributionIsOrderedBySeverity() {
        UUID id = UUID.randomUUID();
        LogFile file = fileWithId(id);
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));
        // Kasıtlı karışık sırada veriyoruz; servis şiddet sırasına göre düzenlemeli
        when(logEntryRepository.countByLevel(id)).thenReturn(List.<Object[]>of(
                new Object[]{LogLevel.ERROR, 3L},
                new Object[]{LogLevel.WARN, 5L},
                new Object[]{LogLevel.INFO, 10L}
        ));
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(id)).thenReturn(List.of());
        when(logEntryRepository.findByFileAndLevels(eq(id), any())).thenReturn(List.of());

        StatsResponse stats = service.computeStats(id);

        assertThat(stats.levelDistribution().keySet()).containsExactly("INFO", "WARN", "ERROR");
        assertThat(stats.totalEntries()).isEqualTo(18L);
    }
}
