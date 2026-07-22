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

import java.time.OffsetDateTime;

/**
 * Parse edilmiş tek bir log satırı (veya birleştirilmiş çok satırlı stack trace).
 * line_number sayesinde AI'ın gösterdiği "kanıt" satırlarına geri dönülebilir.
 */
@Entity
@Table(name = "log_entry")
@Getter
@Setter
@NoArgsConstructor
public class LogEntry {

    @Id                                                  // Birincil anahtar
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // DB'nin IDENTITY (auto-increment) sütununu kullan — çok sayıda satır için verimli
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)                   // Çok kayıt → tek dosya ilişkisi; LAZY: dosyayı gerçekten gerekmedikçe yükleme
    @JoinColumn(name = "file_id", nullable = false)      // İlişkiyi tutan yabancı anahtar sütunu
    private LogFile file;

    @Column(name = "ts")
    private OffsetDateTime ts;                            // Satırın zaman damgası (parse edilemezse null olabilir)

    @Enumerated(EnumType.STRING)                         // Seviye enum'unu adıyla sakla
    @Column(name = "level", length = 10)
    private LogLevel level;

    @Column(name = "logger_class", length = 300)
    private String loggerClass;                          // Kaydı üreten sınıf/logger adı

    @Column(name = "thread", length = 200)
    private String thread;

    @Column(name = "message", columnDefinition = "text") // Uzun olabilir → TEXT sütunu
    private String message;

    @Column(name = "exception_type", length = 300)
    private String exceptionType;                        // Örn. NullPointerException

    @Column(name = "stack_trace", columnDefinition = "text")
    private String stackTrace;                           // Birleştirilmiş çok satırlı stack trace

    @Column(name = "fingerprint", length = 32)
    private String fingerprint;                          // Hata gruplama anahtarı (aynı hata = aynı parmak izi)

    @Column(name = "line_number")
    private Integer lineNumber;                          // Orijinal dosyadaki 1-tabanlı satır numarası
}
