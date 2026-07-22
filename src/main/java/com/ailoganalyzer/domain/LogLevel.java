package com.ailoganalyzer.domain;

/**
 * Log seviyeleri, şiddet sırasına göre (düşükten yükseğe) tanımlıdır.
 * Sıralama önemlidir: ordinal değeri "en az X seviyesindeki kayıtlar" gibi
 * karşılaştırmalarda kullanılır (örn. WARN ve üzerini seç).
 */
public enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL;

    // Verilen seviyenin en az bu seviye kadar şiddetli olup olmadığını söyler (WARN+ filtreleme için)
    public boolean isAtLeast(LogLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
