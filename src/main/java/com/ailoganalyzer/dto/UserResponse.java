package com.ailoganalyzer.dto;

import com.ailoganalyzer.domain.AppUser;

/**
 * Aktif kullanıcı bilgisi (GET /api/auth/me). Şifre hash'i asla dışarı sızdırılmaz.
 */
public record UserResponse(
        String username,
        String role
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getUsername(), user.getRole().name());
    }
}
