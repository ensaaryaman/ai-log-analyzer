package com.ailoganalyzer.security;

import com.ailoganalyzer.domain.AppUser;
import com.ailoganalyzer.repository.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Her HTTP isteğinde bir kez çalışır: Authorization: Bearer <jwt> header'ını okur,
 * token geçerliyse kullanıcıyı DB'den yükleyip SecurityContext'e kimlik nesnesini koyar.
 * Geçersiz/eksik token'da hiçbir şey yapmaz — yetkilendirme kararını SecurityConfig verir (401).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        // Zaten kimlik doğrulanmamışsa ve geçerli bir token varsa context'i doldur
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String username = jwtService.extractUsername(token);
            if (username != null) {
                userRepository.findByUsername(username).ifPresent(user -> authenticate(user, request));
            }
        }
        filterChain.doFilter(request, response);
    }

    // Doğrulanmış kullanıcıyı principal olarak SecurityContext'e yerleştirir (rolü authority'ye çevirir)
    private void authenticate(AppUser user, HttpServletRequest request) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // "Authorization: Bearer xxx" header'ından token'ı çıkarır (yoksa null)
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
