package com.ailoganalyzer.service;

import com.ailoganalyzer.dto.StatsResponse;

import java.util.UUID;

/**
 * Bir log dosyasının damıtılmış istatistiklerini (seviye dağılımı, hata grupları,
 * exception istatistikleri, zaman serisi) hesaplar. Dashboard ve AI prompt'u için veri kaynağıdır.
 */
public interface StatsService {

    // Verilen dosya için istatistikleri hesaplar (dosya yoksa ResourceNotFoundException)
    StatsResponse computeStats(UUID fileId);
}
