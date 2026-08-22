# TTI Platformu — Çalışan Servisler ve Portlar

> Tek makinede, tek `docker compose` ile çalışan yedi konteyner. Tanım: `tti-sunucu/docker-compose.yml`.
> İngilizce sürüm: [SERVICES.md](SERVICES.md) · Kullanıcı/parola: [KULLANICILAR-VE-PAROLALAR.md](KULLANICILAR-VE-PAROLALAR.md)

## Genel bakış

```
                   port 443 (HTTPS) ──────── kullanıcılar (domain adıyla, IP/port YOK)
                   port 80  (HTTP) ────────── 443'e yönlendirir (301)
   tti-proxy (Caddy) ◄───┤
     ├─ hakedis.<DOMAIN> ─► tti-hakedis-server   (iç 8081)
     ├─ rapor.<DOMAIN>   ─► tti-rapor-server      (iç 8080)
     └─ data.<DOMAIN>    ─┬ /api,/admin,/docs ─► tti-data-server (iç 8000)
                          └ geri kalanı        ─► tti-data-panel  (iç 8080)

   tti-db (PostgreSQL 17) ── sadece sunucunun kendisi (127.0.0.1:5432)

   tti-portainer ◄── port 9443 (yalnızca IT subnet) ── IT: konteyner yönetimi
```

## Servisler ve portlar

| Servis | Konteyner | İmaj | İç port | Host bağı | Ağdan erişim |
|---|---|---|---|---|---|
| **Ters vekil** | `tti-proxy` | `caddy:2` | 80, 443 | `0.0.0.0:80`, `0.0.0.0:443` | ✅ **Tüm intranet** — domain adıyla (HTTPS; 80→443) |
| **Portainer** | `tti-portainer` | `portainer/portainer-ce:2.21.5` | 9443 | `0.0.0.0:9443` | ✅ **Yalnızca IT subnet** (firewall) |
| **Hakediş (TTI)** | `tti-hakedis-server` | `tti-hakedis-server:<SURUM>` | 8081 | `127.0.0.1:8081` | ❌ yalnızca proxy üzerinden |
| **Rapor (TTIDataRapor)** | `tti-rapor-server` | `tti-rapor-server:<SURUM>` | 8080 | `127.0.0.1:8082`¹ | ❌ yalnızca proxy üzerinden |
| **Merkezi Veri — API** | `tti-data-server` | `tti-data-server:<SURUM>` | 8000 | `127.0.0.1:8083`¹ | ❌ yalnızca proxy üzerinden |
| **Merkezi Veri — panel** | `tti-data-panel` | `tti-data-panel:<SURUM>` | 8080 | `127.0.0.1:8084`¹ | ❌ yalnızca proxy üzerinden |
| **Veritabanı** | `tti-db` | `postgres:17` | 5432 | `127.0.0.1:5432` | ❌ yalnızca sunucunun kendisi |

¹ Sunucudaki portlar. Bu geliştirme makinesinde `8082`/`8083` dolu olduğu için `.env`'de
`RAPOR_PORT=8083`, `DATA_PORT=8093`, `DATA_PANEL_PORT=8094`. `dogrula.sh` portları `.env`'den
okur, gömmez.

## Nasıl erişilir

| Kim | Ne | Adres |
|---|---|---|
| Kullanıcılar | TTI Saha Hakediş | `https://hakedis.<DOMAIN>` (http → https'e yönlenir) |
| Kullanıcılar | TTIDataRapor | `https://rapor.<DOMAIN>` |
| Kullanıcılar | TTI Merkezi Veri (panel) | `https://data.<DOMAIN>` |
| Android uygulamaları | TTI Merkezi Veri (API) | `https://data.<DOMAIN>/api/...` (`X-API-Key`) |
| IT | Portainer (yönetim) | `https://<sunucu-ip>:9443` (self-signed sertifika) |
| Sunucu-içi | Sağlık kontrolü | `curl http://localhost:8081/api/saglik` · `:8082` · `:8083` |

- **`<DOMAIN>`** `.env`'deki `DOMAIN` değeridir (sunucuda `turktelekomint.com`). `hakedis.<DOMAIN>` → IP eşlemesi **kurumsal DNS**'in işidir (IT bir A kaydı ekler), Docker'ın değil.
- **HTTPS:** proxy `*.<DOMAIN>` wildcard CA sertifikasını sunar (`Caddyfile` → `tls`, `certs/`); son geçerlilik **2027-02-27**, yenileme `ServerDeploy.md` §14. `certs/` git/zip dışında.
- **`data.<DOMAIN>` TEK alan adı, iki konteyner:** Caddy yola göre ayırır — `/api/*`, `/admin/*`,
  `/docs`, `/openapi.json`, `/static/*`, `/health` API'ye; geri kalan her şey panele. Aynı origin
  olduğu için panel API'ye göreli yolla gider ve CORS gerekmez.
- App'ler (`8081`/`8082`/`8083`/`8084`) `127.0.0.1`'e bağlıdır: ağdan doğrudan erişilmez, yalnızca proxy içeriden ulaşır. Güvenlik duvarında **443 + 80** (ve IT için 9443) açılır; **5432 kapalı**.

## Günlük komutlar

```bash
cd tti-sunucu
docker compose ps                     # ne çalışıyor + portlar
docker compose logs -f tti-hakedis-server   # canlı log
docker compose up -d                  # yığını başlat / güncelle
docker compose down                   # durdur (veri korunur — DB volume external)
bash dogrula.sh                       # platform kontrolleri (proxy + port yüzeyi dahil)
```

> **Sürüm etiketi** tarihtir (`YYYY-AA-GG`), `dev`/`latest` değil — geri dönüş buna bağlı. Geri dönüş:
> `.env`'de `SURUM`'u bir önceki tarihe çevir, `docker compose up -d`.

## Yedekleme

Yedek betiği **platform katmanındadır** (tek otorite): `tti-sunucu/yedek.sh`. Her projeye ait olmadığı
için TTI'nin deposundan buraya taşındı. Sunucuda her gece bir **systemd timer** çağırır.

```bash
cd tti-sunucu
bash yedek.sh            # elle bir kez al
bash yedek.sh dogrula    # son yedeği denetle — salt okunur, üretimde de güvenli
```

| | İçerik | Nerede |
|---|---|---|
| ✅ Yedeklenir | Belge **kayıtları** (miktar, fiyat, durum, **EK3 malzeme listesi**) | veritabanı dump'ı (`db/`) |
| ✅ Yedeklenir | Belge **ekleri** — EK1/EK2, saha fotoğrafları, rapor güzergah dosyaları (**ASIL VERİ**) | yükleme kökleri (`yukleme/`) |
| ✅ Yedeklenir | Yüklenen **Excel'ler + rollback JSON anlık görüntüleri** (**ASIL VERİ**) | `/srv/veri/tti-data/yukleme` |
| ✅ Yedeklenir | Roller + `tti-sunucu` ayarları (compose, `.env`, `Caddyfile`, `certs/` — **TLS özel anahtarı dahil**) | `roller/`, `sunucu-ayarlari/` |
| ❌ Yedeklenmez | Üretilen PDF/Excel arşivi (`ARSIV_KOK`) | veritabanından **yeniden üretilir** |

Ayrıntı, systemd timer kurulumu ve **geri yükleme provası**: `tti-hakedis/deploy/RedHatKurulum.md` §12.

> ⚠️ Yedekler makine dışına da kopyalanmalı (tek disk arızası her şeyi götürür) ve **geri yükleme
> provası** yapılmalı — test edilmemiş yedek, yedek değildir.
