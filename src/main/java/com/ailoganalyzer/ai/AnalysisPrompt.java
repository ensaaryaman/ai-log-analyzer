package com.ailoganalyzer.ai;

/**
 * Modele gönderilecek iki parçalı istem (prompt): sistem talimatı + kullanıcı içeriği.
 * Prompt kurma mantığını AI istemcisinden ayırır (Tek Sorumluluk): builder üretir, client gönderir.
 *
 * @param system rol ve kurallar (kim olduğu, ne yapması gerektiği)
 * @param user   analiz edilecek damıtılmış log içeriği
 */
public record AnalysisPrompt(
        String system,
        String user
) {
}
