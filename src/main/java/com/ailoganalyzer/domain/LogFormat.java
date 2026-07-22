package com.ailoganalyzer.domain;

/**
 * Parser tarafından tespit edilen log dosyası formatı.
 * UNKNOWN, hiçbir bilinen desene uymayan dosyalar için fallback'tir —
 * bu durumda bile satırlar tamamen atılmaz (bkz. parse stratejisi).
 */
public enum LogFormat {
    SPRING_BOOT,   // 2026-07-20T14:02:11.123+03:00 ERROR 1234 --- [thread] c.e.Service : mesaj
    LOG4J,         // 2026-07-20 14:02:11,123 [thread] ERROR c.e.Service - mesaj
    LOGBACK,       // Logback'in yaygın pattern varyantı
    SYSLOG,        // Syslog benzeri satırlar
    UNKNOWN        // Tanınamayan format — satır bazlı fallback uygulanır
}
