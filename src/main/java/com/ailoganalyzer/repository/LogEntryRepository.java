package com.ailoganalyzer.repository;

import com.ailoganalyzer.domain.LogEntry;
import com.ailoganalyzer.domain.LogLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Parse edilmiş log satırları için veri erişim arayüzü.
 * Metot adından sorgu üretme (query derivation) ile filtreli okumalar sağlanır.
 */
@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {

    // Bir dosyanın belirli seviyedeki (örn. ERROR) satırlarını, dosyadaki sıraya göre getirir
    List<LogEntry> findByFileIdAndLevelOrderByLineNumberAsc(UUID fileId, LogLevel level);

    // Bir dosyanın tüm satırlarını satır numarasına göre sıralı getirir
    List<LogEntry> findByFileIdOrderByLineNumberAsc(UUID fileId);
}
