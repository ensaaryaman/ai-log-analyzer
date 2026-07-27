package com.ailoganalyzer.domain;

/**
 * Kullanıcı rolü. Yetkilendirme kararları buna göre verilir:
 * USER yalnızca kendi loglarını görür/siler; ADMIN tüm kullanıcıların loglarını denetleyebilir.
 */
public enum Role {
    USER,   // Standart kullanıcı — sadece kendi verisi
    ADMIN   // Yönetici — tüm loglara erişim
}
