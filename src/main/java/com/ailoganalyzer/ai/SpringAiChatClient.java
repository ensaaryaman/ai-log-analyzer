package com.ailoganalyzer.ai;

import com.ailoganalyzer.exception.AiAnalysisException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GERÇEK sohbet istemcisi. Spring AI ChatClient ile modeli çağırır; sistem talimatı,
 * önceki konuşma turları ve yeni soruyu bir arada gönderip serbest metin yanıt alır.
 *
 * @Profile("!mock"): mock profili kapalıyken (varsayılan) devreye girer.
 */
@Component
@Profile("!mock")
public class SpringAiChatClient implements ChatAiClient {

    private final ChatClient chatClient;

    public SpringAiChatClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    // Geçmişi Spring AI mesajlarına çevirip sistem + geçmiş + soru olarak gönderir
    @Override
    public String chat(String systemPrompt, List<ChatTurn> history, String question) {
        try {
            // Her turu uygun mesaj tipine dönüştür (kullanıcı → UserMessage, asistan → AssistantMessage)
            List<Message> priorMessages = history.stream()
                    .map(turn -> (Message) (turn.fromUser()
                            ? new UserMessage(turn.content())
                            : new AssistantMessage(turn.content())))
                    .toList();

            return chatClient.prompt()
                    .system(systemPrompt)
                    .messages(priorMessages)   // Önceki konuşma bağlamı
                    .user(question)            // Güncel soru
                    .call()
                    .content();                // Serbest metin yanıt
        } catch (Exception e) {
            throw new AiAnalysisException("Yapay zeka yanıtı alınamadı: " + e.getMessage(), e);
        }
    }
}
