package com.ailoganalyzer.repository;

import com.ailoganalyzer.domain.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Yapay zeka analiz sonuçları için veri erişim arayüzü.
 */
@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    // Bir dosyaya ait analizleri en yeniden eskiye getirir (aynı dosya birden çok kez analiz edilebilir)
    List<Analysis> findByFileIdOrderByCreatedAtDesc(UUID fileId);

    // Tüm analiz geçmişini en yeniden eskiye getirir (geçmiş ekranı)
    List<Analysis> findAllByOrderByCreatedAtDesc();
}
