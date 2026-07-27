package com.ailoganalyzer.security;

import com.ailoganalyzer.domain.AppUser;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Yetkilendirme kararlarını tek yerde toplar (DRY): "aktif kullanıcı kim?" ve
 * "bu log dosyasına erişebilir mi?" Servis katmanı sahiplik kontrolünü buradan çağırır.
 * ADMIN her şeyi görür; USER yalnızca kendi (owner) loglarına erişir, aksi halde 403.
 */
@Service
public class AccessControlService {

    // SecurityContext'teki doğrulanmış kullanıcıyı döner (JwtAuthenticationFilter principal olarak AppUser koyar)
    public AppUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUser user) {
            return user;
        }
        // Güvenli uçlarda buraya normalde düşülmez (SecurityConfig 401 verir); savunma amaçlı kontrol
        throw new AccessDeniedException("Kimlik doğrulanmadı.");
    }

    public UUID currentUserId() {
        return currentUser().getId();
    }

    public boolean isAdmin() {
        return currentUser().getRole() == Role.ADMIN;
    }

    /**
     * Aktif kullanıcının verilen dosyaya erişebildiğini doğrular; edemiyorsa 403 fırlatır.
     * ADMIN'e her zaman izin verilir. USER yalnızca sahibi olduğu dosyaya erişir; sahipsiz
     * (owner=null, login öncesi yüklenmiş) dosyalar USER'a kapalıdır.
     */
    public void requireAccess(LogFile file) {
        AppUser me = currentUser();
        if (me.getRole() == Role.ADMIN) {
            return;
        }
        AppUser owner = file.getOwner();
        if (owner != null && owner.getId().equals(me.getId())) {
            return;
        }
        throw new AccessDeniedException("Bu log dosyasına erişim yetkiniz yok.");
    }
}
