# TTI Merkezi Veri Yönetim Sistemi — Proje Dokümantasyonu

Bu doküman: sistemin ne yaptığını, nasıl çalıştığını, bugüne kadar neler inşa edildiğini ve
Linux tabanlı bir sunucuya nasıl kurulup çalıştırılacağını anlatır.

---

## 1. Bu Proje Ne Yapıyor?

Saha uygulamaları (Kotlin/Android tabletler) daha önce paylaşılan Excel dosyalarından veri
okuyordu. Her veri değişikliğinde tüm uygulamaların elle güncellenmesi gerekiyordu. Bu sistem:

- Tüm Excel kaynaklı verileri **tek bir merkezi PostgreSQL veritabanında** toplar.
- Yetkili personelin, bir **admin panel** üzerinden Excel dosyalarını sürükle-bırak ile
  yükleyip veritabanını güncelleyebilmesini sağlar.
- Uygulamaların artık dosya yerine, **ortak bir REST API** üzerinden (API key ile) veri
  çekmesini sağlar.
- Her yüklemede **otomatik yedek** alır, hatalı bir yüklemede önceki versiyona **geri dönüş**
  imkânı sunar.

---

## 2. Mimari

```
[Excel Dosyası]
      │  (drag & drop)
      ▼
[Admin Panel — React, Vite, nginx ile servis edilir]
      │  (JWT ile login, HTTPS/HTTP REST istekleri)
      ▼
[Backend API — FastAPI + SQLAlchemy + Alembic]
      │
      ├─► Excel parse & satır bazlı validasyon
      ├─► Gerekirse gruplama (df_parent_data gibi) + başka dataset'ten alan çekme (lookup)
      ├─► Eski aktif veriyi backup'a taşı (disk + DB)
      ├─► Yeni veriyi DB'ye yaz (transaction)
      │
      ▼
[PostgreSQL — Merkezi Veritabanı]
      ▲
      │  GET /api/datasets/{isim}/data   (X-API-Key header'ı ile)
      │
[Kotlin/Android Uygulamaları — TTİ Ticket, Hasar Tespit, İdari, Masraf, KabloEk]
```

Sunucuda iki konteyner çalışır (veritabanı platformla paylaşılır):

| Servis | Teknoloji | İç port | Sunucuda host bağı |
|---|---|---|---|
| `tti-data-server` | FastAPI (Python 3.12) | 8000 | `127.0.0.1:8083` |
| `tti-data-panel` | React + Vite, nginx ile servis edilir | 8080 | `127.0.0.1:8084` |
| *(veritabanı)* | Paylaşımlı `tti-db` — PostgreSQL 17 | 5432 | `127.0.0.1:5432` |

İkisine de ağdan **doğrudan** erişilmez; tek kapı `https://data.<DOMAIN>` (Caddy).

---

## 3. Bugüne Kadar Neler Yapıldı

### Backend (`backend/`)

- **Veri modeli** (`app/models/`): `datasets`, `dataset_uploads`, `dataset_records`,
  `dataset_permissions`, `admin_users` tabloları. Migration'lar Alembic ile yönetiliyor
  (`backend/alembic/versions/`).
- **Dinamik şema sistemi**: Her dataset'in hangi Excel kolonlarını hangi tipte (`string` /
  `number` / `boolean`) alacağı, `datasets.schema_definition` (JSONB) alanında tanımlı.
  Excel'de şemada olmayan fazladan kolonlar otomatik yok sayılır.
- **Kısmi kabul validasyonu**: Bir Excel'de hatalı satırlar varsa sadece o satırlar atlanır
  ve raporlanır; geçerli satırlar yine de kaydedilir. Sadece zorunlu bir kolon Excel'de hiç
  yoksa dosyanın tamamı reddedilir. Zorunlu alan boş olsa bile hata sayılmaz, `null` yazılır
  — "zorunlu" burada sadece "kolon başlığı Excel'de bulunmalı" anlamına gelir.
- **Satır sayısı düşüş uyarısı**: Yeni yükleme, önceki veriye göre satır sayısını ciddi oranda
  (`ROW_COUNT_DROP_WARNING_RATIO`, varsayılan %50) azaltıyorsa, admin onaylamadan
  (`force=true`) kaydedilmez.
- **Gruplama (nested records)**: `df_parent_data` gibi dataset'ler için düz Excel satırları,
  ortak bir alana göre (`group_by`) gruplanıp iç içe bir liste (`items`) hâline getirilir.
- **Lookup / zenginleştirme**: `df_parent_data`'nın kendi Excel'inde olmayan `capacity`,
  `addressA`, `addressB` gibi alanlar, `cable_data`'daki eşleşen kayıttan (`nosid` üzerinden)
  otomatik çekilip kaydediliyor (`lookup_dataset`, `lookup_key`, `lookup_fields`).
- **Backup & rollback**: Her başarılı yüklemede orijinal Excel + verinin JSON anlık görüntüsü
  diske yazılır (`backend/backups/{dataset}/{upload_id}.xlsx|json`). "Bu versiyona dön"
  işlemi bu JSON'dan okuyup aktif veriyi değiştirir.
- **Kimlik doğrulama**: Admin panel JWT (`python-jose`, `bcrypt`) ile; uygulamalar `X-API-Key`
  header'ı ile (`dataset_permissions` tablosunda hangi key'in hangi dataset'e erişebileceği
  tanımlı — bir key birden fazla dataset'e izinli olabilir, bileşik primary key
  `(api_key, dataset_id)`).
- **Performans/kararlılık düzeltmesi**: Excel yükleme işlemi (pandas parse + DB yazma) ayrı
  bir thread'e devrediliyor (`run_in_threadpool`) — büyük bir dosya yüklenirken backend'in
  tüm istekleri (login dahil) donmasın diye.
- **CORS**: Sadece `CORS_ALLOWED_ORIGINS` içinde tanımlı adreslerden (admin panel) isteğe
  izin verilir — backend genel internete açık değil, sadece iç ağdan erişilebilir olacak
  şekilde tasarlandı.
- **Test paketi** (`backend/tests/`): SQLite tabanlı, veritabanı gerektirmeyen 13 test —
  Excel parse/validasyon, upload/rollback akışı, gruplama+lookup senaryosu, yetkisiz erişim
  reddi, dataset silme.

### Admin Panel (`admin-panel/`)

- **Ekranlar**: Giriş, Dataset listesi (istatistik kartlarıyla), Yeni Dataset Ekle, Excel
  Güncelleme (sürükle-bırak), Geçmiş/Rollback.
- **Modern kurumsal tasarım**: TTİ renk paleti (mavi/lacivert), Inter fontu, gerçek TTİ logosu
  (header + giriş ekranı), sayfa geçiş animasyonları, hover mikro-etkileşimleri, toast bildirim
  sistemi, yüklenme sırasında skeleton/shimmer efekti, responsive tasarım.
- Tüm backend çağrıları tek merkezden yönetiliyor (`src/api/client.ts`), tipler
  `src/api/types.ts`'de.

### Tanımlı Dataset'ler (şu an sistemde)

| Dataset | Durum | Not |
|---|---|---|
| `cable_data` | Yüklü (943 satır) | 21 alan, hepsi metin + `capacity` (sayı) |
| `sites_data` | Yüklü | 11 alan |
| `workers_data` | Yüklü (44 kayıt) | 11 alan (Türkçe başlıklar) |
| `df_parent_data` | Yüklü | `cable_data`'dan lookup ile zenginleştirilmiş, `items` altında gruplu |
| `cities_data` | Silindi | (kullanılmıyor) |

### Uygulama Entegrasyonu

5 Kotlin/Android uygulaması için ayrı API key + izin tanımlandı (`dataset_permissions`):
`TTİ Ticket`, `TTİ Hasar Tespit`, `TTİ İdari`, `TTİ Masraf`, `TTİ KabloEk`. Güncel key listesi
`api_keys.txt` dosyasında. Kotlin tarafı için örnek Retrofit client'ı:
`docs/kotlin-client-ornek/TtiApiClient.kt`.

---

## 4. Kurulum ve Çalıştırma

> ⚠️ Bu bölüm 19 Ağustos 2026'da **baştan yazıldı.** Proje artık kendi başına duran bir yığın
> değil; sunucudaki **paylaşımlı platformun** üçüncü servisi. Eski metin (kendi `postgres:16`
> konteyneri, `ufw` kuralları, `0.0.0.0`'a açık portlar, HTTP-only, crontab `pg_dump`) artık
> geçerli **değildir** — hiçbiri sunucuda böyle çalışmıyor.

### Nerede ne var

| | Nerede |
|---|---|
| Bu projenin kurulum/operasyon belgesi | [`deploy/KURULUM.md`](deploy/KURULUM.md) |
| Sunucuya ekleme sırası (adım adım) | `tti-sunucu/NewDeploy.md` |
| Uyulması gereken platform sözleşmesi | `tti-sunucu/YENI-PROJE.md` |
| Üretimde çalışan tanım | `tti-sunucu/docker-compose.yml` |

### Özet

- **İki konteyner:** `tti-data-server` (FastAPI, iç 8000) ve `tti-data-panel` (React SPA + nginx,
  iç 8080). İkisi de kök olmayan kullanıcıyla (`1000:1000`) çalışır.
- **Tek alan adı:** `https://data.<DOMAIN>`. Caddy yola göre ayırır — `/api/*`, `/admin/*`,
  `/docs`, `/openapi.json`, `/static/*`, `/health` API'ye; geri kalan her şey panele. Aynı
  origin olduğu için panel API'ye **göreli** yolla gider ve **CORS gerekmez**.
- **Veritabanı:** kendi `postgres` konteyneri **yok**. Paylaşımlı `tti-db` (PostgreSQL 17) içinde
  veritabanı `data`, ayrıcalıksız rol `data`, şema `data`. Diğer projelerin veritabanlarına
  yapısal olarak erişemez. Provizyon: `tti-sunucu/provizyon/03-data.sql` (bir kez, elle).
- **Migration:** konteyner açılışta `alembic upgrade head` koşar (`deploy/giris.sh`);
  `alembic_version` dahil her şey `data` şemasına düşer.
- **Yüklemeler:** konteynerde `/yukleme`, sunucuda `/srv/veri/tti-data/yukleme` bind mount'u
  (`:Z` SELinux etiketiyle). Gece yedeğine `tti-sunucu/yedek.sh` üzerinden dahildir.
- **TLS:** proxy'de sonlanır (kurumsal CA wildcard sertifikası). Uygulama HTTP konuşur ama
  ağa HTTP açılmaz.
- **Ayarlar:** hepsi ortam değişkeninden; eksikse uygulama **açılmaz** (gömülü varsayılan yok).
- **Havuz:** `pool_size=5 + max_overflow=5 = 10`, `--workers 1`. Bu sayı platform bütçesine
  beyan edilmiştir.

### Geliştirme makinesinde tek başına

```bash
cd tti-data
docker compose -f deploy/docker-compose.yml up -d --build
curl -s http://localhost:8093/api/saglik
bash deploy/dogrula.sh
```

Ayrıntı ve sunucu adımları için [`deploy/KURULUM.md`](deploy/KURULUM.md).

---

## 5. Operasyon / Bakım

### Yeni bir dataset eklemek
1. Admin panelden "+ Yeni Dataset" ile isim/açıklama gir (şema boş oluşturulur).
2. Şema tanımlanana kadar o dataset'e Excel yüklenemez (backend bunu reddeder).
3. Şemayı `PUT /admin/datasets/{isim}/schema` ile ayarla — `https://data.<DOMAIN>/docs`
   üzerinden. Swagger arayüzü **imaja gömülüdür** (CDN'den çekilmez), kapalı ağda da açılır.

### Yeni bir uygulamaya erişim vermek
`data.dataset_permissions` tablosuna yeni bir `(api_key, app_name, dataset_id)` satırı eklenir
(psql ile, ya da ileride eklenecek bir admin panel ekranıyla).

### Logları izlemek
```bash
sudo docker logs -f tti-data-server
sudo docker logs -f tti-data-panel
```

### Güncelleme (kod değişikliği sonrası)
```bash
cd /srv/kod/tti-sunucu
sudo nano .env                                              # SURUM_DATA=<yeni tarih>
sudo bash yedek.sh                                          # ÖNCE YEDEK
sudo docker compose build tti-data-server tti-data-panel    # ⚠️ SERVİS ADLARI VERİLİR
sudo docker compose up -d tti-data-server tti-data-panel
```
⚠️ Çıplak `docker compose build`, hakediş ve rapor imajlarını da yeniden derler ve `SURUM`
etiketlerini ezer — geri dönüş imkânı yok olur.

---

## 6. Güvenlik Notları

- Backend konteyner içinde HTTP konuşur ama **ağa HTTP açılmaz**: TLS ters vekilde (Caddy)
  sonlanır, kurumsal CA wildcard sertifikasıyla. Uygulama portu yalnızca `127.0.0.1`'de dinler
  ve güvenlik duvarında açık değildir. Sistem iç ağ içindir, internete açılmamalıdır.
- API key'ler düz metin olarak saklanır (kullanıcı şifresi değil, uygulama-sunucu erişim
  anahtarıdır) — sızdırılmamaları için Kotlin tarafında kaynak koduna/git'e commit
  edilmemeleri, `BuildConfig`/güvenli config üzerinden verilmeleri önerilir.
- `ADMIN_PASSWORD` ve `JWT_SECRET_KEY` production'a geçmeden mutlaka güçlü/rastgele
  değerlerle değiştirilmeli (şu an geliştirme ortamında test değerleri kullanılıyor olabilir).

---

## 7. Bilinen Sınırlamalar / Sonraki Adımlar

- Admin panelde henüz dataset şeması düzenleme ekranı yok — şema değişiklikleri API üzerinden
  (curl/Swagger) yapılıyor.
- ~~HTTPS/TLS yok~~ **çözüldü** (19 Ağustos 2026): proxy'de TLS sonlanıyor.
- Yüklenen Excel/JSON dosyaları için **saklama politikası yok** — sınırsız birikirler.
- `POST /admin/login` için hız sınırı / hesap kilitleme yok.
- Kotlin uygulama entegrasyonları henüz test edilmedi. Uygulamaların `BASE_URL`'i
  `https://data.turktelekomint.com/` olmalı; tabletlerde sertifika işi yoktur
  (bkz. `docs/kotlin-client-ornek/TtiApiClient.kt`).
