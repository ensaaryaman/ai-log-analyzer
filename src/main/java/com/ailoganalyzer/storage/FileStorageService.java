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

    /**
     * Verilen yoldaki dosyayı "sessizce" siler (yoksa veya hata olursa istisna fırlatmaz).
     * Log kaydı silinirken diskteki ham dosyayı temizlemek için kullanılır; bu işlemin
     * başarısızlığı ana silme işlemini bozmamalıdır (en iyi çaba / best-effort).
     *
     * @param path saklama yolu (null olabilir → hiçbir şey yapmaz)
     */
    void deleteQuietly(String path);
}
