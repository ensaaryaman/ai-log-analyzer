package com.ailoganalyzer.security;

import com.ailoganalyzer.domain.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT üretme ve doğrulama. Stateless kimlik: sunucu oturum tutmaz, kimlik token'ın içindedir.
 * İmza HMAC-SHA256 ile atılır; gizli anahtar config'ten (app.jwt.secret) gelir.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";   // Token'a gömülen rol claim'i (yetkilendirme için)

    private final SecretKey key;                        // İmzalama/doğrulama anahtarı
    private final long expirationMillis;                // Token geçerlilik süresi (ms)

    public JwtService(
            // Gizli anahtar: HS256 için en az 32 bayt olmalı. Prod'da .env ile geçersiz kılınmalı.
            @Value("${app.jwt.secret:change-me-in-production-please-32-bytes-minimum!!}") String secret,
            @Value("${app.jwt.expiration-minutes:1440}") long expirationMinutes) {   // Varsayılan 24 saat
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMinutes * 60_000L;
    }

    // Kullanıcı için imzalı token üretir: sub=username, role claim'i, son kullanma zamanı.
    public String generateToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(key)
                .compact();
    }

    // Token'ı doğrular ve kullanıcı adını (subject) döner; geçersiz/süresi dolmuşsa null.
    public String extractUsername(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.getSubject();
    }

    // İmza + son kullanma tarihini doğrular; geçerliyse claim'leri, değilse null döner.
    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;   // Geçersiz imza, bozuk biçim veya süresi dolmuş → doğrulanamadı
        }
    }
}
