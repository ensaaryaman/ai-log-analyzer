package com.ailoganalyzer.controller;

import com.ailoganalyzer.dto.AnalysisResponse;
import com.ailoganalyzer.service.AnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Yapay zeka analizi uçlarını sunan REST controller.
 * Analiz bir log dosyası üzerinde başlatılır (/api/logs/{id}/analyze),
 * sonuçlar ise /api/analyses altından okunur.
 */
@RestController                      // Metot dönüşleri doğrudan JSON gövdesi olur
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    // POST /api/logs/{id}/analyze — dosyayı yapay zekaya analiz ettirir (senkron)
    @PostMapping("/api/logs/{id}/analyze")
    public AnalysisResponse analyze(@PathVariable UUID id) {
        return analysisService.analyze(id);
    }

    // GET /api/analyses — tüm analiz geçmişi; ?fileId= verilirse o dosyanınki
    @GetMapping("/api/analyses")
    public List<AnalysisResponse> list(
            @RequestParam(value = "fileId", required = false) UUID fileId) {   // opsiyonel filtre
        return fileId == null ? analysisService.listAll() : analysisService.listByFile(fileId);
    }

    // GET /api/analyses/{id} — tek bir analiz sonucu
    @GetMapping("/api/analyses/{id}")
    public AnalysisResponse getOne(@PathVariable UUID id) {
        return analysisService.getById(id);
    }
}
