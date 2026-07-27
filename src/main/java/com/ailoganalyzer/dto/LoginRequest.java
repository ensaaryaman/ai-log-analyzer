package com.ailoganalyzer.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Giriş isteği gövdesi (kullanıcı adı + şifre).
 */
public record LoginRequest(
        @NotBlank(message = "Kullanıcı adı boş olamaz.")
        String username,

        @NotBlank(message = "Şifre boş olamaz.")
        String password
) {
}
