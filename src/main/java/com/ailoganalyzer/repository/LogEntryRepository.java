package com.ailoganalyzer.repository;

import com.ailoganalyzer.domain.LogEntry;
import com.ailoganalyzer.domain.LogLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Parse edilmiş log satırları için veri erişim arayüzü.
 * Metot adından sorgu üretme (query derivation) ve özel JPQL ile okumalar sağlanır.
 */
@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {

    // Bir dosyanın belirli seviyedeki (örn. ERROR) satırlarını, dosyadaki sıraya göre getirir
    List<LogEntry> findByFileIdAndLevelOrderByLineNumberAsc(UUID fileId, LogLevel level);

    // Bir dosyanın tüm satırlarını satır numarasına göre sıralı getirir
    List<LogEntry> findByFileIdOrderByLineNumberAsc(UUID fileId);

    // Seviye dağılımını veritabanında hesaplar (satırları belleğe çekmeden): [level, adet] çiftleri döner
    @Query("select e.level, count(e) from LogEntry e where e.file.id = :fileId group by e.level")
    List<Object[]> countByLevel(@Param("fileId") UUID fileId);

    // Zaman serisi için: belirli seviyelerdeki (WARN+), zaman damgası olan kayıtları kronolojik getirir
    @Query("select e from LogEntry e where e.file.id = :fileId and e.level in :levels and e.ts is not null order by e.ts")
    List<LogEntry> findByFileAndLevels(@Param("fileId") UUID fileId, @Param("levels") Collection<LogLevel> levels);
}
