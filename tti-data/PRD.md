# PRD: Merkezi Veri Yönetim Sistemi (TTI Ortak Veritabanı & Backend)

## 1. Özet

Şu anda saha uygulamaları (tabletler ve diğer uygulamalar) verilerini paylaşılan dizinlerdeki Excel dosyalarından elle alıyor; her veri değişikliğinde tüm uygulamaların ayrı ayrı güncellenip yeniden derlenmesi (APK oluşturma) gerekiyor. Bazı Excel kaynakları birden fazla uygulama tarafından ortak kullanılıyor.

Bu proje; tüm veri kaynaklarını (Excel tabanlı) tek bir merkezi veritabanında toplayan, tüm uygulamaların ortak bir API üzerinden veri çektiği, ve yetkili personelin Excel dosyalarını bir admin panel üzerinden güncelleyebildiği bir sistem kurmayı hedefler.

## 2. Problem Tanımı

- Veri değişikliğinde her uygulamanın ayrı ayrı güncellenip APK olarak yeniden dağıtılması gerekiyor.
- Aynı Excel verisi birden fazla uygulamada kullanılıyor ama merkezi bir kaynak yok; güncelleme tekrar tekrar elle yapılıyor.
- Versiyon/geçmiş takibi yok — hangi dosyanın ne zaman, kim tarafından güncellendiği izlenmiyor.
- Hatalı bir Excel yüklendiğinde geri dönüş (rollback) mekanizması yok.

## 3. Hedefler

1. Tüm Excel kaynaklı verileri tek bir merkezi veritabanında (PostgreSQL) tutmak.
2. Uygulamaların veriyi dosya yerine ortak bir REST API üzerinden çekmesini sağlamak — uygulama başına ayrı API yazılmayacak, veri kaynağı (dataset) bazlı generic endpoint yapısı kurulacak.
3. Yetkili personelin Excel dosyalarını sürükle-bırak ile yükleyip veritabanını güncelleyebildiği bir admin panel sağlamak.
4. Her güncellemede otomatik yedek (backup) almak ve geri dönüş imkânı sunmak.
5. Yükleme anında veri doğrulama (validasyon) yaparak hatalı verinin canlıya geçmesini engellemek.

## 4. Kapsam Dışı (İlk Faz İçin)

- Uygulamaların offline/local senkronizasyon mantığı (ilk fazda uygulamalar online API'ye bağlı çalışacak varsayımıyla ilerlenecek; offline senkron ikinci faz).
- Çoklu ortam (staging/production ayrımı) — ilk fazda tek sunucu üzerinde çalışılacak.
- Detaylı rol/izin yönetimi (RBAC) — ilk fazda basit admin girişi yeterli, ileride genişletilebilir.

## 5. Kullanıcı Rolleri

| Rol | Yetki |
|---|---|
| Admin (yükleyici personel) | Admin panele giriş yapar, dataset yükler/günceller, backup görüntüler/indirir |
| Uygulama (client) | API key ile kimlik doğrulama yapar, sadece kendisine tanımlı dataset'lerden okuma yapar |

## 6. Mimari Genel Bakış

```
[Excel Dosyası] 
      │  (drag & drop)
      ▼
[Admin Panel - React/basit web arayüz]
      │  (upload)
      ▼
[Backend API - FastAPI]
      │
      ├─► Excel Parse & Validasyon
      ├─► Eski veriyi Backup'a taşı
      ├─► Yeni veriyi DB'ye yaz (transaction)
      │
      ▼
[PostgreSQL - Merkezi Veritabanı]
      ▲
      │  (GET /api/datasets/{name}/data)
      │
[Kotlin Uygulamalar / Tabletler] — API key ile erişir
```

## 7. Veritabanı Şeması (İlk Taslak)

### `datasets` (tanımlı veri kaynakları)
| Kolon | Tip | Açıklama |
|---|---|---|
| id | UUID/PK | |
| name | text | örn. `kablo_bilgileri`, `personel_listesi` |
| description | text | |
| schema_definition | jsonb | Beklenen kolonlar ve tipleri (validasyon için) |
| created_at | timestamp | |

### `dataset_uploads` (versiyon/geçmiş takibi)
| Kolon | Tip | Açıklama |
|---|---|---|
| id | UUID/PK | |
| dataset_id | FK -> datasets | |
| uploaded_by | text | kullanıcı adı |
| uploaded_at | timestamp | |
| original_filename | text | |
| backup_file_path | text | eski verinin/dosyanın yedek konumu |
| status | enum | success / failed / rolled_back |
| row_count | int | |
| validation_errors | jsonb | varsa hata detayları |

### `dataset_records` (her dataset için asıl veri — jsonb ile esnek)
| Kolon | Tip | Açıklama |
|---|---|---|
| id | UUID/PK | |
| dataset_id | FK -> datasets | |
| row_data | jsonb | Excel satırının karşılığı |
| is_active | bool | soft-versioning için |
| updated_at | timestamp | |

> Not: Her dataset için ayrı sabit-şemalı tablo da açılabilir (örn. `kablo_bilgileri` tablosu kendi kolonlarıyla). Veri tipleri net ve stabilse bu daha performanslı ve type-safe olur. jsonb yaklaşımı daha esnek ama sorgulama biraz daha karmaşıklaşır. Proje başlarken her iki yaklaşımın da değerlendirilmesi önerilir; küçük/orta ölçekli veri setleri için sabit şema genelde daha sağlıklıdır.

### `dataset_permissions` (uygulama bazlı erişim)
| Kolon | Tip | Açıklama |
|---|---|---|
| api_key | text/PK | |
| app_name | text | |
| dataset_id | FK -> datasets | |

## 8. API Uç Noktaları (İlk Taslak)

### Uygulamalar için (okuma)
- `GET /api/datasets` — erişebildiği dataset listesi (API key'e göre filtrelenir)
- `GET /api/datasets/{dataset_name}/data` — tüm veriyi döner
- `GET /api/datasets/{dataset_name}/data?since={timestamp}` — delta güncelleme (ileride)

### Admin panel için
- `POST /admin/login` — basit kimlik doğrulama
- `GET /admin/datasets` — dataset listesi + son güncelleme bilgisi
- `POST /admin/datasets/{dataset_name}/upload` — excel yükleme (multipart/form-data)
- `GET /admin/datasets/{dataset_name}/history` — yükleme/backup geçmişi
- `POST /admin/datasets/{dataset_name}/rollback/{upload_id}` — önceki backup'a dönüş

## 9. Admin Panel Ekranları

1. **Giriş ekranı** — basit kullanıcı adı/şifre (JWT token).
2. **Dataset listesi** — her satırda: dataset adı, son güncelleme tarihi, güncelleyen kişi, "Geçmişi Gör" ve "Güncelle" butonları.
3. **Güncelleme ekranı** — sürükle-bırak excel yükleme alanı, yükleme öncesi önizleme/validasyon sonucu gösterimi, onay butonu.
4. **Geçmiş/backup ekranı** — versiyon listesi, "İndir" ve "Bu versiyona dön" seçenekleri.

## 10. Doğrulama (Validasyon) Kuralları

- Yüklenen Excel'in kolon başlıkları, `datasets.schema_definition` ile eşleşmeli.
- Zorunlu alanlar boş olamaz.
- Tip kontrolü (sayı olması gereken alan metin içeriyorsa hata).
- Satır sayısı önceki versiyona göre ciddi oranda azaldıysa (örn. %50+) kullanıcıya uyarı gösterilmeli ("Emin misiniz?").
- Hatalı satırlar varsa, hangi satır/kolonda hata olduğu admin'e listelenmeli; yükleme ya tamamen reddedilir ya da sadece geçerli satırlar işlenir (proje başında karar verilecek).

## 11. Teknoloji Seçimleri (Önerilen)

| Katman | Teknoloji |
|---|---|
| Veritabanı | PostgreSQL |
| Backend | Python + FastAPI, SQLAlchemy (ORM), pandas/openpyxl (excel parse) |
| Kimlik doğrulama | JWT (admin panel), API key (uygulamalar) |
| Admin Panel Frontend | React (basit, tek sayfa) |
| Dosya/backup depolama | Sunucu üzerinde yerel dizin (örn. `/data/backups/{dataset}/{timestamp}.xlsx`) |

## 12. Aşamalı Uygulama Planı

### Faz 1 — Temel Altyapı
- PostgreSQL kurulumu, `datasets`, `dataset_uploads`, `dataset_records`, `dataset_permissions` tablolarının oluşturulması.
- Mevcut Excel dosyalarının incelenip her biri için şema (`schema_definition`) çıkarılması.

### Faz 2 — Backend API
- FastAPI proje iskeleti.
- Excel upload + parse + validasyon + DB yazma akışı.
- Backup mekanizması (dosya + DB versiyonlama).
- Okuma endpoint'leri (`GET /api/datasets/{name}/data`).
- API key bazlı yetkilendirme.

### Faz 3 — Admin Panel
- Basit React arayüzü: giriş, dataset listesi, upload (drag&drop), geçmiş/backup görüntüleme.

### Faz 4 — Uygulama Entegrasyonu
- Ortak bir Kotlin API client modülü yazılması (Retrofit).
- Pilot olarak tek bir uygulamanın (örn. en basit tablet uygulaması) dosya yerine API'den veri çekecek şekilde güncellenmesi.
- Test sonrası diğer uygulamalara kademeli geçiş.

### Faz 5 — Test & Geçiş
- Eski (Excel dosya bazlı) ve yeni (API bazlı) sistemin paralel çalıştırılması, veri tutarlılığının doğrulanması.
- Eski sürecin kaldırılması.

## 13. Riskler

- Elle hazırlanan Excellerde format tutarsızlığı → validasyon katmanı olmadan canlıya hatalı veri gidebilir.
- Staj süresi (26 Ağustos'a kadar) tüm fazları bitirmeye yetmeyebilir → Faz 1-2 ve tek bir pilot uygulama entegrasyonu MVP olarak hedeflenmeli, kalanı "sonraki adımlar" olarak sunulmalı.
- Sunucuda PostgreSQL kurulumu için gerekli yetki/erişim (IT/Bilgi İşlem ile koordinasyon gerekebilir).

## 14. Başarı Kriterleri

- En az bir dataset ve en az bir uygulama, Excel dosyası yerine API üzerinden veri çekiyor olmalı.
- Admin panelden yapılan bir Excel güncellemesi, ilgili uygulamada dosya değişmeden/yeniden derlenmeden yansımalı.
- Hatalı bir yükleme denemesinde sistem veriyi reddetmeli veya önceki versiyona dönebilmeli.