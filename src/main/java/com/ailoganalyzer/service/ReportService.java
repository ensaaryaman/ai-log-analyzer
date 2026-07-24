package com.ailoganalyzer.service;

import java.util.UUID;

/**
 * Bir analiz sonucunu indirilebilir PDF raporuna dönüştürür.
 */
public interface ReportService {

    // Verilen analiz için PDF rapor üretir (dosya özeti + analiz + hata grupları)
    byte[] generateAnalysisReport(UUID analysisId);
}
