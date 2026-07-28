# ============================================================================
# AI Log Analyzer — çok aşamalı (multi-stage) Docker imajı
# Amaç: Java/Maven kurulu olmayan bir makinede tek komutla çalıştırılabilir jar üretmek.
#   1) build aşaması: Maven ile jar derler (bağımlılıklar ayrı katmanda cache'lenir)
#   2) runtime aşaması: yalnızca üretilen jar + küçük bir JRE kalır (imaj küçük ve yalın)
# ============================================================================

# ---- 1) Derleme aşaması: Maven + JDK 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Önce yalnızca pom.xml kopyalanır ve bağımlılıklar indirilir.
# Kaynak kod değişse bile pom.xml değişmedikçe bu katman cache'ten gelir → hızlı yeniden derleme.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Sonra kaynak kod. Testler burada atlanır (Docker/Testcontainers gerektirir; imaj derlemesi hızlı kalsın).
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- 2) Çalışma aşaması: yalnızca JRE (derleme araçları taşınmaz) ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Güvenlik: uygulamayı root yerine ayrıcalıksız bir kullanıcıyla çalıştır
RUN addgroup -S app && adduser -S app -G app

# Derleme aşamasından yalnızca çalıştırılabilir jar'ı al (repackage edilmiş; *.jar.original hariç kalır)
COPY --from=build /build/target/*.jar app.jar

# Yüklenen log dosyaları için dizin (compose'ta volume ile kalıcı yapılır)
RUN mkdir -p /app/uploads && chown -R app:app /app
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
