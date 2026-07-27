package com.ailoganalyzer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Güvenlik yapılandırması: stateless JWT bearer token modeli.
 * - Statik SPA dosyaları ve /api/auth/** herkese açık (login olmadan erişilebilir).
 * - Diğer tüm /api/** uçları kimlik doğrulaması ister (401 aksi halde).
 * - JwtAuthenticationFilter, standart kullanıcı-adı/şifre filtresinden önce çalışır.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;   // 401/403 gövdelerini JSON'a yazmak için

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // REST + JWT: CSRF token'a gerek yok (oturum/çerez tabanlı değil)
                .csrf(AbstractHttpConfigurer::disable)
                // Sunucu oturum tutmaz; her istek token ile kimliklenir
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Statik SPA: giriş ekranının kendisi login olmadan yüklenebilmeli
                        .requestMatchers("/", "/index.html", "/favicon.ico",
                                "/css/**", "/js/**").permitAll()
                        // Kayıt/login uçları herkese açık; sağlık ucu izleme için açık
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Geri kalan her şey kimlik doğrulaması ister
                        .anyRequest().authenticated())
                // Kimlik yoksa 401, yetki yoksa 403 — ikisi de RFC 7807 ProblemDetail JSON döner
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                // Her istekte token'ı okuyan filtremizi ekle
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Şifreleri BCrypt ile hash'ler (asla düz metin saklanmaz/karşılaştırılmaz)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthController'ın login akışında ihtiyaç duyabileceği yönetici (şu an manuel doğruluyoruz, ileride kullanılabilir)
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // 401: kimlik doğrulanmamış (token yok/geçersiz)
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) ->
                writeProblem(response, HttpStatus.UNAUTHORIZED, "Kimlik doğrulanmadı",
                        "Bu işlem için giriş yapmanız gerekiyor.");
    }

    // 403: kimliği var ama yetkisi yok (başka kullanıcının kaynağı vb.)
    private AccessDeniedHandler forbiddenHandler() {
        return (request, response, accessDeniedException) ->
                writeProblem(response, HttpStatus.FORBIDDEN, "Yetkisiz",
                        "Bu kaynağa erişim yetkiniz yok.");
    }

    // Ortak: filtre zincirinden fırlayan güvenlik hatalarını ProblemDetail JSON olarak yazar
    private void writeProblem(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws java.io.IOException {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        pd.setDetail(detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), pd);
    }
}
