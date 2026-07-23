package com.ailoganalyzer.parse;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Optional;

/**
 * Log satırlarındaki zaman damgalarını esnek biçimde ayrıştırır.
 * Farklı formatlar farklı biçimler kullanır (T veya boşluk ayracı, nokta veya virgül milisaniye,
 * zaman dilimli veya dilimsiz). Bu sınıf bunları sırayla dener; hiçbiri tutmazsa boş döner
 * (satır atılmaz, sadece timestamp'siz saklanır — dayanıklılık ilkesi).
 */
@Component                           // Spring bileşeni: parser'a enjekte edilir (test için elle de örneklenebilir)
public class TimestampParser {

    private final ZoneId defaultZone;   // Zaman dilimi belirtilmeyen loglar için varsayılan dilim

    // Varsayılan olarak sistemin zaman dilimini kullanır (constructor injection ile testte değiştirilebilir)
    public TimestampParser() {
        this(ZoneId.systemDefault());
    }

    public TimestampParser(ZoneId defaultZone) {
        this.defaultZone = defaultZone;
    }

    // Zaman dilimi İÇEREN biçimler (T ve boşluk ayraçlı) → doğrudan OffsetDateTime
    private static final List<DateTimeFormatter> OFFSET_FORMATTERS = List.of(
            offsetFormatter('T'),
            offsetFormatter(' ')
    );

    // Zaman dilimi İÇERMEYEN biçimler → LocalDateTime olarak okunur, sonra varsayılan dilim eklenir
    private static final List<DateTimeFormatter> LOCAL_FORMATTERS = List.of(
            localFormatter('T'),
            localFormatter(' ')
    );

    /**
     * Ham zaman damgası metnini OffsetDateTime'a çevirir.
     * Önce virgüllü milisaniyeyi noktaya normalize eder, sonra biçimleri sırayla dener.
     */
    public Optional<OffsetDateTime> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().replace(',', '.');   // "14:02:11,123" → "14:02:11.123"

        // 1) Zaman dilimli biçimleri dene
        for (DateTimeFormatter fmt : OFFSET_FORMATTERS) {
            try {
                return Optional.of(OffsetDateTime.parse(normalized, fmt));
            } catch (Exception ignored) {
                // sıradaki biçimi dene
            }
        }
        // 2) Zaman dilimsiz biçimleri dene, varsayılan dilimi ekle
        for (DateTimeFormatter fmt : LOCAL_FORMATTERS) {
            try {
                LocalDateTime local = LocalDateTime.parse(normalized, fmt);
                return Optional.of(local.atZone(defaultZone).toOffsetDateTime());
            } catch (Exception ignored) {
                // sıradaki biçimi dene
            }
        }
        return Optional.empty();     // Hiçbiri tutmadı → timestamp'siz devam
    }

    // "yyyy-MM-dd<sep>HH:mm:ss[.fraction][offset]" biçimini kurar (offset zorunlu değil ama varsa okunur)
    private static DateTimeFormatter offsetFormatter(char separator) {
        return baseBuilder(separator)
                .optionalStart().appendOffsetId().optionalEnd()   // +03:00 veya Z
                .toFormatter();
    }

    // Zaman dilimsiz biçim (offset kısmı yok)
    private static DateTimeFormatter localFormatter(char separator) {
        return baseBuilder(separator).toFormatter();
    }

    // Tarih + ayraç + saat + opsiyonel kesirli saniye (nanoya kadar) — ortak iskele
    private static DateTimeFormatterBuilder baseBuilder(char separator) {
        return new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd")
                .appendLiteral(separator)
                .appendPattern("HH:mm:ss")
                .optionalStart()
                .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)   // ".123" gibi kesir (opsiyonel)
                .optionalEnd();
    }
}
