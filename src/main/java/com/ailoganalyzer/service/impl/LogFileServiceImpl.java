package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.config.StorageProperties;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.LogLevel;
import com.ailoganalyzer.dto.LogEntryResponse;
import com.ailoganalyzer.dto.LogFileSummaryResponse;
import com.ailoganalyzer.exception.InvalidFileException;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import com.ailoganalyzer.repository.LogEntryRepository;
import com.ailoganalyzer.repository.LogFileRepository;
import com.ailoganalyzer.service.ErrorGroupingService;
import com.ailoganalyzer.service.LogFileService;
import com.ailoganalyzer.service.LogParsingService;
import com.ailoganalyzer.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * LogFileService'in uygulaması. Yükleme akışını orkestre eder:
 * doğrulama → diske yazma → DB kaydı → parse (log_entry + özet).
 * Parse algoritması ve persistence ayrı servislere bölünmüştür (Tek Sorumluluk).
 */
@Service                             // Spring servis bileşeni — DI konteyneri bir örnek oluşturup enjekte eder
public class LogFileServiceImpl implements LogFileService {

    private final FileStorageService fileStorageService;   // Depolama soyutlaması (arayüze bağımlıyız, somuta değil)
    private final LogFileRepository logFileRepository;      // DB erişimi (dosyalar)
    private final LogEntryRepository logEntryRepository;    // DB erişimi (parse edilmiş kayıtlar)
    private final StorageProperties storageProperties;     // İzin verilen uzantı gibi kuralları buradan okuruz
    private final LogParsingService logParsingService;     // Ham içeriği parse edip kaydeden servis (arayüz)
    private final ErrorGroupingService errorGroupingService; // Parse sonrası tekrarlanan hataları gruplar (arayüz)

    // Tüm bağımlılıklar constructor ile enjekte edilir (final alanlar → değişmez, test edilebilir, açık bağımlılık)
    public LogFileServiceImpl(FileStorageService fileStorageService,
                              LogFileRepository logFileRepository,
                              LogEntryRepository logEntryRepository,
                              StorageProperties storageProperties,
                              LogParsingService logParsingService,
                              ErrorGroupingService errorGroupingService) {
        this.fileStorageService = fileStorageService;
        this.logFileRepository = logFileRepository;
        this.logEntryRepository = logEntryRepository;
        this.storageProperties = storageProperties;
        this.logParsingService = logParsingService;
        this.errorGroupingService = errorGroupingService;
    }

    // Yükleme: DB'ye yazdığı için okuma-yazma transaction'ı içinde çalışır
    @Override
    @Transactional                   // Metot boyunca tek DB transaction'ı; hata olursa yapılan tüm değişiklikler birlikte geri alınır (atomiklik)
    public LogFileSummaryResponse ingest(String filename, byte[] content) {
        validate(filename, content);                        // Önce iş kuralları: geçerli dosya mı?

        String storagePath = fileStorageService.store(filename, content);  // Ham dosyayı sakla, yolunu al
        int lineCount = countLines(content);                // Hızlı ön satır sayısı

        LogFile saved = logFileRepository.save(              // Entity'yi oluştur ve kalıcılaştır (durum: UPLOADED)
                LogFile.newUpload(filename, content.length, lineCount, storagePath));

        // İçeriği metne çevirip parse et: log_entry kayıtları yazılır, özet güncellenir, durum PARSED olur.
        // (Aynı transaction içinde çalışır; 'saved' yönetilen entity olduğu için güncellemeler DTO'ya yansır.)
        String text = new String(content, StandardCharsets.UTF_8);
        logParsingService.parseAndPersist(saved, text);

        // Parse biter bitmez tekrarlanan hataları grupla (error_group) → istatistikler ve AI için hazır olur
        errorGroupingService.rebuildGroups(saved);

        return LogFileSummaryResponse.from(saved);          // İç modeli dışarı sızdırmadan DTO döndür
    }

    // Listeleme: sadece okuma → readOnly transaction (Hibernate'e dirty-check yapma, performans)
    @Override
    @Transactional(readOnly = true)
    public List<LogFileSummaryResponse> listAll() {
        return logFileRepository.findAllByOrderByUploadedAtDesc()
                .stream()
                .map(LogFileSummaryResponse::from)          // Her entity'yi DTO'ya çevir
                .toList();
    }

    // Tekil erişim: bulunamazsa 404'e karşılık gelen istisna fırlatılır
    @Override
    @Transactional(readOnly = true)
    public LogFileSummaryResponse getById(UUID id) {
        LogFile file = logFileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Log dosyası", id));
        return LogFileSummaryResponse.from(file);
    }

    // Parse edilmiş kayıtları döner; level verilmişse o seviyeye göre filtreler
    @Override
    @Transactional(readOnly = true)
    public List<LogEntryResponse> getEntries(UUID fileId, LogLevel level) {
        // Önce dosyanın var olduğunu doğrula (yoksa filtre boş liste döndürmesin, net 404 verelim)
        if (!logFileRepository.existsById(fileId)) {
            throw new ResourceNotFoundException("Log dosyası", fileId);
        }
        List<com.ailoganalyzer.domain.LogEntry> entries = (level == null)
                ? logEntryRepository.findByFileIdOrderByLineNumberAsc(fileId)
                : logEntryRepository.findByFileIdAndLevelOrderByLineNumberAsc(fileId, level);
        return entries.stream().map(LogEntryResponse::from).toList();
    }

    // Silme: DB kaydını siler (bağlı kayıtlar/analizler/sohbet ON DELETE CASCADE ile gider), sonra diskteki ham dosyayı temizler
    @Override
    @Transactional
    public void delete(UUID id) {
        LogFile file = logFileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Log dosyası", id));
        String storagePath = file.getStoragePath();

        logFileRepository.delete(file);                 // Veritabanı kaydı (ve cascade ile alt kayıtlar) silinir
        fileStorageService.deleteQuietly(storagePath);  // Diskteki ham dosyayı en iyi çaba ile sil
    }

    // --- Yardımcı (private) metotlar: iş kurallarını okunur parçalara böler ---

    // Dosyanın boş olmadığını ve uzantısının izinli olduğunu doğrular
    private void validate(String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidFileException("Dosya boş.");
        }
        if (filename == null || filename.isBlank()) {
            throw new InvalidFileException("Dosya adı boş olamaz.");
        }
        String extension = extractExtension(filename);
        if (!storageProperties.allowedExtensions().contains(extension)) {
            throw new InvalidFileException(
                    "Desteklenmeyen dosya türü: ." + extension +
                    " (izin verilenler: " + storageProperties.allowedExtensions() + ")");
        }
    }

    // Dosya adından uzantıyı küçük harfe çevirerek çıkarır (örn. "app.LOG" → "log")
    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new InvalidFileException("Dosya uzantısı bulunamadı: " + filename);
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // İçerikteki satır sayısını verimli biçimde sayar (dosyayı String'e çevirmeden, bayt üzerinden)
    private int countLines(byte[] content) {
        int lines = 0;
        for (byte b : content) {
            if (b == '\n') {
                lines++;
            }
        }
        // Son satır '\n' ile bitmiyorsa onu da say (örn. tek satırlık, newline'sız dosya)
        if (content.length > 0 && content[content.length - 1] != '\n') {
            lines++;
        }
        return lines;
    }
}
