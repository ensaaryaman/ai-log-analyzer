package com.ailoganalyzer.service;

import com.ailoganalyzer.dto.AnalysisResponse;

import java.util.List;
import java.util.UUID;

/**
 * Bir log dosyasının yapay zeka analizini yönetir: damıtılmış bağlamdan prompt kurar,
 * AI istemcisini çağırır, sonucu saklar ve geçmişe erişim sağlar.
 */
public interface AnalysisService {

    // Verilen dosyayı analiz eder (parse edilmiş olmalı), sonucu kaydeder ve döner
    AnalysisResponse analyze(UUID fileId);

    // Tek bir analiz sonucunu getirir (yoksa 404)
    AnalysisResponse getById(UUID analysisId);

    // Tüm analiz geçmişini (en yeniden eskiye) getirir
    List<AnalysisResponse> listAll();

    // Belirli bir dosyanın analiz geçmişini getirir
    List<AnalysisResponse> listByFile(UUID fileId);
}
