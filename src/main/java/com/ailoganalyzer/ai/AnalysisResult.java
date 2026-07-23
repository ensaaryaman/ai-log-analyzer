package com.ailoganalyzer.ai;

import com.ailoganalyzer.domain.Priority;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Yapay zekadan beklenen YAPILANDIRILMIŞ yanıt.
 * Spring AI, bu record'un JSON şemasını otomatik üretip modele "şu formatta yanıt ver" der;
 * dönen JSON'u da doğrudan bu record'a bağlar (.entity(AnalysisResult.class)).
 *
 * @JsonPropertyDescription açıklamaları şemaya eklenir → modelin her alanı doğru doldurmasına yardım eder.
 */
public record AnalysisResult(

        @JsonPropertyDescription("Sorunun 1-2 cümlelik özeti (Türkçe)")
        String summary,

        @JsonPropertyDescription("En olası kök neden; kanıta dayandır (Türkçe)")
        String rootCause,

        @JsonPropertyDescription("Somut, uygulanabilir çözüm adımları (Türkçe)")
        String solution,

        @JsonPropertyDescription("Önem önceliği: CRITICAL, HIGH, MEDIUM veya LOW")
        Priority priority,

        @JsonPropertyDescription("0.0 ile 1.0 arası güven seviyesi; kanıt zayıfsa düşük ver")
        double confidence,

        @JsonPropertyDescription("Yanıtın dayandığı orijinal log satır numaraları, örn. [1042, 1187]")
        List<Integer> evidenceLines
) {
}
