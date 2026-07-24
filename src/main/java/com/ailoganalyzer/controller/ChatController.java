package com.ailoganalyzer.controller;

import com.ailoganalyzer.dto.ChatMessageResponse;
import com.ailoganalyzer.dto.ChatRequest;
import com.ailoganalyzer.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Log ile sohbet uçlarını sunan REST controller.
 * Sohbet, belirli bir analiz bağlamında yürütülür.
 */
@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // POST /api/analyses/{id}/chat — analize soru sorar (gövde: {"question": "..."})
    @PostMapping("/api/analyses/{id}/chat")
    public ChatMessageResponse ask(@PathVariable UUID id,
                                   @Valid @RequestBody ChatRequest request) {   // @Valid: ChatRequest kurallarını uygular (boş soru → 400)
        return chatService.ask(id, request.question());
    }

    // GET /api/analyses/{id}/chat — sohbet geçmişi
    @GetMapping("/api/analyses/{id}/chat")
    public List<ChatMessageResponse> history(@PathVariable UUID id) {
        return chatService.history(id);
    }
}
