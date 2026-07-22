package com.ailoganalyzer.storage;

/**
 * Ham log dosyalarını kalıcı olarak saklamanın soyutlamasıdır.
 * SOLID/DIP: Üst katmanlar (servisler) somut diske değil bu arayüze bağlıdır;
 * ileride yerel disk yerine S3/MinIO'ya geçmek tek bir implementasyon eklemekle mümkün olur.
 */
public interface FileStorageService {

    /**
     * Dosya içeriğini saklar ve sonradan erişmek için kullanılacak yolu döner.
     *
     * @param originalFilename kullanıcının yüklediği özgün dosya adı (benzersizleştirilir)
     * @param content          dosyanın ham baytları
     * @return saklanan dosyanın yolu (DB'de log_file.storage_path olarak tutulur)
     */
    String store(String originalFilename, byte[] content);
}
