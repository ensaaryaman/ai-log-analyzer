package com.ailoganalyzer.dto;

import com.ailoganalyzer.domain.ErrorGroup;

import java.time.OffsetDateTime;

/**
 * Bir hata grubunun istemciye dönen görünümü (tekrarlanan hata).
 * occurrenceCount ile "aynı hata N kez" bilgisini; first/last seen ile zaman aralığını taşır.
 */
public record ErrorGroupResponse(
        String fingerprint,
        String exceptionType,
        String sampleMessage,
        int occurrenceCount,
        OffsetDateTime firstSeen,
        OffsetDateTime lastSeen,
        Integer sampleLineNumber,   // Temsilci kaydın orijinal satır numarası (kanıta gitmek için)
        KnowledgeHint knowledgeHint  // Bu hata geçmişte başka bir dosyada görüldüyse dolu, yoksa null
) {

    // Entity → DTO dönüşümü (geçmiş bilgisi olmadan)
    public static ErrorGroupResponse from(ErrorGroup g) {
        return from(g, null);
    }

    // Entity → DTO dönüşümü (hata bilgi bankası ipucuyla birlikte)
    public static ErrorGroupResponse from(ErrorGroup g, KnowledgeHint hint) {
        return new ErrorGroupResponse(
                g.getFingerprint(),
                g.getExceptionType(),
                g.getSampleMessage(),
                g.getOccurrenceCount(),
                g.getFirstSeen(),
                g.getLastSeen(),
                g.getSampleEntry() == null ? null : g.getSampleEntry().getLineNumber(),
                hint
        );
    }

    /**
     * "Hata bilgi bankası": aynı hatanın (fingerprint eşleşmesi) geçmişte başka bir dosyada
     * görülüp görülmediğini taşır. AI olmadan, tamamen deterministik hesaplanır — Fingerprinter
     * mesajdaki sayı/UUID'leri maskelediği için aynı hata farklı dosyalarda bile eşleşir.
     *
     * @param sourceFilename  bu hatanın en son görüldüğü (geçmiş) dosyanın adı
     * @param pastFileCount   bu hatanın kaç farklı (geçmiş) dosyada görüldüğü
     * @param lastSeenBefore  en son görülme zamanı
     * @param pastRootCause   o dosya için bir AI analizi varsa, o zamanki kök neden (yoksa null)
     * @param pastSolution    o dosya için bir AI analizi varsa, o zamanki çözüm önerisi (yoksa null)
     */
    public record KnowledgeHint(
            String sourceFilename,
            int pastFileCount,
            OffsetDateTime lastSeenBefore,
            String pastRootCause,
            String pastSolution
    ) {
    }
}
