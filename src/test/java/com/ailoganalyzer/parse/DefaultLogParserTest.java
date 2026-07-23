package com.ailoganalyzer.parse;

import com.ailoganalyzer.domain.LogFormat;
import com.ailoganalyzer.domain.LogLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultLogParser için birim testleri.
 * Bu testler SAF'tır: Spring bağlamı veya veritabanı gerektirmez → milisaniyeler içinde koşar.
 * Parser, projenin en çok edge-case üreten parçası olduğundan test bütçesinin çoğu buradadır.
 * Kullanılan örnek dosyalar (src/test/resources/logs) aynı zamanda teslimattaki "örnek loglar"dır.
 */
class DefaultLogParserTest {

    // Test edilen parser: gerçek desen kaydı + sabit zaman dilimli timestamp ayrıştırıcı (deterministik)
    private final LogParser parser =
            new DefaultLogParser(new LogPatternRegistry(), new TimestampParser(ZoneId.of("Europe/Istanbul")));

    // Test kaynaklarından bir log dosyasını metin olarak yükler
    private String load(String name) throws Exception {
        try (var is = getClass().getResourceAsStream("/logs/" + name)) {
            assertThat(is).as("Test kaynağı bulunamadı: " + name).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Spring Boot formatını tespit eder ve sayaçları doğru hesaplar")
    void springBootDetectionAndCounts() throws Exception {
        ParsedLog result = parser.parse(load("spring-boot-db-pool.log"));

        assertThat(result.format()).isEqualTo(LogFormat.SPRING_BOOT);
        assertThat(result.lines()).hasSize(5);            // 2 INFO + 1 WARN + 2 ERROR (stack trace'ler ayrı kayıt değil)
        assertThat(result.errorCount()).isEqualTo(2);
        assertThat(result.warnCount()).isEqualTo(1);
        assertThat(result.parseErrorCount()).isZero();    // Her satır bir kayda bağlandı
    }

    @Test
    @DisplayName("Çok satırlı stack trace önceki kayda birleştirilir, sonraki kayda taşmaz")
    void mergesMultilineStackTrace() throws Exception {
        ParsedLog result = parser.parse(load("spring-boot-db-pool.log"));

        ParsedLine firstError = result.lines().stream()
                .filter(l -> l.level() == LogLevel.ERROR)
                .findFirst().orElseThrow();

        // Stack trace birleştirildi ve exception tipi çıkarıldı
        assertThat(firstError.exceptionType()).isEqualTo("java.sql.SQLTransientConnectionException");
        assertThat(firstError.stackTrace())
                .contains("at com.zaxxer.hikari.pool.HikariPool")
                .contains("Caused by: java.net.SocketTimeoutException");
        assertThat(firstError.lineNumber()).isEqualTo(3);  // Orijinal dosyadaki 3. satır

        // Stack trace'ten hemen sonraki INFO kaydı, stack satırlarını YUTMAMALI (ayrı kayıt kalmalı)
        ParsedLine infoAfter = result.lines().stream()
                .filter(l -> l.level() == LogLevel.INFO && l.message().contains("Yeniden deneme"))
                .findFirst().orElseThrow();
        assertThat(infoAfter.stackTrace()).isNull();
    }

    @Test
    @DisplayName("Offset içeren zaman damgasını doğru ayrıştırır")
    void parsesTimestampWithOffset() throws Exception {
        ParsedLog result = parser.parse(load("spring-boot-db-pool.log"));

        ParsedLine first = result.lines().get(0);
        assertThat(first.timestamp()).isEqualTo(OffsetDateTime.parse("2026-07-20T14:02:11.123+03:00"));
        assertThat(result.firstTimestamp()).isEqualTo(OffsetDateTime.parse("2026-07-20T14:02:11.123+03:00"));
    }

    @Test
    @DisplayName("Log4j formatını tespit eder ve NullPointerException'ı çıkarır")
    void log4jDetectionAndException() throws Exception {
        ParsedLog result = parser.parse(load("log4j-npe.log"));

        assertThat(result.format()).isEqualTo(LogFormat.LOG4J);
        assertThat(result.lines()).hasSize(3);
        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.warnCount()).isEqualTo(1);

        ParsedLine error = result.lines().stream()
                .filter(l -> l.level() == LogLevel.ERROR).findFirst().orElseThrow();
        assertThat(error.exceptionType()).isEqualTo("java.lang.NullPointerException");
        assertThat(error.thread()).isEqualTo("worker-3");
        assertThat(error.loggerClass()).isEqualTo("com.example.app.UserService");
    }

    @Test
    @DisplayName("Logback formatını tespit eder ve OutOfMemoryError'ı çıkarır")
    void logbackDetectionAndException() throws Exception {
        ParsedLog result = parser.parse(load("logback-oom.log"));

        assertThat(result.format()).isEqualTo(LogFormat.LOGBACK);
        assertThat(result.lines()).hasSize(2);
        assertThat(result.errorCount()).isEqualTo(1);

        ParsedLine error = result.lines().stream()
                .filter(l -> l.level() == LogLevel.ERROR).findFirst().orElseThrow();
        assertThat(error.exceptionType()).isEqualTo("java.lang.OutOfMemoryError");
        assertThat(error.thread()).isEqualTo("scheduler-1");
    }

    @Test
    @DisplayName("Bilinmeyen formatta yedek stratejiye düşer ve ayrıştırılamayan satırları sayar")
    void fallbackForUnknownFormat() throws Exception {
        ParsedLog result = parser.parse(load("mixed-unknown.log"));

        assertThat(result.format()).isEqualTo(LogFormat.UNKNOWN);
        assertThat(result.lines()).hasSize(6);            // Her boş olmayan satır bir kayıt
        assertThat(result.errorCount()).isEqualTo(1);     // "ERROR: disk usage..."
        assertThat(result.warnCount()).isEqualTo(1);      // "WARN retrying..."
        assertThat(result.parseErrorCount()).isEqualTo(4);// Seviyesi tespit edilemeyen 4 satır
    }

    @Test
    @DisplayName("Boş içerik güvenle boş sonuç döner")
    void emptyContentReturnsEmptyResult() {
        ParsedLog result = parser.parse("");

        assertThat(result.format()).isEqualTo(LogFormat.UNKNOWN);
        assertThat(result.lines()).isEmpty();
        assertThat(result.errorCount()).isZero();
        assertThat(result.firstTimestamp()).isNull();
    }

    @Test
    @DisplayName("Boş satırlar kayıt üretmez")
    void blankLinesAreIgnored() {
        String content = "2026-07-21 09:15:01,010 INFO  [main] com.example.App - basladi\n\n\n"
                + "2026-07-21 09:15:02,020 ERROR [main] com.example.App - hata\n";
        ParsedLog result = parser.parse(content);

        assertThat(result.lines()).hasSize(2);            // Aradaki boş satırlar sayılmadı
        assertThat(result.format()).isEqualTo(LogFormat.LOG4J);
    }
}
