package com.ailoganalyzer.service;

import com.ailoganalyzer.dto.AuthResponse;
import com.ailoganalyzer.dto.LoginRequest;
import com.ailoganalyzer.dto.RegisterRequest;

/**
 * Kimlik doğrulama iş mantığı: kayıt ve giriş. Controller yalnızca HTTP ile ilgilenir,
 * şifre hash'leme / token üretme gibi kurallar bu servistedir (katman ayrımı).
 */
public interface AuthService {

    // Yeni kullanıcı oluşturur (rol USER) ve doğrudan giriş yapmış gibi token döner.
    AuthResponse register(RegisterRequest request);

    // Kullanıcı adı + şifreyi doğrular, geçerliyse JWT üretir.
    AuthResponse login(LoginRequest request);
}
