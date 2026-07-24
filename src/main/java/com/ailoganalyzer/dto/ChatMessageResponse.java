package com.ailoganalyzer.dto;

import com.ailoganalyzer.domain.ChatMessage;

import java.time.OffsetDateTime;

/**
 * Sohbet mesajının istemciye dönen görünümü (DTO).
 */
public record ChatMessageResponse(
        Long id,
        String role,        // USER veya ASSISTANT
        String content,
        OffsetDateTime createdAt
) {

    public static ChatMessageResponse from(ChatMessage m) {
        return new ChatMessageResponse(m.getId(), m.getRole().name(), m.getContent(), m.getCreatedAt());
    }
}
