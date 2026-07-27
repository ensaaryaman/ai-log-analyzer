package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.domain.AppUser;
import com.ailoganalyzer.domain.Role;
import com.ailoganalyzer.dto.AuthResponse;
import com.ailoganalyzer.dto.LoginRequest;
import com.ailoganalyzer.dto.RegisterRequest;
import com.ailoganalyzer.exception.InvalidCredentialsException;
import com.ailoganalyzer.exception.UsernameTakenException;
import com.ailoganalyzer.repository.AppUserRepository;
import com.ailoganalyzer.security.JwtService;
import com.ailoganalyzer.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthService uygulaması. Şifreler yalnızca BCrypt hash olarak saklanır ve
 * karşılaştırılır; başarılı işlemde çağıran için JWT üretilir.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;   // BCrypt (SecurityConfig'te bean olarak tanımlı)
    private final JwtService jwtService;

    public AuthServiceImpl(AppUserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        // Çakışma kontrolü: aynı kullanıcı adı ikinci kez alınamaz (409)
        if (userRepository.existsByUsername(username)) {
            throw new UsernameTakenException(username);
        }
        // Şifreyi asla düz saklama: BCrypt hash'ini üret. Yeni kayıtlar varsayılan olarak USER.
        AppUser user = AppUser.of(username, passwordEncoder.encode(request.password()), Role.USER);
        AppUser saved = userRepository.save(user);
        return toAuthResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Kullanıcı yoksa da, şifre yanlışsa da aynı genel hata (401) → kullanıcı adı sızmasın
        AppUser user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return toAuthResponse(user);
    }

    // Kullanıcı için token üretip yanıt DTO'sunu kurar
    private AuthResponse toAuthResponse(AppUser user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
}
