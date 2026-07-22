package com.ailoganalyzer.repository;

import com.ailoganalyzer.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Log ile sohbet mesajları için veri erişim arayüzü.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Bir analizin sohbet geçmişini kronolojik sırayla getirir (modele bağlam olarak verilir)
    List<ChatMessage> findByAnalysisIdOrderByCreatedAtAsc(UUID analysisId);
}
