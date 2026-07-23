package com.ailoganalyzer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Uygulama bağlamının (tüm bean'ler + gerçek PostgreSQL + Flyway) sorunsuz yüklendiğini doğrulayan
 * ENTEGRASYON testi. Testcontainers ile gerçek bir Postgres ayağa kaldırır → Docker gerektirir.
 *
 * Sınıf adı "IT" ile biter: Maven Failsafe eklentisi bunu 'mvn verify' fazında çalıştırır,
 * hızlı 'mvn test' döngüsü ise bunu atlar (Docker'sız ortamda takılmasın diye).
 */
@Import(TestcontainersConfiguration.class)   // Test için Postgres konteynerini bağlama ekler (@ServiceConnection)
@SpringBootTest                              // Tüm uygulama bağlamını başlatır (uçtan uca bean doğrulaması)
class AiLogAnalyzerApplicationIT {

	// Bağlam hatasız yükleniyorsa test geçer: tüm bean'ler ve şema/entity eşleşmesi tutarlı demektir
	@Test
	void contextLoads() {
	}

}
