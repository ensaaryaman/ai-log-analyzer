package com.ailoganalyzer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * "Log ile sohbet" özelliğinde tek bir mesaj (kullanıcı sorusu veya AI yanıtı).
 * Bir analize bağlıdır; geçmiş, sonraki isteklerde modele bağlam olarak verilir.
 */
@Entity
@Table(name = "chat_message")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)                   // Mesaj, ait olduğu analize bağlıdır
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Enumerated(EnumType.STRING)                         // USER / ASSISTANT rolünü adıyla sakla
    @Column(name = "role", length = 20, nullable = false)
    private ChatRole role;

    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
