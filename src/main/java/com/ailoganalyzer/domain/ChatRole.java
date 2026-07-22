package com.ailoganalyzer.domain;

/**
 * Log ile sohbet özelliğinde bir mesajın kime ait olduğunu belirtir.
 * Konuşma geçmişi modele gönderilirken bu rol bilgisi kullanılır.
 */
public enum ChatRole {
    USER,       // Kullanıcının sorusu
    ASSISTANT   // Yapay zekanın yanıtı
}
