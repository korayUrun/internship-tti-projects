# TTIDataRapor sunucu imajı — ÇOK AŞAMALI (multi-stage): derleme imajın İÇİNDE yapılır.
#
# build aşaması .NET SDK ile publish üretir; runtime aşaması yalnızca aspnet çalışma zamanını
# taşır ve build aşamasından çıktıyı kopyalar. Böylece:
#   · Ne dev'de ne sunucuda .NET SDK kurulu olması gerekir (SDK build imajından gelir).
#   · Çalışan imaj küçük kalır (SDK final imaja girmez, build aşaması atılır).
#   · Elle `dotnet publish` adımı kalkar — `docker compose build` her şeyi yapar.
#
# ⚠️ build aşaması NuGet'ten bağımlılık çeker (build anı internet gerekir). Kapalı ağda build'i
#    dev makinesinde koşup imajı `docker save`/`load` ile taşıyın — Dockerfile aynı kalır.

# ---- 1) build aşaması --------------------------------------------------------------------
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

# Önce yalnızca csproj + restore: kaynak değişince NuGet katmanı cache'ten gelsin.
COPY TTIDataAPI/*.csproj TTIDataAPI/
RUN dotnet restore TTIDataAPI/TTIDataAPI.csproj

COPY TTIDataAPI/ TTIDataAPI/
RUN dotnet publish TTIDataAPI/TTIDataAPI.csproj -c Release -o /yayin --no-restore

# ---- 2) runtime aşaması ------------------------------------------------------------------
FROM mcr.microsoft.com/dotnet/aspnet:8.0

# curl, YALNIZCA sağlık kontrolü için. Taban imajda ne curl ne de wget vardır: bu satır olmadan
# compose'daki healthcheck her seferinde başarısız olur, konteyner sonsuza kadar "unhealthy"
# görünür ve ona `depends_on` ile bağlı her servis takılır.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Uygulama kök yetkisiyle çalışmaz. Sayısal UID/GID kullanılıyor: 1000, Linux'ta ilk kullanıcının
# kimliğiyle örtüştüğü için bind mount edilen yükleme klasörü genellikle doğrudan yazılabilir olur.
RUN mkdir -p /uygulama /yukleme && chown -R 1000:1000 /uygulama /yukleme

WORKDIR /uygulama
COPY --from=build --chown=1000:1000 /yayin .

USER 1000:1000

ENV ASPNETCORE_URLS=http://+:8080 \
    ASPNETCORE_ENVIRONMENT=Production \
    RAPOR_YUKLEME_KOK=/yukleme \
    TZ=Europe/Istanbul

EXPOSE 8080

ENTRYPOINT ["dotnet", "TTIDataAPI.dll"]
