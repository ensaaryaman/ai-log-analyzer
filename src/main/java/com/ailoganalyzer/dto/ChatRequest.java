package com.ailoganalyzer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sohbet isteği gövdesi (kullanıcının sorusu).
 * Doğrulama anotasyonları geçersiz girdiyi controller'a girmeden 400 ile reddeder.
 *
 * @param question kullanıcının sorusu
 */
public record ChatRequest(
        @NotBlank(message = "Soru boş olamaz")           // boş/yalnızca boşluk olamaz
        @Size(max = 2000, message = "Soru en fazla 2000 karakter olabilir")   // aşırı uzun girdiyi sınırla
        String question
) {
}
