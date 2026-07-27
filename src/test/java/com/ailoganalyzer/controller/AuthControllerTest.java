package com.ailoganalyzer.controller;

import com.ailoganalyzer.domain.AppUser;
import com.ailoganalyzer.domain.Role;
import com.ailoganalyzer.dto.AuthResponse;
import com.ailoganalyzer.repository.AppUserRepository;
import com.ailoganalyzer.security.AccessControlService;
import com.ailoganalyzer.security.JwtAuthenticationFilter;
import com.ailoganalyzer.security.JwtService;
import com.ailoganalyzer.security.SecurityConfig;
import com.ailoganalyzer.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController web katmanı testi — GERÇEK güvenlik zinciriyle (filtreler açık).
 * Doğrular: /register herkese açık (200); token olmadan korunan uç 401; geçerli token'la /me çalışır.
 * Gerçek SecurityConfig + JWT filtresi import edilir; yalnızca DB (AppUserRepository) ve AuthService mock'lanır.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, AccessControlService.class})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;          // Test içinde geçerli token üretmek için (gerçek bean)

    @MockitoBean private AuthService authService;
    @MockitoBean private AppUserRepository userRepository;   // Filtre kullanıcıyı buradan yükler

    @Test
    @DisplayName("POST /api/auth/register herkese açıktır → 200 ve token döner")
    void registerIsPublic() throws Exception {
        when(authService.register(any())).thenReturn(new AuthResponse("jwt-token", "ensar", "USER"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ensar\",\"password\":\"sifre123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("Geçersiz kayıt gövdesi (kısa şifre) → 400")
    void registerValidatesBody() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Token olmadan GET /api/auth/me → 401 (ProblemDetail)")
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Kimlik doğrulanmadı"));
    }

    @Test
    @DisplayName("Geçerli token ile GET /api/auth/me → 200 ve kullanıcı bilgisi")
    void meReturnsCurrentUser() throws Exception {
        AppUser user = AppUser.of("ensar", "hash", Role.USER);
        user.setId(UUID.randomUUID());
        when(userRepository.findByUsername("ensar")).thenReturn(Optional.of(user));
        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ensar"))
                .andExpect(jsonPath("$.role").value("USER"));
    }
}
