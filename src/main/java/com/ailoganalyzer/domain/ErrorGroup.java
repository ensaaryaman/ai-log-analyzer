package com.ailoganalyzer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Aynı parmak izine sahip hataların gruplandığı kayıt (Sentry benzeri).
 * "Aynı hata 300 kez tekrar etti" gibi çıkarımların ve tekrarlanan hata
 * tespitinin temelidir — üstelik AI olmadan da değer üretir (deterministik).
 */
@Entity
@Table(name = "error_group")
@Getter
@Setter
@NoArgsConstructor
public class ErrorGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)                   // Grup, ait olduğu dosyaya bağlıdır
    @JoinColumn(name = "file_id", nullable = false)
    private LogFile file;

    @Column(name = "fingerprint", length = 32, nullable = false)
    private String fingerprint;                          // Grubu tanımlayan parmak izi

    @Column(name = "exception_type", length = 300)
    private String exceptionType;

    @Column(name = "sample_message", columnDefinition = "text")
    private String sampleMessage;                        // Grubu temsil eden örnek mesaj

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;                         // Bu hatanın toplam tekrar sayısı

    @Column(name = "first_seen")
    private OffsetDateTime firstSeen;                    // İlk görülme zamanı (patlama analizi için)

    @Column(name = "last_seen")
    private OffsetDateTime lastSeen;                     // Son görülme zamanı

    @ManyToOne(fetch = FetchType.LAZY)                   // Grubu temsil eden örnek log kaydına referans
    @JoinColumn(name = "sample_entry_id")
    private LogEntry sampleEntry;
}
