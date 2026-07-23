package com.ailoganalyzer.service;

import com.ailoganalyzer.domain.LogFile;

/**
 * Bir dosyanın hata kayıtlarını parmak izine göre gruplayarak "tekrarlanan hata" tespiti yapar.
 * Sonuçları error_group tablosuna yazar. AI olmadan da değer üreten deterministik bir çekirdektir.
 */
public interface ErrorGroupingService {

    /**
     * Dosyanın hata gruplarını sıfırdan yeniden hesaplar (önce eskileri siler → idempotent).
     * Parse tamamlandıktan sonra çağrılır.
     */
    void rebuildGroups(LogFile file);
}
