package com.ailoganalyzer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uygulama kullanıcısı. Kimlik doğrulama (login) ve log sahipliği bu varlığa dayanır.
 * Şifre asla düz metin tutulmaz — yalnızca BCrypt hash saklanır (passwordHash).
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;                     // Giriş için benzersiz kullanıcı adı

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;                 // BCrypt hash (düz şifre hiçbir zaman tutulmaz)

    @Enumerated(EnumType.STRING)                 // Rolü adıyla ("ADMIN") sakla — okunur ve kırılgan değil
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // Yeni kullanıcı oluşturmak için fabrika metodu (kayıt akışında kullanılır)
    public static AppUser of(String username, String passwordHash, Role role) {
        AppUser user = new AppUser();
        user.username = username;
        user.passwordHash = passwordHash;
        user.role = role;
        user.createdAt = OffsetDateTime.now();
        return user;
    }
}
