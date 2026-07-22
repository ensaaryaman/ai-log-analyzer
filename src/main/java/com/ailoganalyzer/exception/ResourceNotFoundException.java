package com.ailoganalyzer.exception;

/**
 * İstenen kayıt (örn. bir log dosyası veya analiz) bulunamadığında fırlatılır.
 * HTTP 404 ile eşlenir.
 */
public class ResourceNotFoundException extends RuntimeException {

    // Örn. new ResourceNotFoundException("Log dosyası", id) → "Log dosyası bulunamadı: <id>"
    public ResourceNotFoundException(String what, Object id) {
        super(what + " bulunamadı: " + id);
    }
}
