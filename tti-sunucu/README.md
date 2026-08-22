# tti-sunucu — Platform Katmanı

Bu depo bir uygulama içermez. **Makineyi** tanımlar: paylaşılan PostgreSQL, hangi projelerin
üstünde koştuğu, yedekleme, doğrulama ve yeni proje ekleme kuralları.

## Neden ayrı bir depo

Ortak `docker-compose.yml` hiçbir projenin deposuna ait değildir — iki projeyi birden tanır.
Daha önce yalnızca sunucuda, elle düzenlenen, sürüm kontrolü olmayan tek bir dosyaydı; üçüncü
proje eklendiğinde o dosya geçmişsiz ve incelemesiz değiştirilecekti.

İki katman ihlali de buraya taşınarak çözüldü:

- `yedek.sh` **TTI'nin deposundaydı** ama TTIDataRapor'un verisini de yedekliyordu
- `tti-rapor/deploy/dogrula.sh` **TTI'nin şemasını** kontrol ediyordu

Projeler birbirinin işletim sorumluluğunu taşımamalı. Bu depo o sorumluluğun sahibi.

## İçindekiler

| Dosya | Ne işe yarar |
|---|---|
| `docker-compose.yml` | Tüm servisler — tek yer, sürüm kontrollü |
| `Caddyfile` | Ters vekil yönlendirmesi — hangi domain hangi konteynere gider |
| `.env.example` | Sürümler, parolalar, yollar, havuz bütçesi, `DOMAIN` |
| `provizyon/` | Proje başına veritabanı + rol SQL'i (`SABLON.sql` yeni proje için) |
| `yedek.sh` | Günlük yedek: her projenin veritabanı + yükleme kökü + roller |
| `dogrula.sh` | Platform sınırları — projelerin kendi betiklerinin göremediği şeyler |
| `KURULUM.md` | Paylaşılan yığının sıfırdan kurulumu |
| `YENI-PROJE.md` | **Katılım sözleşmesi** — yeni servis eklerken okunacak tek dosya |

## Mimari — tek cümle

Tek PostgreSQL konteyneri, **proje başına ayrı veritabanı ve ayrı rol**; dışarıya tek kapı
**ters vekil** (domain adı), yönetim için **Portainer**.

```
                        ┌─ port 443 (HTTPS) ────── kullanıcılar (domain adıyla)
                        │  port 80  (HTTP) ──────── 443'e yönlendirir (301)
tti-proxy (caddy)  ◄────┤
  ├─ hakedis.<DOMAIN> ─► tti-hakedis-server  (127.0.0.1:8081)
  └─ rapor.<DOMAIN>   ─► tti-rapor-server    (127.0.0.1:8080→8082)

tti-db (postgres:17)
├─ veritabanı tti     → TTI Saha Hakediş  · rol tti_hakedis · şema hakedis
└─ veritabanı rapor   → TTIDataRapor      · rol rapor       · şema rapor

tti-portainer (portainer-ce)  ◄─ port 9443 (yalnızca IT subnet) ── IT: konteyner yönetimi
```

Projeler arası sorgu PostgreSQL'in dilinde yoktur — sınır yapısaldır, disipline bağlı değil.

App'ler 127.0.0.1'e bağlıdır: ağa açık kapılar proxy'nin **443 (HTTPS)** ve **80 (→443 yönlendirme)**
portlarıdır. Kullanıcı `https://hakedis.<DOMAIN>` yazar — IP ve port görmez. **İsmi IP'ye çözmek
DNS'in işidir** (IT'nin eklediği A kaydı), Docker değil; proxy yalnızca Host başlığına göre içeride
yönlendirir. TLS sertifikası kurumsal CA'dan gelir (wildcard `*.<DOMAIN>`), Caddy'ye `certs/` altından
elle verilir (`Caddyfile` → `tls`); `certs/` git'e girmez.

## Günlük komutlar

```bash
docker compose ps                    # ne çalışıyor
docker compose logs -f tti-rapor-server  # canlı log
bash dogrula.sh                      # platform sınırları
bash yedek.sh dogrula                # son yedek güvenilir mi (salt okunur)
```

**Geri dönüş:** `.env`'de `SURUM`'u bir önceki tarihe çevirin, `docker compose up -d`. ~20 saniye.

## Yerel prova (geliştirme makinesi)

Sunucuya çıkmadan, IT'yi ve DNS'i beklemeden proxy'yi kendi makinenizde görebilirsiniz — DNS yerine
`/etc/hosts`:

```bash
echo "127.0.0.1  hakedis.tti.local rapor.tti.local data.tti.local" | sudo tee -a /etc/hosts   # DOMAIN=tti.local ise
docker compose up -d
curl -sk -H "Host: hakedis.tti.local" https://localhost/api/saglik   # 200 + gerçek gövde
```

Tarayıcıda `https://hakedis.tti.local` → arayüz açılır (self-signed uyarısını kabul edin).

> ⚠️ **Proxy artık HTTPS bekler** (`Caddyfile` → `tls`), yani `certs/fullchain.pem` + `certs/privkey.pem`
> yoksa proxy konteyneri **kalkmaz**. Sunucuda gerçek CA sertifikası vardır; **geliştirme makinesinde**
> yerel bir self-signed üretin (bir kez):
> ```bash
> mkdir -p certs
> openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
>   -keyout certs/privkey.pem -out certs/fullchain.pem \
>   -subj "/CN=*.tti.local" -addext "subjectAltName=DNS:hakedis.tti.local,DNS:rapor.tti.local,DNS:data.tti.local"
> ```
> (`certs/` `.gitignore`'da — depoya girmez.) Proxy'yi hiç çalıştırmayacaksanız, app'leri doğrudan
> `TTI_PORT`/`RAPOR_PORT` üzerinden de deneyebilirsiniz.

> ⚠️ **Port çakışması (yalnızca geliştirme makinesi):** 8081/8082'yi başka bir dev sunucusu tutuyorsa
> (ör. Expo'nun varsayılanı **8081**, Supabase vb.) `localhost:8081`'e **doğrudan** istek yanlış
> servisi vurabilir — `localhost` IPv6'ya (`::1`) düşüp öteki dinleyiciye gidebilir. **Proxy yolu
> (`Host` başlığı / domain) bundan etkilenmez**: konteynere Docker ağı üzerinden `tti-hakedis-server:8081`
> ile gider, host portuna değil. Gerekirse `.env`'de `TTI_PORT` / `RAPOR_PORT`'u boş bir porta alın.
> **Sunucuda bu çakışma olmaz** (orada Expo/Supabase yok, 8081/8082 boş).

## Projelerin kendi depoları

| Proje | Depo | Kendi dökümanı |
|---|---|---|
| TTI Saha Hakediş | `../tti-hakedis` | `deploy/RedHatKurulum.md` (TTI'ye özgü adımlar) |
| TTIDataRapor | `../tti-rapor` | `deploy/KURULUM.md` |

Her proje kendi `deploy/docker-compose.yml` dosyasıyla **tek başına** da kalkar (kendi
veritabanı konteyneriyle) — geliştirme içindir. Sunucuda çalışan tanım buradakidir.
