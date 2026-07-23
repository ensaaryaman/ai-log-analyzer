package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.domain.ErrorGroup;
import com.ailoganalyzer.domain.LogEntry;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.repository.ErrorGroupRepository;
import com.ailoganalyzer.repository.LogEntryRepository;
import com.ailoganalyzer.service.ErrorGroupingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ErrorGroupingService uygulaması. Parmak izine göre gruplama mantığı,
 * saf ve statik bir yardımcı metotta (buildGroups) tutulur → veritabanı olmadan test edilebilir.
 */
@Service
public class ErrorGroupingServiceImpl implements ErrorGroupingService {

    private final LogEntryRepository logEntryRepository;
    private final ErrorGroupRepository errorGroupRepository;

    public ErrorGroupingServiceImpl(LogEntryRepository logEntryRepository,
                                    ErrorGroupRepository errorGroupRepository) {
        this.logEntryRepository = logEntryRepository;
        this.errorGroupRepository = errorGroupRepository;
    }

    // Yeniden gruplama tek transaction'da: önce eskileri sil, sonra yenilerini yaz (idempotent)
    @Override
    @Transactional
    public void rebuildGroups(LogFile file) {
        errorGroupRepository.deleteByFileId(file.getId());
        List<LogEntry> entries = logEntryRepository.findByFileIdOrderByLineNumberAsc(file.getId());
        List<ErrorGroup> groups = buildGroups(file, entries);
        errorGroupRepository.saveAll(groups);
    }

    /**
     * Parmak izi olan kayıtları gruplar. Saf fonksiyon (I/O yok) → birim testine uygundur.
     * Her grup için: tekrar sayısı, ilk/son görülme zamanı ve temsilci (örnek) kayıt hesaplanır.
     */
    static List<ErrorGroup> buildGroups(LogFile file, List<LogEntry> entries) {
        // Parmak izini koru (LinkedHashMap → ilk görülme sırası) : parmak izi → o gruba düşen kayıtlar
        Map<String, List<LogEntry>> byFingerprint = new LinkedHashMap<>();
        for (LogEntry entry : entries) {
            if (entry.getFingerprint() == null) {
                continue;   // Sadece parmak izi olan (WARN+) kayıtlar gruplanır
            }
            byFingerprint.computeIfAbsent(entry.getFingerprint(), k -> new ArrayList<>()).add(entry);
        }

        List<ErrorGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<LogEntry>> bucket : byFingerprint.entrySet()) {
            List<LogEntry> members = bucket.getValue();
            LogEntry sample = members.get(0);   // Grubu temsil eden ilk kayıt

            ErrorGroup group = new ErrorGroup();
            group.setFile(file);
            group.setFingerprint(bucket.getKey());
            group.setExceptionType(sample.getExceptionType());
            group.setSampleMessage(sample.getMessage());
            group.setOccurrenceCount(members.size());       // "Bu hata N kez tekrar etti"
            group.setFirstSeen(minTimestamp(members));
            group.setLastSeen(maxTimestamp(members));
            group.setSampleEntry(sample);
            groups.add(group);
        }

        // En çok tekrarlanan grup en başta olacak şekilde sırala (önem sırası)
        groups.sort(Comparator.comparingInt(ErrorGroup::getOccurrenceCount).reversed());
        return groups;
    }

    // Gruptaki kayıtların en erken zaman damgası (hepsi null ise null)
    private static OffsetDateTime minTimestamp(List<LogEntry> members) {
        return members.stream().map(LogEntry::getTs).filter(t -> t != null)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    // Gruptaki kayıtların en geç zaman damgası (hepsi null ise null)
    private static OffsetDateTime maxTimestamp(List<LogEntry> members) {
        return members.stream().map(LogEntry::getTs).filter(t -> t != null)
                .max(Comparator.naturalOrder()).orElse(null);
    }
}
