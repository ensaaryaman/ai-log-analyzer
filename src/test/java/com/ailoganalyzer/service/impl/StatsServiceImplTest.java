package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.domain.Analysis;
import com.ailoganalyzer.domain.ErrorGroup;
import com.ailoganalyzer.domain.LogEntry;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.LogLevel;
import com.ailoganalyzer.dto.ErrorGroupResponse;
import com.ailoganalyzer.dto.StatsResponse;
import com.ailoganalyzer.dto.WarnToErrorTransition;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import com.ailoganalyzer.repository.AnalysisRepository;
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
    @Mock private AnalysisRepository analysisRepository;
    @Mock private AccessControlService accessControl;

    private StatsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StatsServiceImpl(
                logFileRepository, logEntryRepository, errorGroupRepository, analysisRepository, accessControl);
    }

    private LogFile fileWithId(UUID id) {
        LogFile file = new LogFile();
        file.setId(id);
        return file;
    }

    private ErrorGroup groupIn(LogFile file, String fingerprint) {
        ErrorGroup g = new ErrorGroup();
        g.setFile(file);
        g.setFingerprint(fingerprint);
        g.setExceptionType("NullPointerException");
        g.setOccurrenceCount(1);
        g.setLastSeen(OffsetDateTime.parse("2026-01-01T10:00:00Z"));
        return g;
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

    // --- Hata bilgi bankası (knowledgeHint) ---

    @Test
    @DisplayName("USER: aynı hata kendi geçmiş dosyasında görülmüşse knowledgeHint dosya adı + geçmiş çözümle dolar")
    void knowledgeHintFilledWhenUserHasSeenSameErrorBefore() {
        UUID id = UUID.randomUUID();
        LogFile currentFile = fileWithId(id);
        ErrorGroup currentGroup = groupIn(currentFile, "abc123");

        LogFile pastFile = fileWithId(UUID.randomUUID());
        pastFile.setFilename("gecen-hafta.log");
        ErrorGroup pastGroup = groupIn(pastFile, "abc123");
        pastGroup.setLastSeen(OffsetDateTime.parse("2025-12-20T09:00:00Z"));

        Analysis pastAnalysis = new Analysis();
        pastAnalysis.setRootCause("Havuz kapasitesi yetersizdi");
        pastAnalysis.setSolution("Havuz boyutu artırıldı");

        UUID meId = UUID.randomUUID();
        when(logFileRepository.findById(id)).thenReturn(Optional.of(currentFile));
        when(logEntryRepository.countByLevel(id)).thenReturn(List.of());
        when(logEntryRepository.findByFileAndLevels(eq(id), any())).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(id)).thenReturn(List.of(currentGroup));
        when(accessControl.isAdmin()).thenReturn(false);
        when(accessControl.currentUserId()).thenReturn(meId);
        when(errorGroupRepository.findPastOccurrencesByOwner("abc123", id, meId)).thenReturn(List.of(pastGroup));
        when(analysisRepository.findByFileIdOrderByCreatedAtDesc(pastFile.getId())).thenReturn(List.of(pastAnalysis));

        StatsResponse stats = service.computeStats(id);

        ErrorGroupResponse.KnowledgeHint hint = stats.errorGroups().get(0).knowledgeHint();
        assertThat(hint).isNotNull();
        assertThat(hint.sourceFilename()).isEqualTo("gecen-hafta.log");
        assertThat(hint.pastFileCount()).isEqualTo(1);
        assertThat(hint.pastRootCause()).isEqualTo("Havuz kapasitesi yetersizdi");
        assertThat(hint.pastSolution()).isEqualTo("Havuz boyutu artırıldı");
    }

    @Test
    @DisplayName("Geçmişte eşleşme yoksa knowledgeHint null döner")
    void knowledgeHintNullWhenNoPastMatch() {
        UUID id = UUID.randomUUID();
        LogFile currentFile = fileWithId(id);
        ErrorGroup currentGroup = groupIn(currentFile, "yeni-hata");

        when(logFileRepository.findById(id)).thenReturn(Optional.of(currentFile));
        when(logEntryRepository.countByLevel(id)).thenReturn(List.of());
        when(logEntryRepository.findByFileAndLevels(eq(id), any())).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(id)).thenReturn(List.of(currentGroup));
        when(accessControl.isAdmin()).thenReturn(false);
        when(accessControl.currentUserId()).thenReturn(UUID.randomUUID());
        when(errorGroupRepository.findPastOccurrencesByOwner(eq("yeni-hata"), eq(id), any())).thenReturn(List.of());

        StatsResponse stats = service.computeStats(id);

        assertThat(stats.errorGroups().get(0).knowledgeHint()).isNull();
        verify(analysisRepository, never()).findByFileIdOrderByCreatedAtDesc(any());
    }

    // --- Hata fırtınası (anomali) tespiti ---

    @Test
    @DisplayName("Belirgin bir sıçrama varsa hata fırtınası tespit edilir; başlangıç/tepe/oran doğru hesaplanır")
    void detectsErrorStormOnClearSpike() {
        UUID id = UUID.randomUUID();
        LogFile file = fileWithId(id);
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));
        when(logEntryRepository.countByLevel(id)).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(id)).thenReturn(List.of());

        // 5 sakin dakika (yalnızca WARN, hiç ERROR yok) + 1 dakikada ani 5 hatalık sıçrama
        List<LogEntry> entries = List.of(
                entryAt(LogLevel.WARN, "2026-01-01T10:00:00Z"),
                entryAt(LogLevel.WARN, "2026-01-01T10:01:00Z"),
                entryAt(LogLevel.WARN, "2026-01-01T10:02:00Z"),
                entryAt(LogLevel.WARN, "2026-01-01T10:03:00Z"),
                entryAt(LogLevel.WARN, "2026-01-01T10:04:00Z"),
                entryAt(LogLevel.ERROR, "2026-01-01T10:05:00Z"),
                entryAt(LogLevel.ERROR, "2026-01-01T10:05:00Z"),
                entryAt(LogLevel.ERROR, "2026-01-01T10:05:00Z"),
                entryAt(LogLevel.ERROR, "2026-01-01T10:05:00Z"),
                entryAt(LogLevel.ERROR, "2026-01-01T10:05:00Z"));
        when(logEntryRepository.findByFileAndLevels(eq(id), any())).thenReturn(entries);

        StatsResponse stats = service.computeStats(id);

        var storm = stats.errorStorm();
        assertThat(storm).isNotNull();
        assertThat(storm.stormStartMinute()).isEqualTo(OffsetDateTime.parse("2026-01-01T10:05:00Z"));
        assertThat(storm.stormEndMinute()).isEqualTo(OffsetDateTime.parse("2026-01-01T10:05:00Z"));
        assertThat(storm.peakErrorCount()).isEqualTo(5);
        assertThat(storm.baselineAverage()).isCloseTo(0.833, org.assertj.core.data.Offset.offset(0.01));
        assertThat(storm.peakToBaselineRatio()).isCloseTo(6.0, org.assertj.core.data.Offset.offset(0.1));
        assertThat(storm.anomalousMinuteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Yeterli veri noktası yoksa (5 dakikalık kovadan az) fırtına tespit edilmez — az veriyle iddiada bulunulmaz")
    void noStormWhenInsufficientDataPoints() {
        UUID id = UUID.randomUUID();
        LogFile file = fileWithId(id);
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));
        when(logEntryRepository.countByLevel(id)).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(id)).thenReturn(List.of());

        // Yalnızca 4 dakikalık kova (biri belirgin bir sıçrama olsa bile) — eşik altı, tespit yapılmaz
        List<LogEntry> entries = List.of(
                entryAt(LogLevel.WARN, "2026-01-01T10:00:00Z"),
                entryAt(LogLevel.WARN, "2026-01-01T10:01:00Z"),
                entryAt(LogLevel.WARN, "2026-01-01T10:02:00Z"),
                entryAt(LogLevel.ERROR, "2026-01-01T10:03:00Z"));
        when(logEntryRepository.findByFileAndLevels(eq(id), any())).thenReturn(entries);

        StatsResponse stats = service.computeStats(id);

        assertThat(stats.errorStorm()).isNull();
    }

    @Test
    @DisplayName("Hata oranı sabitse (varyans yok) fırtına tespit edilmez — tanım gereği anomali yok")
    void noStormWhenErrorRateIsFlat() {
        UUID id = UUID.randomUUID();
        LogFile file = fileWithId(id);
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));
        when(logEntryRepository.countByLevel(id)).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(id)).thenReturn(List.of());

        // 5 dakikanın her birinde tam olarak 2'şer ERROR — düz seyir, stddev=0
        List<LogEntry> entries = List.of(
                entryAt(LogLevel.ERROR, "2026-01-01T10:00:00Z"), entryAt(LogLevel.ERROR, "2026-01-01T10:00:00Z"),
                entryAt(LogLevel.ERROR, "2026-01-01T10:01:00Z"), entryAt(LogLevel.ERROR, "2026-01-01T10:01:00Z"),
                entryAt(LogLevel.ERROR, "2026-01-01T10:02:00Z"), entryAt(LogLevel.ERROR, "2026-01-01T10:02:00Z"),
                entryAt(LogLevel.ERROR, "2026-01-01T10:03:00Z"), entryAt(LogLevel.ERROR, "2026-01-01T10:03:00Z"),
                entryAt(LogLevel.ERROR, "2026-01-01T10:04:00Z"), entryAt(LogLevel.ERROR, "2026-01-01T10:04:00Z"));
        when(logEntryRepository.findByFileAndLevels(eq(id), any())).thenReturn(entries);

        StatsResponse stats = service.computeStats(id);

        assertThat(stats.errorStorm()).isNull();
    }

    @Test
    @DisplayName("ADMIN: geçmiş arama sahiplikten bağımsız (tüm kullanıcılar) yapılır")
    void adminSearchesPastOccurrencesAcrossAllOwners() {
        UUID id = UUID.randomUUID();
        LogFile currentFile = fileWithId(id);
        ErrorGroup currentGroup = groupIn(currentFile, "abc123");

        when(logFileRepository.findById(id)).thenReturn(Optional.of(currentFile));
        when(logEntryRepository.countByLevel(id)).thenReturn(List.of());
        when(logEntryRepository.findByFileAndLevels(eq(id), any())).thenReturn(List.of());
        when(errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(id)).thenReturn(List.of(currentGroup));
        when(accessControl.isAdmin()).thenReturn(true);
        when(errorGroupRepository.findPastOccurrencesAnyOwner("abc123", id)).thenReturn(List.of());

        service.computeStats(id);

        verify(errorGroupRepository).findPastOccurrencesAnyOwner("abc123", id);
        verify(errorGroupRepository, never()).findPastOccurrencesByOwner(any(), any(), any());
    }
}
