package com.ailoganalyzer.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnalysisPromptBuilder birim testi. Prompt kurma mantığı SAF olduğu için Docker/AI gerekmeden,
 * "damıtılmış bağlam doğru metne dönüşüyor mu?" sorusu hızlıca doğrulanır.
 */
class AnalysisPromptBuilderTest {

    private final AnalysisPromptBuilder builder = new AnalysisPromptBuilder();

    @Test
    @DisplayName("Bağlamdaki dosya, istatistik ve hata grupları prompt metnine yansır")
    void buildsUserPromptFromContext() {
        Map<String, Long> levels = new LinkedHashMap<>();
        levels.put("ERROR", 2L);
        levels.put("WARN", 1L);

        PromptContext ctx = new PromptContext(
                "app.log", "SPRING_BOOT", 100, 2, 1,
                OffsetDateTime.parse("2026-07-20T14:00:00Z"),
                OffsetDateTime.parse("2026-07-20T14:10:00Z"),
                levels,
                List.of(new ErrorGroupDigest(
                        "java.sql.SQLException", "DB baglanti hatasi", 3,
                        OffsetDateTime.parse("2026-07-20T14:05:00Z"),
                        OffsetDateTime.parse("2026-07-20T14:07:00Z"),
                        42, "at com.example.Foo.bar(Foo.java:10)")));

        AnalysisPrompt prompt = builder.build(ctx);

        // Sistem promptu rol ve kuralları içermeli
        assertThat(prompt.system()).contains("CRITICAL").contains("TÜRKÇE");
        // Kullanıcı promptu damıtılmış bağlamı içermeli
        assertThat(prompt.user())
                .contains("app.log")
                .contains("SPRING_BOOT")
                .contains("java.sql.SQLException")
                .contains("×3")
                .contains("satır 42")
                .contains("at com.example.Foo.bar");
    }

    @Test
    @DisplayName("Hata grubu yoksa prompt yine de geçerli üretilir")
    void handlesNoErrorGroups() {
        PromptContext ctx = new PromptContext(
                "clean.log", "LOGBACK", 10, 0, 0, null, null, Map.of("INFO", 10L), List.of());

        AnalysisPrompt prompt = builder.build(ctx);
        assertThat(prompt.user()).contains("belirgin bir hata grubu yok");
    }
}
