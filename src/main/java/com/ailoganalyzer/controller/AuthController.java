package com.ailoganalyzer.controller;

import com.ailoganalyzer.dto.AuthResponse;
import com.ailoganalyzer.dto.LoginRequest;
import com.ailoganalyzer.dto.RegisterRequest;
import com.ailoganalyzer.dto.UserResponse;
import com.ailoganalyzer.security.AccessControlService;
import com.ailoganalyzer.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kimlik doğrulama uçları: kayıt, giriş ve aktif kullanıcı bilgisi.
 * /register ve /login herkese açıktır (SecurityConfig); /me kimlik doğrulaması ister.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccessControlService accessControl;   // /me için aktif kullanıcıyı okur

    public AuthController(AuthService authService, AccessControlService accessControl) {
        this.authService = authService;
        this.accessControl = accessControl;
    }

    // POST /api/auth/register — yeni kullanıcı oluşturur ve token döner (@Valid → geçersiz gövde 400)
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    // POST /api/auth/login — kimlik doğrular, JWT döner
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // GET /api/auth/me — token'ı geçerli olan kullanıcının bilgisi (frontend "kim giriş yapmış" için)
    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(accessControl.currentUser());
    }
}
