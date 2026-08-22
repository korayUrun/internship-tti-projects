# Üretim Notları

> Sistemi **işleten** kişi için: nerede ne durur, ne kendiliğinden olur, ne olmaz, bozulduğunda
> nereye bakılır. Kurulum adımları `deploy/KURULUM.md`'de; buradakiler kurulumdan **sonra** bilinmesi
> gerekenler.

---

## 1. Belgeler diskte nerede durur

Üç belge türü de **gönderildiğinde** ve fizibilite **onaylandığında** kendiliğinden diske yazılır.
Kimsenin indirmesi gerekmez.

```
{ARSIV_KOK}/YedekTTI/
    IST-AVR/
        On_Fizibilite/   TTI_IST-AVR_2026_FIZIBILITE_001_FIYATLI.pdf  (+ .xlsx)
        1_Kesif/         TTI_IST-AVR_2026_KESIF_001_FIYATLI.pdf       (+ .xlsx)
        Hakedis/         TTI_IST-AVR_2026_HAKEDIS_001_FIYATLI.pdf     (+ .xlsx)
    BURSA/
        …
```

- **Yıl klasörü yok**: yıl zaten dosya adında (`…_2026_…`), ayrı klasör bir tıklama derinliği daha
  ekliyordu.
- **Klasör adları ASCII ve boşluksuz** (`On_Fizibilite`, `1_Kesif`). Türkçe karakter ve boşluk;
  Docker bind mount'undan, yedekleme betiklerinden ve Windows'tan geçerken kodlama/tırnak hatası
  üretiyor.
- **Otomatik yazılan yalnızca FİYATLI kopyadır.** Fiyatsız `TEKLIF` varyantı (aracı firmaya giden)
  arayüzden elle indirilir; her belgenin iki katı dosyayı arşivde tutmanın karşılığı yok.
- **Dosya adı kararlıdır** (zaman damgası içermez): aynı belge yeniden üretildiğinde yeni dosya değil,
  aynı dosyanın güncel hâli oluşur. Arşiv çoğalmaz.

**Nerede olduğunu görmek için:** sunucu açılışta log'a yazar → `docker compose logs server | grep "Arşiv kökü"`

### Bu klasör yedeklenmeli mi?

Hayır — **yeniden üretilebilir**. Doğruluğun kaynağı veritabanıdır; arşiv onun tek yönlü aynasıdır.
Yedeklenmesi gereken iki şey var:

| Ne | Nerede | Kaybı |
|---|---|---|
| Veritabanı | `deploy/yedek.sh`, her gece 23:30 | **Geri gelmez** |
| `{YUKLEME_KOK}` — EK1/EK2 | aynı betik, günlük anlık görüntü | **Geri gelmez** |
| `{ARSIV_KOK}` — üretilen PDF/Excel | belge aynası, **yedeklenmez** | Yeniden üretilir |

Arşivin yedeklenmemesi bilinçli: dosya adı kararlı olduğu için yeniden üretim **aynı** dosyayı
yazar, dolayısıyla arşiv veritabanından her zaman geri getirilebilir.

⚠️ İki kök **ayrı tutulmalıdır**. Aynı ağaca konsalardı, arşivi temizleyen bir işlem kullanıcının
yüklediği güzergâh dosyalarını ve imzaları da silerdi.

### Arşiv yazılamazsa ne olur

Hiçbir şey durmaz: belge üretimi ve gönderim çalışmaya devam eder, kopyalama iki kez denenir ve
`WARN` düşer. Kontrol:

```bash
docker compose logs server | grep -E "Arşiv kopyası|Otomatik arşivleme"
```

Belge veritabanında durduğu ve dosya adı kararlı olduğu için, sonraki üretim (indirme ya da yeniden
gönderim) eksik dosyayı aynı yola yazar.

---

## 2. Klasör yolları ve işletim sistemi

Yollar `deploy/.env` içindeki `ARSIV_KOK` ve `YUKLEME_KOK` ile belirlenir. Elle doldurmak yerine:

```bash
bash deploy/kur.sh                                    # macOS / Linux
powershell -ExecutionPolicy Bypass -File deploy\kur.ps1   # Windows
```

| Ortam | Varsayılan |
|---|---|
| macOS | `~/TTI-Hakedis/{arsiv,yukleme}` |
| Windows | `%USERPROFILE%\TTI-Hakedis\{arsiv,yukleme}` — compose'a **ileri eğik çizgiyle** yazılır |
| Linux | `/srv/tti-hakedis/{arsiv,yukleme}`, yazma yetkisi yoksa ana dizin |

⚠️ **Betik dolu bir değeri değiştirmez.** Çalışan bir kurulumun arşivini sessizce başka klasöre
taşımak, eski dosyaları kimsenin bakmadığı bir yerde bırakırdı. Yol değiştirmek isteyen `.env`'i elle
düzenler **ve eski klasörün içeriğini kendisi taşır**.

⚠️ **`./arsiv` gibi göreli bir yol `deploy/` klasörüne göre çözülür**, ana dizine değil.
`.env.example`'dan gelen geliştirme varsayılanı budur; belgeleri ana dizinde arayıp bulamamanın en sık
nedeni. Nereye yazıldığını iki yerden doğrulayabilirsiniz:

```bash
docker inspect tti-server --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}'
docker compose logs server | grep "Arşiv kökü"
```

Yolu değiştirirken **eski klasörün içeriğini yeni yere taşımak da gerekir** — `.env`'i düzeltmek
yalnızca bundan sonrasını yönlendirir, geçmiş dosyalar eski yerde kalır. Ardından konteyner yeniden
oluşturulmalı (`docker compose up -d server`); mount kaynağı değiştiği için `restart` yetmez.

⚠️ **Docker bind mount'u host klasörü silinip yeniden oluşturulunca kopar.** Arşiv klasörünü elle
silerseniz konteyner eski (silinmiş) klasöre yazmaya devam eder ve dosyalar görünmez olur. Çözüm:
`docker compose restart server`.

Jar Docker'sız çalıştırılırsa değişkenler verilmediğinde aynı varsayılanlara düşülür
(`VarsayilanKok`), klasörler açılışta oluşturulur.

---

## 3. Oturum, kimlik ve ağ

- Oturum **sunucu tarafında**, çerezle; JWT yok. Süre **8 saat** (`application.yml`).
- CSRF **giriş ucu dahil** her `POST`/`PUT`/`DELETE`'te açık. Token `XSRF-TOKEN` çerezinden okunur,
  `X-XSRF-TOKEN` başlığıyla geri gönderilir. Yeni bir istemci yazan biri, **yazan ilk istekten önce**
  kimlik istemeyen bir uca (`/api/saglik`) `GET` yapıp çerezi almalıdır; yoksa 403 alır.
- Yetki süzgeci **SQL'de**. Bölge sorumlusu başka bölgenin kaydını istediğinde 403 değil **404**
  görür — varlığını da sızdırmamak için.
- Parolalar yalnızca BCrypt özeti olarak durur; düz metin hiçbir yerde yok, **geri döndürülemez**.
  Unutulan parola sıfırlanır (Yönetici → Kullanıcılar), okunamaz.
- Sunucu şu an **düz HTTP** üzerinden, kurumsal VPN içinde çalışıyor. TLS Faz K'de.

---

## 4. Mobil uygulama

- APK sunucuda derlenmez; Android Studio kurulu bir makinede derlenip **elle** dağıtılır.
- Release APK'da **R8 küçültme açık**. `kotlinx.serialization` ve OkHttp sınıflara isimle ulaştığı
  için keep kuralları `app/proguard-rules.pro`'da; kural eksikse hata derlemede değil **cihazda ilk
  API çağrısında** çıkar. Bu yüzden elle test **release APK** ile yapılır.
- Sunucu adresi uygulamanın giriş ekranından ayarlanır ve cihazda saklanır — sunucunun IP'si
  değişince yeni APK dağıtmak gerekmez.
- **Parola cihazda saklanmaz.** Yalnızca son kullanıcı adı ve sunucu adresi hatırlanır; uygulama
  kapanınca oturum düşer.
- Ekranlar veriyi **açılışta ve ekrana her dönüşte** çeker; listede aşağı çekerek, belge ekranında
  "Yenile" ile elle tazelenir. **Otomatik yoklama yok**: yönetici webden onayladığında tablet
  kendiliğinden güncellenmez, kullanıcının yenilemesi gerekir.

---

## 5. Sık karşılaşılanlar

| Belirti | Sebep / bakılacak yer |
|---|---|
| Arşiv klasörü boş | Konteyner yeniden başlatılmalı (bind mount kopmuş olabilir) · `docker compose logs server \| grep Arşiv` |
| "Bu bölgede … sırası SP-… işinde" | Bölge sıra kuralı (D3/b). Sıradaki işin o türdeki belgesi tamamlanmalı; tıkandıysa yönetici **işi** iptal eder |
| Fizibilite onaylanamıyor | Fiyat listesi önizlemesi geçmeden onay açılmaz; aynı işe **ikinci** liste yüklenemez (409) |
| Tabletten giriş 403 | CSRF akışı — bkz. §3. Sunucu adresi yanlışsa "Sunucuya ulaşılamadı" gelir, 403 değil |
| Web'de eski veri | Sekmeye dönünce kendiliğinden tazelenir; aynı sekmeye tekrar basmak da yeniler |
| Betik "düştü" diyor ama sistem çalışıyor | `SIFIRLA=1` **tüm bölgelerin** iş/belge kayıtlarını siler; üretimde asla çalıştırılmaz |

⚠️ **`deploy/dogrula-faz-*.sh` betikleri üretim veritabanında çalıştırılmaz.** Gerçek iş, belge, ek ve
imza kaydı oluştururlar; `SIFIRLA=1` ise mevcut kayıtları siler.

⚠️ **Yayımlanmış bir Flyway migration'ı düzenlenmez** — checksum tutmaz ve sunucu açılmaz. Şema
değişikliği yeni `V` numarasıyla eklenir.

⚠️ **Geliştirme verisi temizlenirken `TRUNCATE … CASCADE` kullanılmaz**: cascade zinciri
`is_kaydi → fiyat_versiyonu → poz_fiyat` üzerinden **referans fiyat listesini de siler**. Silme sırası
elle yazılır (bkz. doğrulama betiklerinin sıfırlama bloğu).

---

## 6. Yedekleme — işletmecinin bilmesi gerekenler

Her gece 23:30'da `deploy/yedek.sh` koşar (systemd timer; kurulumu `RedHatKurulum.md §12`).
Yedekler `/srv/tti-hakedis/yedek` altında: `db/` · `yukleme/<tarih>/` · `roller/` · `sunucu-ayarlari/`.

**Günlük tek kontrol:**

```bash
sudo bash /srv/tti/deploy/yedek.sh dogrula
```

Bu komut dump'ları tam okuyarak doğrular ve **veritabanındaki her ek dosyasının anlık görüntüde
gerçekten bulunduğunu** çapraz kontrol eder. Daha hızlısı:
`head -1 /srv/tti-hakedis/yedek/SON-DURUM.txt` — **tarihi dünden eskiyse yedeğiniz yok demektir.**

**En sık gerekecek kurtarma — kullanıcı bir eki yanlışlıkla sildi.** Ek silindiğinde dosya diskten
de gider; dünkü anlık görüntüden geri gelir:

```bash
ls /srv/tti-hakedis/yedek/yukleme/                 # hangi günler duruyor
cp /srv/tti-hakedis/yedek/yukleme/<dün>/<BÖLGE>/<yıl>/<BELGE_NO>/<dosya> \
   /srv/tti-hakedis/yukleme/<BÖLGE>/<yıl>/<BELGE_NO>/
```

> Veritabanındaki `ek` satırı da silinmişse dosyayı geri koymak yetmez — belge onu listelemez.
> O durumda `RedHatKurulum.md §12.8b`'deki prova geri yüklemesiyle satırı bulup elle eklemek gerekir.

**Yedek başarısız olursa:** `journalctl -t tti-yedek -n 50`. Çıkış kodlarının anlamı §12.4'te.
En ağır olanı **5** — yükleme anlık görüntüsü alınamamış, yani asıl veri o gece yedeklenmemiş.

⚠️ Yedekler **aynı diskte**. Yanlış silme ve bozulmaya karşı korur, disk arızasına karşı korumaz;
haftalık dış kopya adımı §12.9'da.
