package com.ailoganalyzer.exception;

/**
 * Dosya diske yazılırken/okunurken bir G/Ç (IO) hatası oluştuğunda fırlatılır.
 * HTTP 500 ile eşlenir (sunucu tarafı sorunu).
 */
public class StorageException extends RuntimeException {

    // Mesaj + asıl nedeni (IOException gibi) sarmalar; kök nedeni kaybetmemek için 'cause' iletilir
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
