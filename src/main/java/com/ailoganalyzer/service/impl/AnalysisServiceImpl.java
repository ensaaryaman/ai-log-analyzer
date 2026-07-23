package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.ai.AiAnalysisOutcome;
import com.ailoganalyzer.ai.AnalysisAiClient;
import com.ailoganalyzer.ai.AnalysisPrompt;
import com.ailoganalyzer.ai.AnalysisPromptBuilder;
import com.ailoganalyzer.ai.AnalysisResult;
import com.ailoganalyzer.ai.ErrorGroupDigest;
import com.ailoganalyzer.ai.PromptContext;
import com.ailoganalyzer.domain.Analysis;
import com.ailoganalyzer.domain.ErrorGroup;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.LogFileStatus;
import com.ailoganalyzer.dto.AnalysisResponse;
import com.ailoganalyzer.dto.StatsResponse;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import com.ailoganalyzer.repository.AnalysisRepository;
import com.ailoganalyzer.repository.ErrorGroupRepository;
import com.ailoganalyzer.repository.LogFileRepository;
import com.ailoganalyzer.service.AnalysisService;
import com.ailoganalyzer.service.StatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AnalysisService uygulaması. Damıtma katmanının (Gün 3) çıktısından bir prompt kurar,
 * AI istemcisini (gerçek veya mock) çağırır ve sonucu Analysis tablosuna kaydeder.
 */
@Service
public class AnalysisServiceImpl implements AnalysisService {

    private static final String PROMPT_VERSION = "v1";   // Prompt sürümü (kalite karşılaştırması için kaydedilir)
    private static final int MAX_GROUPS_IN_PROMPT = 5;   // Token bütçesi: prompt'a en fazla 5 hata grubu koy
    private static final int STACK_EXCERPT_LINES = 15;   // Her grup için stack trace'in ilk 15 satırı

    private final LogFileRepository logFileRepository;
    private final ErrorGroupRepository errorGroupRepository;
    private final AnalysisRepository analysisRepository;
    private final StatsService statsService;             // Seviye dağılımı/sayaçlar için (Gün 3)
    private final AnalysisPromptBuilder promptBuilder;   // Bağlam → prompt (saf)
    private final AnalysisAiClient aiClient;             // Gerçek veya mock (profile göre seçilir)

    public AnalysisServiceImpl(LogFileRepository logFileRepository,
                               ErrorGroupRepository errorGroupRepository,
                               AnalysisRepository analysisRepository,
                               StatsService statsService,
                               AnalysisPromptBuilder promptBuilder,
                               AnalysisAiClient aiClient) {
        this.logFileRepository = logFileRepository;
        this.errorGroupRepository = errorGroupRepository;
        this.analysisRepository = analysisRepository;
        this.statsService = statsService;
        this.promptBuilder = promptBuilder;
        this.aiClient = aiClient;
    }

    // Analiz DB'ye yazdığı için okuma-yazma transaction'ı içinde çalışır
    @Override
    @Transactional
    public AnalysisResponse analyze(UUID fileId) {
        LogFile file = logFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Log dosyası", fileId));

        PromptContext context = buildContext(file);
        AnalysisPrompt prompt = promptBuilder.build(context);

        // AI çağrısını ölçerek yap (süre LLMOps için kaydedilir)
        long start = System.currentTimeMillis();
        AiAnalysisOutcome outcome = aiClient.analyze(prompt);
        int durationMs = (int) (System.currentTimeMillis() - start);

        Analysis saved = analysisRepository.save(toEntity(file, outcome, durationMs));

        file.setStatus(LogFileStatus.ANALYZED);          // PARSED → ANALYZED
        logFileRepository.save(file);

        return AnalysisResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisResponse getById(UUID analysisId) {
        return analysisRepository.findById(analysisId)
                .map(AnalysisResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Analiz", analysisId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisResponse> listAll() {
        return analysisRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(AnalysisResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisResponse> listByFile(UUID fileId) {
        return analysisRepository.findByFileIdOrderByCreatedAtDesc(fileId)
                .stream().map(AnalysisResponse::from).toList();
    }

    // --- Yardımcı metotlar ---

    // Dosyanın damıtılmış bağlamını (istatistik + en önemli hata grupları) toplar
    private PromptContext buildContext(LogFile file) {
        StatsResponse stats = statsService.computeStats(file.getId());   // Gün 3 istatistiklerini yeniden kullan

        // En çok tekrarlayan grupları al, her birini prompt'a girecek özete (digest) çevir
        List<ErrorGroupDigest> digests = errorGroupRepository
                .findByFileIdOrderByOccurrenceCountDesc(file.getId())
                .stream()
                .limit(MAX_GROUPS_IN_PROMPT)
                .map(this::toDigest)
                .toList();

        return new PromptContext(
                file.getFilename(),
                file.getDetectedFormat() == null ? "UNKNOWN" : file.getDetectedFormat().name(),
                stats.totalEntries(),
                file.getErrorCount(),
                file.getWarnCount(),
                file.getFirstTs(),
                file.getLastTs(),
                stats.levelDistribution(),
                digests);
    }

    // ErrorGroup entity → prompt digest (stack trace kısaltılır)
    private ErrorGroupDigest toDigest(ErrorGroup group) {
        String excerpt = group.getSampleEntry() == null
                ? null
                : firstLines(group.getSampleEntry().getStackTrace(), STACK_EXCERPT_LINES);
        Integer sampleLine = group.getSampleEntry() == null ? null : group.getSampleEntry().getLineNumber();
        return new ErrorGroupDigest(
                group.getExceptionType(),
                group.getSampleMessage(),
                group.getOccurrenceCount(),
                group.getFirstSeen(),
                group.getLastSeen(),
                sampleLine,
                excerpt);
    }

    // AI sonucunu Analysis entity'sine dönüştürür (kalıcılaştırmak için)
    private Analysis toEntity(LogFile file, AiAnalysisOutcome outcome, int durationMs) {
        AnalysisResult result = outcome.result();
        Analysis analysis = new Analysis();
        analysis.setFile(file);
        analysis.setModel(outcome.model());
        analysis.setPromptVersion(PROMPT_VERSION);
        analysis.setSummary(result.summary());
        analysis.setRootCause(result.rootCause());
        analysis.setSolution(result.solution());
        analysis.setPriority(result.priority());
        analysis.setConfidence(clampConfidence(result.confidence()));
        analysis.setEvidenceLines(result.evidenceLines());
        analysis.setRawResponse(outcome.rawResponse());
        analysis.setPromptTokens(outcome.promptTokens());
        analysis.setCompletionTokens(outcome.completionTokens());
        analysis.setDurationMs(durationMs);
        analysis.setCreatedAt(OffsetDateTime.now());
        return analysis;
    }

    // Güveni [0,1] aralığına sıkıştırır ve numeric(3,2) ile uyumlu 2 haneye yuvarlar
    private BigDecimal clampConfidence(double confidence) {
        double clamped = Math.max(0.0, Math.min(1.0, confidence));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }

    // Metnin ilk n satırını döner (stack trace'i prompt için kısaltmak amacıyla)
    private String firstLines(String text, int n) {
        if (text == null) {
            return null;
        }
        return text.lines().limit(n).collect(Collectors.joining("\n"));
    }
}
