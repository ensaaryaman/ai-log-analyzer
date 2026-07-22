package com.ailoganalyzer.repository;

import com.ailoganalyzer.domain.LogFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * LogFile için veri erişim arayüzü.
 * JpaRepository'yi genişletmek, CRUD metotlarını (save, findById, delete...) hazır getirir;
 * implementasyonu Spring Data çalışma anında otomatik üretir (kod yazmamıza gerek yok).
 */
@Repository                          // Bu arayüzün bir veri erişim bileşeni olduğunu belirtir; JPA istisnalarını Spring istisnalarına çevirir
public interface LogFileRepository extends JpaRepository<LogFile, UUID> {

    // En son yüklenenden en eskiye sıralı dosya listesi (geçmiş ekranı için). Metot adından sorgu üretilir.
    List<LogFile> findAllByOrderByUploadedAtDesc();
}
