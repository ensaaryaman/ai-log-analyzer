package com.ailoganalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Uygulamanın giriş noktası. Spring Boot'u başlatır ve otomatik yapılandırmayı tetikler.
 */
@SpringBootApplication               // @Configuration + @EnableAutoConfiguration + @ComponentScan bileşimi: Boot uygulamasını tanımlar
@ConfigurationPropertiesScan         // @ConfigurationProperties ile işaretli sınıfları (StorageProperties) tarayıp bean yapar
public class AiLogAnalyzerApplication {

    // JVM giriş metodu: gömülü web sunucusunu ve Spring bağlamını ayağa kaldırır
    public static void main(String[] args) {
        SpringApplication.run(AiLogAnalyzerApplication.class, args);
    }

}
