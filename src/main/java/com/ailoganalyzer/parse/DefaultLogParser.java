package com.ailoganalyzer.parse;

import com.ailoganalyzer.domain.LogFormat;
import com.ailoganalyzer.domain.LogLevel;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LogParser'ın varsayılan uygulaması.
 * Akış: (1) formatı tespit et → (2) satırları gez, başlık satırları yeni kayıt başlatır,
 * diğer satırlar önceki kaydın stack trace'ine eklenir → (3) exception tipini çıkar,
 * istatistikleri hesapla. Bilinmeyen formatta satır-bazlı bir yedek (fallback) strateji uygulanır.
 */
@Component
public class DefaultLogParser implements LogParser {

    private final LogPatternRegistry registry;
    private final TimestampParser timestampParser;

    // Bağımlılıklar constructor ile enjekte edilir (test için elle de örneklenebilir)
    public DefaultLogParser(LogPatternRegistry registry, TimestampParser timestampParser) {
        this.registry = registry;
        this.timestampParser = timestampParser;
    }

    // Format tespitinde kaç satıra bakılacağı (tüm dosyayı taramaya gerek yok — başlar başlamaz belli olur)
    private static final int DETECTION_SAMPLE_SIZE = 100;

    // Stack trace / mesajdan istisna tipini yakalar: "...Exception", "...Error", "...Throwable" ile biten nitelikli ad
    private static final Pattern EXCEPTION_TYPE =
            Pattern.compile("([\\w$]+(?:\\.[\\w$]+)*(?:Exception|Error|Throwable))");

    // Bilinmeyen formatta bir satırda seviye anahtar sözcüğü aramak için
    private static final Pattern LEVEL_KEYWORD =
            Pattern.compile("\\b(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\b");

    // Bilinmeyen formatta satır başındaki zaman damgasını yakalamak için (opsiyonel)
    private static final Pattern LEADING_TS = Pattern.compile(
            "^\\s*(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:\\s?(?:[+-]\\d{2}:?\\d{2}|Z))?)");

    // Ana giriş: ham metni ParsedLog'a çevirir
    @Override
    public ParsedLog parse(String content) {
        if (content == null || content.isBlank()) {
            return new ParsedLog(LogFormat.UNKNOWN, List.of(), 0, 0, 0, null, null);
        }
        String[] lines = content.split("\\R", -1);              // \R: her tür satır sonunu (\n, \r\n) böler
        Optional<LogLinePattern> detected = detectFormat(lines);

        // Tespit sonucuna göre iki farklı strateji
        List<ParsedLine> parsed = detected.isPresent()
                ? parseWithHeaderPattern(lines, detected.get())
                : parseFallback(lines);

        LogFormat format = detected.map(LogLinePattern::format).orElse(LogFormat.UNKNOWN);
        return buildResult(format, parsed, detected.isPresent(), lines);
    }

    // --- 1) Format tespiti: örnek satırlarda en çok eşleşen deseni seç ---
    private Optional<LogLinePattern> detectFormat(String[] lines) {
        LogLinePattern best = null;
        int bestScore = 0;
        int sampled = 0;

        for (LogLinePattern pattern : registry.patterns()) {
            int score = 0;
            sampled = 0;
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                if (pattern.matches(line)) {
                    score++;
                }
                if (++sampled >= DETECTION_SAMPLE_SIZE) {
                    break;
                }
            }
            if (score > bestScore) {           // En yüksek skorlu deseni tut
                bestScore = score;
                best = pattern;
            }
        }
        // Hiç eşleşme yoksa (bestScore == 0) format bilinmiyordur → fallback
        return Optional.ofNullable(bestScore > 0 ? best : null);
    }

    // --- 2a) Bilinen format: başlık satırları kayıt başlatır, diğerleri stack trace olarak eklenir ---
    private List<ParsedLine> parseWithHeaderPattern(String[] lines, LogLinePattern pattern) {
        List<ParsedLine> result = new ArrayList<>();
        Pending pending = null;                 // Şu an biriktirilmekte olan kayıt

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;                       // Boş satırları atla (kayıt başlatmaz, gürültü eklemez)
            }
            Optional<HeaderFields> header = pattern.parseHeader(line);
            if (header.isPresent()) {
                if (pending != null) {
                    result.add(finalizeEntry(pending));   // Önceki kaydı tamamla
                }
                pending = new Pending(header.get(), i + 1);   // Yeni kayıt başlat (1-tabanlı satır no)
            } else if (pending != null) {
                pending.continuation.add(line);  // Başlık değil → önceki kaydın devamı (stack trace)
            }
            // pending == null iken başlık olmayan satır: ilk kayıttan önceki "çöp" → aşağıda sayılır
        }
        if (pending != null) {
            result.add(finalizeEntry(pending));  // Dosya sonundaki son kaydı da ekle
        }
        return result;
    }

    // --- 2b) Bilinmeyen format: her satırı ayrı bir kayıt olarak al, seviyeyi anahtar sözcükten tahmin et ---
    private List<ParsedLine> parseFallback(String[] lines) {
        List<ParsedLine> result = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            LogLevel level = findLevel(line);                  // Bulunamazsa null (parse hatası olarak sayılır)
            OffsetDateTime ts = findLeadingTimestamp(line);
            String exceptionType = extractExceptionType(line);
            result.add(new ParsedLine(ts, level, null, null, line, exceptionType, null, i + 1));
        }
        return result;
    }

    // Biriktirilen bir kaydı nihai ParsedLine'a dönüştürür (timestamp çevir, stack trace birleştir, exception çıkar)
    private ParsedLine finalizeEntry(Pending pending) {
        HeaderFields h = pending.header;
        OffsetDateTime ts = timestampParser.parse(h.rawTimestamp()).orElse(null);
        String stackTrace = pending.continuation.isEmpty() ? null : String.join("\n", pending.continuation);
        String exceptionType = extractExceptionType(
                (h.message() == null ? "" : h.message()) + "\n" + (stackTrace == null ? "" : stackTrace));
        return new ParsedLine(ts, h.level(), h.logger(), h.thread(), h.message(),
                exceptionType, stackTrace, pending.lineNumber);
    }

    // --- 3) Sonuç nesnesini kur: sayaçlar + zaman aralığı + parse hatası sayısı ---
    private ParsedLog buildResult(LogFormat format, List<ParsedLine> lines, boolean knownFormat, String[] rawLines) {
        int errorCount = 0, warnCount = 0;
        OffsetDateTime first = null, last = null;

        for (ParsedLine l : lines) {
            if (l.level() == LogLevel.ERROR || l.level() == LogLevel.FATAL) {
                errorCount++;
            } else if (l.level() == LogLevel.WARN) {
                warnCount++;
            }
            if (l.timestamp() != null) {
                if (first == null || l.timestamp().isBefore(first)) {
                    first = l.timestamp();
                }
                if (last == null || l.timestamp().isAfter(last)) {
                    last = l.timestamp();
                }
            }
        }
        int parseErrorCount = countParseErrors(knownFormat, lines, rawLines);
        return new ParsedLog(format, lines, errorCount, warnCount, parseErrorCount, first, last);
    }

    // Parse edilemeyen satır sayısı (dürüst raporlama):
    // - bilinen formatta: hiçbir kayda bağlanamayan, ilk başlıktan önceki boş-olmayan satırlar
    // - bilinmeyen formatta: seviyesi tespit edilemeyen satırlar
    private int countParseErrors(boolean knownFormat, List<ParsedLine> lines, String[] rawLines) {
        if (!knownFormat) {
            return (int) lines.stream().filter(l -> l.level() == null).count();
        }
        if (lines.isEmpty()) {
            // Format eşleşti ama hiç kayıt yoksa: boş olmayan tüm satırlar çözümlenememiştir
            return (int) java.util.Arrays.stream(rawLines).filter(s -> !s.isBlank()).count();
        }
        int firstEntryLine = lines.get(0).lineNumber();     // İlk kaydın başladığı satır (1-tabanlı)
        int preamble = 0;
        for (int i = 0; i < firstEntryLine - 1; i++) {
            if (!rawLines[i].isBlank()) {
                preamble++;                                 // İlk kayıttan önceki bağlanamayan satırlar
            }
        }
        return preamble;
    }

    // Metinden ilk istisna tipini çıkarır (yoksa null)
    private String extractExceptionType(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = EXCEPTION_TYPE.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    // Satırdaki ilk seviye anahtar sözcüğünü bulur (yoksa null)
    private LogLevel findLevel(String line) {
        Matcher m = LEVEL_KEYWORD.matcher(line);
        return m.find() ? LogLevel.valueOf(m.group(1)) : null;
    }

    // Satır başındaki zaman damgasını (varsa) ayrıştırır
    private OffsetDateTime findLeadingTimestamp(String line) {
        Matcher m = LEADING_TS.matcher(line);
        return m.find() ? timestampParser.parse(m.group(1)).orElse(null) : null;
    }

    // Biriktirilmekte olan kaydın geçici (mutable) hâli — parse boyunca kullanılır, dışarı sızmaz
    private static final class Pending {
        private final HeaderFields header;
        private final int lineNumber;
        private final List<String> continuation = new ArrayList<>();   // Stack trace / devam satırları

        private Pending(HeaderFields header, int lineNumber) {
            this.header = header;
            this.lineNumber = lineNumber;
        }
    }
}
