# TTIDataRapor — Kurulum ve Çalıştırma

Saha altyapı/kablo durum takibi. .NET 8 Web API + tek sayfalık arayüz (`wwwroot`), veritabanı
**PostgreSQL 17**.

> **Veritabanı ayrımı pazarlıksız.** Bu servis TTI Saha Hakediş ile **aynı PostgreSQL örneğini**
> paylaşır ama **kendi veritabanında** (`rapor`) yaşar; TTI `tti` veritabanında. Projeler arası
> sorgu PostgreSQL'in dilinde **yoktur** — `postgres_fdw` kurulmadıkça imkânsızdır. Ayrıca rol
> düzeyinde de kapalı: `rapor` rolü `tti` veritabanına **bağlanamaz**.
> `deploy/dogrula.sh` bu iki sınırı da kilitliyor.
>
> Sunucudaki paylaşılan yığının kurulumu **`tti-sunucu/KURULUM.md`** dosyasındadır.
> Burada yalnızca bu projeye ait adımlar var.

---

## 1. Geliştirme makinesinde çalıştırma

### Konteynerle (önerilen — üretimdeki kurulumun birebir taklidi)

```bash
# Dockerfile çok aşamalı (multi-stage): derlemeyi imajın içinde yapar. Önce publish GEREKMEZ.
cd deploy
docker compose up -d --build          # imaj kaynaktan derlenir (.NET SDK build imajında)
curl -s localhost:8082/api/saglik
```

Arayüz: <http://localhost:8082>

> Bu compose bu projeyi **kendi veritabanı konteyneriyle tek başına** kaldırır ve yalnızca
> geliştirme içindir. TTI ile birlikte, paylaşılan PostgreSQL üzerinde çalıştırmak için
> `tti-sunucu/docker-compose.yml` kullanılır:
> `cd ../../tti-sunucu && docker compose up -d tti-rapor-server`

`deploy/veritabani-kur.sql` yalnızca veritabanı **ilk kez** oluşturulurken çalışır (PostgreSQL'in
`initdb.d` mekanizması). Sıfırdan başlamak için: `docker compose down -v`.

### Konteynersiz

```bash
# 1) Rolü ve VERİTABANINI aç (bir kez)
psql -d postgres -f deploy/veritabani-kur.sql

# 2) Çalıştır — şema ve tablolar açılışta migration ile kurulur
cd TTIDataAPI
dotnet run
```

Bağlantı dizesi `appsettings.json` içindedir ve yalnızca yerel geliştirme içindir.

---

## 2. Sunucuya yayın

Sunucu **host'una .NET SDK kurulmaz.** İmaj multi-stage Dockerfile ile **kaynaktan** derlenir; SDK
build imajının içinde çalışır (TTI'nin jar yaklaşımıyla aynı mantık). Sunucuya taşınan **kaynak**tır,
publish çıktısı değil.

```bash
# Geliştirici makinesinde — sadece kaynağı taşı (publish YOK):
cd ..
zip -r ttidatarapor-kaynak.zip TTIDataAPI deploy \
  -x '*/bin/*' '*/obj/*' '*/yayin/*'
sha256sum ttidatarapor-kaynak.zip     # sunucuda karşılaştırın
```

Sunucuda imaj, ortak yığının bir parçası olarak `docker compose build` ile üretilir (etiket
`.env`'deki `SURUM_RAPOR`'dan gelir — tarih, `dev` değil). Tek başına da denenebilir:

```bash
docker build -f deploy/Dockerfile -t tti-rapor-server:2026-08-11 ..   # bağlam repo kökü (..)
```

> ⚠️ Multi-stage build **NuGet'ten bağımlılık çeker** — build anı internet gerekir. Kapalı ağda
> build'i dev'de koşup imajı `docker save`/`load` ile taşıyın.

Geri kalanı (ortak compose, provizyon, güvenlik duvarı, yedekleme) **`tti-sunucu/KURULUM.md`**
adımlarındadır — paylaşılan yığının otoritesi orasıdır.

---

## 3. Yapılandırma

| Ayar | Ortam değişkeni | Varsayılan |
|---|---|---|
| Bağlantı dizesi | `ConnectionStrings__Varsayilan` | `appsettings.json` (yalnızca geliştirme) |
| Sürüm etiketi | `SURUM` | `bilinmiyor` — `/api/saglik` bunu döner |
| Fotoğraf kökü | `RAPOR_YUKLEME_KOK` | konteynerde `/yukleme` |
| Başlangıç parolası | `RAPOR_VARSAYILAN_PAROLA` (ya da `Baslangic__VarsayilanParola`) | `CHANGE_ME_PASSWORD` |
| Ortam | `ASPNETCORE_ENVIRONMENT` | `Production` |

**Bağlantı dizesi yoksa uygulama açılışta durur.** Gömülü bir varsayılana düşmek, üretimde yanlış
veritabanına sessizce bağlanmak demek olurdu.

Swagger yalnızca `Development` ortamında açılır.

---

## 4. Kullanıcılar

12 kullanıcı açılışta oluşturulur: **1 sistem yöneticisi** + 4 yönetici + 7 saha kullanıcısı.
Sistem yöneticisi dışındaki 11'i **ilk girişte parolasını değiştirmek zorundadır**. Zorunluluk
sunucuda uygulanır: parola değişene kadar veri uçları `403 PAROLA_DEGISTIR` döner, dolayısıyla
arayüzü atlayıp doğrudan API'ye istek atmak da işe yaramaz.

### Sistem yöneticisi (`admin`)

| Kullanıcı adı | Ad Soyad | Rol | Parola |
|---|---|---|---|
| `admin` | Sistem Yöneticisi | yönetici + **kullanıcı yönetimi** | `CHANGE_ME_PASSWORD` |

`RAPOR_ADMIN_PAROLA` ile değiştirilebilir. Giriş ekranında **kendi sekmesi** var (*Admin Girişi*);
o sekme role değil `sistem_yoneticisi_mi` bayrağına bakar.

- **Ne yapar:** üst çubuktaki **👥 Kullanıcılar** ekranından herkesin **giriş adını** ve
  **parolasını** değiştirir. Sıfırlanan parola sahibine bir sonraki girişte kendi parolasını
  belirletir.
- ⚠️ **İlk girişte parola değiştirmesi ZORUNLU DEĞİL** — diğer 11 hesaptan farkı budur. Bu hesap
  parolasını unutanları kurtarmak için var; kendisi kilitlenirse kurtaracak kimse kalmaz.
  **Teslimden sonra parolasını değiştirin.**
- ⚠️ **Giriş adı değişince o kişinin eski talepleri de yeni ada taşınır** (`kablo_talep.olusturan`,
  `son_degistiren`, `guncelleme_gecmisi.degistiren` — tek transaction). Taşınmasaydı saha
  kullanıcısı kendi açtığı talepleri listede göremez hâle gelirdi ve hiçbir hata çıkmazdı.
- Yetki **rolde değil ayrı bir bayrakta**: diğer dört yönetici bu ekranı görmez. Veritabanındaki
  kısmi benzersiz indeks ikinci bir sistem yöneticisi açılmasını engeller.

### Diğer 11 hesap

| Kullanıcı adı | Ad Soyad | Rol |
|---|---|---|
| `yonetici-1` | Yonetici Bir | yönetici |
| `scorbaci` | Serhat Çorbacı | yönetici |
| `ofirat` | Ömer Firat | yönetici |
| `spamuk` | Sinan Pamuk | yönetici |
| `saha-1` | Saha Kullanici Bir | saha |
| `saha-2` | Saha Kullanici Iki | saha |
| `myener` | Mehmet Yener | saha |
| `saha-4` | Saha Kullanici Dort | saha |
| `saha-5` | Saha Kullanici Bes | saha |
| `saha-6` | Saha Kullanici Alti | saha |
| `saha-7` | Saha Kullanici Yedi | saha |

Başlangıç parolası: **`CHANGE_ME_PASSWORD`** (`RAPOR_VARSAYILAN_PAROLA` ya da `Baslangic__VarsayilanParola`
ile değiştirilebilir).

**Yetki:** yönetici tüm kayıtları görür; saha kullanıcısı yalnızca kendi açtığı ya da son
güncellediği kayıtları. Süzgeç **SQL sorgusunda** uygulanır — listeyi çekip bellekte filtrelemek
yasak, çünkü süzgeci her uçta tekrarlamak gerekir ve biri unutulduğunda hata sessizdir.

---

## 5. Doğrulama

```bash
bash deploy/dogrula.sh
```

Kontroller: veritabanı ayrımı (`rapor` dolu, `public` boş, `__EFMigrationsHistory` doğru şemada,
**`rapor` rolü `tti` veritabanına bağlanamıyor**), migration durumu, `timestamptz` ve snake_case,
kullanıcı sayısı ve parolaların BCrypt özeti olduğu, sağlık ucu, yükleme kökünün yazılabilirliği,
oturumsuz isteğin 401 dönmesi ve **bağlantı havuzu sınırının gerçekten uygulandığı**.

Konteynersiz ortamda:

```bash
PSQL_KOMUTU="psql -h localhost -p 5432 -U postgres" SUNUCU=http://localhost:5199 bash deploy/dogrula.sh
```

Sunucuda (paylaşılan yığında):

```bash
DB_KAPSAYICI=tti-db bash deploy/dogrula.sh
```

Projeler ARASINDAKİ sınırlar ayrı bir betikte: `tti-sunucu/dogrula.sh`.

---

## 6. Veri saklama ve yedekleme

| Nerede | İçerik | Kaybolursa |
|---|---|---|
| `rapor` veritabanı | Talepler, geçmiş, kullanıcılar, kablo kataloğu | **Geri gelmez** |
| `RAPOR_YUKLEME_KOK` | Saha fotoğrafları | **Geri gelmez** |

İkisi de `tti-sunucu/yedek.sh` kapsamındadır (`VERITABANLARI` ve `YUKLEMELER` listelerinde).
Fotoğraflar veritabanında değil diskte tutulur: base64 olarak satırda saklandığında ~%33 şişiyor
ve her listeleme sorgusu tüm fotoğrafları da çekiyordu.

---

## 7. Migration ekleme

```bash
cd TTIDataAPI
dotnet ef migrations add <Ad>
```

`AppDbContextFactory` sayesinde komut veritabanına bağlanmaz; migration üretmek için sunucunun
ayakta olması gerekmez.

⚠️ **Yayımlanmış bir migration düzenlenmez** — TTI tarafındaki Flyway kuralının karşılığı.
Değişiklik gerekiyorsa yeni bir migration eklenir.

> **Yerel not:** makinede yalnızca .NET 10 runtime kuruluysa `dotnet ef` net8.0 hedefini
> çalıştıramaz. Çözüm ya .NET 8 runtime kurmak ya da komutları `DOTNET_ROLL_FORWARD=LatestMajor`
> ile çalıştırmaktır. Konteynerde bu sorun yoktur; taban imaj zaten .NET 8 taşır.

---

## 8. Bilinen sınırlar

- **TLS yok.** İki servis de HTTP üzerinden, intranette çalışır. Oturum çerezine
  `Secure` bayrağı bu yüzden konmuyor (`Program.cs`); HTTPS'e geçildiğinde değişecek tek satır o.
- **Kablo kataloğu.** NOS+ envanterinin tamamı (**943 kayıt**) `KatalogTohumu943` migration'ına
  gömülüdür; boş bir veritabanı açılışta doğrudan 943 kayıtla gelir (eski JSON tohum yolu
  kaldırıldı). Migration `ON CONFLICT (nosid) DO NOTHING` kullandığı için, daha önce eski 10
  kayıtlık tohumla dolmuş bir veritabanında da çakışmadan çalışır — mevcut nosid'ler atlanır,
  eksikler eklenir, sonuç yine 943. Sonraki güncellemeler **yönetici** arayüzündeki "📥 Kablo
  Kataloğu Yükle" ile yapılır (`POST /api/kablo/katalog-yukle`, `.xlsx`): yalnızca kataloğda
  olmayan yeni NOSID'ler eklenir (insert-only), mevcut kayıtlar değişmez, hiçbir kayıt silinmez.
  Beklenen Excel biçimi TTI'nin `cable_data_*.xlsx` NOS+ dışa aktarımıyla aynıdır; `Uzunluk`
  Excel'deki `length` sütunundan, `LocationA/B` konum yolu sütunlarından (`" >> "`) kurulur.
  Aynı envanter TTI'nin veritabanında da var, ama veritabanı ayrımı gereği oradan **okunmuyor**
  (zaten `rapor` rolü `tti` veritabanına bağlanamaz) — bilinçli bir tekrar.
- **Gönderilmiş talebin kimlik alanları değiştirilemez.** Düzenleme ekranı yalnızca statü ve
  düzenleme notunu günceller; kablo ve güzergâh bilgileri kilitlidir. **İstisna: fotoğraf ve
  güzergah dosyası** — ikisi de ayrı uçlarla, kaydın kimlik alanlarına dokunmadan sonradan
  eklenebilir/değiştirilebilir (sahadan dönen kullanıcı unuttuğu dosyayı yükleyebilmeli).
- **GPS koordinatı yok, güzergah DOSYASI var** *(18 Ağustos 2026)*. Başlangıç/bitiş konumu alanları
  kaldırıldı: masaüstü tarayıcıda ve kurumsal VPN arkasında konum izni çoğunlukla reddediliyordu,
  dolayısıyla iki uçlu güzergâh neredeyse hiç oluşmuyordu. Yerine **`.kml`/`.kmz` yükleme ve
  indirme** geldi (`POST/GET/DELETE /api/kablo/{id}/guzergah`). Dosya **zorunlu değil**; talep
  dosyasız da açılabilir. `KapsamTablosuVeGuzergahDosyasi` migration'ı `konum_a_lat/lon` ve
  `konum_b_lat/lon` sütunlarını düşürür — ⚠️ **veri kaybı**, uygulamadan önce yedek alın.
- **Güzergah uzunluğu zorunlu ve metre.** Katalogdan otomatik doldurulmuyor: kataloğun `length`
  değeri envanterin kablo uzunluğu, sahada ölçülen güzergâh uzunluğu değil.
- **Proje kapsamı listesi veritabanında** (`proje_kapsami`), kodda değil. Yönetici arayüzden yeni
  kapsam ekleyebilir; koddaki dizi yalnızca ilk açılışta tohum olarak kullanılır. Silme yok —
  eski talepler kapsam adını düz metin taşıyor.
- **`Masraf` modeli kullanılmıyor.** `Models/Masraf.cs` incelenmeyi bekliyor, `DbSet`'i olmadığı
  için veritabanında karşılığı yok. Ölü kod sanılıp silinmemesi için dosyada not var.
