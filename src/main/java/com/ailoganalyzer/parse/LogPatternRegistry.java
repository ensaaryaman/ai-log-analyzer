package com.ailoganalyzer.parse;

import com.ailoganalyzer.domain.LogFormat;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bilinen log formatı desenlerini öncelik sırasına göre tutar.
 * OCP (Açık/Kapalı): Yeni bir format desteği eklemek, buradaki listeye bir desen eklemek demektir;
 * parser'ın algoritması değişmez.
 *
 * Sıralama önemlidir: en "ayırt edici" (spesifik) desen en başta olmalı ki
 * genel desenler onu yanlışlıkla kapmasın (SPRING_BOOT, LOG4J'den daha spesifiktir).
 */
@Component
public class LogPatternRegistry {

    // Ortak zaman damgası alt-deseni: "2026-07-20T14:02:11.123+03:00" ve benzeri varyantlar
    // Milisaniye ve zaman dilimi opsiyoneldir; T veya boşluk ayracı kabul edilir.
    private static final String TS =
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:\\s?(?:[+-]\\d{2}:?\\d{2}|Z))?";

    // Geçerli log seviyeleri (regex alternatifi olarak)
    private static final String LVL = "TRACE|DEBUG|INFO|WARN|ERROR|FATAL";

    private final List<LogLinePattern> patterns = List.of(
            // 1) Spring Boot: "TS LEVEL <pid> --- [thread] logger : mesaj" (en spesifik → en başta)
            new LogLinePattern(LogFormat.SPRING_BOOT,
                    "^(?<ts>" + TS + ")\\s+(?<level>" + LVL + ")\\s+\\d+\\s+---\\s+" +
                    "\\[\\s*(?<thread>[^\\]]*?)\\s*\\]\\s+(?<logger>\\S+)\\s+:\\s?(?<msg>.*)$"),

            // 2) Log4j: "TS LEVEL [thread] logger - mesaj" (seviye, thread'den ÖNCE)
            new LogLinePattern(LogFormat.LOG4J,
                    "^(?<ts>" + TS + ")\\s+(?<level>" + LVL + ")\\s+" +
                    "\\[(?<thread>[^\\]]*)\\]\\s+(?<logger>\\S+)\\s+-\\s?(?<msg>.*)$"),

            // 3) Logback: "TS [thread] LEVEL logger - mesaj" (thread, seviyeden ÖNCE)
            new LogLinePattern(LogFormat.LOGBACK,
                    "^(?<ts>" + TS + ")\\s+\\[(?<thread>[^\\]]*)\\]\\s+" +
                    "(?<level>" + LVL + ")\\s+(?<logger>\\S+)\\s+-\\s?(?<msg>.*)$")
    );

    // Desenleri öncelik sırasıyla döner (değiştirilemez liste)
    public List<LogLinePattern> patterns() {
        return patterns;
    }
}
