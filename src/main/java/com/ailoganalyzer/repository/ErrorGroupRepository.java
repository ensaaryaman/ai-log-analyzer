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

    // --- Hata bilgi bankası: aynı fingerprint'e (aynı hata) sahip, BAŞKA bir dosyada daha önce
    // görülmüş kayıtları bulur. Fingerprint mesajdaki sayı/UUID'leri maskelediği için aynı hata
    // farklı log dosyalarında (farklı spesifik değerlerle) bile aynı parmak izini üretir — bu da
    // "bu hatayı daha önce görmüştünüz" tespitini AI olmadan, deterministik biçimde mümkün kılar. ---

    // USER için: yalnızca KENDİ geçmiş dosyalarında arar (sahiplik sınırı — başka kullanıcının
    // hata detayları sızmasın diye). En son görülenden en eskiye sıralı.
    @Query("""
            select eg from ErrorGroup eg
            where eg.fingerprint = :fingerprint
              and eg.file.id <> :excludeFileId
              and eg.file.owner.id = :ownerId
            order by eg.lastSeen desc
            """)
    List<ErrorGroup> findPastOccurrencesByOwner(@Param("fingerprint") String fingerprint,
                                                @Param("excludeFileId") UUID excludeFileId,
                                                @Param("ownerId") UUID ownerId);

    // ADMIN için: sahiplikten bağımsız, tüm kullanıcıların dosyaları arasında arar.
    @Query("""
            select eg from ErrorGroup eg
            where eg.fingerprint = :fingerprint
              and eg.file.id <> :excludeFileId
            order by eg.lastSeen desc
            """)
    List<ErrorGroup> findPastOccurrencesAnyOwner(@Param("fingerprint") String fingerprint,
                                                 @Param("excludeFileId") UUID excludeFileId);
}
