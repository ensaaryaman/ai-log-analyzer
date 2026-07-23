package com.ailoganalyzer.repository;

import com.ailoganalyzer.domain.ErrorGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Bir dosyanın mevcut gruplarını toplu siler; yeniden gruplama (rebuild) öncesi çağrılır (idempotentlik)
    @Modifying                       // Bu sorgunun veri DEĞİŞTİRDİĞİNİ (select değil) belirtir
    @Query("delete from ErrorGroup g where g.file.id = :fileId")   // Entity'leri tek tek yüklemeden toplu sil (verimli)
    void deleteByFileId(@Param("fileId") UUID fileId);
}
