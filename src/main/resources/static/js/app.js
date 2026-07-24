/* ============================================================
   AI Log Analyzer — arayüz mantığı (saf JavaScript, çerçeve yok)
   Backend REST API'siyle fetch üzerinden konuşur.
   ============================================================ */

// Basit uygulama durumu (state)
const state = {
    files: [],          // Yüklenen loglar
    selectedId: null,   // Seçili logun kimliği
};

// --- DOM kısayolları ---
const $ = (id) => document.getElementById(id);

// Sayfa yüklenince: mevcut logları getir ve olayları bağla
document.addEventListener('DOMContentLoaded', async () => {
    $('providerBadge').textContent = 'Spring AI';
    bindUpload();
    await loadFiles();
    // Deep-link: /?file=<id> verilirse o logu otomatik seç (paylaşılabilir bağlantı)
    const preselect = new URLSearchParams(location.search).get('file');
    if (preselect) selectFile(preselect);
});

/* ---------------- Yükleme ---------------- */

// Dosya seçimi ve sürükle-bırak olaylarını bağlar
function bindUpload() {
    const input = $('fileInput');
    input.addEventListener('change', () => {
        if (input.files.length) uploadFile(input.files[0]);
    });

    const zone = $('dropZone');
    ['dragover', 'dragenter'].forEach(ev =>
        zone.addEventListener(ev, (e) => { e.preventDefault(); zone.classList.add('dragover'); }));
    ['dragleave', 'drop'].forEach(ev =>
        zone.addEventListener(ev, (e) => { e.preventDefault(); zone.classList.remove('dragover'); }));
    zone.addEventListener('drop', (e) => {
        if (e.dataTransfer.files.length) uploadFile(e.dataTransfer.files[0]);
    });
}

// Seçilen dosyayı POST /api/logs ile yükler
async function uploadFile(file) {
    const status = $('uploadStatus');
    status.className = 'upload-status muted';
    status.textContent = `"${file.name}" yükleniyor ve ayrıştırılıyor...`;

    const form = new FormData();
    form.append('file', file);

    try {
        const res = await fetch('/api/logs', { method: 'POST', body: form });
        if (!res.ok) throw await problem(res);
        const summary = await res.json();
        status.className = 'upload-status ok';
        status.textContent = `"${summary.filename}" yüklendi — ${summary.lineCount} satır, ${summary.errorCount} hata.`;
        await loadFiles();
        selectFile(summary.id);   // Yeni yükleneni otomatik seç
    } catch (err) {
        status.className = 'upload-status err';
        status.textContent = err.message;
    }
}

/* ---------------- Dosya listesi ---------------- */

// GET /api/logs → durumu güncelle ve listeyi çiz
async function loadFiles() {
    try {
        const res = await fetch('/api/logs');
        state.files = await res.json();
        renderFileList();
    } catch (err) {
        console.error('Dosyalar yüklenemedi', err);
    }
}

// Yüklenen loglar listesini çizer
function renderFileList() {
    const box = $('fileList');
    if (!state.files.length) {
        box.innerHTML = '<p class="empty muted">Henüz dosya yok. Yukarıdan bir log yükleyin.</p>';
        return;
    }
    box.innerHTML = state.files.map(f => `
        <div class="file-item ${f.id === state.selectedId ? 'selected' : ''}" data-id="${f.id}">
            <div class="fi-name">${esc(f.filename)}</div>
            <div class="fi-sub">
                <span>${f.detectedFormat || '—'}</span>
                <span>${f.errorCount} hata</span>
                <span>${f.warnCount} uyarı</span>
                <span>${statusLabel(f.status)}</span>
            </div>
        </div>`).join('');
    // Her öğeye tıklama olayı
    box.querySelectorAll('.file-item').forEach(el =>
        el.addEventListener('click', () => selectFile(el.dataset.id)));
}

/* ---------------- Seçili dosya detayı ---------------- */

// Bir logu seçer: istatistiklerini ve geçmiş analizlerini getirir
async function selectFile(id) {
    state.selectedId = id;
    renderFileList();
    $('detailEmpty').classList.add('hidden');
    $('detailContent').classList.remove('hidden');

    const file = state.files.find(f => f.id === id);
    $('detailFilename').textContent = file ? file.filename : '';
    $('detailMeta').textContent = file
        ? `${file.detectedFormat || 'bilinmeyen format'} · ${file.lineCount} satır · ${(file.sizeBytes / 1024).toFixed(1)} KB`
        : '';
    $('analyzeBtn').onclick = () => analyzeFile(id);
    $('analysisArea').innerHTML = '';

    // İstatistik ve geçmiş analizleri paralel getir
    const [stats, analyses] = await Promise.all([
        fetch(`/api/logs/${id}/stats`).then(r => r.json()),
        fetch(`/api/analyses?fileId=${id}`).then(r => r.json()),
    ]);
    renderStats(stats);
    renderHistory(analyses);
}

// İstatistik rozetlerini ve hata gruplarını çizer
function renderStats(stats) {
    const d = stats.levelDistribution || {};
    $('statChips').innerHTML = `
        ${chip('Toplam', stats.totalEntries, '')}
        ${chip('Hata', (d.ERROR || 0) + (d.FATAL || 0), 'error')}
        ${chip('Uyarı', d.WARN || 0, 'warn')}
        ${chip('Grup', (stats.errorGroups || []).length, '')}`;

    const groups = stats.errorGroups || [];
    $('errorGroups').innerHTML = groups.length ? `
        <div class="eg-title">Tekrarlanan Hata Grupları</div>
        ${groups.slice(0, 5).map(g => `
            <div class="eg-item">
                <div class="eg-head">
                    <span class="eg-type">${esc(g.exceptionType || 'istisna yok')}</span>
                    <span class="eg-count">×${g.occurrenceCount}</span>
                </div>
                <div class="eg-msg">${esc(g.sampleMessage || '')}${g.sampleLineNumber ? ` <em>(satır ${g.sampleLineNumber})</em>` : ''}</div>
            </div>`).join('')}` : '';
}

/* ---------------- Analiz ---------------- */

// POST /api/logs/{id}/analyze → yapay zeka analizini başlatır
async function analyzeFile(id) {
    const area = $('analysisArea');
    const btn = $('analyzeBtn');
    btn.disabled = true;
    area.innerHTML = `<div class="loading"><div class="spinner"></div>
        <span>Yapay zeka analiz ediyor... (10-20 saniye sürebilir)</span></div>`;

    try {
        const res = await fetch(`/api/logs/${id}/analyze`, { method: 'POST' });
        if (!res.ok) throw await problem(res);
        const analysis = await res.json();
        $('providerBadge').textContent = analysis.model || 'Spring AI';
        // Yeni sonucu göster ve geçmişi tazele
        const analyses = await fetch(`/api/analyses?fileId=${id}`).then(r => r.json());
        renderHistory(analyses);
    } catch (err) {
        area.innerHTML = `<div class="analysis-card"><strong style="color:var(--p-critical)">Analiz başarısız</strong>
            <p class="muted">${esc(err.message)}</p></div>`;
    } finally {
        btn.disabled = false;
    }
}

// Bir dosyanın tüm analiz geçmişini (en yeni en üstte) çizer
function renderHistory(analyses) {
    const area = $('analysisArea');
    if (!analyses.length) {
        area.innerHTML = '<p class="history-title">Bu log henüz analiz edilmedi.</p>';
        return;
    }
    area.innerHTML = '<div class="history-title">Analiz Sonuçları</div>'
        + analyses.map(renderAnalysisCard).join('');
}

// Tek bir analiz sonucunu kart olarak biçimlendirir
function renderAnalysisCard(a) {
    const conf = Math.round((a.confidence || 0) * 100);
    const cc = confClass(a.confidence || 0);   // Güven seviyesine göre renk sınıfı (rozet + çubuk aynı rengi kullanır)
    const evidence = (a.evidenceLines || []).length
        ? `<div class="analysis-section"><h4>Kanıt Satırları</h4>
             <div class="evidence">${a.evidenceLines.map(n => `<span class="ev-chip">satır ${n}</span>`).join('')}</div></div>`
        : '';
    return `
    <div class="analysis-card">
        <div class="analysis-top">
            <span class="badge ${cc}">${priorityLabel(a.priority)}</span>
            <div class="confidence">
                <div class="conf-lbl">Güven: %${conf}</div>
                <div class="conf-bar"><div class="conf-fill ${cc}" style="width:${conf}%"></div></div>
            </div>
        </div>
        <div class="analysis-section"><h4>Özet</h4><div class="content">${esc(a.summary)}</div></div>
        <div class="analysis-section"><h4>Olası Kök Neden</h4><div class="content">${esc(a.rootCause)}</div></div>
        <div class="analysis-section"><h4>Çözüm Önerisi</h4><div class="content">${esc(a.solution)}</div></div>
        ${evidence}
        <div class="analysis-foot">
            <span>Model: ${esc(a.model || '—')}</span>
            <span>Süre: ${a.durationMs ?? '—'} ms</span>
            <span>Token: ${a.promptTokens ?? '—'} + ${a.completionTokens ?? '—'}</span>
            <span>${formatDate(a.createdAt)}</span>
        </div>
    </div>`;
}

// Güven değerini (0-1) renk sınıfına eşler: yüksek=yeşil, orta=sarı, düşük=kırmızı
function confClass(confidence) {
    if (confidence >= 0.8) return 'conf-high';
    if (confidence >= 0.5) return 'conf-medium';
    return 'conf-low';
}

/* ---------------- Yardımcılar ---------------- */

// İstatistik rozeti HTML'i
function chip(label, value, cls) {
    return `<div class="chip ${cls}"><div class="chip-val">${value}</div><div class="chip-lbl">${label}</div></div>`;
}

// Öncelik enum'unu Türkçe etikete çevirir
function priorityLabel(p) {
    return { CRITICAL: 'KRİTİK', HIGH: 'YÜKSEK', MEDIUM: 'ORTA', LOW: 'DÜŞÜK' }[p] || p;
}

// Durum enum'unu Türkçe etikete çevirir
function statusLabel(s) {
    return { UPLOADED: 'yüklendi', PARSED: 'ayrıştırıldı', ANALYZED: 'analiz edildi', FAILED: 'hata' }[s] || s;
}

// ISO tarihi okunur biçime çevirir
function formatDate(iso) {
    if (!iso) return '—';
    try { return new Date(iso).toLocaleString('tr-TR'); } catch { return iso; }
}

// XSS'e karşı metni güvenli hale getirir (kullanıcı/model içeriği HTML'e basılmadan önce)
function esc(s) {
    if (s == null) return '';
    return String(s).replace(/[&<>"']/g, c =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// Hata yanıtını (RFC 7807 ProblemDetail) okunur bir Error'a çevirir
async function problem(res) {
    try {
        const p = await res.json();
        return new Error(p.detail || p.title || `HTTP ${res.status}`);
    } catch {
        return new Error(`HTTP ${res.status}`);
    }
}
