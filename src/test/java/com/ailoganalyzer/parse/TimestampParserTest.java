package com.ailoganalyzer.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TimestampParser birim testleri: farklı log formatlarındaki zaman damgası varyantlarının
 * doğru ayrıştırıldığını doğrular. Zaman dilimsiz biçimler için sabit bir varsayılan dilim
 * (Europe/Istanbul, +03:00) kullanılır → testler deterministiktir.
 */
class TimestampParserTest {

    private final TimestampParser parser = new TimestampParser(ZoneId.of("Europe/Istanbul"));

    @Test
    @DisplayName("T ayraçlı ve offset'li ISO biçimini ayrıştırır")
    void parsesIsoWithOffset() {
        assertThat(parser.parse("2026-07-20T14:02:11.123+03:00"))
                .contains(OffsetDateTime.parse("2026-07-20T14:02:11.123+03:00"));
    }

    @Test
    @DisplayName("Z (UTC) zaman dilimini ayrıştırır")
    void parsesZuluZone() {
        assertThat(parser.parse("2026-07-20T14:02:11.123Z"))
                .contains(OffsetDateTime.parse("2026-07-20T14:02:11.123Z"));
    }

    @Test
    @DisplayName("Boşluk ayraçlı ve virgüllü milisaniye (zaman dilimsiz) — varsayılan dilim eklenir")
    void parsesSpaceSeparatedCommaMillis() {
        assertThat(parser.parse("2026-07-21 09:15:01,010"))
                .contains(OffsetDateTime.parse("2026-07-21T09:15:01.010+03:00"));
    }

    @Test
    @DisplayName("Boşluk ayraçlı ve noktalı milisaniye — varsayılan dilim eklenir")
    void parsesSpaceSeparatedDotMillis() {
        assertThat(parser.parse("2026-07-22 22:03:11.900"))
                .contains(OffsetDateTime.parse("2026-07-22T22:03:11.900+03:00"));
    }

    @Test
    @DisplayName("Milisaniyesiz biçimi ayrıştırır")
    void parsesWithoutMillis() {
        assertThat(parser.parse("2026-07-22 10:00:05"))
                .contains(OffsetDateTime.parse("2026-07-22T10:00:05+03:00"));
    }

    @Test
    @DisplayName("Ayrıştırılamayan metinde boş döner (satır atılmaz, timestamp'siz devam eder)")
    void returnsEmptyForInvalid() {
        assertThat(parser.parse("bu bir tarih degil")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
    }
}
