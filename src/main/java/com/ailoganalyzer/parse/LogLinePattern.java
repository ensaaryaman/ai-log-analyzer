package com.ailoganalyzer.parse;

import com.ailoganalyzer.domain.LogFormat;
import com.ailoganalyzer.domain.LogLevel;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tek bir log formatının "başlık satırı" desenini temsil eder.
 * Bir satırın bu formatta yeni bir kayıt başlatıp başlatmadığını söyler ve alanları çıkarır.
 * Tüm desenler ortak isimli grupları kullanır: ts, level, thread, logger, msg.
 */
public class LogLinePattern {

    private final LogFormat format;
    private final Pattern pattern;   // Derlenmiş regex (isimli gruplarla)

    public LogLinePattern(LogFormat format, String regex) {
        this.format = format;
        this.pattern = Pattern.compile(regex);
    }

    public LogFormat format() {
        return format;
    }

    // Satır bu formatta bir başlık satırı mı? (format tespitinde ve parse'ta kullanılır)
    public boolean matches(String line) {
        return pattern.matcher(line).matches();
    }

    // Eşleşiyorsa alanları çıkarır; eşleşmiyorsa boş döner
    public Optional<HeaderFields> parseHeader(String line) {
        Matcher m = pattern.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        // Regex yalnızca geçerli seviye anahtar sözcüklerini eşlediği için valueOf güvenlidir
        LogLevel level = LogLevel.valueOf(m.group("level"));
        return Optional.of(new HeaderFields(
                groupOrNull(m, "ts"),
                level,
                trimOrNull(groupOrNull(m, "thread")),
                groupOrNull(m, "logger"),
                groupOrNull(m, "msg")
        ));
    }

    // İsimli grup yoksa/boşsa null döner (bazı formatlarda grup opsiyonel olabilir)
    private static String groupOrNull(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            return null;   // Bu desende böyle bir grup tanımlı değil
        }
    }

    private static String trimOrNull(String s) {
        return s == null ? null : s.trim();
    }
}
