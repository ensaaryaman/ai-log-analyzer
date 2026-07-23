package com.ailoganalyzer.parse;

/**
 * Ham log metnini yapılandırılmış {@link ParsedLog}'a dönüştüren soyutlama.
 * SOLID/DIP: Servisler bu arayüze bağımlıdır; parse algoritması değişse (örn. ML tabanlı bir parser)
 * üst katmanlar etkilenmez. Saf bir fonksiyondur (metin girer, sonuç çıkar) → kolayca test edilir.
 */
public interface LogParser {

    /**
     * Verilen log metnini parse eder: formatı tespit eder, satırları ayrıştırır,
     * çok satırlı stack trace'leri birleştirir ve istatistikleri hesaplar.
     *
     * @param content ham log dosyası içeriği (metin)
     * @return parse sonucu (asla null değil; boş içerik boş sonuç döner)
     */
    ParsedLog parse(String content);
}
