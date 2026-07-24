/* ============================================================
   AI Log Analyzer — arayüz mantığı (saf JavaScript, çerçeve yok)
   Backend REST API'siyle fetch üzerinden konuşur.
   ============================================================ */

// Basit uygulama durumu (state)
const state = {
    files: [],          // Yüklenen loglar
    selectedId: null,   // Seçili logun kimliği
    charts: {},         // Aktif Chart.js örnekleri (yeni seçimde yok edilir)
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
    renderCharts(stats);            // Dashboard grafikleri

    // Seviye filtreli kayıt tablosu
    const filter = $('levelFilter');
    filter.value = '';
    filter.onchange = () => loadEntries(id, filter.value);
    loadEntries(id, '');

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

/* ---------------- Dashboard grafikleri (Chart.js) ---------------- */

// Seçili logun istatistiklerinden 3 grafik çizer. Renkler status/semantik (dataviz ilkeleri).
function renderCharts(stats) {
    destroyCharts();                 // Önceki grafikleri temizle (canvas yeniden kullanım hatasını önler)
    const t = themeColors();

    // 1) Seviye dağılımı — doughnut (kimlik: her seviye kendi status rengi; legend + etiket ile)
    const dist = stats.levelDistribution || {};
    const levels = Object.keys(dist);
    if (levels.length) {
        clearEmpty('levelChart');
        state.charts.level = new Chart($('levelChart'), {
            type: 'doughnut',
            data: {
                labels: levels,
                datasets: [{
                    data: levels.map(l => dist[l]),
                    backgroundColor: levels.map(levelColor),
                    borderColor: t.surface,   // Dilimler arası 2px yüzey boşluğu (dataviz mark spec)
                    borderWidth: 2,
                }],
            },
            options: {
                responsive: true, maintainAspectRatio: false, cutout: '58%',
                plugins: { legend: { position: 'bottom', labels: { color: t.text, boxWidth: 12, padding: 10 } } },
            },
        });
    } else {
        setEmpty('levelChart', 'Veri yok');
    }

    // 2) En sık istisnalar — yatay bar (büyüklük: tek seri, tek renk → legend yok, başlık adlandırır)
    const exc = stats.topExceptions || [];
    if (exc.length) {
        clearEmpty('exceptionsChart');
        state.charts.exceptions = new Chart($('exceptionsChart'), {
            type: 'bar',
            data: {
                labels: exc.map(e => truncLabel(shortType(e.type), 20)),   // Uzun adlar kırpılır; tam adı tooltip'te
                datasets: [{ data: exc.map(e => e.count), backgroundColor: '#4f46e5', borderRadius: 4, maxBarThickness: 26 }],
            },
            options: {
                responsive: true, maintainAspectRatio: false, indexAxis: 'y',
                plugins: { legend: { display: false }, tooltip: { callbacks: { title: (i) => exc[i[0].dataIndex].type } } },
                scales: {
                    x: { beginAtZero: true, ticks: { color: t.muted, precision: 0 }, grid: { color: t.grid } },
                    y: { ticks: { color: t.text }, grid: { display: false } },
                },
            },
        });
    } else {
        setEmpty('exceptionsChart', 'İstisna yok');
    }

    // 3) Zaman serisi — çizgi (değişim: 2 seri WARN/ERROR, legend her zaman var)
    const tl = stats.problemTimeline || [];
    if (tl.length) {
        clearEmpty('timelineChart');
        state.charts.timeline = new Chart($('timelineChart'), {
            type: 'line',
            data: {
                labels: tl.map(b => hhmm(b.minute)),
                datasets: [
                    { label: 'WARN', data: tl.map(b => b.warnCount), borderColor: '#ca8a04', backgroundColor: '#ca8a04', borderWidth: 2, tension: .3, pointRadius: 3 },
                    { label: 'ERROR', data: tl.map(b => b.errorCount), borderColor: '#dc2626', backgroundColor: '#dc2626', borderWidth: 2, tension: .3, pointRadius: 3 },
                ],
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                plugins: { legend: { position: 'bottom', labels: { color: t.text, boxWidth: 12, padding: 10 } } },
                scales: {
                    x: { ticks: { color: t.muted, maxRotation: 0, autoSkip: true }, grid: { color: t.grid } },
                    y: { beginAtZero: true, ticks: { color: t.muted, precision: 0 }, grid: { color: t.grid } },
                },
            },
        });
    } else {
        setEmpty('timelineChart', 'Zaman damgalı WARN/ERROR yok');
    }
}

// Aktif grafikleri yok eder (yeni dosya seçilince canvas temizlensin)
function destroyCharts() {
    Object.values(state.charts).forEach(c => c && c.destroy());
    state.charts = {};
}

// Log seviyesine göre status rengi (dataviz doğrulayıcısıyla ayrıştırıldı: WARN #ca8a04 kırmızıdan ayrı)
function levelColor(level) {
    return {
        FATAL: '#7f1d1d', ERROR: '#dc2626', WARN: '#ca8a04',
        INFO: '#2563eb', DEBUG: '#64748b', TRACE: '#cbd5e1', UNKNOWN: '#94a3b8',
    }[level] || '#94a3b8';
}

// Grafik metin/ızgara renklerini aktif temaya (açık/koyu) göre verir
function themeColors() {
    const dark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    return dark
        ? { text: '#e6eaf2', muted: '#94a1b8', grid: '#2a3346', surface: '#171d2b' }
        : { text: '#1c2330', muted: '#6b7688', grid: '#e2e8f0', surface: '#ffffff' };
}

// Veri olmayan grafik için canvas'ı gizleyip mesaj gösterir (canvas'ı DOM'dan silmeden)
function setEmpty(canvasId, msg) {
    const canvas = $(canvasId);
    const wrap = canvas.parentElement;
    canvas.style.display = 'none';
    let ov = wrap.querySelector('.chart-empty');
    if (!ov) { ov = document.createElement('div'); ov.className = 'chart-empty muted'; wrap.appendChild(ov); }
    ov.textContent = msg;
    ov.style.display = 'flex';
}
function clearEmpty(canvasId) {
    const canvas = $(canvasId);
    canvas.style.display = '';
    const ov = canvas.parentElement.querySelector('.chart-empty');
    if (ov) ov.style.display = 'none';
}

/* ---------------- Kayıt tablosu ---------------- */

// GET /api/logs/{id}/entries?level= → tabloyu doldurur
async function loadEntries(fileId, level) {
    const box = $('entriesTable');
    box.innerHTML = '<div class="entry-note muted">yükleniyor...</div>';
    try {
        const url = `/api/logs/${fileId}/entries` + (level ? `?level=${level}` : '');
        const entries = await fetch(url).then(r => r.json());
        renderEntries(entries);
    } catch {
        box.innerHTML = '<div class="entry-note muted">Kayıtlar yüklenemedi.</div>';
    }
}

// Parse edilmiş kayıtları satır satır çizer (çok büyük listelerde ilk 300 gösterilir)
function renderEntries(entries) {
    const box = $('entriesTable');
    if (!entries.length) {
        box.innerHTML = '<div class="entry-note muted">Bu filtreye uygun kayıt yok.</div>';
        return;
    }
    const rows = entries.slice(0, 300).map(e => `
        <div class="entry-row">
            <span class="entry-line">#${e.lineNumber}</span>
            <span class="lvl-badge ${e.level || 'UNKNOWN'}">${e.level || '—'}</span>
            <span class="entry-msg">${esc(e.message || '')}${e.exceptionType ? ` <span class="exc">${esc(shortType(e.exceptionType))}</span>` : ''}${e.hasStackTrace ? ' <span class="muted">(stack trace)</span>' : ''}</span>
        </div>`).join('');
    const more = entries.length > 300 ? `<div class="entry-note muted">... ve ${entries.length - 300} kayıt daha</div>` : '';
    box.innerHTML = rows + more;
}

// Tam nitelikli istisna/logger adından yalnızca son parçayı (sınıf adı) alır
function shortType(t) {
    if (!t) return '—';
    const parts = t.split('.');
    return parts[parts.length - 1];
}

// Uzun etiketi kısaltır (grafik ekseni taşmasın); tam metin tooltip'te gösterilir
function truncLabel(s, max) {
    return s.length > max ? s.slice(0, max - 1) + '…' : s;
}

// ISO zamanı SS:dd biçimine çevirir (zaman serisi ekseni için)
function hhmm(iso) {
    try { return new Date(iso).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }); }
    catch { return iso; }
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
