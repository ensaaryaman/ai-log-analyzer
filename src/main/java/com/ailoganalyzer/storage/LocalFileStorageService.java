package com.ailoganalyzer.storage;

import com.ailoganalyzer.config.StorageProperties;
import com.ailoganalyzer.exception.StorageException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * FileStorageService'in yerel dosya sistemi implementasyonu.
 * Dosyaları application.yml'de tanımlı 'app.storage.directory' klasörüne yazar.
 */
@Service                             // Bu sınıfı bir Spring servis bileşeni yapar; DI konteyneri yönetir ve enjekte eder
public class LocalFileStorageService implements FileStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileStorageService.class);   // Hata ayıklama/uyarı logları için

    private final Path root;         // final: kurulumdan sonra değişmez (thread-safe, öngörülebilir)

    // Constructor injection: StorageProperties Spring tarafından enjekte edilir (alan enjeksiyonu yerine tercih edilir — test edilebilir)
    public LocalFileStorageService(StorageProperties properties) {
        this.root = Paths.get(properties.directory()).toAbsolutePath().normalize();
    }

    // Bean oluşturulduktan sonra bir kez çalışır; depolama klasörü yoksa oluşturur (ilk çalıştırma kolaylığı)
    @PostConstruct                   // Bağımlılıklar enjekte edildikten sonra çağrılacak başlatma metodunu işaretler
    void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new StorageException("Depolama klasörü oluşturulamadı: " + root, e);
        }
    }

    // Dosya içeriğini benzersiz bir adla diske yazar ve mutlak yolunu döner
    @Override
    public String store(String originalFilename, byte[] content) {
        // Path traversal (../../ gibi) saldırılarına karşı yalnızca dosya adını al, klasör bilgisini at
        String safeName = Paths.get(originalFilename).getFileName().toString();
        // Aynı adlı yüklemelerin birbirini ezmemesi için UUID önekiyle benzersizleştir
        String uniqueName = UUID.randomUUID() + "_" + safeName;
        Path target = root.resolve(uniqueName).normalize();

        // Ek güvenlik: hedef, kök klasörün dışına çıkıyorsa reddet
        if (!target.startsWith(root)) {
            throw new StorageException("Geçersiz hedef yol: " + target, null);
        }

        try {
            Files.write(target, content);   // Baytları dosyaya yazar (dosya yoksa oluşturur)
            return target.toString();
        } catch (IOException e) {
            throw new StorageException("Dosya diske yazılamadı: " + uniqueName, e);
        }
    }

    // Diskteki dosyayı en iyi çaba ile siler; hata olursa yutar (ana silme işlemini bozmasın)
    @Override
    public void deleteQuietly(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException | RuntimeException e) {
            // En iyi çaba: dosya silinemese bile (ör. yetki), akışı durdurma — yalnızca logla
            LOGGER.warn("Ham log dosyası silinemedi: {} ({})", path, e.getMessage());
        }
    }
}
