package com.ailoganalyzer.dto;

import java.time.OffsetDateTime;

/**
 * WARN→ERROR geçişi: uyarıların ne zaman başlayıp ne kadar sonra hataya dönüştüğü.
 * "Önce uyarılar geldi, N dakika sonra hataya dönüştü" içgörüsünü taşır.
 * Geçiş yoksa (hata yok ya da uyarılar hatalardan sonra) null olur.
 *
 * @param firstWarn   ilk WARN'ın görüldüğü dakika
 * @param firstError  ilk ERROR'ın görüldüğü dakika
 * @param gapMinutes  aradaki süre (dakika)
 */
public record WarnToErrorTransition(
        OffsetDateTime firstWarn,
        OffsetDateTime firstError,
        long gapMinutes
) {
}
