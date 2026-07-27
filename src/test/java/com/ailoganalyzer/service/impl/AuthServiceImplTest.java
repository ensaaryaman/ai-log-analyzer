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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl birim testi (Mockito).
 * Doğrular: kayıtta şifre hash'lenip USER rolüyle kaydedilir; kullanıcı adı çakışması 409;
 * yanlış şifre / bilinmeyen kullanıcı 401 (InvalidCredentialsException).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AppUserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthServiceImpl authService() {
        return new AuthServiceImpl(userRepository, passwordEncoder, jwtService);
    }

    @Test
    @DisplayName("Kayıt: şifre hash'lenir, USER rolüyle kaydedilir ve token döner")
    void registerHashesPasswordAndReturnsToken() {
        when(userRepository.existsByUsername("ensar")).thenReturn(false);
        when(passwordEncoder.encode("sifre123")).thenReturn("HASHED");
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(AppUser.class))).thenReturn("jwt-token");

        AuthResponse response = authService().register(new RegisterRequest("ensar", "sifre123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("ensar");
        assertThat(response.role()).isEqualTo("USER");

        // Kaydedilen kullanıcı: hash'li şifre + USER rolü (düz şifre asla saklanmaz)
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("HASHED");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("Kayıt: kullanıcı adı zaten alınmışsa 409 (UsernameTakenException) ve kayıt yapılmaz")
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("ensar")).thenReturn(true);

        assertThatThrownBy(() -> authService().register(new RegisterRequest("ensar", "sifre123")))
                .isInstanceOf(UsernameTakenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Giriş: doğru şifreyle token döner")
    void loginWithCorrectPasswordReturnsToken() {
        AppUser user = AppUser.of("ensar", "HASHED", Role.USER);
        when(userRepository.findByUsername("ensar")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("sifre123", "HASHED")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = authService().login(new LoginRequest("ensar", "sifre123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Giriş: yanlış şifre → 401 (InvalidCredentialsException)")
    void loginWithWrongPasswordIsRejected() {
        AppUser user = AppUser.of("ensar", "HASHED", Role.USER);
        when(userRepository.findByUsername("ensar")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("yanlis", "HASHED")).thenReturn(false);

        assertThatThrownBy(() -> authService().login(new LoginRequest("ensar", "yanlis")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Giriş: bilinmeyen kullanıcı → 401 (kullanıcı adının varlığı sızdırılmaz)")
    void loginWithUnknownUserIsRejected() {
        when(userRepository.findByUsername("yok")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService().login(new LoginRequest("yok", "sifre123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
