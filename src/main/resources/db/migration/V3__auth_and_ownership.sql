-- ============================================================================
-- V3__auth_and_ownership.sql — Kimlik doğrulama ve log sahipliği (Gün 9)
-- Amaç: her kullanıcı yalnızca kendi yüklediği logları görsün; ADMIN tümünü görebilsin.
-- ============================================================================

-- Uygulama kullanıcıları. Şifre asla düz metin tutulmaz; BCrypt hash saklanır.
CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,            -- Giriş için benzersiz kullanıcı adı
    password_hash VARCHAR(100) NOT NULL,                   -- BCrypt hash (~60 karakter)
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',    -- USER / ADMIN
    created_at    TIMESTAMPTZ  NOT NULL
);

-- Log dosyasına sahiplik: hangi kullanıcının yüklediği. Mevcut (eski) kayıtlarda NULL kalır
-- → bu "sahipsiz" loglar yalnızca ADMIN'e görünür (USER'lar 403 alır). Kullanıcı silinirse
-- logları öksüz kalmasın diye ON DELETE SET NULL (log verisi korunur, ADMIN denetleyebilir).
ALTER TABLE log_file
    ADD COLUMN owner_id UUID REFERENCES app_user(id) ON DELETE SET NULL;

CREATE INDEX idx_log_file_owner ON log_file(owner_id);     -- USER'ın kendi loglarını çekerken
