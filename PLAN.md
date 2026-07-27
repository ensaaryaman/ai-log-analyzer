# AI Log Analyzer — 10 Günlük Uygulama Planı

## 0. Varsayımlar (belirsiz noktalar için verilen kararlar)

| Konu | Karar | Gerekçe |
|---|---|---|
| AI sağlayıcı | **Google Gemini** (`gemini-2.5-flash`) | Ücretsiz tier'ı cömert, hızlı, Spring AI'ın OpenAI-uyumlu veya Vertex starter'ı ile çalışır. OpenAI yedek plan. |
| Frontend | Spring Boot'un servis ettiği **statik SPA** (vanilla JS + Chart.js) | Ayrı React projesi 10 günde ekstra yük; tek jar ile deploy "temiz iş" izlenimi verir. |
| Kimlik doğrulama | **JWT tabanlı login + rol (ADMIN/USER)** — kapsam sonradan genişletildi, Gün 9'da eklenir | Staj sorumlusunun talebi: çoklu kullanıcı senaryosu (her kullanıcı kendi loglarını görür, ADMIN hepsini görür) + audit izi göstermek için. MVP'de yoktu; bkz. Bölüm 6. |
| Dağıtım (deployment) | **Docker Compose ile uygulama + veritabanı birlikte** — Gün 10'da eklenir | Şu ana kadar yalnızca PostgreSQL container'daydı, uygulama elle çalıştırılıyordu. Değerlendiren kişinin Java/Maven kurmadan tek komutla ürünü çalıştırabilmesi için kapsam genişletildi; bkz. Bölüm 7. |
| Dosya limiti | 10 MB, `.log` / `.txt` | Token maliyeti ve bellek kontrolü. |
| Java / Spring | Java 21, Spring Boot 3.4.x, Spring AI 1.0.x | Güncel LTS + stabil Spring AI. |
| Analiz modu | MVP'de **senkron** (istek ~10-30 sn sürer), gün 8+'da async status polling opsiyonel | Async kuyruk (queue) 10 günlük projede gereksiz karmaşıklık. |

---

## 1. Genel Sistem Mimarisi

Klasik katmanlı mimari + AI entegrasyonunu izole eden bir servis katmanı:

```
┌──────────────────────────────────────────────────────────────┐
│  Browser (statik SPA: login, upload, sonuç ekranı,           │
│           dashboard, chat)                                    │
└──────────────────────────┬───────────────────────────────────┘
                           │ REST / JSON (Authorization: Bearer <jwt>)
┌──────────────────────────▼───────────────────────────────────┐
│  Spring Security Filter Chain — JWT doğrulama, SecurityContext│
├──────────────────────────────────────────────────────────────┤
│  Controller Katmanı                                          │
│  AuthController · LogFileController · AnalysisController     │
│  ChatController · StatsController · ReportController         │
├──────────────────────────────────────────────────────────────┤
│  Servis Katmanı                                              │
│  ┌──────────────┐ ┌───────────────┐ ┌─────────────────────┐  │
│  │ LogParser    │→│ LogDistiller  │→│ AiAnalysisService   │  │
│  │ Service      │ │ (istatistik,  │ │ (Spring AI          │  │
│  │ (format      │ │  gruplama,    │ │  ChatClient,        │  │
│  │  tespiti,    │ │  örnekleme)   │ │  structured output) │  │
│  │  regex parse)│ └───────────────┘ └──────────┬──────────┘  │
│  └──────────────┘ ┌───────────────┐            │             │
│                   │ ReportService │            ▼             │
│                   │ (PDF)         │   Gemini / OpenAI API    │
│                   └───────────────┘                          │
├──────────────────────────────────────────────────────────────┤
│  Repository Katmanı (Spring Data JPA)                        │
└──────────────────────────┬───────────────────────────────────┘
                           ▼
                     PostgreSQL
```

**Veri akışı:**
1. Kullanıcı dosya yükler → `LogFileController` dosyayı alır, meta kaydeder.
2. `LogParserService` formatı tespit eder, satırları `LogEntry`'lere parse eder (multiline stack trace birleştirme dahil).
3. `LogDistillerService` istatistik çıkarır (level dağılımı, exception sayıları), hataları parmak izine göre gruplar, AI'a gönderilecek **damıtılmış özet** üretir (tam log değil!).
4. `AiAnalysisService` yapılandırılmış prompt ile modeli çağırır, JSON yanıtı `AnalysisResult` nesnesine bağlar.
5. Sonuç DB'ye kaydedilir, kullanıcıya döner; geçmiş ekranından tekrar erişilebilir.

**Kritik mimari karar:** AI'a asla ham dosyanın tamamı gönderilmez. Parse → grupla → damıt → gönder. Bu hem token maliyetini düşürür hem analiz kalitesini yükseltir (bkz. Bölüm 5 ve 9).

---

## 2. Veritabanı Şeması

```
app_user 1──* log_file 1──* log_entry
              log_file 1──* analysis 1──* chat_message
              log_file 1──* error_group
```

**app_user** — kayıtlı kullanıcılar (Flyway V3, Gün 9)
| Alan | Tip | Not |
|---|---|---|
| id | UUID PK | |
| username | varchar unique | |
| password_hash | varchar | BCrypt |
| role | varchar | ADMIN / USER |
| created_at | timestamptz | |

**log_file** — yüklenen dosya ve parse özeti
| Alan | Tip | Not |
|---|---|---|
| id | UUID PK | |
| owner_id | FK → app_user | log'un sahibi; yetkilendirme sorguları buradan filtrelenir (Gün 9, V3 migration) |
| filename, size_bytes | varchar, bigint | |
| uploaded_at | timestamptz | |
| detected_format | varchar | SPRING_DEFAULT / LOG4J / SYSLOG / UNKNOWN |
| line_count, error_count, warn_count | int | dashboard için denormalize |
| first_ts, last_ts | timestamptz | logun kapsadığı zaman aralığı |
| status | varchar | UPLOADED / PARSED / ANALYZED / FAILED |

**log_entry** — parse edilmiş kayıtlar (tamamı değil: WARN ve üzeri + her leveldan örnekler; INFO/DEBUG selini DB'ye basmamak için)
| Alan | Tip | Not |
|---|---|---|
| id | bigserial PK | |
| file_id | FK → log_file | |
| ts | timestamptz | |
| level | varchar | TRACE..FATAL |
| logger_class, thread | varchar | |
| message | text | |
| exception_type | varchar | ör. NullPointerException |
| stack_trace | text | |
| fingerprint | varchar(32) | hata gruplama anahtarı |
| line_number | int | orijinal dosyadaki satır (kanıt gösterimi için) |

**error_group** — tekrarlanan hata tespiti
| Alan | Tip | Not |
|---|---|---|
| id, file_id | PK, FK | |
| fingerprint | varchar(32) | exception + normalize mesaj + top frame hash'i |
| exception_type, sample_message | varchar, text | |
| occurrence_count | int | |
| first_seen, last_seen | timestamptz | "5 dk'da 300 kez" gibi çıkarımlar için |
| sample_entry_id | FK → log_entry | temsilci kayıt |

**analysis** — AI analiz sonuçları
| Alan | Tip | Not |
|---|---|---|
| id, file_id | UUID PK, FK | |
| model, prompt_version | varchar | tekrarlanabilirlik / karşılaştırma |
| summary, root_cause, solution | text | |
| priority | varchar | CRITICAL / HIGH / MEDIUM / LOW |
| confidence | numeric(3,2) | 0.00–1.00 |
| evidence_lines | int[] | AI'ın dayandığı orijinal satır numaraları |
| raw_response | jsonb | ham JSON — debug + ileride yeni alanlar |
| prompt_tokens, completion_tokens | int | maliyet takibi |
| duration_ms, created_at | int, timestamptz | |

**chat_message** — log ile sohbet geçmişi
| Alan | Tip |
|---|---|
| id, analysis_id | PK, FK |
| role (USER/ASSISTANT), content, created_at | |

Şema yönetimi: **Flyway** migration (V1__init.sql …) — "iyi mühendislik" sinyali, kurulumu da kolaylaştırır.

---

## 3. REST API Endpoint Listesi

| Method | Path | Açıklama |
|---|---|---|
| POST | `/api/auth/register` | Yeni kullanıcı kaydı (username + şifre) |
| POST | `/api/auth/login` | Giriş yap, JWT token dön |
| GET | `/api/auth/me` | Aktif kullanıcının bilgisi (username, rol) |
| POST | `/api/logs` | Dosya yükle (multipart), parse et, özet dön — kaydedilen `log_file.owner_id` = aktif kullanıcı |
| GET | `/api/logs` | Yüklenen dosyalar (sayfalı) — USER sadece kendi dosyalarını, ADMIN tümünü görür |
| GET | `/api/logs/{id}` | Dosya detayı + parse istatistikleri |
| GET | `/api/logs/{id}/entries?level=ERROR&page=0` | Parse edilmiş kayıtlar (filtreli) |
| GET | `/api/logs/{id}/stats` | Dashboard verisi (level dağılımı, zaman serisi, top exception'lar) |
| POST | `/api/logs/{id}/analyze` | AI analizini çalıştır |
| GET | `/api/analyses` | Analiz geçmişi |
| GET | `/api/analyses/{id}` | Analiz detayı |
| POST | `/api/analyses/{id}/chat` | Log bağlamında soru sor |
| GET | `/api/analyses/{id}/report.pdf` | PDF rapor indir |
| DELETE | `/api/logs/{id}` | Dosya + bağlı verileri sil — yalnızca sahibi veya ADMIN silebilir |

**Yetkilendirme kuralı:** `/api/auth/**` dışındaki tüm uçlar `Authorization: Bearer <jwt>` ister; token yoksa/geçersizse 401. `LogFileServiceImpl` her sorguda aktif kullanıcıyı `SecurityContext`'ten okur; ADMIN değilse tüm log sorguları `owner_id`'ye göre filtrelenir, başkasının logunu silmeye/görmeye çalışan USER 403 alır. Yetkilendirme mantığı controller'da değil **servis katmanında** uygulanır (SOLID — controller HTTP'yi, servis iş kuralını bilir).

**Örnek — `POST /api/logs` yanıtı:**
```json
{
  "id": "c1f7...",
  "filename": "app-2026-07-20.log",
  "detectedFormat": "SPRING_DEFAULT",
  "lineCount": 15230,
  "errorCount": 47,
  "warnCount": 312,
  "topExceptions": [{ "type": "SQLTransientConnectionException", "count": 31 }],
  "status": "PARSED"
}
```

**Örnek — `POST /api/logs/{id}/analyze` yanıtı:**
```json
{
  "id": "9e2a...",
  "summary": "Uygulama 14:02–14:07 arasında veritabanı bağlantı havuzu tükenmesi yaşadı.",
  "rootCause": "HikariCP havuzu (max 10) uzun süren raporlama sorguları tarafından dolduruldu; 14:02'deki WARN'lar (connection timeout) 14:05'te SQLTransientConnectionException fırtınasına dönüştü.",
  "solution": "1) Raporlama sorgusuna timeout ekleyin, 2) maximumPoolSize'ı artırın, 3) yavaş sorguyu indeksleyin (orders.created_at).",
  "priority": "HIGH",
  "confidence": 0.85,
  "evidenceLines": [1042, 1187, 2210],
  "model": "gemini-2.5-flash",
  "promptVersion": "v2"
}
```

Hatalar RFC 7807 `ProblemDetail` formatında döner. API dokümantasyonu: **springdoc-openapi** → `/swagger-ui.html` (neredeyse sıfır maliyet, teslimatlardan birini otomatik karşılar). **Durum:** bu madde en baştan plandaydı (Gün 10'a bağlıydı); henüz eklenmedi — Gün 10'da Docker paketlemesiyle birlikte hayata geçirilecek.

---

## 4. Log Parse Stratejisi

**Yaklaşım: kütüphanesiz, named-group regex + format dedektörü.** Grok/özel parser kütüphaneleri 10 günlük projede öğrenme riski; regex bu iş için yeterli ve savunması kolay.

1. **Format tespiti:** İlk 50 (boş olmayan) satırı bilinen pattern havuzuna karşı dene; en çok eşleşen format seçilir. Havuz:
   - Spring Boot default: `2026-07-20T14:02:11.123+03:00 ERROR 1234 --- [thread] c.e.Service : mesaj`
   - Log4j/Logback yaygın pattern: `2026-07-20 14:02:11,123 [thread] ERROR c.e.Service - mesaj`
   - Syslog benzeri ve genel `ISO-timestamp LEVEL mesaj` varyantları
2. **Named group'lu regex** ile alan çıkarımı:
```java
Pattern.compile("^(?<ts>\\S+[ T]\\S+)\\s+(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+" +
                "(?:\\d+ --- )?\\[\\s*(?<thread>[^\\]]+)\\]\\s+(?<logger>\\S+)\\s*[:-]\\s(?<msg>.*)$");
```
3. **Multiline / stack trace birleştirme:** Satır timestamp ile başlamıyorsa (veya `at `, `Caused by:`, `\t` ile başlıyorsa) bir önceki entry'nin stack trace'ine eklenir. Stack trace'ten `exception_type` (ilk `X{2,}Exception|Error` eşleşmesi) ve top frame çıkarılır.
4. **Timestamp esnekliği:** 4-5 `DateTimeFormatter` sıralı denenir (ISO, virgüllü milisaniye, offset'li/siz); hiçbiri tutmazsa entry timestamp'siz kaydedilir ama atılmaz.
5. **Fallback (UNKNOWN format):** Satırda level keyword'ü aranır (`\bERROR\b` vb.), bulunursa level + tüm satır mesaj olarak alınır. **Hiçbir satır sessizce çöpe gitmez** — parse edilemeyen satır sayısı `log_file` özetinde raporlanır (dürüstlük + debug kolaylığı).
6. **Fingerprint (tekrarlanan hata tespiti):**
```java
// normalize: sayılar, UUID'ler, hex ID'ler maskelenir → "Order 12345 not found" ≡ "Order 67890 not found"
String normalized = message.replaceAll("\\b\\d+\\b", "#").replaceAll("[0-9a-f-]{20,}", "#");
String fingerprint = md5(exceptionType + "|" + normalized + "|" + topStackFrame);
```
7. Parse **streaming** yapılır (`BufferedReader.lines()`), dosya belleğe komple alınmaz.

---

## 5. Spring AI Entegrasyon Yaklaşımı

**Structured output — kalbi burası.** Spring AI'ın entity binding'i ile model yanıtı doğrudan Java record'a bağlanır:

```java
public record AnalysisResult(String summary, String rootCause, String solution,
                             Priority priority, double confidence, List<Integer> evidenceLines) {}

AnalysisResult result = chatClient.prompt()
    .system(SYSTEM_PROMPT)          // rol + kurallar + JSON şema talimatı (BeanOutputConverter otomatik ekler)
    .user(buildUserPrompt(distilled))
    .options(ChatOptions.builder().temperature(0.2).build())  // düşük sıcaklık → tutarlı JSON
    .call()
    .entity(AnalysisResult.class);
```

**System prompt (özet):**
> Sen kıdemli bir SRE/backend mühendisisin. Sana bir uygulamanın log dosyasından damıtılmış bilgiler verilecek. Görevin: (1) sorunu özetle, (2) en olası kök nedeni belirle, (3) somut, uygulanabilir çözüm adımları öner, (4) öncelik ata (CRITICAL/HIGH/MEDIUM/LOW), (5) 0–1 arası güven seviyesi ver, (6) dayandığın kanıt satır numaralarını listele. Emin olmadığın şeyi uydurma; kanıt yoksa güveni düşür. Yanıtları Türkçe yaz.

**User prompt'a giden damıtılmış içerik** (ham log değil):
```
DOSYA: app.log | 15.230 satır | 20.07 14:00–14:10 | 47 ERROR, 312 WARN

LEVEL DAĞILIMI (dakikalık): 14:02 → 45 WARN, 14:05 → 31 ERROR ...

HATA GRUPLARI (tekrar sayısına göre):
1. SQLTransientConnectionException ×31 (ilk: 14:05:02, son: 14:07:44)
   Örnek mesaj + stack trace'in ilk 15 satırı [satır 1187]
2. ...

ERROR ÖNCESİ SON WARN'LAR (bağlam penceresi):
[satır 1042] 14:02:11 WARN HikariPool-1 - Connection is not available...
```

Bu tasarımın nedenleri: (a) 15 bin satır token limitine sığmaz, (b) gruplama + bağlam penceresi modele "neyin önemli olduğunu" önceden söyler → kalite artar, (c) satır numaraları sayesinde model **kanıt gösterebilir**.

**Dayanıklılık:** `spring-retry` ile 2 deneme (exponential backoff); JSON parse hatasında yanıt `raw_response`a kaydedilip kullanıcıya anlamlı hata dönülür; `mock` Spring profili ile AI çağrısı sahte yanıtla değiştirilebilir (demo + test + kota bittiğinde hayat kurtarır).

**Chat özelliği:** System prompt'a aynı damıtılmış özet + mevcut analiz sonucu konur; konuşma geçmişi `chat_message` tablosundan okunup her istekte modele verilir. Ayrı bir vektör DB / RAG gerekmez — bu ölçekte damıtılmış özet yeterli bağlamdır.

---

## 6. Kimlik Doğrulama ve Yetkilendirme (Gün 9'da eklenecek)

**Neden eklendi:** Staj sorumlusu projeyi "daha kapsamlı" görmek istedi ve login istedi. Login'in dekoratif değil **gerçek bir amacı** olsun diye şu senaryoya bağlandı: bugün herkes herkesin logunu görüp silebiliyor; login ile her kullanıcı yalnızca kendi yüklediği logları görür/analiz eder/siler, bir **ADMIN** rolü ise tüm kullanıcıların loglarını denetleyebilir (staj sorumlusunun "herkesin ne yaptığını görme" ihtiyacına karşılık gelir). Bu aynı zamanda doğal bir audit izi de sağlar (hangi log kime ait).

**Yaklaşım: JWT tabanlı stateless bearer token.** Mevcut mimari zaten tamamen `fetch`-tabanlı bir REST API + statik SPA; session/cookie yerine `Authorization: Bearer <jwt>` header'ı bu mimariye session-based login'den daha doğal oturur (CSRF derdi yok, sunucu tarafında oturum tutulmaz). Refresh-token gibi ek karmaşıklığa bu ölçekte gerek yok — tek access token, makul bir süre (ör. 24 saat) yeterli.

- **Bağımlılık:** `spring-boot-starter-security` + `jjwt` (veya Spring'in kendi `JwtEncoder`/`JwtDecoder`'ı).
- **`app_user` entity + repository:** username unique, `password_hash` (BCrypt, `PasswordEncoder` bean), `role` (ADMIN/USER enum).
- **`AuthController`:**
  - `POST /api/auth/register` → yeni kullanıcı oluşturur (varsayılan rol USER; ilk kullanıcı script/seed ile ADMIN yapılabilir).
  - `POST /api/auth/login` → username+şifre doğrular, JWT üretir (claim: `sub`=username, `role`).
  - `GET /api/auth/me` → aktif kullanıcı bilgisini döner (frontend'in "kim giriş yapmış" göstermesi için).
- **`JwtAuthenticationFilter`:** her istekte `Authorization` header'ını okuyup token'ı doğrular, `SecurityContext`'e authentication nesnesini koyar. `/api/auth/**` ve statik dosyalar (`/`, `/css/**`, `/js/**`) filtre dışı bırakılır.
- **Sahiplik filtresi:** `LogFile.owner_id` eklenir (Flyway V3). `LogFileServiceImpl` her sorguda (`listAll`, `getById`, `delete`, `getEntries`, `stats`) aktif kullanıcıyı okur; rol ADMIN değilse repository sorgusuna `owner_id = currentUserId` koşulu eklenir. Başka bir kullanıcının logunu silmeye/görmeye çalışan USER **403 Forbidden** alır (404 değil — kaynağın var olduğunu gizlemek bu ölçekte gereksiz karmaşıklık).
- **Frontend:** basit bir giriş ekranı (mevcut emerald temayla tutarlı bir kart — kullanıcı adı/şifre + "Giriş Yap"/"Kayıt Ol" sekmesi). Token `localStorage`'da tutulur, tüm `fetch` çağrılarına `Authorization` header'ı eklenir; `401` yanıtı gelirse otomatik olarak login ekranına yönlendirilir. Üst başlıkta aktif kullanıcı adı + "Çıkış Yap" butonu gösterilir.
- **Test stratejisi:** `AuthControllerTest` (kayıt/login happy-path + yanlış şifre), yetkilendirme testi (USER-A'nın USER-B'nin logunu silmeye çalışması → 403), anonim istek → 401.

---

## 7. Docker Paketleme — Tek Komutla Tam Kurulum (Gün 10'da eklenecek)

**Neden eklendi:** Şu ana kadar `docker-compose.yml` yalnızca PostgreSQL'i içeriyordu; uygulamanın kendisi `./mvnw spring-boot:run` ile elle çalıştırılıyordu. Değerlendiren kişinin Java/Maven kurulu olmasına gerek kalmadan projeyi tek komutla ayağa kaldırabilmesi "kapsamlı"lığın somut bir göstergesi — Bölüm 9'daki 5. fikirle ("tek komutla ayağa kalkan demo") aynı doğrultuda, onu tamamlıyor.

- **`Dockerfile` (çok aşamalı / multi-stage):**
  1. **build stage:** `maven:3.9-eclipse-temurin-21` imajı ile `./mvnw package -DskipTests` çalıştırılır (bağımlılık katmanı ayrı `COPY` ile cache'lenir).
  2. **runtime stage:** küçük bir `eclipse-temurin:21-jre-alpine` imajına yalnızca üretilen jar kopyalanır, `ENTRYPOINT ["java","-jar","app.jar"]`.
- **`docker-compose.yml` genişletilir:** yeni bir `app` servisi eklenir — `build: .`, `depends_on: [db]`, `environment`'tan `.env` okunur (`GEMINI_API_KEY`, `SPRING_DATASOURCE_URL` vb. — db servisinin adı `db` olduğundan container-içi hostname otomatik kullanılır), `ports: ["8080:8080"]`.
- **Sonuç:** `docker compose up --build` → hem Postgres hem uygulama ayağa kalkar, `http://localhost:8080` üzerinden erişilebilir. README bu tek komutu ön plana çıkarır; `./mvnw spring-boot:run` ile elle çalıştırma da (geliştirme için) ayrıca belgelenir.
- **Not:** `mock` Spring profili (AI çağrısı sahte yanıtla değiştirilebilir) Docker imajında da çalışır — API anahtarı olmayan bir değerlendirici bile `SPRING_PROFILES_ACTIVE=mock docker compose up` ile projeyi AI'sız gezebilir.

---

## 8. Genişletilmiş Takvim

> **Not:** Orijinal plan 10 gündü. Kapsam staj sorumlusunun talebiyle genişledi (Bölüm 6 — login/roller, Bölüm 7 — Docker paketleme); bu yüzden takvim **12 güne** çıkarıldı. Ayrıca plan dışı iki ek iş de tamamlandı ve raporlandı: log silme özelliği (Gün 7-8 arası) ve arayüzün baştan tasarımı (Gün 8-9 arası) — bkz. `raporlar/`.

| Gün | Hedef | Gün sonu "bitti" tanımı |
|---|---|---|
| 1 | Proje iskeleti: Git repo, Spring Boot + bağımlılıklar, `docker-compose.yml` (PostgreSQL), Flyway V1, entity/repository'ler, dosya upload endpoint'i. **Gemini API anahtarını al ve "merhaba" smoke testi yap.** | Dosya yüklenip DB'ye meta kaydediliyor; AI anahtarı çalışıyor. |
| 2 | Parser: format dedektörü, 2-3 format regex'i, multiline stack trace, timestamp esnekliği + parser unit testleri. | Örnek loglar doğru parse ediliyor, testler yeşil. |
| 3 | Damıtma katmanı: level istatistikleri, fingerprint + `error_group`, ERROR öncesi WARN penceresi, `/stats` endpoint'i. | `GET /api/logs/{id}/stats` anlamlı veri dönüyor. |
| 4 | Spring AI: ChatClient config, prompt v1, structured output, `analysis` kaydı, retry + mock profili. **→ Backend MVP tamam.** | `POST /analyze` gerçek AI yanıtı dönüyor ve DB'de. |
| 5 | Frontend: upload sayfası, analiz sonucu kartı (özet/neden/çözüm/öncelik/güven), geçmiş listesi. **→ Uçtan uca MVP tamam.** | Tarayıcıdan dosya yükle → analizi gör akışı çalışıyor. |
| 6 | Dashboard: Chart.js ile level dağılımı (pasta), zaman serisi (ERROR/WARN dakikalık çizgi), top exception bar grafiği; entries tablosu + filtre. | Dashboard sekmesi dolu ve doğru. |
| 7 | Log ile sohbet: chat endpoint + geçmiş + basit chat UI. Prompt v2 iyileştirmeleri (kanıt satırları, güven kalibrasyonu). | Analiz sayfasından loga soru sorulabiliyor. |
| 8 | WARN→ERROR geçiş analizi (WARN kümelerinin ERROR'a dönüşümünü zaman ekseninde işaretle) + PDF rapor (openhtmltopdf ile HTML→PDF). | İki ekstra özellik demo edilebilir durumda. |
| 9 | **(Yeni kapsam)** Kimlik doğrulama ve yetkilendirme: `app_user` + Flyway V3, JWT login/register, `JwtAuthenticationFilter`, log sahipliği filtresi (USER kendi loglarını görür, ADMIN tümünü), basit giriş ekranı. Bkz. Bölüm 6. | Kayıt olup giriş yapılabiliyor; USER başka kullanıcının logunu göremiyor/silemiyor, ADMIN her şeyi görüyor. |
| 10 | **(Yeni kapsam)** Docker paketleme: çok aşamalı `Dockerfile`, `docker-compose.yml`'e `app` servisi eklenir. + `springdoc-openapi` ile Swagger UI (zaten plandaydı, burada hayata geçirilir). Bkz. Bölüm 7. | `docker compose up --build` tek komutla hem DB hem uygulamayı ayağa kaldırıyor; `/swagger-ui.html` çalışıyor. |
| 11 | Test + sağlamlaştırma: integration testler (Testcontainers), hata senaryoları (bozuk dosya, boş dosya, AI hatası), edge-case parser düzeltmeleri, auth testleri (401/403), UI cilası. | Bilinen kırmızı senaryo yok. |
| 12 | Teslimat: README (kurulum: `docker compose up --build`, alternatif olarak `./mvnw spring-boot:run`), örnek log dosyaları (3-4 senaryo), örnek analiz çıktıları, Swagger kontrolü, demo provası. **Tampon gün.** | Repo teslim edilebilir durumda. |

**Kayma durumunda kesme sırası (önce kesilecek):**
1. **PDF rapor** — etkileyicilik/maliyet oranı en düşük ("yazdır" ile PDF zaten alınabilir).
2. **WARN→ERROR görsel analizi** — metin olarak zaten prompt'ta var; ayrı görselleştirme lüks.
3. **Chat** — en son gözden çıkar; jüri/sorumlu üzerinde etkisi büyük.
4. Dashboard'dan **asla** tamamen vazgeçme; en kötü tek grafiğe indir.

MVP **5. gün** hazır → kalan gün ekstra özellik + kalite. Bu, "sona her şeyi yığma" riskini ortadan kaldırır. (Not: takvim sonradan 12 güne çıktı — bkz. Bölüm 8 başındaki not; MVP kuralı hâlâ geçerli, sadece "kalan gün" sayısı arttı.)

---

## 9. Riskler ve Önlemler

| Risk | Etki | Önlem |
|---|---|---|
| AI API anahtarı/kota sorunları (en olası bloker) | Analiz hiç çalışmaz | Anahtarı **1. gün** al ve test et; Gemini ücretsiz tier limitlerini öğren; `mock` profili ile geliştirme AI'sız sürebilsin; OpenAI'ı yedek sağlayıcı olarak config'te hazır tut (Spring AI ile sağlayıcı değişimi birkaç satır). |
| Log formatı çeşitliliği dipsiz kuyu | Parser'da günler kaybolur | Kapsamı **3 format + fallback** ile sınırla ve README'de belirt; "desteklenmeyen format" da düzgün bir kullanıcı mesajıdır. |
| Büyük dosya / token limiti | Analiz patlar veya pahalanır | 10 MB dosya limiti; damıtma katmanı zaten token'ı sınırlar; prompt'a giden içeriği ~15K token ile kırp. |
| Structured output bozuk JSON dönmesi | Analiz kaydedilemez | temperature 0.2, retry, ham yanıtı `raw_response`a kaydet, kullanıcıya "tekrar dene" sun. |
| Senkron analizde HTTP timeout | Kötü UX | Frontend'te yükleniyor durumu + timeout'u 60 sn'ye çek; süre kalırsa async status polling. |
| Zaman kayması | Teslim riski | MVP gün 5 kuralı; her ekstra özellik bağımsız modül → yarım kalan özellik MVP'yi bozmaz; gün 12 tampon. |
| PostgreSQL kurulum sorunları (değerlendiren kişide) | Demo yapılamaz | Docker Compose tek komut kurulum + README'de adım adım anlatım. |
| Auth kapsamı büyüyüp asıl işi (log analizi) gölgeler | Sunumda odak kayar | JWT + tek rol çifti (ADMIN/USER) ile sınırla; refresh token, e-posta doğrulama, şifre sıfırlama gibi genişlemelere **girme** — bunlar login'in "amacını" (log sahipliği) değiştirmez, sadece süre yer. |
| Docker imajı büyük/yavaş build olur | Demo sırasında bekleme | `eclipse-temurin:21-jre-alpine` gibi küçük bir runtime imajı + multi-stage build ile katman cache'i; ilk build'in süre aldığı README'de belirtilir. |

---

## 10. Test Stratejisi (süre kısıtına göre önceliklendirilmiş)

Kısıtlı sürede test bütçesini **deterministik ve kırılgan** olan yere harca:

1. **Parser unit testleri (en yüksek öncelik, ~%60 bütçe):** Her format için örnek satırlar, multiline stack trace, bozuk satır, timestamp varyantları, boş/dev dosya. Parser bu projenin en çok edge-case üreten parçası ve testi ucuz. `src/test/resources/logs/` altına konan örnek dosyalar aynı zamanda teslimattaki "örnek log dosyaları" olur — çifte kazanç.
2. **Fingerprint/gruplama unit testleri:** "Aynı hata farklı ID ile aynı gruba düşer" gibi 4-5 kritik senaryo.
3. **AI servis testi (mock ile):** `ChatClient`'ı mock'la; (a) damıtılmış prompt doğru kurgulanıyor mu, (b) örnek JSON yanıt `AnalysisResult`'a doğru bağlanıyor mu, (c) bozuk JSON'da hata akışı çalışıyor mu. **Gerçek AI çağrısını asla otomatik teste koyma** — deterministik değil, yavaş ve kota yer.
4. **1-2 integration test (Testcontainers + MockMvc):** upload → parse → (mock) analyze → GET happy path. JSONB kullanıldığı için H2 yerine Testcontainers şart.
5. Controller validasyonları (yanlış uzantı, boyut aşımı) için birkaç ince test.
6. **(Yeni — auth) Yetkilendirme testleri:** kayıt/login happy-path + yanlış şifre; anonim istek → 401; USER-A'nın USER-B'nin logunu silmeye/görmeye çalışması → 403; ADMIN'in tüm logları görebilmesi.

---

## 11. Katma Değer: Projeyi Sıradanlıktan Çıkaracak Somut Fikirler

1. **Log damıtma pipeline'ı (parse → grupla → örnekle → gönder).** "Dosyayı AI'a yapıştıran" naif çözüm yerine token bütçesi yöneten bilinçli bir ön işleme katmanı. Hem maliyet mühendisliği hem analiz kalitesi demektir; sunumda "15.000 satırı 200 satırlık kanıta indiriyorum" cümlesi tek başına fark yaratır.
2. **Kanıta dayalı analiz (evidence lines).** AI yanıtı, dayandığı orijinal satır numaralarını içerir; UI'da tıklanınca ilgili log satırları vurgulanır. LLM hallucination problemine mühendisçe bir cevap — sorumlunun "AI'ın dediğine neden güveneyim?" sorusunu ürün özelliğine dönüştürür.
3. **Hata parmak izi ile Sentry-vari gruplama.** Değişken kısımları (ID, sayı) maskeleyip hash'leyerek "aynı hata 300 kez" tespiti. AI olmadan da değer üreten deterministik bir çekirdek; sistemin tamamının "AI sosu" olmadığını gösterir.
4. **Prompt versiyonlama + token/maliyet ve süre takibi.** Her analizde `prompt_version`, token sayıları ve süre DB'ye yazılır; README'de "prompt v1 → v2 şu yüzden değişti" kısa notu. LLM tabanlı ürünlerde olgunluk (LLMOps) sinyali — stajyerden beklenmeyen bir detay.
5. **Tek komutla ayağa kalkan demo:** Docker Compose (artık uygulamanın kendisi de dahil — bkz. Bölüm 7) + Flyway + `mock` AI profili + seed örnek loglar + Swagger UI. Değerlendiren kişi Java/Maven kurmadan, API anahtarı olmadan bile `docker compose up --build` ile ürünü gezebilir. "Çalıştıramadım" riskini sıfırlar; DX'e verilen önem iyi mühendislik izlenimi bırakır.
6. **Çoklu kullanıcı + rol bazlı erişim (Bölüm 6).** Login'in kendisi değil, **log sahipliği** kavramı değer üretir: her geliştirici kendi loglarını görür, ADMIN hepsini denetleyebilir. Bu, projeyi "tek kişilik demo scripti"nden "gerçek bir ekip aracı" izlenimine taşır.

---

## Teslimat Kontrol Listesi (Gün 12)

- [ ] Kaynak kod — anlamlı commit geçmişiyle (gün gün commit at, son gün tek commit değil!)
- [ ] README: mimari özeti + kurulum (`docker compose up --build`, alternatif `./mvnw spring-boot:run`) + ekran görüntüleri (login ekranı dahil)
- [ ] API dokümantasyonu: Swagger UI (`/swagger-ui.html`) + README'de endpoint tablosu (auth uçları dahil)
- [ ] `samples/` altında 3-4 senaryolu örnek log dosyası (DB bağlantı fırtınası, NPE, OOM, karışık)
- [ ] `samples/outputs/` altında örnek analiz JSON çıktıları
- [ ] `.env.example` — API anahtarı + JWT secret yapılandırması
- [ ] Login/kayıt akışı + rol bazlı erişim (403/401 senaryoları) manuel test edildi
- [ ] `Dockerfile` + genişletilmiş `docker-compose.yml` tek komutla çalışıyor (temiz bir makinede denendi)
