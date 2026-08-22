# Yeni Proje Katılım Sözleşmesi

Bu makineye yeni bir servis eklenecekse aşağıdaki sekiz maddeyi karşılamalıdır. Liste tartışma
her seferinde sıfırdan başlamasın diye var: yeni bir stajyer projesi geldiğinde "bunlar nasıl
olmalıydı" sorusunun cevabı burada yazılı.

Maddeler keyfi değil — her biri, atlandığında **hiçbir hata vermeden** bozulan bir şeyi kapatıyor.

---

## 1. Kendi veritabanı, kendi rolü

Proje `public` şemasına ve başka bir projenin veritabanına dokunmaz.

`provizyon/SABLON.sql` kopyalanır, `<proje>` yerine gerçek ad yazılır, sıradaki numarayla
kaydedilir ve bir kez çalıştırılır.

> **Neden şema değil veritabanı:** Şema ayrımında `select ... from baska_sema.tablo` **geçerli bir
> SQL ifadesidir**; yalnızca yetki kontrolünden düşer ve o yetki, hatayı çözmeye çalışan iyi
> niyetli biri tarafından verilebilir. Veritabanları arası sorgu ise PostgreSQL'in dilinde
> **yoktur** — `postgres_fdw` kurulmadıkça imkânsızdır ve o kurulum incelemede görünür.
> Yazarların değiştiği bir ortamda sınırın yapısal olması, disipline dayanmasından üstündür.

> ⚠️ **`revoke connect ... from public` atlanmaz.** PostgreSQL yeni bir veritabanına bağlanma
> yetkisini varsayılan olarak PUBLIC'e verir; yani makinedeki her rol oraya bağlanabilir. Veri
> okuyamaz ama bağlanır ve katalogları görür. `dogrula.sh`'ın 2. bölümü tam bunu kontrol eder.

## 2. Migration'ını kendi çalıştırır, hedefini açıkça belirtir

Şema açılışta uygulama tarafından kurulur; elle SQL çalıştırılmaz.

- **EF Core:** `HasDefaultSchema("<proje>")` **ve** `MigrationsHistoryTable("__EFMigrationsHistory", "<proje>")`.
  İkincisi kolayca unutulur ve unutulduğunda geçmiş tablosu `public`'e düşer.
- **Flyway:** `schemas` + `default-schema` + `create-schemas: true`.
- **Yayımlanmış bir migration düzenlenmez** — yeni bir numara eklenir. (Flyway checksum'ı tutmaz
  ve sunucu hiç açılmaz; EF'te ise şema ile kod sessizce ayrışır.)

## 3. `GET /api/saglik`

Sağlıklıysa **200**, değilse **503** döner. Gövdesinde en az:

```json
{ "durum": "ayakta", "surum": "2026-08-11", "veritabani": "ayakta" }
```

`surum` alanı `SURUM` ortam değişkeninden okunur. Bu alan olmadan **çalışan sürümü sormanın
güvenilir bir yolu yoktur**: `docker ps` yalnızca konteynerin ne ile *başlatıldığını* gösterir.

Konteyner healthcheck'i bu ucu çağırır. Taban imajda `curl`/`wget` olduğundan emin olun —
`mcr.microsoft.com/dotnet/aspnet` imajında **ikisi de yoktur** ve eksikse konteyner sonsuza kadar
`unhealthy` görünür, uygulama sorunsuz çalışırken.

## 4. Ayarlar ortamdan, eksikse açılışta durur

Bağlantı dizesi, yollar, sırlar — hepsi ortam değişkeninden. Gömülü varsayılana **düşülmez**:
düşerse üretimde yanlış veritabanına sessizce bağlanmak mümkün olur.

```csharp
var cs = builder.Configuration.GetConnectionString("Varsayilan");
if (string.IsNullOrWhiteSpace(cs)) throw new InvalidOperationException("Bağlantı dizesi yok...");
```

## 5. Dockerfile çok aşamalıdır (multi-stage) — imaj kaynaktan derlenir

Dockerfile iki aşamalıdır: **build** aşaması derleyiciyi taşır (JDK+Node ya da .NET SDK) ve kaynağı
derler; **runtime** aşaması yalnızca çalışma zamanını taşır ve çıktıyı kopyalar. Böylece ne dev'de
ne sunucuda SDK kurulur, çalışan imaj küçük kalır, ve elle build adımı olmaz — `docker compose build`
kaynaktan derler. İmaj `.env`'deki `SURUM` ile etiketlenir.

⚠️ build aşaması bağımlılıkları internetten çeker (Maven/npm/NuGet), yani **build anı internet**
gerekir; sunucunun eriştiği kurulumda `curl` ile doğrulanır. Kapalı ağda build **dev'de** koşulup
imaj `docker save`/`load` ile taşınır — Dockerfile aynı kalır.

> ~~Sunucuda SDK kurulmaz; derleme dev'de yapılır, Dockerfile yalnızca hazır jar/publish'i kopyalar~~
> *(değişti)* — elle `bootJar`/`publish` adımı her güncellemede tekrar ediyor ve unutulmaya açıktı;
> multi-stage bunu kaldırdı. Sunucunun build bağımlılıklarına eriştiği doğrulandığı için derleme
> imajın içine alındı.

## 6. Kök olmayan kullanıcı, `mem_limit`, **havuz sınırı beyanı**

```dockerfile
USER 1000:1000
```

`docker-compose.yml`'de `mem_limit` verilir. Bugünkü bütçe (6 GB RAM):

| | Ölçülen | Ayrılan |
|---|---|---|
| PostgreSQL | ~60 MB | 512 MB |
| JVM servis | ~670 MB | 900 MB |
| .NET servis | ~95 MB | 640 MB |
| Python/FastAPI servis | ~250 MB¹ | 768 MB |
| nginx (statik panel) | ~16 MB | 128 MB |

¹ pandas Excel'i tamamen belleğe açar; ayrılan pay ölçülenin üstünde tutuldu.

JVM bir servis .NET'inkinin ~7 katı yer tutuyor. Kalan alan **2-3 .NET/Python servisi** ya da
**1-2 JVM servisi** kadar.

### Bağlantı havuzu — atlanırsa DİĞER projeleri düşürür

PostgreSQL her bağlantı için ayrı bir **işletim sistemi süreci** açar; bu yüzden `max_connections`
sert bir sınırdır ve **makinedeki tüm projelerce paylaşılır**. Uygulamalar birbirinden habersizdir:
havuzu sınırsız bırakan bir servis bütçeyi tek başına tüketir ve o an *diğer* servisler
`sorry, too many clients` ile düşer — hata da yanlış projenin log'una çıkar.

```
Σ(havuz sınırları) + bakım yedeği ≤ max_connections − superuser_reserved
```

| Proje | Havuz | Nerede ayarlanır |
|---|---|---|
| TTI | 10 | `application.yml` → `spring.datasource.hikari.maximum-pool-size` |
| TTIDataRapor | 10 | bağlantı dizesi → `Maximum Pool Size=10` |
| TTI Merkezi Veri | 10 | `backend/app/database.py` → `pool_size=5` + `max_overflow=5` |
| bakım/yedek | 5 | — |
| **toplam** | **35** | 47 kullanılabilirde → ~1 proje daha |

⚠️ Varsayılanlar aldatıcı: **Npgsql 100**, **SQLAlchemy 5+10=15**, HikariCP 10. Yeni projenin
havuz sınırı **açıkça** yazılmalı ve yukarıdaki tabloya eklenmelidir.

⚠️ Havuz tavanı **SÜREÇ BAŞINADIR**. Çok işçili (worker/process) bir sunucu — `uvicorn
--workers N`, `gunicorn`, PHP-FPM — beyanı sessizce N katına çıkarır. İşçi sayısı da sabitlenmeli
(TTI Merkezi Veri: `deploy/giris.sh` → `--workers 1`).

## 7. Kullanıcı verisi bind-mount'ta ve yedek listesinde

Kullanıcının **yüklediği** veri (fotoğraf, ek dosya) uygulamanın ürettiği çıktıdan ayrı bir kökte
tutulur; ilki kaybolursa geri gelmez, ikincisi yeniden üretilir. Konteynerde yol `/yukleme`
olmalıdır — `yedek.sh` yükleme kökünü `docker inspect` ile o bağlamadan okur.

RHEL'de bind mount `:Z` etiketiyle verilir. Etiket olmadan uygulama açılır, sağlık kontrolü geçer
ve hata **ilk dosya yazımında** çıkar.

## 8. Kendini dört yere ekler

| Nereye | Ne |
|---|---|
| `docker-compose.yml` | servis bloğu |
| `yedek.sh` | `VERITABANLARI` ve `YUKLEMELER` listelerine birer satır |
| `dogrula.sh` | `PROJELER` listesine `veritabanı:konteyner:port`, `BEYAN_EDILEN_HAVUZ` toplamı |
| ~~Güvenlik duvarı~~ | ~~`firewall-cmd --permanent --add-port=<port>/tcp`~~ **BU SATIR ESKİDİR** |

> **Güvenlik duvarı satırı neden eskidi:** app portları artık `127.0.0.1`'e bağlanıyor ve dışarı
> yalnızca ters vekil (80/443) açık. Yeni bir web servisi **hiçbir** firewall değişikliği
> gerektirmez; portunu açmak onu tam da kapatmak istediğimiz yüzeye geri koyar. (bkz. `NewDeploy.md`)

Ayrıca şunlar güncellenir: `Caddyfile` (site bloğu — bu tablo yazıldığında proxy henüz yoktu),
`.env` ve `.env.example` (değişkenler + havuz bütçesi tablosu).

`dogrula.sh`'ta dört yer vardır, ikisi kolayca gözden kaçar: `PROJELER` üçlüsü, `BEYAN_EDILEN_HAVUZ`
toplamı, **6a'daki alan adı döngüsü** (`for ad in ...`) ve veritabanı olmayan kapsayıcılar için
`EK_KAPSAYICILAR`.

---

## Kabul kontrolü

Proje eklendikten sonra ikisi de temiz geçmeli:

```bash
bash dogrula.sh          # platform sınırları
bash yedek.sh dogrula    # yeni projenin veritabanı ve yükleme kökü yedekleniyor mu
```

---

## Bu modelin sınırı

Tek makine + tek PostgreSQL, şu üç durumdan biri doğana kadar geçerlidir:

1. Bir proje **farklı bir veritabanı motoru** ya da çakışan bir PostgreSQL eklentisi istiyor
2. **Kesintisizlik (HA)** talebi doğuyor
3. Servis **internete açılacak** (intranet dışına)

Bunlardan biri olduğunda konuşulacak şey ayrı sunucu ya da gerçek orkestrasyondur. Öncesinde
eklemeye gerek yok.
