package com.ailoganalyzer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Kayıt isteği gövdesi. Doğrulama anotasyonları geçersiz girdiyi controller'a girmeden 400 yapar.
 */
public record RegisterRequest(
        @NotBlank(message = "Kullanıcı adı boş olamaz.")
        @Size(min = 3, max = 50, message = "Kullanıcı adı 3-50 karakter olmalı.")
        String username,

        @NotBlank(message = "Şifre boş olamaz.")
        @Size(min = 6, max = 100, message = "Şifre en az 6 karakter olmalı.")
        String password
) {
}
