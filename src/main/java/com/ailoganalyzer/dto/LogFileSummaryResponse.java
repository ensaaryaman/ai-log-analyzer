package com.ailoganalyzer.dto;

import com.ailoganalyzer.domain.LogFile;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * İstemciye dönen log dosyası özeti (DTO).
 * Entity'yi doğrudan dışarı açmamak önemlidir: API sözleşmesini iç modelden ayırır
 * (entity değişse bile API kırılmaz) ve gereksiz/hassas alanların sızmasını önler.
 *
 * record: sadece veri taşıyan, değişmez bir yapı — getter/equals/toString otomatik üretilir.
 */
public record LogFileSummaryResponse(
        UUID id,
        String filename,
        String detectedFormat,
        long sizeBytes,
        int lineCount,
        int errorCount,
        int warnCount,
        int parseErrorCount,
        OffsetDateTime firstTs,
        OffsetDateTime lastTs,
        String status,
        OffsetDateTime uploadedAt
) {

    // Entity → DTO dönüşümünü tek yerde toplar (map'leme mantığı controller'a sızmasın)
    public static LogFileSummaryResponse from(LogFile file) {
        return new LogFileSummaryResponse(
                file.getId(),
                file.getFilename(),
                file.getDetectedFormat() == null ? null : file.getDetectedFormat().name(),
                file.getSizeBytes(),
                file.getLineCount(),
                file.getErrorCount(),
                file.getWarnCount(),
                file.getParseErrorCount(),
                file.getFirstTs(),
                file.getLastTs(),
                file.getStatus().name(),
                file.getUploadedAt()
        );
    }
}
