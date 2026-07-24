package com.ailoganalyzer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Tüm controller'lardan fırlayan istisnaları tek merkezde yakalayıp
 * standart hata yanıtına (RFC 7807 ProblemDetail) çevirir.
 * Böylece hata biçimi her uçta tutarlı olur ve controller'lar try/catch ile kirlenmez (DRY, SRP).
 */
@RestControllerAdvice                // Uygulamadaki tüm @RestController'lar için ortak istisna işleme sağlar
public class GlobalExceptionHandler {

    // Geçersiz dosya (boş, yanlış uzantı) → 400 Bad Request
    @ExceptionHandler(InvalidFileException.class)   // Bu tip istisnayı bu metodun ele alacağını belirtir
    public ProblemDetail handleInvalidFile(InvalidFileException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Geçersiz dosya", ex.getMessage());
    }

    // Kayıt bulunamadı → 404 Not Found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Bulunamadı", ex.getMessage());
    }

    // İstek gövdesi doğrulaması başarısız (ör. boş soru) → 400 Bad Request
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // İlk alan hatasının mesajını kullanıcıya göster
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .findFirst().orElse("Geçersiz istek");
        return problem(HttpStatus.BAD_REQUEST, "Geçersiz istek", detail);
    }

    // Dosya boyutu sınırı aşıldı → 413 Payload Too Large (multipart limitinden gelir)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Dosya çok büyük",
                "Yüklenen dosya izin verilen boyutu aşıyor (maksimum 10MB).");
    }

    // Depolama/IO hatası → 500 Internal Server Error
    @ExceptionHandler(StorageException.class)
    public ProblemDetail handleStorage(StorageException ex) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Depolama hatası", ex.getMessage());
    }

    // Yapay zeka analizi hatası (API/parse/zaman aşımı) → 502 Bad Gateway (dış servis kaynaklı)
    @ExceptionHandler(AiAnalysisException.class)
    public ProblemDetail handleAiAnalysis(AiAnalysisException ex) {
        return problem(HttpStatus.BAD_GATEWAY, "Analiz başarısız",
                ex.getMessage() + " — 'mock' profiliyle deneyebilir veya API anahtarınızı kontrol edebilirsiniz.");
    }

    // ProblemDetail nesnesini tek yerde kurar (kod tekrarını önler)
    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatus(status);   // status alanını RFC 7807 gövdesine koyar
        pd.setTitle(title);
        pd.setDetail(detail);
        return pd;
    }
}
