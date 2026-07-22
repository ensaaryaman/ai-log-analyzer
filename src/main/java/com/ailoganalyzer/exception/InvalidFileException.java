package com.ailoganalyzer.exception;

/**
 * Kullanıcı geçersiz bir dosya yüklediğinde fırlatılır
 * (boş dosya, desteklenmeyen uzantı vb.). HTTP 400 ile eşlenir.
 */
public class InvalidFileException extends RuntimeException {   // RuntimeException: kontrol edilmeyen (unchecked) — çağıran her yerde yakalamak zorunda değil

    // Hata mesajını üst sınıfa iletir; mesaj kullanıcıya anlamlı bir açıklama olarak döner
    public InvalidFileException(String message) {
        super(message);
    }
}
