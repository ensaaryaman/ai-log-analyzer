package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.config.StorageProperties;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.dto.LogFileSummaryResponse;
import com.ailoganalyzer.exception.InvalidFileException;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import com.ailoganalyzer.repository.LogFileRepository;
import com.ailoganalyzer.service.LogFileService;
import com.ailoganalyzer.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * LogFileService'in uygulaması. Yükleme akışının iş kurallarını içerir:
 * doğrulama → diske yazma → satır sayımı → DB kaydı.
 * Not: Ayrıntılı parse (Gün 2) ve AI analizi (Gün 4) ayrı servislere bırakılmıştır (Tek Sorumluluk).
 */
@Service                             // Spring servis bileşeni — DI konteyneri bir örnek oluşturup enjekte eder
public class LogFileServiceImpl implements LogFileService {

    private final FileStorageService fileStorageService;   // Depolama soyutlaması (arayüze bağımlıyız, somuta değil)
    private final LogFileRepository logFileRepository;      // DB erişimi
    private final StorageProperties storageProperties;     // İzin verilen uzantı gibi kuralları buradan okuruz

    // Tüm bağımlılıklar constructor ile enjekte edilir (final alanlar → değişmez, test edilebilir, açık bağımlılık)
    public LogFileServiceImpl(FileStorageService fileStorageService,
                              LogFileRepository logFileRepository,
                              StorageProperties storageProperties) {
        this.fileStorageService = fileStorageService;
        this.logFileRepository = logFileRepository;
        this.storageProperties = storageProperties;
    }

    // Yükleme: DB'ye yazdığı için okuma-yazma transaction'ı içinde çalışır
    @Override
    @Transactional                   // Metot boyunca tek DB transaction'ı; hata olursa yapılan değişiklikler geri alınır (atomiklik)
    public LogFileSummaryResponse ingest(String filename, byte[] content) {
        validate(filename, content);                        // Önce iş kuralları: geçerli dosya mı?

        String storagePath = fileStorageService.store(filename, content);  // Ham dosyayı sakla, yolunu al
        int lineCount = countLines(content);                // Hızlı ön özet (ayrıntılı parse Gün 2'de)

        LogFile saved = logFileRepository.save(              // Entity'yi oluştur ve kalıcılaştır
                LogFile.newUpload(filename, content.length, lineCount, storagePath));

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
