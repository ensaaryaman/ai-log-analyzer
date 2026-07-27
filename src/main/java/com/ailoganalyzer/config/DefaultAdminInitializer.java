package com.ailoganalyzer.config;

import com.ailoganalyzer.domain.AppUser;
import com.ailoganalyzer.domain.Role;
import com.ailoganalyzer.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Uygulama açılışında hiç kullanıcı yoksa varsayılan bir ADMIN oluşturur.
 * Amaç: taze bir veritabanında (veya Docker demosunda) değerlendiren kişi hemen giriş yapabilsin
 * ve sahipsiz eski logları görebilsin. Kullanıcı adı/şifre config'ten (ör. .env) geçersiz kılınabilir.
 */
@Component
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public DefaultAdminInitializer(AppUserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.admin.username:admin}") String adminUsername,
                                   @Value("${app.admin.password:admin123}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Yalnızca sistemde HİÇ kullanıcı yokken çalışır — mevcut kullanıcıları asla değiştirmez/ezmez
        if (userRepository.count() > 0) {
            return;
        }
        AppUser admin = AppUser.of(adminUsername,
                passwordEncoder.encode(adminPassword), Role.ADMIN);
        userRepository.save(admin);
        log.info("Varsayılan ADMIN kullanıcısı oluşturuldu: '{}' (ilk girişten sonra şifreyi değiştirin).",
                adminUsername);
    }
}
