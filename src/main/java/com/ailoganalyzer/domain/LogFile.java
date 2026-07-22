package com.ailoganalyzer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Yüklenen bir log dosyasını ve onun parse özetini temsil eder (aggregate root).
 * Sayaçlar (error_count vb.) dashboard sorgularını hızlandırmak için burada denormalize tutulur.
 */
@Entity                              // Bu sınıfın bir JPA varlığı (DB tablosuna karşılık gelir) olduğunu belirtir
@Table(name = "log_file")            // Eşleşeceği tablo adını sabitler (sınıf adından türetmeye bırakmayız)
@Getter                              // Lombok: tüm alanlar için getter'ları derleme anında üretir (boilerplate azaltır)
@Setter                              // Lombok: setter'ları üretir — servis katmanı alanları böyle doldurur
@NoArgsConstructor                   // JPA, entity'yi yansımayla (reflection) oluşturmak için parametresiz ctor ister
public class LogFile {

    @Id                                              // Birincil anahtar alanı
    @GeneratedValue(strategy = GenerationType.UUID)  // ID'yi Hibernate UUID olarak üretir (tahmin edilemez, dağıtık-dostu)
    private UUID id;

    @Column(nullable = false)        // filename NULL olamaz (DB kısıtıyla uyumlu)
    private String filename;

    @Column(name = "storage_path")   // Ham dosyanın diskteki yolu — sonradan yeniden parse edebilmek için
    private String storagePath;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    @Enumerated(EnumType.STRING)     // Enum'u ordinal (0,1,2) yerine adıyla ("UPLOADED") saklar — okunur ve kırılgan değil
    @Column(name = "detected_format")
    private LogFormat detectedFormat;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "warn_count", nullable = false)
    private int warnCount;

    @Column(name = "parse_error_count", nullable = false)
    private int parseErrorCount;     // Parse edilemeyen satır sayısı (kullanıcıya dürüst raporlanır)

    @Column(name = "first_ts")
    private OffsetDateTime firstTs;  // Logdaki en erken zaman damgası (parse sonrası dolar)

    @Column(name = "last_ts")
    private OffsetDateTime lastTs;   // Logdaki en geç zaman damgası

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogFileStatus status;

    // Yükleme anında yeni bir LogFile oluşturmak için kullanılır (parse öncesi durum = UPLOADED)
    public static LogFile newUpload(String filename, long sizeBytes, int lineCount, String storagePath) {
        LogFile file = new LogFile();
        file.filename = filename;
        file.sizeBytes = sizeBytes;
        file.lineCount = lineCount;
        file.storagePath = storagePath;
        file.uploadedAt = OffsetDateTime.now();
        file.status = LogFileStatus.UPLOADED;
        return file;
    }
}
