package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.domain.Analysis;
import com.ailoganalyzer.domain.ErrorGroup;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import com.ailoganalyzer.exception.StorageException;
import com.ailoganalyzer.repository.AnalysisRepository;
import com.ailoganalyzer.repository.ErrorGroupRepository;
import com.ailoganalyzer.service.ReportService;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * ReportService uygulaması. Analizi + hata gruplarını iyi biçimli bir XHTML'e dönüştürür,
 * openhtmltopdf ile PDF'e çevirir. Türkçe karakterler için DejaVu Sans fontu gömülür
 * (yerleşik PDF fontları ş/ğ/ı gibi harfleri desteklemez).
 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final int MAX_GROUPS = 10;   // Rapordaki hata grubu üst sınırı

    private final AnalysisRepository analysisRepository;
    private final ErrorGroupRepository errorGroupRepository;

    public ReportServiceImpl(AnalysisRepository analysisRepository,
                             ErrorGroupRepository errorGroupRepository) {
        this.analysisRepository = analysisRepository;
        this.errorGroupRepository = errorGroupRepository;
    }

    // Lazy ilişkilere (analysis.getFile) erişildiği için okuma transaction'ı içinde çalışır
    @Override
    @Transactional(readOnly = true)
    public byte[] generateAnalysisReport(UUID analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analiz", analysisId));
        LogFile file = analysis.getFile();
        List<ErrorGroup> groups = errorGroupRepository.findByFileIdOrderByOccurrenceCountDesc(file.getId());

        String html = buildHtml(analysis, file, groups);
        return renderPdf(html);
    }

    // XHTML'i PDF baytlarına çevirir (fontları classpath'ten gömerek)
    private byte[] renderPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            // Türkçe destekli fontu normal ve kalın ağırlıkla göm (subset=true → yalnız kullanılan glifler)
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/DejaVuSans.ttf"),
                    "DejaVu Sans", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/DejaVuSans-Bold.ttf"),
                    "DejaVu Sans", 700, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new StorageException("PDF raporu üretilemedi", e);
        }
    }

    // Analiz + hata gruplarından iyi biçimli (XML) bir rapor HTML'i kurar
    private String buildHtml(Analysis a, LogFile file, List<ErrorGroup> groups) {
        int confPct = a.getConfidence() == null ? 0
                : a.getConfidence().multiply(BigDecimal.valueOf(100)).intValue();
        String priority = a.getPriority() == null ? "-" : a.getPriority().name();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><style>\n");
        sb.append("@page { size: A4; margin: 1.8cm; }\n");
        sb.append("body { font-family: 'DejaVu Sans', sans-serif; font-size: 11px; color: #1c2330; line-height: 1.5; }\n");
        sb.append("h1 { font-size: 20px; margin: 0 0 2px; }\n");
        sb.append(".sub { color: #6b7688; font-size: 10px; margin: 0 0 14px; }\n");
        sb.append("h2 { font-size: 13px; margin: 16px 0 5px; border-bottom: 1px solid #e2e8f0; padding-bottom: 3px; }\n");
        sb.append(".badge { padding: 3px 10px; border-radius: 10px; color: #fff; font-weight: bold; }\n");
        sb.append(".meta { color: #6b7688; font-size: 10px; }\n");
        sb.append(".content { white-space: pre-wrap; }\n");
        sb.append("table { width: 100%; border-collapse: collapse; margin-top: 6px; }\n");
        sb.append("th, td { border: 1px solid #e2e8f0; padding: 5px 7px; text-align: left; font-size: 10px; vertical-align: top; }\n");
        sb.append("th { background: #f0f3f9; }\n");
        sb.append(".foot { margin-top: 22px; color: #94a1b8; font-size: 9px; border-top: 1px solid #e2e8f0; padding-top: 6px; }\n");
        sb.append("</style></head><body>\n");

        sb.append("<h1>AI Log Analyzer — Analiz Raporu</h1>\n");
        sb.append("<div class=\"sub\">Dosya: ").append(esc(file.getFilename()))
          .append(" · Format: ").append(file.getDetectedFormat() == null ? "-" : file.getDetectedFormat().name())
          .append(" · Rapor tarihi: ").append(a.getCreatedAt() == null ? "-" : a.getCreatedAt().toString())
          .append("</div>\n");

        sb.append("<h2>Genel Bakış</h2>\n");
        sb.append("<p><span class=\"badge\" style=\"background:").append(confColor(confPct)).append(";\">")
          .append(esc(priority)).append("</span> &#160; Güven: %").append(confPct).append("</p>\n");
        sb.append("<p class=\"meta\">Toplam kayıt: ").append(file.getLineCount())
          .append(" · Hata: ").append(file.getErrorCount())
          .append(" · Uyarı: ").append(file.getWarnCount());
        if (file.getFirstTs() != null && file.getLastTs() != null) {
            sb.append(" · Zaman aralığı: ").append(file.getFirstTs()).append(" — ").append(file.getLastTs());
        }
        sb.append("</p>\n");

        sb.append("<h2>Özet</h2><div class=\"content\">").append(esc(plain(a.getSummary()))).append("</div>\n");
        sb.append("<h2>Olası Kök Neden</h2><div class=\"content\">").append(esc(plain(a.getRootCause()))).append("</div>\n");
        sb.append("<h2>Çözüm Önerisi</h2><div class=\"content\">").append(esc(plain(a.getSolution()))).append("</div>\n");

        if (a.getEvidenceLines() != null && !a.getEvidenceLines().isEmpty()) {
            sb.append("<h2>Kanıt Satırları</h2><p class=\"meta\">")
              .append(a.getEvidenceLines().stream().map(n -> "satır " + n)
                      .reduce((x, y) -> x + ", " + y).orElse("-"))
              .append("</p>\n");
        }

        sb.append("<h2>Hata Grupları</h2>\n");
        if (groups.isEmpty()) {
            sb.append("<p class=\"meta\">Belirgin bir hata grubu yok.</p>\n");
        } else {
            sb.append("<table><thead><tr><th>İstisna</th><th>Örnek Mesaj</th><th>Tekrar</th><th>Satır</th></tr></thead><tbody>\n");
            groups.stream().limit(MAX_GROUPS).forEach(g -> sb.append("<tr><td>")
                    .append(esc(g.getExceptionType() == null ? "(istisna yok)" : g.getExceptionType()))
                    .append("</td><td>").append(esc(truncate(g.getSampleMessage(), 120)))
                    .append("</td><td>").append(g.getOccurrenceCount())
                    .append("</td><td>").append(g.getSampleEntry() == null ? "-" : g.getSampleEntry().getLineNumber())
                    .append("</td></tr>\n"));
            sb.append("</tbody></table>\n");
        }

        sb.append("<div class=\"foot\">Model: ").append(esc(nz(a.getModel())))
          .append(" · Token: ").append(a.getPromptTokens() == null ? "-" : a.getPromptTokens())
          .append(" + ").append(a.getCompletionTokens() == null ? "-" : a.getCompletionTokens())
          .append(" · Süre: ").append(a.getDurationMs() == null ? "-" : a.getDurationMs()).append(" ms")
          .append(" · Prompt sürümü: ").append(esc(nz(a.getPromptVersion())))
          .append("</div>\n");

        sb.append("</body></html>");
        return sb.toString();
    }

    // Güven yüzdesine göre renk (UI ile tutarlı: yüksek yeşil, orta sarı, düşük kırmızı)
    private String confColor(int pct) {
        if (pct >= 80) return "#16a34a";
        if (pct >= 50) return "#ca8a04";
        return "#dc2626";
    }

    // Modelin markdown işaretlerini (**, `) rapordan temizler
    private String plain(String s) {
        return s == null ? "-" : s.replace("**", "").replace("`", "");
    }

    private String nz(String s) {
        return s == null ? "-" : s;
    }

    private String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // XML/HTML özel karakterlerini kaçışlar (iyi biçimli XHTML için zorunlu)
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
