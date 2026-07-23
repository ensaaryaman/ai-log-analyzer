package com.ailoganalyzer.service;

import com.ailoganalyzer.domain.LogLevel;
import com.ailoganalyzer.dto.LogEntryResponse;
import com.ailoganalyzer.dto.LogFileSummaryResponse;

import java.util.List;
import java.util.UUID;

/**
 * Log dosyalarının yaşam döngüsünü (yükleme, listeleme, tekil erişim) yöneten servis sözleşmesi.
 * SOLID/DIP: Controller bu arayüze bağımlıdır, somut implementasyona değil —
 * bu sayede implementasyon test için sahte (mock) ile değiştirilebilir.
 */
public interface LogFileService {

    // Yüklenen dosyayı doğrular, diske ve DB'ye kaydeder, özetini döner
    LogFileSummaryResponse ingest(String filename, byte[] content);

    // Yüklenmiş tüm dosyaları (en yeniden eskiye) listeler
    List<LogFileSummaryResponse> listAll();

    // Verilen kimliğe sahip dosyanın özetini döner (yoksa ResourceNotFoundException)
    LogFileSummaryResponse getById(UUID id);

    // Bir dosyanın parse edilmiş kayıtlarını döner; level verilirse (null değilse) o seviyeye göre filtreler
    List<LogEntryResponse> getEntries(UUID fileId, LogLevel level);
}
