package com.ailoganalyzer.dto;

/**
 * Başarılı giriş/kayıt yanıtı: JWT token + frontend'in göstereceği kullanıcı bilgisi.
 * Token localStorage'da tutulur ve sonraki isteklerde Authorization header'ı olarak gönderilir.
 */
public record AuthResponse(
        String token,
        String username,
        String role
) {
}
