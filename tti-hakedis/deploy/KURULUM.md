# Kurulum

> **Hangi rehber?** Bu dosya **yalnızca TTI'nin** kendi ayarlarını anlatır (`.env`, arşiv
> klasörleri, kable içe aktarımı, doğrulama betikleri). Makineyi sıfırdan kurmak — özellikle ikinci
> bir projeyle birlikte — ayrı rehberlerdedir:
> - `WindowsDeploy.md` — Windows makine, WSL2 + Docker Engine ile
> - `WindowsDockersiz.md` — Windows makine, konteynersiz
> - `RedHatKurulum.md` — Red Hat sunucu, Docker Engine ile

Sunucu iki konteynerden oluşur: `db` (PostgreSQL 17) ve `server` (Spring Boot).
Docker içinde Gradle çalışmaz — jar önceden derlenir, imaja kopyalanır.

Web arayüzü **ayrı bir servis değildir**: `web/` derlenip jar'ın içine gömülür, aynı porttan
(8081) servis edilir. Bu yüzden nginx, CORS ayarı ya da ikinci bir konteyner yoktur.

> ⚠️ **Jar'ı derleyen makinede Node.js (npm) bulunmalıdır** — `:server:bootJar` arayüzü de derler.
> Node kurulu değilse `./gradlew :server:bootJar -PwebAtla` ile atlanabilir; çıkan jar'da **API
> çalışır, arayüz bulunmaz** (build çıktısında bunu söyleyen bir uyarı basılır).
> Sunucu makinesine Node gerekmez: oraya yalnızca hazır jar gider.

## 1. Ön kontrol (yalnızca ilk kurulumda)

Kurumsal ağda Docker Hub proxy veya güvenlik duvarı arkasında olabilir:

```bash
docker pull postgres:17
docker pull eclipse-temurin:17-jre
```

İkisi de geliyorsa devam edin. Gelmiyorsa ya IT'den proxy adresi isteyin ya da §7'deki
çevrimdışı aktarım yolunu kullanın.

## 2. Yapılandırma

Klasörleri açan ve `.env`'i işletim sistemine göre dolduran betik:

```bash
bash deploy/kur.sh                                          # macOS / Linux
powershell -ExecutionPolicy Bypass -File deploy\kur.ps1      # Windows
```

| Ortam | `ARSIV_KOK` · `YUKLEME_KOK` |
|---|---|
| macOS | `~/TTI-Hakedis/arsiv` · `~/TTI-Hakedis/yukleme` |
| Windows | `%USERPROFILE%/TTI-Hakedis/arsiv` · `.../yukleme` *(compose ileri eğik çizgi ister)* |
| Linux | `/srv/veri/tti-hakedis/arsiv` · `/srv/veri/tti-hakedis/yukleme` (yetki yoksa ana dizin) |

⚠️ Betik **dolu bir değeri değiştirmez**: çalışan bir kurulumun arşivini sessizce başka klasöre
taşımak, eski dosyaları kimsenin bakmadığı bir yerde bırakırdı. Yol değiştirmek isteyen `.env`'i elle
düzenler ve eski klasörün içeriğini kendisi taşır.

Elle yapmak isterseniz:

```bash
cd deploy
cp .env.example .env
```

`.env` içinde en az `DB_PAROLA`, `ARSIV_KOK` ve `YUKLEME_KOK` doldurulur. `.env` depoya girmez.

**İki ayrı klasör vardır ve ayrı kalmalıdır:**

| Değişken | İçerik | Kaybolursa |
|---|---|---|
| `ARSIV_KOK` | Sunucunun **ürettiği** PDF/Excel — `YedekTTI/{BÖLGE}/{On_Fizibilite\|1_Kesif\|Hakedis}/` ağacında, belge gönderilir gönderilmez | Yeniden üretilir |
| `YUKLEME_KOK` | Kullanıcının **yüklediği** EK1/EK2 ve imza dosyaları | **Geri gelmez** |

İkisi de named volume değil, **doğrudan dosya sisteminde görünen klasörler** olmalıdır: uygulamaya
veya veritabanına ulaşılamadığı bir durumda belgelere buradan erişilir. Yedekleme `YUKLEME_KOK`'ü
mutlaka kapsamalıdır.

Yerel geliştirmede `./arsiv` ve `./yukleme` yeterlidir (compose dosyasına göreli).

> ⚠️ **Docker bind mount'u host klasörü silinip yeniden oluşturulunca kopar**: konteyner silinmiş
> klasöre yazmaya devam eder ve dosyalar görünmez olur. Arşiv klasörünü elle sildiyseniz
> `docker compose restart server` gerekir.

> ⚠️ **Mevcut bir kurulumu güncelliyorsanız:** `YUKLEME_KOK` Faz F'de eklendi. Sunucudaki `.env`
> daha önce kopyalandıysa bu satır orada **yoktur** ve `docker compose up` boş değişkenle düşer.
> Yeni sürüme geçmeden önce `.env`'e elle ekleyin:
>
> ```bash
> echo 'YUKLEME_KOK=/srv/veri/tti-hakedis/yukleme' >> deploy/.env
> ```

Klasörler konteynerden yazılamıyorsa (Linux'ta izin hatası):

```bash
sudo mkdir -p /srv/veri/tti-hakedis/arsiv /srv/veri/tti-hakedis/yukleme
sudo chown -R 1000:1000 /srv/veri/tti-hakedis
```

> İşletme sırasında bilinmesi gerekenler (klasör düzeni, yedekleme, sık karşılaşılanlar):
> **`docs/uretim-notlari.md`**.

## 3. Jar'ı derle ve ayağa kaldır

```bash
cd ..                       # depo kökü
./gradlew :server:bootJar   # web/ arayüzünü de derler ve jar'a gömer
cd deploy
docker compose up -d --build
docker compose ps           # ikisi de "healthy" olmalı
curl -s localhost:8081/api/saglik
# {"durum":"AYAKTA","veritabani":true,"arsivYazilabilir":true,"yuklemeYazilabilir":true}
```

Şema Flyway ile açılışta kurulur; elle SQL çalıştırmak gerekmez.

Arayüz aynı adrestedir: tarayıcıda **http://localhost:8081/** açılır ve giriş ekranı gelir.

## 3b. Arayüz geliştirme (yalnızca geliştirici makinesinde)

Her değişiklikte jar derlemeye gerek yok; Vite'in geliştirme sunucusu API'yi 8081'e vekiller:

```bash
cd web
npm install      # ilk sefer
npm run dev      # http://localhost:5173
```

Vekil sayesinde tarayıcı iki tarafı da aynı köken sayar; oturum cookie'si (`SameSite=Strict`) ve
CSRF token'ı geliştirmede de üretimdeki gibi çalışır. Arka planda `docker compose up -d` ile
sunucunun ayakta olması yeterlidir.

## 4. Kable envanterini yükle

```bash
cd ..
./gradlew :tools:kable-import:run -PDB_PAROLA=<parola>
# Farklı bir dosya için:
./gradlew :tools:kable-import:run -PDB_PAROLA=<parola> --args="tools/kable/yeni_dosya.xlsx"
```

İçe aktarım idempotenttir: aynı dosya iki kez çalıştırılabilir. Envanterden düşen kayıtlar
silinmez, `aktif = false` yapılır — silinseydi o kableye bağlı eski işlerin bağlantısı kopardı.

Çıktının başındaki `Log4j API could not find a logging provider` satırı Apache POI'den gelir,
zararsızdır.

Bir danışman "Saha" sayfasında yoksa ya da saha adı `bolge.xlsx_saha_adi` ile eşleşmiyorsa içe
aktarım **hiçbir şey yazmadan durur**. Yeni bir yazım geldiyse eşleşmeyi güncelleyin:

```sql
update hakedis.bolge set xlsx_saha_adi = 'Yeni Yazım' where kod = 'KUTAHYA';
```

## 5. Doğrulama

```bash
bash deploy/dogrula.sh          # kimlik, bölge süzgeci ve para hassasiyeti kontrolleri
source deploy/dogrula.sh        # yalnızca yardımcılar: giris / istek / durum

# Faz kontrolleri — GERÇEK kayıt oluştururlar, yalnızca geliştirme veritabanında çalıştırın
SIFIRLA=1 bash deploy/dogrula-faz-d.sh    # iş/belge, numaralandırma, aşama kuralı, iptal
SIFIRLA=1 bash deploy/dogrula-faz-e.sh    # onay akışı, işe özel fiyat listesi
SIFIRLA=1 bash deploy/dogrula-faz-f.sh    # EK1/EK2 ve imza
SIFIRLA=1 bash deploy/dogrula-faz-g.sh    # PDF/Excel üretimi ve belge aynası
SIFIRLA=1 bash deploy/dogrula-faz-h.sh    # web arayüzü: statik servis, akış, kaynak kod güvenceleri
SIFIRLA=1 bash deploy/dogrula-faz-i.sh    # yönetici: onay kuyruğu, fiyat listesi, iptal, kullanıcılar
```

`giris <kullanici> <sifre>` bir cookie kavanozu döndürür; `istek <jar> POST /api/... '<json>'`
CSRF başlığını kendisi ekler.

## 6. Günlük işler

```bash
docker compose logs -f server          # log takibi
docker compose restart server          # yeniden başlat
docker compose down                    # durdur (veri kalır)

# Yeni sürüm çıkarma
cd .. && ./gradlew :server:bootJar && cd deploy && docker compose up -d --build
```

`psql` ile bağlanırken tabloları şema adıyla nitelemek gerekir:

```bash
docker compose exec db psql -U tti_hakedis -d tti -c "select kod, ad from hakedis.bolge order by sira;"
```

## 7. Yedekleme

Yedeği **elle komut dizerek almayın** — `tti-sunucu/yedek.sh` (platform deposu) bunu yapar ve elle yazılan komutların iki
sessiz kusurunu kapatır (kabuk yönlendirmesinin bıraktığı 0 baytlık sahte dump ve `docker exec -t`'nin
ikili arşive soktuğu `\r`). Betik ayrıca her dump'ı arşivin **tamamını okuyarak** doğrular.

```bash
bash tti-sunucu/yedek.sh            # yedek al
bash tti-sunucu/yedek.sh dogrula    # son yedeği denetle, hiçbir şey yazmaz
```

Yedeklenenler: `hakedis` (+ varsa `proje2`) şeması · roller · `YUKLEME_KOK`'ün günlük anlık
görüntüsü · makineye özel compose/`.env`. **`ARSIV_KOK` yedeklenmez** — veritabanından yeniden
üretilir (bkz. `docs/uretim-notlari.md`).

**Üretim sunucusunda** hedef `/srv/veri/yedek` ve zamanlama systemd timer ile yapılır:
kurulum adımları `RedHatKurulum.md §12`'de. Geliştirme makinesinde denemek için hedefi ezin:

```bash
YEDEK_KOK=$PWD/deploy/yedek-test bash tti-sunucu/yedek.sh
```

> ⚠️ `docker compose exec` ile `docker exec` arasında ince bir fark var: compose varsayılan olarak
> TTY ayırdığı için ikili çıktı alırken **`-T` vermek zorunludur**; `docker exec`'te ise tam tersine
> **`-t` verilmemelidir**. İkisi karıştırılırsa `-Fc` dump'ı sessizce bozulur.

Geri yükleme (prova ve gerçek felaket kurtarma ayrı yordamlardır): `RedHatKurulum.md §12.8`.

## 8. İnternet erişimi olmayan makine

Görüntüleri erişimi olan bir makinede hazırlayıp taşıyın:

```bash
# Erişimi olan makinede
docker pull postgres:17
docker save postgres:17 tti-hakedis-server:dev | gzip > tti-imajlar.tar.gz

# Hedef makinede
gunzip -c tti-imajlar.tar.gz | docker load
docker compose up -d          # --build olmadan
```

## Notlar

- **Sunucu APK derlemez.** Mobil uygulama, Android Studio kurulu ayrı bir geliştirici makinesinde
  derlenip elle dağıtılır. `:app` modülü yalnızca Android SDK bulunan makinelerde build'e girer,
  bu yüzden sunucuda `./gradlew :server:bootJar` Android SDK'sı olmadan çalışır.
- **Üç servis tek veritabanını paylaşır.** Bu servis yalnızca `hakedis` şemasına yazar; diğer
  servisler kendi şemalarında kalır.
- Veritabanı portu yalnızca `127.0.0.1`'e bağlanır, dışarıya açılmaz.
