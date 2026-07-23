package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.domain.LogEntry;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.LogFileStatus;
import com.ailoganalyzer.parse.LogParser;
import com.ailoganalyzer.parse.ParsedLine;
import com.ailoganalyzer.parse.ParsedLog;
import com.ailoganalyzer.repository.LogEntryRepository;
import com.ailoganalyzer.repository.LogFileRepository;
import com.ailoganalyzer.service.LogParsingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * LogParsingService'in uygulaması: parser'ı çağırır, sonuçları LogEntry entity'lerine
 * dönüştürüp kaydeder ve LogFile özetini günceller.
 */
@Service
public class LogParsingServiceImpl implements LogParsingService {

    private final LogParser logParser;                 // Saf parse algoritması (arayüze bağlıyız)
    private final LogEntryRepository logEntryRepository;
    private final LogFileRepository logFileRepository;

    public LogParsingServiceImpl(LogParser logParser,
                                 LogEntryRepository logEntryRepository,
                                 LogFileRepository logFileRepository) {
        this.logParser = logParser;
        this.logEntryRepository = logEntryRepository;
        this.logFileRepository = logFileRepository;
    }

    // Parse + kayıt tek transaction içinde: hata olursa entry'ler ve özet güncellemesi birlikte geri alınır
    @Override
    @Transactional
    public void parseAndPersist(LogFile file, String content) {
        ParsedLog result = logParser.parse(content);

        // Saf ParsedLine'ları JPA entity'lerine dönüştür ve toplu kaydet
        List<LogEntry> entries = result.lines().stream()
                .map(line -> toEntity(file, line))
                .toList();
        logEntryRepository.saveAll(entries);

        // Dosya özetini parse sonuçlarıyla güncelle
        file.setDetectedFormat(result.format());
        file.setErrorCount(result.errorCount());
        file.setWarnCount(result.warnCount());
        file.setParseErrorCount(result.parseErrorCount());
        file.setFirstTs(result.firstTimestamp());
        file.setLastTs(result.lastTimestamp());
        file.setStatus(LogFileStatus.PARSED);          // UPLOADED → PARSED durumuna geç
        logFileRepository.save(file);
    }

    // ParsedLine (saf) → LogEntry (entity) dönüşümü. Sütun sınırlarına karşı savunmacı kırpma yapılır.
    private LogEntry toEntity(LogFile file, ParsedLine line) {
        LogEntry entry = new LogEntry();
        entry.setFile(file);
        entry.setTs(line.timestamp());
        entry.setLevel(line.level());
        entry.setLoggerClass(truncate(line.loggerClass(), 300));   // logger_class VARCHAR(300)
        entry.setThread(truncate(line.thread(), 200));             // thread VARCHAR(200)
        entry.setMessage(line.message());                          // message TEXT (sınırsız)
        entry.setExceptionType(truncate(line.exceptionType(), 300));
        entry.setStackTrace(line.stackTrace());                    // stack_trace TEXT (sınırsız)
        entry.setLineNumber(line.lineNumber());
        // fingerprint (tekrarlanan hata gruplama) Gün 3'te hesaplanacak → şimdilik null
        return entry;
    }

    // Değeri en fazla 'max' karaktere kısaltır; tek bir uzun alan tüm yüklemeyi bozmasın diye (dayanıklılık)
    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
