# TTI Platformu — Kullanıcılar ve Parolalar

> ⚠️ **GİZLİ.** Bu dosya başlangıç parolalarını içerir. Sürüm kontrolüne (git) **koymayın**, paylaşımlı
> bir yere atmayın, teslimden sonra parolaları **değiştirin**. Servis/port özeti: [SERVISLER.md](SERVISLER.md)

Bu belge **uygulamalara girişte** kullanılan hesapları listeler. Veritabanı rolleri ayrı bölümde (altta).

---

## 1. TTI Saha Hakediş (`http://hakedis.<DOMAIN>`)

8 hesap açılışta oluşturulur (7 bölge sorumlusu + 1 yönetici). Parolalar veritabanında yalnızca
**BCrypt hash'i** olarak durur.

| Kullanıcı adı | Ad / Bölge | Rol | İlk parola |
|---|---|---|---|
| `ist-avr` | İstanbul Avrupa | Bölge Sorumlusu | `TTI111111` |
| `ist-and` | İstanbul Anadolu | Bölge Sorumlusu | `TTI222222` |
| `edirne` | Edirne | Bölge Sorumlusu | `TTI333333` |
| `ankara` | Ankara | Bölge Sorumlusu | `TTI444444` |
| `kutahya` | Kütahya-Şırnak | Bölge Sorumlusu | `TTI555555` |
| `izmir` | İzmir | Bölge Sorumlusu | `TTI666666` |
| `bursa` | Bursa | Bölge Sorumlusu | `TTI777777` |
| `yonetici` | — (tüm bölgeler) | Yönetici | `TTI000000` |
| `admin` | — (tüm bölgeler) | Yönetici | `TTI000000` |

> Kalıp: `TTI` + bölge numarasının tekrarı; yönetici `000000`.

> `admin` **19 Ağustos 2026'da eklendi** (`V20__admin_yoneticisi.sql`). `yonetici` ile **birebir aynı
> yetkilere** sahip ikinci bir hesap — hakedişte üçüncü bir rol yok. Rapor'daki `admin` hesabından
> farklı: orada kullanıcı yönetimi yetkisi var, burada yok.

- **Yetki:** Bölge sorumlusu yalnızca kendi bölgesini görür (SQL `WHERE` süzgeci). Yönetici hepsini görür;
  onay/iade, durum müdahalesi, kullanıcılar, sözleşmeler ve kablo envanteri **yalnızca yönetici web'inde**.
- ⚠️ **Hakediş ilk girişte parola değiştirmeyi ZORUNLU tutmaz.** Bu başlangıç parolaları değiştirilene
  kadar geçerli kalır — teslim öncesi elle değiştirin.

---

## 2. TTIDataRapor (`http://rapor.<DOMAIN>`)

12 hesap açılışta oluşturulur: **1 sistem yöneticisi** + 4 yönetici + 7 saha.

### Sistem yöneticisi (`admin`)

| Kullanıcı adı | Ad Soyad | Rol | Parola |
|---|---|---|---|
| `admin` | Sistem Yöneticisi | Sistem Yöneticisi | `TTI000000` |

*(`RAPOR_ADMIN_PAROLA` ortam değişkeniyle değiştirilebilir. ~~`TTIAdmin2026`~~ 19 Ağustos 2026'da
`TTI000000` yapıldı — hakediş'teki `admin` hesabıyla aynı olsun diye.)*

- **Giriş ekranında kendi sekmesi var:** *Admin Girişi*. Bu sekme role değil **sistem yöneticisi
  bayrağına** bakar; aynı hesap *Yönetici Girişi* sekmesinden de girebilir (rolü `admin`).

- ⚠️ **İlk girişte parola değiştirmek ZORUNLU DEĞİL** — diğer 11 hesaptan farkı budur. Parolayı
  kullanıcı bilerek belirledi; ayrıca bu hesabın varlık nedeni parolasını unutanları kurtarmak,
  kendisi kilitlenirse kurtaracak kimse kalmaz. **Teslimden sonra bu parolayı değiştirin.**
- **Ne yapabilir:** üst çubuktaki **👥 Kullanıcılar** ekranından herkesin **giriş adını** ve
  **parolasını** değiştirir. Sıfırlanan parola sahibine bir sonraki girişte kendi parolasını
  belirletir (`IlkGirisMi` yeniden `true` olur).
- **Ne yapamaz:** kullanıcı ekleyemez/silemez, rol ve aktiflik değiştiremez, **kendi giriş adını**
  değiştiremez (oturumu geçersiz kalırdı — kendi parolasını normal "Şifre Değiştir" ile değiştirir).
- ⚠️ **Giriş adı değişince o kişinin eski talepleri de yeni ada taşınır** (`kablo_talep.olusturan`,
  `son_degistiren`, `guncelleme_gecmisi.degistiren` — hepsi tek transaction). Taşınmasaydı saha
  kullanıcısı kendi açtığı talepleri listede göremez hâle gelirdi ve hiçbir hata çıkmazdı.
- Yetki **rolde değil ayrı bir bayrakta** (`kullanici.sistem_yoneticisi_mi`): diğer dört yönetici
  bu ekranı görmez. Veritabanındaki kısmi benzersiz indeks ikinci bir sistem yöneticisi açılmasını
  engeller.

### Diğer 11 hesap

Hepsinin **ilk parolası aynıdır** ve **ilk girişte değiştirmek ZORUNLUDUR**
(parola değişene kadar API `403 PAROLA_DEGISTIR` döner).

**Başlangıç parolası (11'i de):** `TTIrapor2026`
*(`RAPOR_VARSAYILAN_PAROLA` ortam değişkeniyle değiştirilebilir.)*

| Kullanıcı adı | Ad Soyad | Rol |
|---|---|---|
| `kkestane` | Kemal Kestane | Yönetici |
| `scorbaci` | Serhat Çorbacı | Yönetici |
| `ofirat` | Ömer Firat | Yönetici |
| `spamuk` | Sinan Pamuk | Yönetici |
| `ogeyve` | Oğuz Geyve | Saha |
| `mince` | Mehmet İnce | Saha |
| `myener` | Mehmet Yener | Saha |
| `mbirlik` | Muharrem Birlik | Saha |
| `sozkan` | Servet Özkan | Saha |
| `tkarabocek` | Taylan Karaböcek | Saha |
| `sakturk` | Süleyman Aktürk | Saha |

- **Yetki:** Yönetici tüm kayıtları görür; saha kullanıcısı yalnızca kendi açtığı/güncellediği kayıtları.

### Parola sıfırlama (unutulduğunda)

**Önce `admin` ile deneyin** *(18 Ağustos 2026'dan beri)*: `admin` / `TTI000000` ile girip
üst çubuktaki **👥 Kullanıcılar** → ilgili satırda **Düzenle** → yeni şifre. Sunucuya girmek
gerekmez. Aşağıdaki SQL yolu yalnızca `admin` hesabının kendisi kilitlendiğinde gerekir.

Uygulamada "parolamı unuttum" akışı **yok**. Hesap listesi kodda
(`TTIDataAPI/Services/BaslangicVerisi.cs`), hesabın kendisi ve parola özeti veritabanında
(`rapor.kullanici`). Açılışta kod yalnızca **eksik** kullanıcıyı ekler, var olanı asla ezmez —
dolayısıyla satırı silmek parolayı başlangıç değerine döndürmenin yoludur:

```bash
# Tek kullanıcı (örnek: scorbaci) — diğerlerinin kendi parolası bozulmaz
sudo docker exec -i tti-db psql -U tti_hakedis -d rapor -v ON_ERROR_STOP=1 \
  -c "DELETE FROM rapor.kullanici WHERE kullanici_adi IN ('scorbaci');"

# ya da 11'inin hepsi
sudo docker exec -i tti-db psql -U tti_hakedis -d rapor -v ON_ERROR_STOP=1 \
  -c "DELETE FROM rapor.kullanici;"

sudo docker restart tti-rapor-server        # BaslangicVerisi eksik hesapları yeniden açar
```

Silinen hesap `TTIrapor2026` ile ve **ilk girişte değiştirme zorunluluğuyla** geri gelir.
Talep kayıtları etkilenmez: `kablo_talep.olusturan` kullanıcı adını düz metin tutar, tablolar
arasında yabancı anahtar yoktur — kullanıcı silinse de kaydın "kim açtı" bilgisi kalır.

---

## 3. TTI Merkezi Veri Yönetim Sistemi (`https://data.<DOMAIN>`)

Excel yükleyip 5 Kotlin/Android uygulamasının okuduğu veriyi güncelleyen yönetim paneli.
**Tek** yönetici hesabı vardır; uygulamalar hesapla değil **API anahtarıyla** bağlanır.

### Yönetici hesabı

| Kullanıcı adı | Rol | Parola |
|---|---|---|
| `admin` | Yönetici (tek yetki düzeyi) | `TTI000000` |

> Hakediş ve rapor'daki `admin` ile **aynı parola** — kurumun mevcut kalıbı korundu.
> `.env` → `DATA_ADMIN_PAROLA` ile değiştirilebilir (ama aşağıdaki uyarıyı okuyun).

- Hesap açılışta koddan oluşturulur (`_ensure_default_admin`), parolası **BCrypt hash'i** olarak
  veritabanında (`data.admin_users`) durur.
- ⚠️ **`DATA_ADMIN_PAROLA`'yı değiştirmek tek başına HİÇBİR ŞEY YAPMAZ.** Açılışta kod yalnızca
  **eksik** kullanıcıyı ekler, var olanı asla ezmez — rapor'daki davranışın aynısı. Parolayı
  değiştirmenin yolu satırı silip konteyneri yeniden başlatmaktır:

```bash
sudo docker exec -i tti-db psql -U tti_hakedis -d data -v ON_ERROR_STOP=1 \
  -c "delete from data.admin_users where username='admin';"
sudo docker restart tti-data-server      # yeni parolayla yeniden oluşturur
```

- ⚠️ Uygulamada "parolamı unuttum" akışı **yok**, ikinci bir yönetici hesabı da yok. Yukarıdaki
  SQL tek kurtarma yoludur.
- ⚠️ İlk girişte parola değiştirme **zorunlu değildir**. Teslim öncesi elle değiştirin.
- Rate limiting / hesap kilitleme **yok** ve giriş ucu tüm intranetten erişilebilir.

### API anahtarları (uygulama–sunucu erişimi — kullanıcı hesabı DEĞİL)

5 Kotlin/Android uygulamasının `X-API-Key` başlığında gönderdiği anahtarlar. **Otoritesi
veritabanıdır** (`data.dataset_permissions`, bileşik birincil anahtar `(api_key, dataset_id)`);
bir anahtar birden fazla dataset'e izinli olabilir.

| Uygulama | Anahtar |
|---|---|
| TTİ Ticket | `tti-data/api_keys.txt` |
| TTİ Hasar Tespit | `tti-data/api_keys.txt` |
| TTİ İdari | `tti-data/api_keys.txt` |
| TTİ Masraf | `tti-data/api_keys.txt` |
| TTİ KabloEk | `tti-data/api_keys.txt` |

- ⚠️ Anahtarlar veritabanında **düz metin** saklanır (kullanıcı parolası değil, uygulama-sunucu
  erişim anahtarıdır — tasarım gereği). `api_keys.txt` `.gitignore`'da ve transfer zip'inin
  dışlama listesindedir.
- Kotlin tarafında anahtar kaynak koda/git'e **commit edilmemeli**, `BuildConfig`/güvenli config
  üzerinden verilmelidir.
- Yeni izin: `insert into data.dataset_permissions (api_key, app_name, dataset_id) values (...);`
- Anahtar yenileme: ilgili satırları güncelleyin ve 5 uygulamaya yeni anahtarı dağıtın.

### Erişim listesi

```bash
sudo docker exec -i tti-db psql -U tti_hakedis -d data -c \
  "select p.app_name, d.name as dataset from data.dataset_permissions p
     join data.datasets d on d.id = p.dataset_id order by 1,2;"
```

---

## 4. Portainer (`https://<sunucu-ip>:9443` — kurulum: `https://localhost:9443`)

| Kullanıcı adı | Parola |
|---|---|
| `admin` | `TtiPortainer2026` |

- Sunucu OS kullanıcısından (`aurun`) **bağımsız** — kasıtlı (kimlik bilgisi tekrarı yapılmadı).
- ⚠️ İlk kurulum **birkaç dakika içinde** yapılmalı; kaçırılırsa Portainer kilitlenir →
  `sudo docker restart tti-portainer` sonra hemen `localhost:9443`'ten kur.

---

## 5. Veritabanı rolleri (uygulama girişi DEĞİL — altyapı)

Bunlar web girişleri değildir; servislerin PostgreSQL'e bağlanmak için kullandığı rollerdir. Canlı
değerler sunucuda `/srv/kod/tti-sunucu/.env`'dedir; aşağıda yedeği.

| Rol | Ne | `.env` değişkeni | Parola (canlı) |
|---|---|---|---|
| `tti_hakedis` | **Bakım (superuser)** — bootstrap/provizyon/yedek; **uygulama KULLANMAZ** | `DB_BAKIM_PAROLA` | `RRTSIpIWUAwp5Z3xUbLD9LMdpUDH` |
| `tti_uygulama` | TTI uygulaması (**ayrıcalıksız**) | `DB_PAROLA` | `AxTkwxvkF2pPWtOxNe8Tnay2JaGk` |
| `rapor` | Rapor uygulaması (**ayrıcalıksız**) | `RAPOR_DB_PAROLA` | `V4sajBqvgfrJXMgtLAI4oNOLsSAg` |
| `data` | Merkezi Veri uygulaması (**ayrıcalıksız**) | `DATA_DB_PAROLA` | `VuqOMGHBxRqrzroI9kxtUlDOaWZS` |

- ⚠️ `tti_uygulama`/`rapor`/`data` parolaları provizyon SQL'lerine
  (`01-tti.sql`/`02-rapor.sql`/`03-data.sql`) `.env`'den `sed` ile eşlenir; ikisi aynı olmalı
  yoksa uygulama bağlanamaz.
- `data` rolünün parolası ve JWT anahtarı 19 Ağustos 2026'da üretildi. Sunucuya konacak hazır
  dosyalar (depo DIŞINDA, transfer zip'ine girmez):
  `~/TTI-Hakedis/sunucu-ayarlari-2026-08-19/` → `env-eklenecek-blok.txt`, `03-data-HAZIR.sql`
- Bakım superuser'ı (`tti_hakedis`) ile uygulama rolleri **ayrıdır**: uygulama superuser'la
  bağlanmaz, aksi halde projeler-arası sınır delinir. (Dağıtımda bu düzeltildi — bkz. [ServerDeploy.md](ServerDeploy.md) §6.)
- Üç uygulama rolünün hiçbiri diğerinin veritabanına **bağlanamaz** (yapısal izolasyon;
  `tti-sunucu/dogrula.sh` §2 altı yönü de kilitler).
- 5432 dışarı açılmaz — veritabanı yalnızca `127.0.0.1`'e bağlıdır.

---

## 6. Android APK imzalama anahtarı (dağıtım — giriş DEĞİL)

Tablete kurulan **release APK'sını** imzalayan anahtar. Bir hesap değildir; kimse bu parolayı elle
girmez, Gradle derleme sırasında dosyadan okur.

| | |
|---|---|
| **Keystore** | `~/.tti-keys/tti-hakedis-release.jks` (APK derlenen geliştirici makinesinde) |
| **Ayar dosyası** | `~/.tti-keys/tti-hakedis-keystore.properties` |
| **Alias** | `tti-hakedis` |
| **Parola** (store = key) | `mGmeBhZrdG1Je1t1xc4RDH59LEnnbJR6` |
| **Anahtar** | RSA 4096, geçerlilik 10.000 gün (~2053) |
| **Sertifika SHA-256** | `18c634e9596459b7150bff13361a6988fbf14e026138aee014a8e83eefefcbee` |

⚠️ **Bu anahtar kaybolursa geri getirilemez.** Android, kurulu bir uygulamanın üzerine güncellemeyi
ancak **aynı anahtarla** imzalanmış APK ile kabul eder; anahtar kaybolursa yeni sürüm tabletlere
ancak uygulama **önce kaldırılarak** kurulabilir (cihazda veri tutulmadığı için veri kaybı yok, ama
her tablete elle gitmek gerekir). Keystore dosyasını ayrı bir yere yedekleyin.

⚠️ Keystore **depo dışındadır** ve öyle kalmalı: depo klasörü sunucuya zip'lenerek gidiyor
(`ServerDeploy.md`) ve `zip -r` `.gitignore`'a bakmaz. `app/build.gradle.kts` dosyayı
`~/.tti-keys/`'ten okur; **dosya yoksa release imzasız üretilir**, derleme kırılmaz — yani anahtarı
olmayan bir makinede de `assembleRelease` çalışır, çıktı sadece tablete kurulamaz.

```bash
./gradlew :app:assembleRelease        # → app/build/outputs/apk/release/app-release.apk
apksigner verify -v app/build/outputs/apk/release/app-release.apk   # "Verifies" + v2 true
```

---

## Teslim öncesi kontrol

- [ ] Hakediş 8 hesabının parolası değiştirildi (ZORUNLU değil, o yüzden elle)
- [ ] Rapor kullanıcılarına `TTIrapor2026` verildi; ilk girişte değiştirecekler (ZORUNLU)
- [ ] Portainer admin parolası kuruldu
- [ ] `.env`'deki `DB_PAROLA`, `RAPOR_DB_PAROLA` ve `DATA_DB_PAROLA` gerçek güçlü değerlerle dolduruldu
- [ ] `DATA_JWT_ANAHTARI` rastgele üretildi (`openssl rand -base64 48`)
- [ ] Merkezi Veri `admin` parolası değiştirildi (ZORUNLU değil, o yüzden elle)
- [ ] `api_keys.txt` sunucuya gitmedi, git'e girmedi
- [ ] Bu dosya git'e girmedi, gizli tutuldu
- [ ] **APK imzalama keystore'u yedeklendi** (`~/.tti-keys/`) — kaybolursa geri getirilemez
