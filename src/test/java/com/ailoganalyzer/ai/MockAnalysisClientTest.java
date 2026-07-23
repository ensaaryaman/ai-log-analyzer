package com.ailoganalyzer.ai;

import com.ailoganalyzer.domain.Priority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockAnalysisClient birim testi. Mock istemci API çağrısı yapmadığı için tamamen deterministiktir;
 * demo/test ortamında beklendiği gibi çalıştığını doğrular.
 */
class MockAnalysisClientTest {

    private final MockAnalysisClient client = new MockAnalysisClient();

    @Test
    @DisplayName("Logda hata varsa mock yüksek öncelik döner")
    void returnsHighPriorityWhenErrorsPresent() {
        AiAnalysisOutcome outcome = client.analyze(new AnalysisPrompt("sistem", "DOSYA: x | 5 ERROR, 2 WARN"));

        assertThat(outcome.model()).isEqualTo("mock");
        assertThat(outcome.result().priority()).isEqualTo(Priority.HIGH);
        assertThat(outcome.result().summary()).startsWith("[MOCK]");
        assertThat(outcome.rawResponse()).contains("mock");
    }

    @Test
    @DisplayName("Hata yoksa mock düşük öncelik döner")
    void returnsLowPriorityWhenNoErrors() {
        AiAnalysisOutcome outcome = client.analyze(new AnalysisPrompt("sistem", "DOSYA: x | 0 ERROR, 0 WARN"));
        assertThat(outcome.result().priority()).isEqualTo(Priority.LOW);
    }
}
