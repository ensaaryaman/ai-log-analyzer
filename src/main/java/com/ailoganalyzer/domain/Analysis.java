package com.ailoganalyzer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;   // Hibernate: bir alanın JDBC tipini elle belirtmeyi sağlar
import org.hibernate.type.SqlTypes;              // JSON, ARRAY gibi standart SQL tip sabitleri

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bir log dosyası için üretilmiş yapay zeka analiz sonucu.
 * model/prompt_version ve token sayaçları da saklanır → tekrarlanabilirlik ve maliyet takibi (LLMOps).
 */
@Entity
@Table(name = "analysis")
@Getter
@Setter
@NoArgsConstructor
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)                   // Analiz, kaynak log dosyasına bağlıdır
    @JoinColumn(name = "file_id", nullable = false)
    private LogFile file;

    @Column(name = "model", length = 100)
    private String model;                                // Kullanılan model, örn. gemini-2.5-flash

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;                        // Prompt sürümü — kalite karşılaştırması için

    @Column(name = "summary", columnDefinition = "text")
    private String summary;                              // Sorunun kısa özeti

    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;                            // Olası kök neden

    @Column(name = "solution", columnDefinition = "text")
    private String solution;                             // Önerilen çözüm adımları

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private Priority priority;

    @Column(name = "confidence", precision = 3, scale = 2)  // NUMERIC(3,2) ile eşleşir: 0.00 - 1.00
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)                         // List<Integer>'ı jsonb sütununa [1042,1187] olarak yazar
    @Column(name = "evidence_lines", columnDefinition = "jsonb")
    private List<Integer> evidenceLines;                 // AI'ın dayandığı orijinal satır numaraları (kanıt)

    @JdbcTypeCode(SqlTypes.JSON)                         // Modelin ham JSON yanıtı — debug ve ileride yeni alanlar için
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;                        // Maliyet takibi

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "duration_ms")
    private Integer durationMs;                          // Analizin sürdüğü süre (ms)

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
