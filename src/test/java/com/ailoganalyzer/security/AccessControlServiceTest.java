package com.ailoganalyzer.security;

import com.ailoganalyzer.domain.AppUser;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AccessControlService birim testi — sahiplik yetkilendirme kararının kalbi.
 * Kural: ADMIN her dosyaya erişir; USER yalnızca sahibi olduğu dosyaya; sahipsiz dosya USER'a kapalı.
 * Bu test, "USER-A başka kullanıcının logunu göremez/silemez → 403" senaryosunu doğrudan doğrular.
 */
class AccessControlServiceTest {

    private final AccessControlService accessControl = new AccessControlService();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();   // Testler arası sızıntı olmasın
    }

    // Verilen kullanıcıyı SecurityContext'e principal olarak koyar (JwtAuthenticationFilter'ın yaptığı gibi)
    private AppUser authenticateAs(Role role) {
        AppUser user = AppUser.of("kullanici-" + role, "hash", role);
        user.setId(UUID.randomUUID());
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return user;
    }

    private LogFile fileOwnedBy(AppUser owner) {
        LogFile file = new LogFile();
        file.setId(UUID.randomUUID());
        file.setOwner(owner);
        return file;
    }

    @Test
    @DisplayName("USER kendi dosyasına erişebilir")
    void userAccessesOwnFile() {
        AppUser me = authenticateAs(Role.USER);
        assertThatCode(() -> accessControl.requireAccess(fileOwnedBy(me))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("USER başka kullanıcının dosyasına erişemez → 403")
    void userCannotAccessOthersFile() {
        authenticateAs(Role.USER);                       // giriş yapan: A
        AppUser otherUser = AppUser.of("baskasi", "hash", Role.USER);
        otherUser.setId(UUID.randomUUID());              // dosya sahibi: B
        assertThatThrownBy(() -> accessControl.requireAccess(fileOwnedBy(otherUser)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("USER sahipsiz (owner=null) dosyaya erişemez → 403")
    void userCannotAccessOrphanFile() {
        authenticateAs(Role.USER);
        assertThatThrownBy(() -> accessControl.requireAccess(fileOwnedBy(null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ADMIN her dosyaya (başkasının ve sahipsiz dahil) erişebilir")
    void adminAccessesAnyFile() {
        authenticateAs(Role.ADMIN);
        AppUser someone = AppUser.of("biri", "hash", Role.USER);
        someone.setId(UUID.randomUUID());
        assertThatCode(() -> accessControl.requireAccess(fileOwnedBy(someone))).doesNotThrowAnyException();
        assertThatCode(() -> accessControl.requireAccess(fileOwnedBy(null))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Kimlik yoksa (anonim) currentUser 403 verir")
    void anonymousIsRejected() {
        // SecurityContext boş → kimlik yok
        assertThatThrownBy(accessControl::currentUser).isInstanceOf(AccessDeniedException.class);
    }
}
