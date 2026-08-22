# TTI Merkezi Veri — Kurulum ve Operasyon

Bu belge **bu projeye** aittir. Sunucunun tamamına dair sıra (yedek al, zip'le, scp'le, derle)
platform katmanındadır: `tti-sunucu/NewDeploy.md` ve `tti-sunucu/YENI-PROJE.md`.

---

## 1. Ne çalışıyor

| Konteyner | Ne | İç port | Host bağı (sunucu) |
|---|---|---|---|
| `tti-data-server` | FastAPI + Alembic. Excel ayrıştırma, REST API | 8000 | `127.0.0.1:8083` |
| `tti-data-panel` | React SPA, nginx ile servis edilir | 8080 | `127.0.0.1:8084` |

İkisi de **tek alan adının** arkasında: `https://data.<DOMAIN>`. Caddy yola göre ayırır —
`/api/*`, `/admin/*`, `/docs`, `/openapi.json`, `/static/*`, `/health` backend'e; geri kalan
her şey panele. Aynı origin olduğu için panel API'ye **göreli** yolla gider ve CORS gerekmez.

Veritabanı paylaşımlı `tti-db` içinde: veritabanı `data`, rol `data` (ayrıcalıksız), şema `data`.

---

## 2. Geliştirme makinesinde tek başına çalıştırma

```bash
cd tti-data
mkdir -p deploy/yukleme
docker compose -f deploy/docker-compose.yml up -d --build

curl -s http://localhost:8093/api/saglik      # {"durum":"ayakta",...}
open http://localhost:8094                     # panel — admin / admin
bash deploy/dogrula.sh                         # proje kontrolleri
```

Durdurma: `docker compose -f deploy/docker-compose.yml down`
(veri korunur; `-v` eklerseniz `data_db_verisi` volume'ü de silinir).

Panel üzerinde canlı geliştirme (hot reload) için:
```bash
cd admin-panel && npm install && npm run dev    # :5173, /api'yi :8000'e vekiller
```

### Testler

```bash
cd backend
python -m pytest -q          # 13 test, SQLite tabanlı, veritabanı gerektirmez
```
Testler `DB_SCHEMA=""` ile koşar — SQLite şema tanımaz. Üretimde bu değişken `data`'dır.

---

## 3. Sunucuya kurulum (ilk sefer)

Tam sıra `tti-sunucu/NewDeploy.md`'de. Bu projeye özgü adımlar:

```bash
# 1) Veri kökü (sunucuda, bir kez)
sudo mkdir -p /srv/veri/tti-data/yukleme
sudo chown -R 1000:1000 /srv/veri/tti-data

# 2) .env (tti-sunucu/.env) — bkz. .env.example'daki "Proje 3" bloğu
#    DATA_DB_PAROLA, provizyon/03-data.sql'deki parolayla AYNI olmalı.
#    DATA_JWT_ANAHTARI:  openssl rand -base64 48

# 3) Veritabanı provizyonu (bir kez, bakım rolüyle)
cd /srv/kod/tti-sunucu
sudo docker exec -i tti-db psql -U tti_hakedis -d tti -v ON_ERROR_STOP=1 < provizyon/03-data.sql

# 4) Derle ve kaldır
#    ⚠️ SERVİS ADLARI VERİLİR. Çıplak `docker compose build`, hakediş ve rapor imajlarını da
#    yeniden derler ve SURUM etiketlerini ezer — geri dönüş imkânı yok olur.
sudo docker compose build tti-data-server tti-data-panel
sudo docker compose up -d tti-data-server tti-data-panel
sudo docker compose up -d proxy        # Caddyfile değişti

# 5) Doğrula
sudo docker ps --filter name=tti-data                     # ikisi de (healthy)
curl -s http://localhost:8083/api/saglik
DOCKER="sudo docker" DB=tti-db DB_BAKIM=tti_hakedis API_PORT=8083 PANEL_PORT=8084 \
  bash /srv/kod/tti-data/deploy/dogrula.sh
cd /srv/kod/tti-sunucu && sudo bash dogrula.sh && sudo bash yedek.sh dogrula
```

⚠️ **DNS:** `data.<DOMAIN>` adının sunucunun IP'sine gitmesi kurumsal DNS'in işidir (IT bir A
kaydı ekler). Caddy yalnızca `Host` başlığına göre içeride yönlendirir. Sertifika için ek iş
yok — wildcard `*.<DOMAIN>` bu alt alan adını kapsar.

### Transfer zip'i

```bash
cd ~/Desktop/tti-projeler
zip -r tti-data.zip tti-data \
  -x '*/.venv/*' '*/node_modules/*' '*/dist/*' '*/backups/*' '*/__pycache__/*' '*.pyc' \
     '*/.pytest_cache/*' '*/api_keys.txt' '*/.env' '*/deploy/yukleme/*' '*.DS_Store'
shasum -a 256 tti-data.zip
```
⚠️ `zip -r` `.gitignore`'a **bakmaz** — dışlamalar elle verilir. `api_keys.txt` ve `.env`
kasıtlı olarak dışarıda; anahtarların otoritesi veritabanıdır.

---

## 4. Güncelleme

```bash
# Mac: zip → scp → sunucuda unzip -o -d /srv/kod
cd /srv/kod/tti-sunucu
sudo nano .env                                    # SURUM_DATA=<yeni tarih>
sudo bash yedek.sh                                # ÖNCE YEDEK
sudo docker compose build tti-data-server tti-data-panel
sudo docker compose up -d tti-data-server tti-data-panel
```

**Geri dönüş:** `.env`'de `SURUM_DATA`'yı bir önceki tarihe çevirin ve `up -d`. Bu yüzden imaj
etiketi tarihtir, `dev`/`latest` değil — `dogrula.sh` hareketli etiketi hata sayar.

---

## 5. Operasyon

### Yeni dataset eklemek
1. Panelden **+ Yeni Dataset** ile isim/açıklama.
2. Şema tanımlanana kadar Excel yüklenemez (backend reddeder).
3. Şemayı `PUT /admin/datasets/{isim}/schema` ile ayarlayın — `https://data.<DOMAIN>/docs`
   üzerinden. Swagger arayüzü **imaja gömülüdür**, kapalı ağda da açılır.

### Yeni uygulamaya erişim vermek
`data.dataset_permissions` tablosuna `(api_key, app_name, dataset_id)` satırı eklenir.

### Admin parolası
`ADMIN_PASSWORD` (`.env` → `DATA_ADMIN_PAROLA`) yalnızca hesap **yokken** kullanılır; açılışta
kod eksik kullanıcıyı ekler, var olanı **asla ezmez**. Parolayı değiştirmek için satırı silip
konteyneri yeniden başlatın:

```bash
sudo docker exec -i tti-db psql -U tti_hakedis -d data -v ON_ERROR_STOP=1 \
  -c "delete from data.admin_users where username='admin';"
sudo docker restart tti-data-server
```

### Loglar
```bash
sudo docker logs -f tti-data-server
sudo docker logs -f tti-data-panel
```

Açılışta `alembic upgrade head` koşar ve veritabanı hazır değilse 5 saniyede bir 30 kez yeniden
dener (makine yeniden başladığında Docker konteyner sırasına uymaz).

---

## 5b. Var olan veriyi sunucuya taşımak

Sunucuda veritabanı **sıfırdan** açılır. Neyin geri geldiğine dikkat:

| Veri | Yeniden yüklemeyle geri gelir mi? |
|---|---|
| `dataset_records` (aktif satırlar) | **Evet** — 4 Excel'den deterministik türetilir |
| `dataset_uploads` geçmişi | Evet (yeni ve doğru bir geçmiş başlar) |
| `admin_users` | Evet — açılışta oluşturulur |
| `datasets.schema_definition` | **Hayır** — aşağıya bakın |
| `dataset_permissions` | **Hayır** — aşağıya bakın |

### Şema tanımları

**En iyi yol:** eski veritabanı hâlâ duruyorsa iki tabloyu birlikte dökün — birlikte dökmek
`datasets.id` ↔ `dataset_permissions.dataset_id` bütünlüğünü korur:

```bash
pg_dump -U <kullanici> -d <eski_vt> --data-only --column-inserts \
  -t datasets -t dataset_permissions > deploy/tohum.sql
# sunucuda, migration'lar koştuktan SONRA:
sudo docker exec -i tti-db psql -U tti_hakedis -d data -v ON_ERROR_STOP=1 \
  -c "set search_path to data" -f - < deploy/tohum.sql
```

**Eski veritabanı yoksa:** şema tanımı `backend/backups/` altındaki `.xlsx` + `.json`
çiftlerinden büyük ölçüde geri çıkarılabilir:

```bash
python3 deploy/sema-taslagi-uret.py > sema-taslagi.json
```

⚠️ Çıktı **taslaktır**. Geri getirilemeyen üç şey `TODO` ile işaretlenir ve elle doldurulmalıdır:
- `required` bayrakları (JSON'da izi yok)
- `df_parent_data`'nın `group_by` alanının hangi Excel kolonundan geldiği
- lookup ayarı — PROJE.md §3'e göre: `lookup_dataset=cable_data`, `lookup_key=nosid`,
  `lookup_fields=[capacity, addressA, addressB]`

`workers_data`'da Excel başlıkları Türkçe, alan adları İngilizcedir; betik sırayla eşler ve bunu
bir **tahmin** olarak işaretler — gözle doğrulayın.

### API izinleri

Anahtarlar `api_keys.txt`'te (5 uygulama). Hangi uygulamanın hangi dataset'e erişeceği bir
karardır; eski veritabanı yoksa yeniden verilir:

```bash
sudo docker exec -i tti-db psql -U tti_hakedis -d data -v ON_ERROR_STOP=1 -c \
  "insert into data.dataset_permissions (api_key, app_name, dataset_id)
   select '<ANAHTAR>', '<Uygulama Adı>', id from data.datasets where name='<dataset>';"
```
⚠️ Bir satır eksik/yanlışsa o Android uygulaması **sessizce 403** alır — göç sonrası her
uygulamayla bir kez veri çekilmesi test edilmelidir.

### 76 MB'lık `backend/backups/` sunucuya KOPYALANMAZ

`{upload_id}` UUID'leri taze `dataset_uploads` tablosunda hiçbir satırla eşleşmez: her gece
yedeklenen ama asla geri dönülemeyen ölü ağırlık olurlar. Harici diske tarihsel kayıt olarak
arşivleyin. Admin 4 Excel'i panelden **yeniden yükler** — bu, Excel ayrıştırma + `:Z` etiketli
bind mount + anlık görüntü yazıcısının üretimde çalıştığının tek uçtan uca kanıtıdır.

Beklenen satır sayıları: `cable_data` 943 · `sites_data` 565 · `df_parent_data` 804 ·
`workers_data` 43.

---

## 6. Yedekleme

Platform katmanındadır: `tti-sunucu/yedek.sh`. Bu proje iki listede kayıtlıdır —
`VERITABANLARI`'nda `data:data`, `YUKLEMELER`'de `data:tti-data-server`.

| | İçerik | Nerede |
|---|---|---|
| ✅ | Dataset tanımları, şemalar, aktif satırlar, API izinleri | veritabanı dump'ı |
| ✅ | Yüklenen Excel'ler + rollback JSON anlık görüntüleri (**ASIL VERİ**) | `/srv/veri/tti-data/yukleme` |

⚠️ JSON anlık görüntüleri de asıl veridir: `dataset_records` yalnızca **aktif** sürümü tutar,
her **önceki** sürümün tek kopyası o JSON'lardır ve "bu versiyona dön" onlardan okur.

⚠️ Bu dosyalar biriktikçe büyür ve bugün bir saklama politikası **yok**.

---

## 7. Sözleşme maddeleri nerede karşılanıyor

`tti-sunucu/YENI-PROJE.md`'deki 8 madde:

| # | Madde | Nerede |
|---|---|---|
| 1 | Kendi veritabanı + ayrıcalıksız rol | `tti-sunucu/provizyon/03-data.sql` |
| 2 | Migration'ı kendi koşar, hedefi açık | `backend/alembic/env.py` (`version_table_schema`), `deploy/giris.sh` |
| 3 | `GET /api/saglik` 200/503 | `backend/app/api/routes/saglik.py` |
| 4 | Ayarlar ortamdan, eksikse durur | `backend/app/config.py` |
| 5 | Çok aşamalı Dockerfile | `deploy/Dockerfile.backend`, `deploy/Dockerfile.panel` |
| 6 | Kök olmayan kullanıcı, `mem_limit`, havuz beyanı | Dockerfile'lar (`USER 1000:1000`), `backend/app/database.py` (5+5=10) |
| 7 | Kullanıcı verisi `/yukleme`'de ve yedekte | `tti-sunucu/docker-compose.yml`, `yedek.sh` |
| 8 | Dört yere kayıt | compose, `Caddyfile`, `yedek.sh`, `dogrula.sh`, `.env(.example)` |
