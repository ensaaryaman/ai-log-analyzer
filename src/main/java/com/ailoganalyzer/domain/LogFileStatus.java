package com.ailoganalyzer.domain;

/**
 * Bir log dosyasının yaşam döngüsündeki durumu.
 * İş akışı: UPLOADED → PARSED → ANALYZED (hata olursa FAILED).
 */
public enum LogFileStatus {
    UPLOADED,   // Dosya alındı ve diske/DB'ye kaydedildi, henüz parse edilmedi
    PARSED,     // Satırlar ayrıştırıldı, istatistikler çıkarıldı
    ANALYZED,   // Yapay zeka analizi tamamlandı
    FAILED      // Parse veya analiz sırasında hata oluştu
}
