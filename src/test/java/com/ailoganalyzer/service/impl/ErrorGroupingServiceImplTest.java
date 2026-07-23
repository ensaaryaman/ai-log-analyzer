package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.domain.ErrorGroup;
import com.ailoganalyzer.domain.LogEntry;
import com.ailoganalyzer.domain.LogFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ErrorGroupingServiceImpl.buildGroups için birim testi.
 * Gruplama mantığı saf/statik olduğu için VERİTABANI GEREKMEDEN test edilir (elle LogEntry kurup çağırırız).
 */
class ErrorGroupingServiceImplTest {

    // Test için hızlı bir LogEntry üretici
    private LogEntry entry(String fingerprint, String message, String exceptionType, OffsetDateTime ts, int line) {
        LogEntry e = new LogEntry();
        e.setFingerprint(fingerprint);
        e.setMessage(message);
        e.setExceptionType(exceptionType);
        e.setTs(ts);
        e.setLineNumber(line);
        return e;
    }

    @Test
    @DisplayName("Aynı parmak izi tek grupta toplanır; tekrar sayısı ve zaman aralığı doğru hesaplanır")
    void groupsByFingerprintWithCountsAndTimeRange() {
        LogFile file = new LogFile();
        OffsetDateTime t1 = OffsetDateTime.parse("2026-07-20T14:05:00Z");
        OffsetDateTime t2 = OffsetDateTime.parse("2026-07-20T14:07:00Z");

        List<LogEntry> entries = List.of(
                entry("AAA", "DB baglanti hatasi", "SQLException", t1, 3),
                entry("BBB", "Null deger", "NullPointerException", OffsetDateTime.parse("2026-07-20T14:06:00Z"), 8),
                entry("AAA", "DB baglanti hatasi", "SQLException", t2, 12),
                entry(null, "sadece bir info", null, t2, 15)   // parmak izi yok → gruplanmaz
        );

        List<ErrorGroup> groups = ErrorGroupingServiceImpl.buildGroups(file, entries);

        assertThat(groups).hasSize(2);                       // AAA ve BBB (null olan hariç)

        ErrorGroup top = groups.get(0);                      // En çok tekrarlanan (AAA) en başta
        assertThat(top.getFingerprint()).isEqualTo("AAA");
        assertThat(top.getOccurrenceCount()).isEqualTo(2);
        assertThat(top.getExceptionType()).isEqualTo("SQLException");
        assertThat(top.getFirstSeen()).isEqualTo(t1);        // en erken
        assertThat(top.getLastSeen()).isEqualTo(t2);         // en geç
        assertThat(top.getSampleEntry().getLineNumber()).isEqualTo(3);   // temsilci = ilk kayıt

        ErrorGroup second = groups.get(1);
        assertThat(second.getFingerprint()).isEqualTo("BBB");
        assertThat(second.getOccurrenceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Parmak izi olmayan (INFO/DEBUG) kayıtlar hiç gruplanmaz")
    void ignoresEntriesWithoutFingerprint() {
        LogFile file = new LogFile();
        List<LogEntry> entries = List.of(
                entry(null, "info 1", null, null, 1),
                entry(null, "info 2", null, null, 2)
        );
        assertThat(ErrorGroupingServiceImpl.buildGroups(file, entries)).isEmpty();
    }
}
