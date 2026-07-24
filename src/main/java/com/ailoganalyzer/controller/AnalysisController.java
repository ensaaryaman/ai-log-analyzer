package com.ailoganalyzer.controller;

import com.ailoganalyzer.dto.AnalysisResponse;
import com.ailoganalyzer.service.AnalysisService;
import com.ailoganalyzer.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final ReportService reportService;

    public AnalysisController(AnalysisService analysisService, ReportService reportService) {
        this.analysisService = analysisService;
        this.reportService = reportService;
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

    // GET /api/analyses/{id}/report.pdf — analizi indirilebilir PDF rapor olarak döner
    @GetMapping("/api/analyses/{id}/report.pdf")
    public ResponseEntity<byte[]> report(@PathVariable UUID id) {
        byte[] pdf = reportService.generateAnalysisReport(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)   // Tarayıcı bunu PDF olarak tanır
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analiz-raporu.pdf\"")   // indirme olarak sun
                .body(pdf);
    }
}

