package com.ailoganalyzer.exception;

/**
 * Yapay zeka analizi sırasında bir hata olduğunda fırlatılır
 * (API hatası, bozuk JSON yanıtı, zaman aşımı vb.). HTTP 502 ile eşlenir.
 */
public class AiAnalysisException extends RuntimeException {

    public AiAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
