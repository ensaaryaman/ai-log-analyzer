package com.ailoganalyzer.distill;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * Bir hata kaydı için "parmak izi" (fingerprint) üretir.
 * Amaç: aynı hatanın farklı değişken değerlerle (ID, sayı, UUID) tekrarını AYNI kabul etmek.
 * Örn. "Order 12345 not found" ile "Order 67890 not found" aynı parmak izini alır → tek grupta toplanır.
 *
 * Saf ve deterministik bir bileşendir (DB/Spring durumundan bağımsız) → kolayca test edilir.
 * Tekrarlanan hata tespitinin (Sentry benzeri gruplama) çekirdeğidir; AI olmadan da değer üretir.
 */
@Component
public class Fingerprinter {

    // Uzun hex/UUID dizilerini maskele (örn. "a1b2c3d4-...-e5f6" veya uzun hash'ler)
    private static final Pattern HEX_OR_UUID = Pattern.compile("\\b[0-9a-fA-F-]{16,}\\b");

    // Tekil sayıları maskele (örn. "12345")
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+\\b");

    /**
     * (exceptionType + normalize mesaj + normalize en üst stack frame) üçlüsünden bir MD5 parmak izi üretir.
     * @return 32 karakterlik onaltılık (hex) MD5 dizisi (log_entry.fingerprint sütununa sığar)
     */
    public String fingerprint(String exceptionType, String message, String stackTrace) {
        // Top frame'deki satır numaralarını at: aynı hata, dağıtımlar arası satır kayınca da aynı grupta kalsın
        String frame = stripLineNumbers(topStackFrame(stackTrace));
        String basis = safe(exceptionType) + "|" + normalizeMessage(message) + "|" + frame;
        return md5(basis);
    }

    // "(HikariPool.java:696)" → "(HikariPool.java)" : parmak izini satır numarası değişimlerine karşı dayanıklı yapar
    private String stripLineNumbers(String frame) {
        return frame.replaceAll(":\\d+", "");
    }

    // Mesajdaki değişken kısımları maskeler; böylece sadece "sabit" iskelet karşılaştırılır (paket-görünür: test için)
    String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }
        String masked = HEX_OR_UUID.matcher(message).replaceAll("#");   // önce uzun hex/UUID
        masked = NUMBER.matcher(masked).replaceAll("#");                // sonra sayılar
        return masked.trim();
    }

    // Stack trace'in en üst çerçevesini ("at ..." ile başlayan ilk satır) döner; hatanın oluştuğu yer
    String topStackFrame(String stackTrace) {
        if (stackTrace == null) {
            return "";
        }
        for (String line : stackTrace.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at ")) {
                return trimmed;
            }
        }
        return "";
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    // Verilen metnin MD5 özetini onaltılık dize olarak üretir
    private String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");   // Güvenlik amaçlı değil, yalnızca gruplama anahtarı
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 her JVM'de vardır; buraya normalde hiç düşülmez
            throw new IllegalStateException("MD5 algoritması bulunamadı", e);
        }
    }
}
