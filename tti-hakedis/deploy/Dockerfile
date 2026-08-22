# TTI Saha Hakediş sunucu imajı — ÇOK AŞAMALI (multi-stage): derleme imajın İÇİNDE yapılır.
#
# build aşaması JDK 17 + Node 22 taşır ve `:server:bootJar` ile hem sunucuyu hem gömülü web
# arayüzünü (Vite/React) derler; runtime aşaması yalnızca JRE taşır ve jar'ı kopyalar. Böylece:
#   · Ne dev'de ne sunucuda JDK/Gradle/Node kurulu olması gerekir (build imajından gelir).
#   · Çalışan imaj küçük kalır (SDK/Node final imaja girmez).
#   · Elle `./gradlew :server:bootJar` adımı kalkar — `docker compose build` her şeyi yapar.
#
# ⚠️ NOTLAR:
#   · build aşaması Maven/Gradle/npm'den bağımlılık çeker (build anı internet gerekir). Kapalı
#     ağda build'i dev'de koşup imajı `docker save`/`load` ile taşıyın — Dockerfile aynı kalır.
#   · `:app` (Android) modülü SUNUCU build'ine GİRMEZ: settings.gradle onu yalnızca
#     `local.properties` ya da `ANDROID_HOME` varsa dahil eder. `.dockerignore` `local.properties`'i
#     bağlam dışında bırakır; build imajında ANDROID_HOME yok → yalnızca sunucu tarafı derlenir.

# ---- 1) build aşaması: JDK 17 + Node 22 --------------------------------------------------
FROM eclipse-temurin:17-jdk AS build

# Vite 7 → Node 20.19+/22 gerekir; `bootJar`, `npm ci` + `vite build` çalıştırıp çıktıyı jar'ın
# static/ klasörüne gömer.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
 && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
 && apt-get install -y --no-install-recommends nodejs \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . .

# --no-daemon: konteynerde tek seferlik build, arka planda Gradle daemon bırakma.
RUN ./gradlew :server:bootJar --no-daemon

# ---- 2) runtime aşaması: yalnızca JRE ----------------------------------------------------
FROM eclipse-temurin:17-jre

# Uygulama kök yetkisiyle çalışmaz. 1000, Linux'ta ilk kullanıcının kimliğiyle örtüştüğünden
# bind mount edilen arşiv/yükleme klasörü genellikle doğrudan yazılabilir olur.
RUN mkdir -p /uygulama /arsiv /yukleme && chown -R 1000:1000 /uygulama /arsiv /yukleme

WORKDIR /uygulama
COPY --from=build --chown=1000:1000 /src/server/build/libs/tti-hakedis-server.jar uygulama.jar

USER 1000:1000
EXPOSE 8081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "uygulama.jar"]
