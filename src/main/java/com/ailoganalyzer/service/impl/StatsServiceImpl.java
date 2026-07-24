package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.domain.ErrorGroup;
import com.ailoganalyzer.domain.LogEntry;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.LogLevel;
import com.ailoganalyzer.dto.ErrorGroupResponse;
import com.ailoganalyzer.dto.ExceptionStat;
import com.ailoganalyzer.dto.StatsResponse;
import com.ailoganalyzer.dto.TimeBucket;
import com.ailoganalyzer.dto.WarnToErrorTransition;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import com.ailoganalyzer.repository.ErrorGroupRepository;
import com.ailoganalyzer.repository.LogEntryRepository;
import com.ailoganalyzer.repository.LogFileRepository;
import com.ailoganalyzer.service.StatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * StatsService uygulaması. İstatistikleri mümkün olduğunca veritabanında (aggregate sorgu ile)
 * hesaplar; yalnızca "problem" (WARN+) kayıtları belleğe çekip zaman serisine kovalar.
 */
@Service
public class StatsServiceImpl implements StatsService {

    private final LogFileRepository logFileRepository;
    private final LogEntryRepository logEntryRepository;
    private final ErrorGroupRepository errorGroupRepository;

    public StatsServiceImpl(LogFileRepository logFileRepository,
                            LogEntryRepository logEntryRepository,
                            ErrorGroupRepository errorGroupRepository) {
        this.logFileRepository = logFileRepository;
        this.logEntryRepository = logEntryRepository;
        this.errorGroupRepository = errorGroupRepository;
    }

    // Sadece okuma → readOnly transaction (lazy ilişkiler için oturum açık kalır, dirty-check yapılmaz)
    @Override
    @Transactional(readOnly = true)
    public StatsResponse computeStats(UUID fileId) {
        LogFile file = logFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Log dosyası", fileId));

        Map<String, Long> levelDistribution = levelDistribution(fileId);
        long total = levelDistribution.values().stream().mapToLong(Long::longValue).sum();

        List<ErrorGroup> groups = errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(fileId);
        List<ErrorGroupResponse> groupDtos = groups.stream()
                .limit(50)                                  // Yanıtı makul tut: en önemli 50 grup
                .map(ErrorGroupResponse::from)
                .toList();

        List<ExceptionStat> topExceptions = topExceptions(groups);
        List<TimeBucket> timeline = problemTimeline(fileId);
        WarnToErrorTransition transition = computeTransition(timeline);

        return new StatsResponse(
                file.getId(),
                file.getDetectedFormat() == null ? null : file.getDetectedFormat().name(),
                total,
                levelDistribution,
                topExceptions,
                groupDtos,
                timeline,
                transition,
                file.getFirstTs(),
                file.getLastTs()
        );
    }

    // WARN→ERROR geçişini hesaplar: uyarılar hatalardan ÖNCE (veya aynı anda) başladıysa geçiş vardır
    private WarnToErrorTransition computeTransition(List<TimeBucket> timeline) {
        OffsetDateTime firstWarn = timeline.stream()
                .filter(b -> b.warnCount() > 0).map(TimeBucket::minute).findFirst().orElse(null);
        OffsetDateTime firstError = timeline.stream()
                .filter(b -> b.errorCount() > 0).map(TimeBucket::minute).findFirst().orElse(null);
        // Geçiş yok: hata yok, uyarı yok, ya da uyarılar hatalardan sonra geldi
        if (firstWarn == null || firstError == null || firstWarn.isAfter(firstError)) {
            return null;
        }
        long gap = Duration.between(firstWarn, firstError).toMinutes();
        return new WarnToErrorTransition(firstWarn, firstError, gap);
    }

    // Seviye → adet haritasını, şiddet sırasına göre düzenli biçimde kurar
    private Map<String, Long> levelDistribution(UUID fileId) {
        Map<String, Long> raw = new HashMap<>();
        for (Object[] row : logEntryRepository.countByLevel(fileId)) {
            LogLevel level = (LogLevel) row[0];
            long count = (Long) row[1];
            raw.put(level == null ? "UNKNOWN" : level.name(), count);   // Seviyesizler "UNKNOWN" altında
        }
        // Çıktıyı TRACE→FATAL sırasıyla düzenle (okunabilirlik)
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (LogLevel level : LogLevel.values()) {
            if (raw.containsKey(level.name())) {
                ordered.put(level.name(), raw.get(level.name()));
            }
        }
        if (raw.containsKey("UNKNOWN")) {
            ordered.put("UNKNOWN", raw.get("UNKNOWN"));
        }
        return ordered;
    }

    // Hata gruplarından istisna tiplerini toplayıp en sık 10'unu döner
    private List<ExceptionStat> topExceptions(List<ErrorGroup> groups) {
        Map<String, Long> aggregate = new HashMap<>();
        for (ErrorGroup group : groups) {
            if (group.getExceptionType() != null) {
                aggregate.merge(group.getExceptionType(), (long) group.getOccurrenceCount(), Long::sum);
            }
        }
        return aggregate.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new ExceptionStat(e.getKey(), e.getValue()))
                .toList();
    }

    // WARN+ kayıtları dakikalık kovalara ayırıp zaman serisi üretir
    private List<TimeBucket> problemTimeline(UUID fileId) {
        List<LogEntry> problems = logEntryRepository.findByFileAndLevels(
                fileId, List.of(LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL));

        // Dakikaya göre sıralı kova: her kovada [warn, error] sayaçları
        Map<OffsetDateTime, long[]> buckets = new TreeMap<>();
        for (LogEntry entry : problems) {
            OffsetDateTime minute = entry.getTs().truncatedTo(ChronoUnit.MINUTES);
            long[] counts = buckets.computeIfAbsent(minute, k -> new long[2]);
            if (entry.getLevel() == LogLevel.WARN) {
                counts[0]++;
            } else {
                counts[1]++;   // ERROR ve FATAL
            }
        }
        return buckets.entrySet().stream()
                .map(e -> new TimeBucket(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }
}
