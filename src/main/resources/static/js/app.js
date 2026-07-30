/* ============================================================
   AI Log Analyzer — arayüz mantığı (saf JavaScript, çerçeve yok)
   Kullanıcının Claude Design tasarımından uyarlanan sekmeli düzen;
   backend REST API'siyle fetch üzerinden konuşur.
   ============================================================ */

// Basit uygulama durumu (state)
const state = {
    files: [],            // Yüklenen loglar
    selectedId: null,     // Seçili logun kimliği
    charts: {},           // Aktif Chart.js örnekleri (yeni seçimde yok edilir)
    stats: null,          // Seçili logun istatistikleri (tema değişince grafikleri yeniden çizmek için)
    analyses: [],         // Seçili logun analiz geçmişi (dil değişince yeniden çizmek için)
    chatAnalysisId: null, // Sohbetin bağlı olduğu analiz (en son analiz)
    activeTab: 'overview',
    lang: localStorage.getItem('lang') || 'tr',   // Arayüz dili: 'tr' | 'en' (tema gibi kalıcı)
};

// --- DOM kısayolları ---
const $ = (id) => document.getElementById(id);

/* ---------------- Dil (Türkçe/İngilizce) ---------------- */
// Tüm arayüz metinleri burada toplanır; sabit HTML metinleri data-i18n* attribute'ları üzerinden,
// JS'in ürettiği dinamik metinler t() çağrılarıyla çevrilir. Yeni bir metin eklerken önce burada
// bir anahtar aç, sonra hem HTML'e hem JS'e anahtarı kullan — ham Türkçe/İngilizce metin gömme.
const I18N = {
    tr: {
        appSubtitle: 'Log dosyalarını yapay zeka ile analiz edin',
        providerBadgeTitle: 'Aktif yapay zeka modeli',
        userChipTitle: 'Giriş yapan kullanıcı',
        logout: 'Çıkış Yap',
        authSubtitle: 'Devam etmek için giriş yapın',
        loginTab: 'Giriş Yap',
        registerTab: 'Kayıt Ol',
        usernamePlaceholder: 'Kullanıcı adı',
        passwordPlaceholder: 'Şifre',
        authHint: 'Varsayılan yönetici: <code>admin / admin123</code>',
        uploadTitle: 'Log dosyası yükleyin',
        uploadSubtitle: '.log / .txt — sürükleyip bırakın veya seçin (maks. 10 MB)',
        chooseFile: 'Dosya Seç',
        uploadedLogs: 'Yüklenen Loglar',
        noFilesYet: 'Henüz dosya yok. Yukarıdan bir log yükleyin.',
        noLogSelected: 'Görüntülenecek log yok',
        noLogSelectedHint: 'Bir log seçin veya yeni bir dosya yükleyin.',
        analyzeBtn: 'Yapay Zeka ile Analiz Et',
        reanalyzeBtn: 'Yeniden Analiz Et',
        analyzingBtn: 'Analiz ediliyor...',
        tabOverview: 'Genel Bakış',
        tabRecords: 'Kayıtlar',
        tabAnalysis: 'Analiz',
        tabChat: 'Sohbet',
        chartLevelDistribution: 'Seviye Dağılımı',
        chartTopExceptions: 'En Sık İstisnalar',
        chartTimeline: 'Zaman Serisi — Dakikalık WARN / ERROR',
        logEntriesHeading: 'Log Kayıtları',
        levelFilterTitle: 'Seviyeye göre filtrele',
        allLevels: 'Tüm seviyeler',
        sessionExpired: 'Oturumunuz sona erdi. Lütfen tekrar giriş yapın.',
        lightTheme: 'Açık Tema',
        darkTheme: 'Koyu Tema',
        uploadingStatus: '"{name}" yükleniyor ve ayrıştırılıyor...',
        uploadedStatus: '"{name}" yüklendi — {lines} satır, {errors} hata.',
        deleteTitle: 'Bu logu ve analizlerini sil',
        delete: 'Sil',
        fileCounts: '{errors} hata · {warns} uyarı',
        deleteConfirm: '"{name}" ve tüm analizleri kalıcı olarak silinsin mi?',
        thisLog: 'bu log',
        deleteFailed: 'Silinemedi: {msg}',
        unknownFormat: 'bilinmeyen format',
        detailMetaLine: '{format} · {lines} satır · {kb} KB',
        statTotal: 'Toplam',
        statErrors: 'Hata',
        statWarnings: 'Uyarı',
        statGroups: 'Grup',
        repeatedGroupsHeading: 'Tekrarlanan Hata Grupları',
        noException: 'istisna yok',
        knownError: 'Bilinen hata',
        lineNumSuffix: '(satır {n})',
        evidenceChip: 'satır {n}',
        knowledgeSeenInMore: ' (toplam {count} farklı dosyada görülmüş)',
        knowledgeSolutionLabel: 'O zamanki çözüm:',
        knowledgeNoAnalysis: 'O dosya için henüz bir yapay zeka analizi yapılmamış.',
        knowledgeTitle: 'Bu hatayı daha önce <strong>{filename}</strong> dosyasında görmüştünüz{extra} — {date}',
        chartNoData: 'Veri yok',
        chartNoExceptions: 'İstisna yok',
        chartNoTimeline: 'Zaman damgalı WARN/ERROR yok',
        transitionHeading: 'WARN&#8594;ERROR Geçişi',
        gapMinutesLater: '{n} dakika sonra',
        sameMinute: 'aynı dakikada',
        transitionSentence: 'Uyarılar {firstWarn} itibarıyla başladı ve {gap} ({firstError}) hataya dönüştü.',
        stormHeading: 'Hata Fırtınası Tespit Edildi',
        stormRatioSuffix: ' — dosya ortalamasının (~{avg}/dk) yaklaşık {ratio} katı',
        stormSentence: '{range} aralığında hata oranı dakikada {peak} hataya sıçradı{ratioTxt}.',
        loadingEllipsis: 'yükleniyor...',
        entriesLoadFailed: 'Kayıtlar yüklenemedi.',
        noMatchingEntries: 'Bu filtreye uygun kayıt yok.',
        moreEntries: '... ve {n} kayıt daha',
        goToLineTitle: 'Satıra git',
        reanalyzeConfirm: 'Bu dosya zaten analiz edildi. Yeniden analiz etmek istediğinize emin misiniz?\nÖnceki sonuç geçmişte saklanmaya devam edecek.',
        analyzingArea: 'Yapay zeka analiz ediyor... (10-20 saniye sürebilir)',
        analysisFailed: 'Analiz başarısız',
        noAnalysisYet: 'Bu log için henüz analiz yapılmadı. Yukarıdaki "{btn}" butonuyla başlatabilirsiniz.',
        historyNote: '{n} analiz — en yenisi üstte',
        confidenceLabel: 'Güven: %{pct}',
        downloadPdfBtn: 'PDF İndir',
        downloadPdfTitle: 'Analizi PDF olarak indir',
        summaryLabel: 'Özet',
        rootCauseLabel: 'Olası Kök Neden',
        solutionLabel: 'Çözüm Önerisi',
        modelLabel: 'Model: {model}',
        durationLabel: 'Süre: {ms} ms',
        tokensLabel: 'Token: {p} + {c}',
        pdfDownloadFailed: 'PDF indirilemedi: {msg}',
        pdfFilename: 'analiz-raporu.pdf',
        chatHeading: 'Log ile Sohbet',
        chatHint: '(en son analiz bağlamında)',
        chatPlaceholder: 'Bu log hakkında bir soru sorun...',
        chatSend: 'Gönder',
        chatNeedsAnalysis: 'Sohbet için önce bir analiz gerekir.',
        chatNoMessages: 'Henüz mesaj yok. İlk soruyu sorun.',
        chatError: 'Hata: {msg}',
        chatTyping: 'yanıtlıyor...',
        priority: { CRITICAL: 'KRİTİK', HIGH: 'YÜKSEK', MEDIUM: 'ORTA', LOW: 'DÜŞÜK' },
        status: { UPLOADED: 'yüklendi', PARSED: 'ayrıştırıldı', ANALYZED: 'analiz edildi', FAILED: 'hata' },
        locale: 'tr-TR',
    },
    en: {
        appSubtitle: 'Analyze log files with artificial intelligence',
        providerBadgeTitle: 'Active AI model',
        userChipTitle: 'Signed-in user',
        logout: 'Log Out',
        authSubtitle: 'Sign in to continue',
        loginTab: 'Sign In',
        registerTab: 'Register',
        usernamePlaceholder: 'Username',
        passwordPlaceholder: 'Password',
        authHint: 'Default admin: <code>admin / admin123</code>',
        uploadTitle: 'Upload a log file',
        uploadSubtitle: '.log / .txt — drag & drop or choose a file (max 10 MB)',
        chooseFile: 'Choose File',
        uploadedLogs: 'Uploaded Logs',
        noFilesYet: 'No files yet. Upload a log above.',
        noLogSelected: 'No log selected',
        noLogSelectedHint: 'Select a log or upload a new file.',
        analyzeBtn: 'Analyze with AI',
        reanalyzeBtn: 'Re-analyze',
        analyzingBtn: 'Analyzing...',
        tabOverview: 'Overview',
        tabRecords: 'Records',
        tabAnalysis: 'Analysis',
        tabChat: 'Chat',
        chartLevelDistribution: 'Level Distribution',
        chartTopExceptions: 'Top Exceptions',
        chartTimeline: 'Time Series — WARN / ERROR per Minute',
        logEntriesHeading: 'Log Entries',
        levelFilterTitle: 'Filter by level',
        allLevels: 'All levels',
        sessionExpired: 'Your session has expired. Please sign in again.',
        lightTheme: 'Light Theme',
        darkTheme: 'Dark Theme',
        uploadingStatus: '"{name}" uploading and parsing...',
        uploadedStatus: '"{name}" uploaded — {lines} lines, {errors} errors.',
        deleteTitle: 'Delete this log and its analyses',
        delete: 'Delete',
        fileCounts: '{errors} errors · {warns} warnings',
        deleteConfirm: 'Permanently delete "{name}" and all its analyses?',
        thisLog: 'this log',
        deleteFailed: 'Could not delete: {msg}',
        unknownFormat: 'unknown format',
        detailMetaLine: '{format} · {lines} lines · {kb} KB',
        statTotal: 'Total',
        statErrors: 'Errors',
        statWarnings: 'Warnings',
        statGroups: 'Groups',
        repeatedGroupsHeading: 'Repeated Error Groups',
        noException: 'no exception',
        knownError: 'Known error',
        lineNumSuffix: '(line {n})',
        evidenceChip: 'line {n}',
        knowledgeSeenInMore: ' (seen in {count} different files in total)',
        knowledgeSolutionLabel: 'Solution at the time:',
        knowledgeNoAnalysis: 'No AI analysis has been done for that file yet.',
        knowledgeTitle: 'You have seen this error before in <strong>{filename}</strong>{extra} — {date}',
        chartNoData: 'No data',
        chartNoExceptions: 'No exceptions',
        chartNoTimeline: 'No timestamped WARN/ERROR',
        transitionHeading: 'WARN&#8594;ERROR Transition',
        gapMinutesLater: '{n} minutes later',
        sameMinute: 'in the same minute',
        transitionSentence: 'Warnings started at {firstWarn} and turned into errors {gap} ({firstError}).',
        stormHeading: 'Error Storm Detected',
        stormRatioSuffix: ' — about {ratio}× the file\'s average (~{avg}/min)',
        stormSentence: 'Between {range}, the error rate spiked to {peak} errors/minute{ratioTxt}.',
        loadingEllipsis: 'loading...',
        entriesLoadFailed: 'Could not load entries.',
        noMatchingEntries: 'No entries match this filter.',
        moreEntries: '... and {n} more entries',
        goToLineTitle: 'Go to line',
        reanalyzeConfirm: 'This file has already been analyzed. Are you sure you want to re-analyze it?\nThe previous result will remain in the history.',
        analyzingArea: 'AI is analyzing... (may take 10-20 seconds)',
        analysisFailed: 'Analysis failed',
        noAnalysisYet: 'No analysis has been done for this log yet. You can start one with the "{btn}" button above.',
        historyNote: '{n} analyses — newest first',
        confidenceLabel: 'Confidence: {pct}%',
        downloadPdfBtn: 'Download PDF',
        downloadPdfTitle: 'Download analysis as PDF',
        summaryLabel: 'Summary',
        rootCauseLabel: 'Possible Root Cause',
        solutionLabel: 'Solution',
        modelLabel: 'Model: {model}',
        durationLabel: 'Duration: {ms} ms',
        tokensLabel: 'Tokens: {p} + {c}',
        pdfDownloadFailed: 'Could not download PDF: {msg}',
        pdfFilename: 'analysis-report.pdf',
        chatHeading: 'Chat with the Log',
        chatHint: '(in the context of the latest analysis)',
        chatPlaceholder: 'Ask a question about this log...',
        chatSend: 'Send',
        chatNeedsAnalysis: 'An analysis is required before chatting.',
        chatNoMessages: 'No messages yet. Ask the first question.',
        chatError: 'Error: {msg}',
        chatTyping: 'responding...',
        priority: { CRITICAL: 'CRITICAL', HIGH: 'HIGH', MEDIUM: 'MEDIUM', LOW: 'LOW' },
        status: { UPLOADED: 'uploaded', PARSED: 'parsed', ANALYZED: 'analyzed', FAILED: 'failed' },
        locale: 'en-US',
    },
};

// Anahtarı aktif dile çevirir; {ad} biçimli yer tutucuları params ile değiştirir
function t(key, params) {
    const dict = I18N[state.lang] || I18N.tr;
    let str = dict[key] !== undefined ? dict[key] : I18N.tr[key];
    if (str === undefined) return key;
    if (params) {
        Object.keys(params).forEach(k => { str = str.split('{' + k + '}').join(params[k]); });
    }
    return str;
}

// Sabit HTML metinlerini (data-i18n / data-i18n-placeholder / data-i18n-title) aktif dile çevirir
function applyStaticTranslations() {
    document.documentElement.lang = state.lang;
    document.querySelectorAll('[data-i18n]').forEach(el => { el.innerHTML = t(el.dataset.i18n); });
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => { el.placeholder = t(el.dataset.i18nPlaceholder); });
    document.querySelectorAll('[data-i18n-title]').forEach(el => { el.title = t(el.dataset.i18nTitle); });
    updateAuthSubmitLabel();
    updateThemeLabel();
}

// authSubmit butonu aktif sekmeye (login/register) göre farklı metin gösterir → data-i18n'den ayrı yönetilir
function updateAuthSubmitLabel() {
    $('authSubmit').textContent = authMode === 'login' ? t('loginTab') : t('registerTab');
}

// Dil düğmesini bağlar: her zaman KARŞI dilin adını gösterir (tema düğmesiyle aynı desen).
// Seçim localStorage'da kalıcı olur; o an ekranda görünen dinamik içerik yeniden çizilir.
function bindLang() {
    const btn = $('langToggle');
    const label = () => { btn.textContent = state.lang === 'tr' ? 'English' : 'Türkçe'; };
    label();
    btn.addEventListener('click', () => {
        state.lang = state.lang === 'tr' ? 'en' : 'tr';
        localStorage.setItem('lang', state.lang);
        label();
        applyStaticTranslations();
        refreshDynamicView();
    });
}

// Dil değişince, o an ekranda görünen dinamik (JS tarafından üretilmiş) içerikleri yeniden çizer.
// Statik HTML zaten applyStaticTranslations() ile çevrilir; burada yalnızca veriye bağlı kısımlar var.
function refreshDynamicView() {
    renderFileList();
    if (!state.selectedId) return;
    const file = state.files.find(f => f.id === state.selectedId);
    $('detailMeta').textContent = file
        ? t('detailMetaLine', { format: file.detectedFormat || t('unknownFormat'), lines: file.lineCount, kb: (file.sizeBytes / 1024).toFixed(1) })
        : '';
    if (state.stats) {
        renderStats(state.stats);
        renderCharts(state.stats);
        renderGroups(state.stats);
    }
    loadEntries(state.selectedId, $('levelFilter').value);
    setAnalyzeButtonState($('analyzeBtn').dataset.analyzed === 'true');
    renderHistory(state.analyses);
}

// Sayfa yüklenince: her zaman dil/tema/sekme/yükleme/giriş olaylarını bağla, sonra oturumu doğrula
document.addEventListener('DOMContentLoaded', async () => {
    $('providerBadge').textContent = 'Spring AI';
    applyStaticTranslations();
    bindLang();
    bindTheme();
    bindTabs();
    bindUpload();
    bindAuth();
    await ensureAuthenticated();
});

/* ---------------- Kimlik doğrulama (JWT) ---------------- */

const TOKEN_KEY = 'authToken';
const token = () => localStorage.getItem(TOKEN_KEY);
const setToken = (tok) => localStorage.setItem(TOKEN_KEY, tok);
const clearToken = () => localStorage.removeItem(TOKEN_KEY);

// Token varsa Authorization header'ı üretir (yoksa boş nesne)
function authHeaders() {
    const tok = token();
    return tok ? { Authorization: 'Bearer ' + tok } : {};
}

/**
 * Kimlik doğrulamalı fetch sarmalayıcısı: her isteğe token'ı ekler.
 * 401 dönerse oturum bitmiştir → token temizlenir ve giriş ekranı gösterilir.
 * Tüm /api çağrıları (giriş/kayıt hariç) bunu kullanır.
 */
async function api(url, opts = {}) {
    const headers = Object.assign({}, opts.headers || {}, authHeaders());
    const res = await fetch(url, Object.assign({}, opts, { headers }));
    if (res.status === 401) {
        clearToken();
        showLogin();
        throw new Error(t('sessionExpired'));
    }
    return res;
}

// Açılışta: token yoksa giriş ekranı; varsa /me ile doğrula, geçerliyse uygulamayı başlat
async function ensureAuthenticated() {
    if (!token()) { showLogin(); return; }
    try {
        const res = await fetch('/api/auth/me', { headers: authHeaders() });
        if (!res.ok) { clearToken(); showLogin(); return; }
        onLoggedIn(await res.json());
    } catch {
        showLogin();   // Ağ hatası vb. → güvenli tarafta kal
    }
}

// Giriş/kayıt ekranı olay bağlama: sekme geçişi, form gönderimi, çıkış butonu
function bindAuth() {
    // Giriş / Kayıt sekmeleri
    $('authTabs').querySelectorAll('.auth-tab').forEach(btn =>
        btn.addEventListener('click', () => setAuthMode(btn.dataset.authTab)));
    $('authForm').addEventListener('submit', onAuthSubmit);
    $('logoutBtn').addEventListener('click', logout);
}

let authMode = 'login';   // 'login' | 'register'

function setAuthMode(mode) {
    authMode = mode;
    $('authTabs').querySelectorAll('.auth-tab').forEach(b =>
        b.classList.toggle('active', b.dataset.authTab === mode));
    updateAuthSubmitLabel();
    $('authError').textContent = '';
}

// Giriş veya kayıt isteğini gönderir (bu iki uç herkese açıktır → token gerekmez)
async function onAuthSubmit(e) {
    e.preventDefault();
    const username = $('authUsername').value.trim();
    const password = $('authPassword').value;
    const errBox = $('authError');
    errBox.textContent = '';
    const submit = $('authSubmit');
    submit.disabled = true;

    const endpoint = authMode === 'login' ? '/api/auth/login' : '/api/auth/register';
    try {
        const res = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password }),
        });
        if (!res.ok) throw await problem(res);
        const auth = await res.json();
        setToken(auth.token);
        $('authForm').reset();
        onLoggedIn(auth);
    } catch (err) {
        errBox.textContent = err.message;
    } finally {
        submit.disabled = false;
    }
}

// Başarılı girişten sonra: ekranı aç, kullanıcı bilgisini göster, verileri yükle
async function onLoggedIn(user) {
    hideLogin();
    showUser(user);
    await loadFiles();
    // Deep-link: /?file=<id>&tab=<sekme> verilirse o logu otomatik seç (paylaşılabilir bağlantı)
    const params = new URLSearchParams(location.search);
    const preselect = params.get('file');
    if (preselect) {
        await selectFile(preselect);
        const tab = params.get('tab');
        if (tab) showTab(tab);
    }
}

// Üst başlıkta kullanıcı adı (+ ADMIN rozeti) ve "Çıkış Yap" düğmesini gösterir
function showUser(user) {
    const chip = $('userChip');
    chip.textContent = user.role === 'ADMIN' ? `${user.username} (ADMIN)` : user.username;
    chip.classList.remove('hidden');
    $('logoutBtn').classList.remove('hidden');
}

// Çıkış: token'ı sil, durumu temizle, giriş ekranını göster
function logout() {
    clearToken();
    state.files = [];
    state.selectedId = null;
    state.stats = null;
    renderFileList();
    $('detailContent').classList.add('hidden');
    $('detailEmpty').classList.remove('hidden');
    $('userChip').classList.add('hidden');
    $('logoutBtn').classList.add('hidden');
    showLogin();
}

function showLogin() {
    setAuthMode('login');
    $('authError').textContent = '';
    $('authOverlay').classList.remove('hidden');
}

function hideLogin() {
    $('authOverlay').classList.add('hidden');
}

/* ---------------- Tema (açık/koyu) ---------------- */

// Tema düğmesini bağlar; seçim localStorage'da saklanır, grafikler yeniden boyanır
function bindTheme() {
    const btn = $('themeToggle');
    updateThemeLabel();
    btn.addEventListener('click', () => {
        const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
        document.documentElement.dataset.theme = next;
        localStorage.setItem('theme', next);
        updateThemeLabel();
        if (state.stats) renderCharts(state.stats);   // Grafik renkleri yeni temaya uysun
    });
}

// Tema düğmesinin metnini günceller (dil değişince de çağrılır, bkz. applyStaticTranslations)
function updateThemeLabel() {
    $('themeToggle').textContent = document.documentElement.dataset.theme === 'dark' ? t('lightTheme') : t('darkTheme');
}

// Aktif temanın CSS değişkenlerini okur (grafikler CSS ile aynı paleti kullansın)
function themeColors() {
    const css = getComputedStyle(document.documentElement);
    const v = (name) => css.getPropertyValue(name).trim();
    return {
        text: v('--text'), muted: v('--muted'), grid: v('--border'), surface: v('--surface'),
        accent: v('--accent'),
        warn: v('--chart-warn'), error: v('--chart-error'), info: v('--chart-info'), debug: v('--chart-debug'),
    };
}

/* ---------------- Sekmeler ---------------- */

// Sekme düğmelerini bağlar; içerikler gizle/göster ile değişir
function bindTabs() {
    $('tabBar').querySelectorAll('.tab-btn').forEach(btn =>
        btn.addEventListener('click', () => showTab(btn.dataset.tab)));
}

function showTab(tab) {
    state.activeTab = tab;
    $('tabBar').querySelectorAll('.tab-btn').forEach(b =>
        b.classList.toggle('active', b.dataset.tab === tab));
    ['overview', 'records', 'analysis', 'chat'].forEach(t =>
        $('tab-' + t).classList.toggle('hidden', t !== tab));
    // Gizliyken çizilen grafikler 0 boyutta kalabilir; görünür olunca tazele
    if (tab === 'overview' && state.stats) renderCharts(state.stats);
}

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
    status.textContent = t('uploadingStatus', { name: file.name });

    const form = new FormData();
    form.append('file', file);

    try {
        const res = await api('/api/logs', { method: 'POST', body: form });
        if (!res.ok) throw await problem(res);
        const summary = await res.json();
        status.className = 'upload-status ok';
        status.textContent = t('uploadedStatus', { name: summary.filename, lines: summary.lineCount, errors: summary.errorCount });
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
        const res = await api('/api/logs');
        if (!res.ok) return;   // 401 zaten api() içinde ele alınır (giriş ekranı)
        state.files = await res.json();
        renderFileList();
    } catch (err) {
        console.error('Dosyalar yüklenemedi', err);
    }
}

// Yüklenen loglar listesini çizer (kart: ad + Sil, format rozeti, sayılar, durum rozeti)
function renderFileList() {
    const box = $('fileList');
    if (!state.files.length) {
        box.innerHTML = `<p class="empty muted">${t('noFilesYet')}</p>`;
        return;
    }
    box.innerHTML = state.files.map(f => `
        <div class="file-item ${f.id === state.selectedId ? 'selected' : ''}" data-id="${f.id}">
            <div class="fi-top">
                <div class="fi-name">${esc(f.filename)}</div>
                <button class="fi-del" data-del="${f.id}" title="${t('deleteTitle')}">${t('delete')}</button>
            </div>
            <div class="fi-meta">
                <span class="fi-format">${esc(f.detectedFormat || '—')}</span>
                <span class="fi-counts">${t('fileCounts', { errors: f.errorCount, warns: f.warnCount })}</span>
            </div>
            <span class="fi-status ${f.status === 'ANALYZED' ? 'analyzed' : ''}">${statusLabel(f.status)}</span>
        </div>`).join('');
    // Karta tıklama → seç
    box.querySelectorAll('.file-item').forEach(el =>
        el.addEventListener('click', () => selectFile(el.dataset.id)));
    // Sil butonuna tıklama → seçimi tetiklemeden sil
    box.querySelectorAll('.fi-del').forEach(btn =>
        btn.addEventListener('click', (e) => { e.stopPropagation(); deleteFile(btn.dataset.del); }));
}

// DELETE /api/logs/{id} → log dosyasını ve bağlı verilerini siler
async function deleteFile(id) {
    const file = state.files.find(f => f.id === id);
    const name = file ? file.filename : t('thisLog');
    if (!confirm(t('deleteConfirm', { name }))) return;
    try {
        const res = await api(`/api/logs/${id}`, { method: 'DELETE' });
        if (!res.ok) throw await problem(res);
        // Silinen dosya seçiliyse detay panelini kapat
        if (state.selectedId === id) {
            state.selectedId = null;
            state.stats = null;
            $('detailContent').classList.add('hidden');
            $('detailEmpty').classList.remove('hidden');
        }
        await loadFiles();
    } catch (err) {
        alert(t('deleteFailed', { msg: err.message }));
    }
}

/* ---------------- Seçili dosya detayı ---------------- */

// Bir logu seçer: istatistiklerini ve geçmiş analizlerini getirir
async function selectFile(id) {
    state.selectedId = id;
    renderFileList();
    $('detailEmpty').classList.add('hidden');
    $('detailContent').classList.remove('hidden');
    showTab('overview');            // Yeni seçimde Genel Bakış'a dön (grafikler görünürken çizilsin)

    const file = state.files.find(f => f.id === id);
    $('detailFilename').textContent = file ? file.filename : '';
    $('detailMeta').textContent = file
        ? t('detailMetaLine', { format: file.detectedFormat || t('unknownFormat'), lines: file.lineCount, kb: (file.sizeBytes / 1024).toFixed(1) })
        : '';
    $('analyzeBtn').onclick = () => onAnalyzeClick(id);
    $('analysisArea').innerHTML = '';

    // İstatistik ve geçmiş analizleri paralel getir
    const [stats, analyses] = await Promise.all([
        api(`/api/logs/${id}/stats`).then(r => r.json()),
        api(`/api/analyses?fileId=${id}`).then(r => r.json()),
    ]);
    state.stats = stats;
    state.analyses = analyses;      // Dil değişince yeniden çizebilmek için sakla
    renderStats(stats);
    renderCharts(stats);            // Dashboard grafikleri
    renderGroups(stats);            // Katlanabilir hata grupları (Kayıtlar sekmesi)

    // Seviye filtreli kayıt tablosu
    const filter = $('levelFilter');
    filter.value = '';
    filter.onchange = () => loadEntries(id, filter.value);
    loadEntries(id, '');

    setAnalyzeButtonState(analyses.length > 0);   // Zaten analiz edilmişse butonu "Yeniden Analiz Et"e çevir
    renderHistory(analyses);
}

// İstatistik kartlarını (Toplam/Hata/Uyarı/Grup) çizer
function renderStats(stats) {
    const d = stats.levelDistribution || {};
    $('statChips').innerHTML = `
        ${stat(t('statTotal'), stats.totalEntries, '')}
        ${stat(t('statErrors'), (d.ERROR || 0) + (d.FATAL || 0), 'error')}
        ${stat(t('statWarnings'), d.WARN || 0, 'warn')}
        ${stat(t('statGroups'), (stats.errorGroups || []).length, 'accent')}`;
}

// Tekrarlanan hata gruplarını katlanabilir kartlar olarak çizer (başlığa tıkla → mesajı aç/kapa)
function renderGroups(stats) {
    const groups = stats.errorGroups || [];
    const box = $('errorGroups');
    if (!groups.length) { box.innerHTML = ''; return; }
    box.innerHTML = `
        <h4 class="section-heading" style="margin-bottom:12px">${t('repeatedGroupsHeading')}</h4>
        <div class="group-list">
        ${groups.slice(0, 5).map((g, i) => `
            <div class="eg-item">
                <div class="eg-head" data-eg="${i}">
                    <span class="eg-type">${esc(g.exceptionType || t('noException'))}</span>
                    ${g.knowledgeHint ? `<span class="eg-known">${t('knownError')}</span>` : ''}
                    <span class="eg-count">×${g.occurrenceCount}</span>
                    <span class="eg-chevron">&#9660;</span>
                </div>
                <div class="eg-msg hidden">
                    <div>${esc(g.sampleMessage || '')}${g.sampleLineNumber ? ` <em>${t('lineNumSuffix', { n: g.sampleLineNumber })}</em>` : ''}</div>
                    ${knowledgeHintHtml(g.knowledgeHint)}
                </div>
            </div>`).join('')}
        </div>`;
    box.querySelectorAll('.eg-head').forEach(head =>
        head.addEventListener('click', () => {
            const msg = head.nextElementSibling;
            const open = msg.classList.toggle('hidden');
            head.querySelector('.eg-chevron').innerHTML = open ? '&#9660;' : '&#9650;';
        }));
}

// Hata bilgi bankası: bu hata geçmişte başka bir dosyada görüldüyse bir bilgi kartı çizer.
// AI olmadan, yalnızca fingerprint eşleşmesiyle hesaplanır (bkz. StatsServiceImpl.buildKnowledgeHint).
function knowledgeHintHtml(hint) {
    if (!hint) return '';
    const extra = hint.pastFileCount > 1 ? t('knowledgeSeenInMore', { count: hint.pastFileCount }) : '';
    const solutionPart = hint.pastSolution
        ? `<div class="kh-solution"><strong>${t('knowledgeSolutionLabel')}</strong> ${mdLite(hint.pastSolution)}</div>`
        : `<div class="kh-solution muted">${t('knowledgeNoAnalysis')}</div>`;
    return `<div class="knowledge-hint">
        <div class="kh-title">${t('knowledgeTitle', { filename: esc(hint.sourceFilename), extra, date: formatDate(hint.lastSeenBefore) })}</div>
        ${solutionPart}
    </div>`;
}

/* ---------------- Dashboard grafikleri (Chart.js) ---------------- */

// Seçili logun istatistiklerinden 3 grafik çizer. Renkler status/semantik (dataviz ilkeleri).
function renderCharts(stats) {
    destroyCharts();                 // Önceki grafikleri temizle (canvas yeniden kullanım hatasını önler)
    const col = themeColors();       // NOT: 't' değil — global çeviri fonksiyonu t() ile ÇAKIŞMASIN diye 'col'

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
                    backgroundColor: levels.map(l => levelColor(l, col)),
                    borderColor: col.surface,   // Dilimler arası 2px yüzey boşluğu (dataviz mark spec)
                    borderWidth: 2,
                }],
            },
            options: {
                responsive: true, maintainAspectRatio: false, cutout: '68%',
                plugins: { legend: { position: 'bottom', labels: { color: col.muted, boxWidth: 10, padding: 10, font: { size: 11 } } } },
            },
        });
    } else {
        setEmpty('levelChart', t('chartNoData'));
    }

    // 2) En sık istisnalar — yatay bar (büyüklük: tek seri, vurgu rengi → legend yok, başlık adlandırır)
    const exc = stats.topExceptions || [];
    if (exc.length) {
        clearEmpty('exceptionsChart');
        state.charts.exceptions = new Chart($('exceptionsChart'), {
            type: 'bar',
            data: {
                labels: exc.map(e => truncLabel(shortType(e.type), 20)),   // Uzun adlar kırpılır; tam adı tooltip'te
                datasets: [{ data: exc.map(e => e.count), backgroundColor: col.accent, borderRadius: 4, maxBarThickness: 22 }],
            },
            options: {
                responsive: true, maintainAspectRatio: false, indexAxis: 'y',
                plugins: { legend: { display: false }, tooltip: { callbacks: { title: (i) => exc[i[0].dataIndex].type } } },
                scales: {
                    x: { beginAtZero: true, ticks: { color: col.muted, precision: 0 }, grid: { color: col.grid, drawTicks: false } },
                    y: { ticks: { color: col.muted }, grid: { display: false } },
                },
            },
        });
    } else {
        setEmpty('exceptionsChart', t('chartNoExceptions'));
    }

    // 3) Zaman serisi — çizgi (değişim: 2 seri WARN/ERROR, legend her zaman var)
    const tl = stats.problemTimeline || [];
    if (tl.length) {
        clearEmpty('timelineChart');
        // Hata fırtınası penceresindeki noktaları grafikte büyük+halkalı göstererek göze çarptırır
        // (istatistiksel anomali — bkz. StatsServiceImpl.detectErrorStorm, AI gerekmez).
        const storm = stats.errorStorm;
        const inStorm = (iso) => storm && new Date(iso) >= new Date(storm.stormStartMinute)
            && new Date(iso) <= new Date(storm.stormEndMinute);
        state.charts.timeline = new Chart($('timelineChart'), {
            type: 'line',
            data: {
                labels: tl.map(b => hhmm(b.minute)),
                datasets: [
                    { label: 'WARN', data: tl.map(b => b.warnCount), borderColor: col.warn, backgroundColor: col.warn, borderWidth: 2, tension: .3, pointRadius: 3 },
                    {
                        label: 'ERROR', data: tl.map(b => b.errorCount), borderColor: col.error, backgroundColor: col.error,
                        borderWidth: 2, tension: .3,
                        pointRadius: tl.map(b => inStorm(b.minute) ? 7 : 3),
                        pointBorderColor: tl.map(b => inStorm(b.minute) ? col.surface : col.error),
                        pointBorderWidth: tl.map(b => inStorm(b.minute) ? 2 : 1),
                    },
                ],
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                plugins: { legend: { position: 'bottom', labels: { color: col.muted, boxWidth: 10, padding: 10 } } },
                scales: {
                    x: { ticks: { color: col.muted, maxRotation: 0, autoSkip: true }, grid: { display: false } },
                    y: { beginAtZero: true, ticks: { color: col.muted, precision: 0 }, grid: { color: col.grid, drawTicks: false } },
                },
            },
        });
    } else {
        setEmpty('timelineChart', t('chartNoTimeline'));
    }

    renderTransition(stats);         // WARN→ERROR geçiş içgörüsü
    renderStorm(stats);              // Hata fırtınası (anomali) içgörüsü
}

// WARN→ERROR geçişi varsa bir içgörü cümlesi gösterir ("uyarılar başladı, N dk sonra hataya dönüştü")
function renderTransition(stats) {
    const el = $('transitionInsight');
    const tr = stats.warnToErrorTransition;
    if (!tr) { el.innerHTML = ''; return; }
    const gapTxt = tr.gapMinutes > 0 ? t('gapMinutesLater', { n: tr.gapMinutes }) : t('sameMinute');
    el.innerHTML = `<div class="insight">
        <strong>${t('transitionHeading')}</strong>
        <span>${t('transitionSentence', { firstWarn: hhmm(tr.firstWarn), gap: gapTxt, firstError: hhmm(tr.firstError) })}</span>
    </div>`;
}

// Hata fırtınası (istatistiksel anomali) tespit edildiyse bir uyarı paneli gösterir.
// AI'sız, yalnızca dosyanın kendi ortalama+standart sapmasına göre hesaplanır (z-score).
function renderStorm(stats) {
    const el = $('stormInsight');
    const s = stats.errorStorm;
    if (!s) { el.innerHTML = ''; return; }
    const ratioTxt = s.peakToBaselineRatio != null
        ? t('stormRatioSuffix', { avg: s.baselineAverage.toFixed(1), ratio: s.peakToBaselineRatio.toFixed(1) })
        : '';
    const rangeTxt = s.stormStartMinute === s.stormEndMinute
        ? hhmm(s.stormStartMinute)
        : `${hhmm(s.stormStartMinute)}–${hhmm(s.stormEndMinute)}`;
    el.innerHTML = `<div class="insight storm">
        <strong>${t('stormHeading')}</strong>
        <span>${t('stormSentence', { range: rangeTxt, peak: s.peakErrorCount, ratioTxt })}</span>
    </div>`;
}

// Aktif grafikleri yok eder (yeni dosya seçilince canvas temizlensin)
function destroyCharts() {
    Object.values(state.charts).forEach(c => c && c.destroy());
    state.charts = {};
}

// Log seviyesine göre status rengi (temaya göre; WARN kırmızıdan ayrık — dataviz doğrulaması)
function levelColor(level, col) {
    return {
        FATAL: '#7f1d1d', ERROR: col.error, WARN: col.warn,
        INFO: col.info, DEBUG: col.debug, TRACE: '#cbd5e1', UNKNOWN: col.debug,
    }[level] || col.debug;
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
    box.innerHTML = `<div class="entry-note muted">${t('loadingEllipsis')}</div>`;
    try {
        const url = `/api/logs/${fileId}/entries` + (level ? `?level=${level}` : '');
        const entries = await api(url).then(r => r.json());
        renderEntries(entries);
    } catch {
        box.innerHTML = `<div class="entry-note muted">${t('entriesLoadFailed')}</div>`;
    }
}

// Parse edilmiş kayıtları satır satır çizer (çok büyük listelerde ilk 300 gösterilir)
function renderEntries(entries) {
    const box = $('entriesTable');
    if (!entries.length) {
        box.innerHTML = `<div class="entry-note muted">${t('noMatchingEntries')}</div>`;
        return;
    }
    const rows = entries.slice(0, 300).map(e => `
        <div class="entry-row" id="entry-row-${e.lineNumber}">
            <span class="entry-line">#${e.lineNumber}</span>
            <span><span class="lvl-badge ${e.level || 'UNKNOWN'}">${e.level || '—'}</span></span>
            <span class="entry-msg">${esc(e.message || '')}${e.exceptionType ? ` <span class="exc">${esc(shortType(e.exceptionType))}</span>` : ''}${e.hasStackTrace ? ' <span class="muted">(stack trace)</span>' : ''}</span>
        </div>`).join('');
    const more = entries.length > 300 ? `<div class="entry-note muted">${t('moreEntries', { n: entries.length - 300 })}</div>` : '';
    box.innerHTML = rows + more;
}

// Kanıt çipinden gelen satıra atlar: Kayıtlar sekmesine geç, satırı ortala ve kısa süre vurgula
function jumpToLine(line) {
    showTab('records');
    const filter = $('levelFilter');
    const doJump = () => {
        const row = $('entry-row-' + line);
        if (!row) return;
        row.scrollIntoView({ block: 'center' });
        row.classList.add('highlight');
        setTimeout(() => row.classList.remove('highlight'), 2200);
    };
    if (filter.value) {              // Filtre satırı gizliyorsa önce tüm seviyelere dön
        filter.value = '';
        loadEntries(state.selectedId, '').then(doJump);
    } else {
        setTimeout(doJump, 60);
    }
}

// Tam nitelikli istisna/logger adından yalnızca son parçayı (sınıf adı) alır
function shortType(type) {
    if (!type) return '—';
    const parts = type.split('.');
    return parts[parts.length - 1];
}

// Uzun etiketi kısaltır (grafik ekseni taşmasın); tam metin tooltip'te gösterilir
function truncLabel(s, max) {
    return s.length > max ? s.slice(0, max - 1) + '…' : s;
}

// ISO zamanı SS:dd biçimine çevirir (zaman serisi ekseni için)
function hhmm(iso) {
    try { return new Date(iso).toLocaleTimeString(t('locale'), { hour: '2-digit', minute: '2-digit' }); }
    catch { return iso; }
}

/* ---------------- Analiz ---------------- */

// Analiz butonuna tıklanınca çağrılır. Dosya daha önce analiz edilmişse (buton "Yeniden Analiz Et"
// durumundaysa) kazara ikinci bir analiz üretilmesini önlemek için önce onay ister.
function onAnalyzeClick(id) {
    const btn = $('analyzeBtn');
    if (btn.dataset.analyzed === 'true') {
        const ok = confirm(t('reanalyzeConfirm'));
        if (!ok) return;
    }
    analyzeFile(id);
}

// Analiz butonunun görünümünü dosyanın analiz durumuna göre ayarlar: hiç analiz yoksa vurgulu
// "Yapay Zeka ile Analiz Et" (btn-accent); en az bir analiz varsa soluk "Yeniden Analiz Et" (btn-outline)
// — bu, kazara tekrar tıklamayı görsel olarak caydırır ama bilinçli tekrar analizi engellemez.
function setAnalyzeButtonState(hasAnalyses) {
    const btn = $('analyzeBtn');
    btn.dataset.analyzed = hasAnalyses ? 'true' : 'false';
    btn.textContent = hasAnalyses ? t('reanalyzeBtn') : t('analyzeBtn');
    btn.classList.toggle('btn-accent', !hasAnalyses);
    btn.classList.toggle('btn-outline', hasAnalyses);
}

// POST /api/logs/{id}/analyze → yapay zeka analizini başlatır
async function analyzeFile(id) {
    const area = $('analysisArea');
    const btn = $('analyzeBtn');
    const wasAnalyzed = btn.dataset.analyzed === 'true';   // Hata durumunda butonu buna göre geri yükle
    btn.disabled = true;
    btn.textContent = t('analyzingBtn');
    showTab('analysis');
    area.innerHTML = `<div class="loading"><div class="spinner"></div>
        <span>${t('analyzingArea')}</span></div>`;

    try {
        const res = await api(`/api/logs/${id}/analyze`, { method: 'POST' });
        if (!res.ok) throw await problem(res);
        const analysis = await res.json();
        $('providerBadge').textContent = analysis.model || 'Spring AI';
        // Yeni sonucu göster ve geçmişi/listeyi tazele (durum rozetleri değişmiş olabilir)
        const analyses = await api(`/api/analyses?fileId=${id}`).then(r => r.json());
        state.analyses = analyses;
        renderHistory(analyses);
        setAnalyzeButtonState(true);   // Başarılı → artık "Yeniden Analiz Et"
        loadFiles();
    } catch (err) {
        area.innerHTML = `<div class="analysis-card"><strong style="color:var(--danger)">${t('analysisFailed')}</strong>
            <p class="muted" style="margin:0">${esc(err.message)}</p></div>`;
        setAnalyzeButtonState(wasAnalyzed);   // Başarısız → deneme öncesi duruma dön
    } finally {
        btn.disabled = false;
    }
}

// Bir dosyanın tüm analiz geçmişini (en yeni en üstte) çizer
function renderHistory(analyses) {
    const area = $('analysisArea');
    if (!analyses.length) {
        area.innerHTML = `<div class="empty-analysis">${t('noAnalysisYet', { btn: t('analyzeBtn') })}</div>`;
    } else {
        area.innerHTML = analyses.map(renderAnalysisCard).join('')
            + (analyses.length > 1 ? `<div class="history-note">${t('historyNote', { n: analyses.length })}</div>` : '');
        bindAnalysisEvents(area);
    }
    renderChat(analyses);          // Analiz varsa sohbet arayüzünü kur
}

// Tek bir analiz sonucunu kart olarak biçimlendirir (akordeonlu bölümler + tıklanır kanıt çipleri)
function renderAnalysisCard(a) {
    const conf = Math.round((a.confidence || 0) * 100);
    const cc = confClass(a.confidence || 0);   // Güven seviyesine göre renk sınıfı (rozet + çubuk aynı rengi kullanır)
    const evidence = (a.evidenceLines || []).length
        ? `<div class="evidence-row">${a.evidenceLines.map(n =>
              `<span class="ev-chip" data-line="${n}" title="${t('goToLineTitle')}">${t('evidenceChip', { n })}</span>`).join('')}</div>`
        : '';
    return `
    <div class="analysis-card">
        <div class="analysis-top">
            <span class="badge ${cc}">${priorityLabel(a.priority)}</span>
            <div class="confidence">
                <div class="conf-lbl">${t('confidenceLabel', { pct: conf })}</div>
                <div class="conf-bar"><div class="conf-fill ${cc}" style="width:${conf}%"></div></div>
            </div>
            <button type="button" class="btn-sm" data-pdf="${a.id}" title="${t('downloadPdfTitle')}">${t('downloadPdfBtn')}</button>
        </div>
        <div>
            <div class="analysis-label">${t('summaryLabel')}</div>
            <div class="analysis-text">${mdLite(a.summary)}</div>
        </div>
        <div class="accordion">
            <div class="accordion-head" data-acc>
                <span class="analysis-label">${t('rootCauseLabel')}</span>
                <span class="eg-chevron">&#9650;</span>
            </div>
            <div class="analysis-text">${mdLite(a.rootCause)}</div>
        </div>
        <div class="accordion">
            <div class="accordion-head" data-acc>
                <span class="analysis-label">${t('solutionLabel')}</span>
                <span class="eg-chevron">&#9650;</span>
            </div>
            <div class="analysis-text">${mdLite(a.solution)}</div>
        </div>
        ${evidence}
        <div class="analysis-foot">
            <span>${t('modelLabel', { model: esc(a.model || '—') })}</span>
            <span>${t('durationLabel', { ms: a.durationMs ?? '—' })}</span>
            <span>${t('tokensLabel', { p: a.promptTokens ?? '—', c: a.completionTokens ?? '—' })}</span>
            <span>${formatDate(a.createdAt)}</span>
        </div>
    </div>`;
}

// Analiz kartındaki akordeon ve kanıt çipi olaylarını bağlar
function bindAnalysisEvents(area) {
    area.querySelectorAll('[data-acc]').forEach(head =>
        head.addEventListener('click', () => {
            const body = head.nextElementSibling;
            const nowHidden = body.classList.toggle('hidden');
            head.querySelector('.eg-chevron').innerHTML = nowHidden ? '&#9660;' : '&#9650;';
        }));
    area.querySelectorAll('.ev-chip').forEach(chip =>
        chip.addEventListener('click', () => jumpToLine(chip.dataset.line)));
    // PDF: token header'ı anchor ile gönderilemez → auth'lu fetch ile blob indir
    area.querySelectorAll('[data-pdf]').forEach(btn =>
        btn.addEventListener('click', () => downloadPdf(btn.dataset.pdf)));
}

// Analiz PDF'ini kimlik doğrulamalı olarak indirir (blob → geçici bağlantı)
async function downloadPdf(id) {
    try {
        const res = await api(`/api/analyses/${id}/report.pdf`);
        if (!res.ok) throw await problem(res);
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = t('pdfFilename');
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    } catch (err) {
        alert(t('pdfDownloadFailed', { msg: err.message }));
    }
}

// Güven değerini (0-1) renk sınıfına eşler: yüksek=yeşil, orta=sarı, düşük=kırmızı
function confClass(confidence) {
    if (confidence >= 0.8) return 'conf-high';
    if (confidence >= 0.5) return 'conf-medium';
    return 'conf-low';
}

/* ---------------- Log ile sohbet ---------------- */

// Bir analiz varsa (en sonuncusu bağlam alınır) sohbet panelini kurar
function renderChat(analyses) {
    const area = $('chatArea');
    if (!analyses.length) {
        area.innerHTML = `<div class="empty-analysis">${t('chatNeedsAnalysis')}</div>`;
        state.chatAnalysisId = null;
        return;
    }

    const latest = analyses[0];              // En son analiz (liste createdAt'e göre azalan)
    state.chatAnalysisId = latest.id;
    area.innerHTML = `
        <div class="chat-card">
            <h4 class="section-heading" style="margin:0">${t('chatHeading')} <span class="chat-hint muted">${t('chatHint')}</span></h4>
            <div id="chatMessages" class="chat-messages"></div>
            <form id="chatForm" class="chat-input" autocomplete="off">
                <input id="chatInput" type="text" maxlength="2000" placeholder="${t('chatPlaceholder')}">
                <button type="submit" class="btn-accent">${t('chatSend')}</button>
            </form>
        </div>`;
    $('chatForm').addEventListener('submit', onChatSubmit);
    loadChatHistory(latest.id);
}

// GET /api/analyses/{id}/chat → geçmiş mesajları çizer
async function loadChatHistory(analysisId) {
    try {
        const msgs = await api(`/api/analyses/${analysisId}/chat`).then(r => r.json());
        renderChatMessages(msgs);
    } catch { /* geçmiş yüklenemezse sessiz geç */ }
}

function renderChatMessages(msgs) {
    const box = $('chatMessages');
    if (!msgs || !msgs.length) {
        box.innerHTML = `<div class="chat-empty muted">${t('chatNoMessages')}</div>`;
        return;
    }
    box.innerHTML = msgs.map(chatBubble).join('');
    box.scrollTop = box.scrollHeight;
}

// Tek bir mesajı baloncuk olarak biçimlendirir (kullanıcı sağ, asistan sol)
function chatBubble(m) {
    const who = m.role === 'USER' ? 'user' : 'assistant';
    // Kullanıcı mesajı düz metin; asistan yanıtında hafif markdown biçimlendirmesi uygulanır
    const content = who === 'user' ? esc(m.content) : mdLite(m.content);
    return `<div class="chat-bubble ${who}"><div class="chat-content">${content}</div></div>`;
}

// Soru gönderme: optimistik olarak kullanıcı mesajını + "yanıtlıyor" göster, yanıt gelince ekle
async function onChatSubmit(e) {
    e.preventDefault();
    const input = $('chatInput');
    const question = input.value.trim();
    if (!question || !state.chatAnalysisId) return;
    input.value = '';

    appendBubble({ role: 'USER', content: question });
    const loading = appendLoadingBubble();
    const btn = e.target.querySelector('button');
    btn.disabled = true; input.disabled = true;

    try {
        const res = await api(`/api/analyses/${state.chatAnalysisId}/chat`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question }),
        });
        if (!res.ok) throw await problem(res);
        const reply = await res.json();
        loading.remove();
        appendBubble(reply);
    } catch (err) {
        loading.remove();
        appendBubble({ role: 'ASSISTANT', content: t('chatError', { msg: err.message }) });
    } finally {
        btn.disabled = false; input.disabled = false; input.focus();
    }
}

// Mesaj listesine yeni bir baloncuk ekler ve en alta kaydırır
function appendBubble(m) {
    const box = $('chatMessages');
    const empty = box.querySelector('.chat-empty');
    if (empty) empty.remove();
    box.insertAdjacentHTML('beforeend', chatBubble(m));
    box.scrollTop = box.scrollHeight;
}

// "yanıtlıyor..." geçici baloncuğu ekler; döndürdüğü öğe yanıt gelince kaldırılır
function appendLoadingBubble() {
    const box = $('chatMessages');
    const div = document.createElement('div');
    div.className = 'chat-bubble assistant';
    div.innerHTML = `<div class="chat-content"><span class="chat-typing">${t('chatTyping')}</span></div>`;
    box.appendChild(div);
    box.scrollTop = box.scrollHeight;
    return div;
}

/* ---------------- Yardımcılar ---------------- */

// İstatistik kartı HTML'i (Genel Bakış'taki bitişik hücreler)
function stat(label, value, cls) {
    return `<div class="stat-card"><div class="stat-val ${cls}">${value}</div><div class="stat-lbl">${label}</div></div>`;
}

// Öncelik enum'unu aktif dile çevirir
function priorityLabel(p) {
    return I18N[state.lang].priority[p] || p;
}

// Durum enum'unu aktif dile çevirir
function statusLabel(s) {
    return I18N[state.lang].status[s] || s;
}

// ISO tarihi okunur biçime çevirir
function formatDate(iso) {
    if (!iso) return '—';
    try { return new Date(iso).toLocaleString(t('locale')); } catch { return iso; }
}

// XSS'e karşı metni güvenli hale getirir (kullanıcı/model içeriği HTML'e basılmadan önce)
function esc(s) {
    if (s == null) return '';
    return String(s).replace(/[&<>"']/g, c =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// Hafif markdown: modelin döndürdüğü **kalın** ve `kod` işaretlerini HTML'e çevirir.
// ÖNCE esc() ile kaçışlanır (XSS güvenliği), SONRA sınırlı biçimlendirme uygulanır (kütüphane yok).
function mdLite(s) {
    let html = esc(s);
    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
    return html;
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
