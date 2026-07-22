package com.ailoganalyzer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;   // Testcontainers 1.x (Spring Boot 3.x) paket yolu
import org.testcontainers.utility.DockerImageName;

/**
 * Testler için gerçek bir PostgreSQL veritabanını Docker konteynerinde ayağa kaldırır.
 * H2 gibi gömülü DB yerine gerçek Postgres kullanılır çünkü projede jsonb/timestamptz
 * gibi Postgres'e özgü tipler var — testler üretimle aynı motorda koşmalı.
 */
@TestConfiguration(proxyBeanMethods = false)   // Test bağlamına ek bean tanımı ekler; proxy kapalı → daha hızlı
class TestcontainersConfiguration {

	// Postgres konteynerini bean olarak tanımlar; @ServiceConnection sayesinde Spring datasource'u otomatik bağlanır
	@Bean
	@ServiceConnection                          // Konteynerin host/port/kullanıcı bilgisini Spring'e otomatik aktarır (elle URL yazmaya gerek yok)
	PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));   // Compose ile aynı sürüm
	}

}
