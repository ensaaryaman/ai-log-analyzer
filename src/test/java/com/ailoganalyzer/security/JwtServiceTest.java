package com.ailoganalyzer.security;

import com.ailoganalyzer.domain.AppUser;
import com.ailoganalyzer.domain.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtService birim testi (saf, Spring/DB olmadan).
 * Doğrular: üretilen token'dan kullanıcı adı geri okunur; kurcalanmış/geçersiz token null döner.
 */
class JwtServiceTest {

    // Test için sabit, ≥32 baytlık gizli anahtar (HS256 gereği)
    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes!!";

    private final JwtService jwtService = new JwtService(SECRET, 60);   // 60 dk geçerli

    private AppUser user(String username, Role role) {
        AppUser u = AppUser.of(username, "hash", role);
        return u;
    }

    @Test
    @DisplayName("Üretilen token'dan kullanıcı adı (subject) doğru okunur")
    void roundTripsUsername() {
        String token = jwtService.generateToken(user("ensar", Role.USER));
        assertThat(jwtService.extractUsername(token)).isEqualTo("ensar");
    }

    @Test
    @DisplayName("Kurcalanmış token doğrulanamaz → null")
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(user("ensar", Role.ADMIN));
        String tampered = token.substring(0, token.length() - 3) + "abc";   // imzayı boz
        assertThat(jwtService.extractUsername(tampered)).isNull();
    }

    @Test
    @DisplayName("Farklı anahtarla imzalanmış token reddedilir → null")
    void tokenFromDifferentKeyIsRejected() {
        JwtService other = new JwtService("another-completely-different-secret-32bytes!!", 60);
        String foreign = other.generateToken(user("mallory", Role.USER));
        // Bizim anahtarımızla doğrulanamaz
        assertThat(jwtService.extractUsername(foreign)).isNull();
    }

    @Test
    @DisplayName("Süresi dolmuş token reddedilir → null")
    void expiredTokenIsRejected() {
        JwtService expired = new JwtService(SECRET, -1);   // negatif süre → anında geçmişte kalır
        String token = expired.generateToken(user("ensar", Role.USER));
        assertThat(jwtService.extractUsername(token)).isNull();
    }
}
