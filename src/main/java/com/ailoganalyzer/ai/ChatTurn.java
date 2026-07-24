package com.ailoganalyzer.ai;

/**
 * Sohbet geçmişindeki tek bir tur (mesaj) — AI istemcisine geçmişi taşımak için saf veri nesnesi.
 * Entity yerine bu kullanılır ki AI istemcisi persistence katmanından habersiz kalsın.
 *
 * @param fromUser true ise kullanıcı, false ise asistan (AI) mesajı
 * @param content  mesaj metni
 */
public record ChatTurn(
        boolean fromUser,
        String content
) {
}
