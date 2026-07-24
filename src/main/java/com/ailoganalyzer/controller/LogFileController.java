package com.ailoganalyzer.controller;

import com.ailoganalyzer.domain.LogLevel;
import com.ailoganalyzer.dto.LogEntryResponse;
import com.ailoganalyzer.dto.LogFileSummaryResponse;
import com.ailoganalyzer.dto.StatsResponse;
import com.ailoganalyzer.exception.InvalidFileException;
import com.ailoganalyzer.exception.StorageException;
import com.ailoganalyzer.service.LogFileService;
import com.ailoganalyzer.service.StatsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Log dosyası yükleme ve listeleme uçlarını (endpoint) sunan REST controller.
 * Controller yalnızca HTTP ile ilgilenir; iş mantığı LogFileService'e devredilir (katman ayrımı).
 */
@RestController                      // @Controller + @ResponseBody: metot dönüşleri doğrudan JSON gövdesi olur
@RequestMapping("/api/logs")         // Bu controller'daki tüm uçların ortak yol öneki
public class LogFileController {

    private final LogFileService logFileService;   // İş mantığı arayüzüne bağımlılık (somut sınıfa değil)
    private final StatsService statsService;       // İstatistik hesaplama servisi (arayüz)

    // Bağımlılıklar constructor ile enjekte edilir (Spring tek constructor'ı otomatik kullanır)
    public LogFileController(LogFileService logFileService, StatsService statsService) {
        this.logFileService = logFileService;
        this.statsService = statsService;
    }

    // POST /api/logs — multipart form ile .log/.txt dosyası yükler ve parse özetini döner
    @PostMapping                     // POST isteklerini bu metoda yönlendirir
    public ResponseEntity<LogFileSummaryResponse> upload(
            @RequestParam("file") MultipartFile file) {     // Form-data'daki "file" alanını yakalar
        if (file.isEmpty()) {
            throw new InvalidFileException("Yüklenen dosya boş.");
        }
        byte[] content;
        try {
            content = file.getBytes();                      // Dosya içeriğini baytlara oku
        } catch (IOException e) {
            throw new StorageException("Yüklenen dosya okunamadı.", e);
        }

        LogFileSummaryResponse response =
                logFileService.ingest(file.getOriginalFilename(), content);

        // 201 Created + Location header: yeni kaynağın nerede bulunacağını REST kurallarına uygun bildirir
        return ResponseEntity
                .created(URI.create("/api/logs/" + response.id()))
                .body(response);
    }

    // GET /api/logs — yüklenmiş dosyaların listesi (geçmiş ekranı için)
    @GetMapping
    public List<LogFileSummaryResponse> list() {
        return logFileService.listAll();
    }

    // GET /api/logs/{id} — tek bir dosyanın özeti
    @GetMapping("/{id}")             // Yoldaki {id} kısmını değişkene bağlar
    public LogFileSummaryResponse getOne(@PathVariable UUID id) {   // @PathVariable: URL'deki id'yi parametreye aktarır
        return logFileService.getById(id);
    }

    // GET /api/logs/{id}/entries?level=ERROR — parse edilmiş kayıtlar (level filtresi opsiyonel)
    @GetMapping("/{id}/entries")
    public List<LogEntryResponse> entries(
            @PathVariable UUID id,
            @RequestParam(value = "level", required = false) LogLevel level) {   // required=false: parametre verilmezse null → hepsi
        return logFileService.getEntries(id, level);
    }

    // GET /api/logs/{id}/stats — damıtılmış istatistikler (seviye dağılımı, hata grupları, zaman serisi)
    @GetMapping("/{id}/stats")
    public StatsResponse stats(@PathVariable UUID id) {
        return statsService.computeStats(id);
    }

    // DELETE /api/logs/{id} — log dosyasını ve bağlı tüm verilerini siler
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)   // Başarılı silmede gövdesiz 204 döner (REST kuralı)
    public void delete(@PathVariable UUID id) {
        logFileService.delete(id);
    }
}
