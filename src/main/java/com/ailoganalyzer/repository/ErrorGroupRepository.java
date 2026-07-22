package com.ailoganalyzer.repository;

import com.ailoganalyzer.domain.ErrorGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Hata grupları için veri erişim arayüzü (tekrarlanan hata tespiti).
 */
@Repository
public interface ErrorGroupRepository extends JpaRepository<ErrorGroup, Long> {

    // Bir dosyanın hata gruplarını en çok tekrarlanandan en aza doğru getirir (dashboard/analiz için)
    List<ErrorGroup> findByFileIdOrderByOccurrenceCountDesc(UUID fileId);
}
