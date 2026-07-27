package com.ailoganalyzer.repository;

import com.ailoganalyzer.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * AppUser için veri erişim arayüzü. Giriş ve kayıt akışları kullanıcı adına göre sorgular.
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    // Login: kullanıcı adından kullanıcıyı bulur (şifre doğrulaması servis katmanında yapılır)
    Optional<AppUser> findByUsername(String username);

    // Kayıt: aynı kullanıcı adı ikinci kez alınamasın diye çakışma kontrolü
    boolean existsByUsername(String username);
}
