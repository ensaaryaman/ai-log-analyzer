package com.ailoganalyzer.distill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fingerprinter birim testleri. Tekrarlanan hata tespitinin doğruluğu buna bağlı olduğu için
 * "aynı hata farklı değerlerle aynı parmak izini almalı" kuralı ayrıntılı test edilir.
 */
class FingerprinterTest {

    private final Fingerprinter fingerprinter = new Fingerprinter();

    @Test
    @DisplayName("Sadece sayı/ID farkı olan aynı hata AYNI parmak izini alır")
    void sameErrorWithDifferentIdsProducesSameFingerprint() {
        String fp1 = fingerprinter.fingerprint("java.lang.IllegalStateException", "Order 12345 not found", null);
        String fp2 = fingerprinter.fingerprint("java.lang.IllegalStateException", "Order 67890 not found", null);
        assertThat(fp1).isEqualTo(fp2);   // "12345" ve "67890" maskelendiği için aynı grup
    }

    @Test
    @DisplayName("Sadece stack trace satır numarası farkı olan aynı hata AYNI parmak izini alır")
    void sameErrorWithDifferentLineNumbersProducesSameFingerprint() {
        String stack1 = "    at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:696)";
        String stack2 = "    at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:698)";
        String fp1 = fingerprinter.fingerprint("SQLException", "baglanti hatasi", stack1);
        String fp2 = fingerprinter.fingerprint("SQLException", "baglanti hatasi", stack2);
        assertThat(fp1).isEqualTo(fp2);   // :696 ve :698 normalize edildiği için aynı grup
    }

    @Test
    @DisplayName("Farklı istisna tipi FARKLI parmak izi üretir")
    void differentExceptionTypeProducesDifferentFingerprint() {
        String fp1 = fingerprinter.fingerprint("java.lang.NullPointerException", "hata", null);
        String fp2 = fingerprinter.fingerprint("java.io.IOException", "hata", null);
        assertThat(fp1).isNotEqualTo(fp2);
    }

    @Test
    @DisplayName("normalizeMessage: sayıları ve uzun hex/UUID'leri maskeler")
    void normalizeMessageMasksVariables() {
        assertThat(fingerprinter.normalizeMessage("User 42 failed")).isEqualTo("User # failed");
        assertThat(fingerprinter.normalizeMessage("id=a1b2c3d4e5f6a7b8c9d0"))
                .isEqualTo("id=#");
    }

    @Test
    @DisplayName("topStackFrame: ilk 'at ...' satırını döner")
    void topStackFrameReturnsFirstAtLine() {
        String stack = "java.lang.NullPointerException: null\n"
                + "    at com.example.Foo.bar(Foo.java:10)\n"
                + "    at com.example.Baz.qux(Baz.java:20)";
        assertThat(fingerprinter.topStackFrame(stack)).isEqualTo("at com.example.Foo.bar(Foo.java:10)");
    }

    @Test
    @DisplayName("Parmak izi 32 karakterlik hex MD5'tir")
    void fingerprintIs32HexChars() {
        String fp = fingerprinter.fingerprint("X", "mesaj", null);
        assertThat(fp).hasSize(32).matches("[0-9a-f]{32}");
    }
}
