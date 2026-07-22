package com.ailoganalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * application.yml içindeki 'app.storage.*' ayarlarını tip-güvenli biçimde temsil eder.
 * record kullanımı: değerler değişmez (immutable) olur ve Boot, constructor binding ile doldurur.
 *
 * @param directory         Yüklenen ham log dosyalarının yazılacağı klasör
 * @param allowedExtensions İzin verilen dosya uzantıları (örn. log, txt)
 */
@ConfigurationProperties(prefix = "app.storage")   // Bu prefix altındaki ayarları bu record'a bağlar (magic string'leri koddan uzaklaştırır)
public record StorageProperties(
        String directory,
        List<String> allowedExtensions
) {
}
