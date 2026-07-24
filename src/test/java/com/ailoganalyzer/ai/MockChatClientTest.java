package com.ailoganalyzer.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockChatClient birim testi — API çağrısı yapmadan deterministik yanıt döndüğünü doğrular.
 */
class MockChatClientTest {

    private final MockChatClient client = new MockChatClient();

    @Test
    @DisplayName("Mock yanıt, soruyu içerir ve sahte olduğunu belirtir")
    void returnsDeterministicReply() {
        String reply = client.chat("sistem", List.of(), "Bu hata neden oluyor?");
        assertThat(reply).contains("[MOCK]").contains("Bu hata neden oluyor?");
    }
}
