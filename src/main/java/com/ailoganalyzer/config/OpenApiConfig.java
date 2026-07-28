package com.ailoganalyzer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI / OpenAPI belgesinin üst bilgisini ve güvenlik şemasını tanımlar.
 * JWT bearer şeması eklenir → Swagger UI'daki "Authorize" düğmesiyle token girilip
 * korunan uçlar tarayıcıdan denenebilir.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Log Analyzer API")
                        .version("v1")
                        .description("Log dosyalarını yapay zeka ile analiz eden servisin REST API'si. "
                                + "Çoğu uç JWT ister: /api/auth/login ile token alıp sağ üstteki "
                                + "\"Authorize\" düğmesine girin."))
                // JWT bearer güvenlik şeması: Authorization: Bearer <token>
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // Şemayı global gereksinim yap (uçlar varsayılan olarak kilitli görünsün)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
