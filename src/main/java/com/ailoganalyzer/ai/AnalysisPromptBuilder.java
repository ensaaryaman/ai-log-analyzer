package com.ailoganalyzer.ai;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Damıtılmış bağlamı (PromptContext) modele gönderilecek metne dönüştürür.
 * SAF bir bileşendir (I/O yok) → prompt'un doğru kurulduğu birim testiyle doğrulanır.
 *
 * TASARIM: Ham log ASLA gönderilmez. Bunun yerine "hata grupları + istatistik + kanıt satırları"
 * gönderilir. Bu, hem token maliyetini düşürür hem de analiz kalitesini artırır.
 */
@Component
public class AnalysisPromptBuilder {

    // Modelin rolü ve uyması gereken kurallar. Sürüm: v1 (Analysis.promptVersion ile eşleşir).
    static final String SYSTEM_PROMPT = """
            Sen kıdemli bir SRE / backend mühendisisin. Sana bir uygulamanın log dosyasından
            DAMITILMIŞ bilgiler verilecek (ham log değil, özet + hata grupları + kanıt satırları).
            Görevin:
            1) Sorunu kısaca özetle.
            2) En olası KÖK NEDENİ belirle ve kanıta dayandır.
            3) Somut, uygulanabilir ÇÖZÜM adımları öner.
            4) Öncelik ata: CRITICAL, HIGH, MEDIUM veya LOW.
            5) 0.0-1.0 arası bir GÜVEN seviyesi ver; kanıt zayıfsa düşür.
            6) Dayandığın orijinal satır numaralarını evidenceLines listesine yaz.
            Emin olmadığın şeyi UYDURMA. Tüm metinsel yanıtları TÜRKÇE yaz.
            """;

    // Sistem + kullanıcı promptunu bir arada döner
    public AnalysisPrompt build(PromptContext ctx) {
        return new AnalysisPrompt(SYSTEM_PROMPT, buildUserPrompt(ctx));
    }

    // Damıtılmış bağlamı okunur bir metne dönüştürür (modelin göreceği içerik)
    private String buildUserPrompt(PromptContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("DOSYA: ").append(ctx.filename())
          .append(" | format: ").append(ctx.format())
          .append(" | ").append(ctx.totalEntries()).append(" kayıt")
          .append(" | ").append(ctx.errorCount()).append(" ERROR, ")
          .append(ctx.warnCount()).append(" WARN\n");

        if (ctx.firstTs() != null && ctx.lastTs() != null) {
            sb.append("ZAMAN ARALIĞI: ").append(ctx.firstTs()).append(" — ").append(ctx.lastTs()).append("\n");
        }

        sb.append("\nSEVİYE DAĞILIMI:\n");
        for (Map.Entry<String, Long> e : ctx.levelDistribution().entrySet()) {
            sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }

        sb.append("\nHATA GRUPLARI (tekrar sayısına göre):\n");
        if (ctx.errorGroups().isEmpty()) {
            sb.append("  (belirgin bir hata grubu yok)\n");
        } else {
            int index = 1;
            for (ErrorGroupDigest g : ctx.errorGroups()) {
                sb.append(index++).append(") ")
                  .append(g.exceptionType() == null ? "(istisna yok)" : g.exceptionType())
                  .append(" ×").append(g.occurrenceCount());
                if (g.firstSeen() != null) {
                    sb.append("  [ilk: ").append(g.firstSeen()).append(", son: ").append(g.lastSeen()).append("]");
                }
                if (g.sampleLineNumber() != null) {
                    sb.append("  (satır ").append(g.sampleLineNumber()).append(")");
                }
                sb.append("\n     mesaj: ").append(g.sampleMessage()).append("\n");
                if (g.stackTraceExcerpt() != null && !g.stackTraceExcerpt().isBlank()) {
                    sb.append("     stack:\n").append(indent(g.stackTraceExcerpt())).append("\n");
                }
            }
        }

        sb.append("\nBu bilgilere dayanarak analizini yap.");
        return sb.toString();
    }

    // Çok satırlı bir metnin her satırını girintiler (prompt okunabilirliği için)
    private String indent(String text) {
        return text.lines().map(line -> "       " + line).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
