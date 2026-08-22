# NewDeploy.md — Sunucuya Yeni Bir Uygulama Ekleme

> **Bu belge sırayı anlatır:** hangi adımda, nerede, hangi komut.
> **Şartları anlatmaz:** uygulamanızın karşılaması gereken sekiz madde
> [`tti-sunucu/YENI-PROJE.md`](tti-sunucu/YENI-PROJE.md)'dedir — önce onu okuyun, sonra buraya dönün.
>
> Sunucuda hâlihazırda üç uygulama çalışıyor (`tti-hakedis`, `tti-rapor`, `tti-data`).
> Dördüncüsünü eklemek yeni bir sunucu kurmak değil, **var olan yığına bir servis bloğu
> eklemektir**.

---

## 0. Önce şunu sorun: yer var mı?

Eklemeye başlamadan önce üç bütçeye bakın. Üçü de doluysa mesele "nasıl eklerim" değil,
"ayrı sunucu mu gerekiyor" olur (bkz. YENI-PROJE.md → *Bu modelin sınırı*).

| Bütçe | Sınır | Şu an kullanılan | Kalan |
|---|---|---|---|
| **Bellek** | 6 GB (makine) | ~3.3 GB (7 konteyner) | ~2.6 GB → 2-3 .NET/Python ya da 1-2 JVM servisi |
| **DB bağlantısı** | `max_connections=50` − 3 superuser = **47** | 35 (10+10+10+5 bakım) | 12 → bir proje daha |
| **Port** | — | 80, 443, 9443 dışa · 5432, 8081-8084 loopback | sıradaki uygulama portu **8085** |

`SURUM` etiketleri, parolalar ve yollar `tti-sunucu/.env`'de; bütçe tablosu `.env.example`'da.

---

## ADIM 1 — İsimleri ve sayıları önceden seçin

Sonradan değiştirmek altı dosyaya birden dokunmak demek. Baştan karar verin ve bir yere yazın:

| Ne | Örnek | Nerede kullanılacak |
|---|---|---|
| Proje adı (kısa, tek kelime) | `envanter` | dosya adları, veritabanı, rol |
| Compose servis + konteyner adı | `tti-envanter-server` | compose, Caddyfile, yedek.sh, dogrula.sh |
| Uygulama portu (loopback) | `8085` | compose `127.0.0.1:8085:<iç port>` |
| Alt alan adı | `envanter.turktelekomint.com` | Caddyfile, DNS |
| Veritabanı adı = rol adı | `envanter` | provizyon SQL, bağlantı dizesi |
| Havuz sınırı | `10` | bağlantı dizesi + dogrula.sh |
| Bellek sınırı | `640m` (.NET) / `900m` (JVM) / `768m` (Python) | compose `mem_limit` |

> **Projeniz birden fazla imaj üretiyorsa** (örn. API + ayrı bir statik panel konteyneri —
> `tti-data` böyle): tek `context:` (depo kökü) + iki `dockerfile:` kullanın. `.dockerignore`
> context kökünde olmak zorundadır ve o konumda iki build'i birden yönetir. Veritabanı ve
> `/yukleme` bağlaması olmayan ikinci konteyner `yedek.sh` listelerine **girmez**, ama
> `dogrula.sh`'taki `EK_KAPSAYICILAR`'a girer (yoksa imaj etiketi hiç denetlenmez).

> ⚠️ **Konteyner adı = Caddy'nin ulaşacağı ad.** Caddy uygulamaya `http://<servis-adı>:<iç-port>`
> ile Docker ağı üzerinden gider, host portundan değil. İkisini karıştırmak "proxy 502 veriyor
> ama `curl localhost:8083` çalışıyor" durumunu üretir.

---

## ADIM 2 — Uygulamayı sözleşmeye uydurun

YENI-PROJE.md'deki sekiz madde. Kod tarafında yapılacaklar, en sık atlananlar önce:

- [ ] **`GET /api/saglik`** — 200/503, gövdede `durum`, `surum`, `veritabani`.
      `surum` **`SURUM` ortam değişkeninden** okunur; olmazsa çalışan sürümü sormanın yolu kalmaz.
- [ ] **Taban imajda `curl` ya da `wget` var mı?** `mcr.microsoft.com/dotnet/aspnet` imajında
      **ikisi de yok** — healthcheck sessizce hep başarısız olur, konteyner sonsuza kadar
      `unhealthy` görünür ve uygulama sorunsuz çalışır. Dockerfile'a `curl` kurun.
- [ ] **Havuz sınırı açıkça yazılı.** Npgsql varsayılanı **100**'dür ve tek başına bütçeyi yer;
      o an *diğer* projeler `sorry, too many clients` ile düşer, hata yanlış projenin log'una çıkar.
- [ ] **Ayarlar ortam değişkeninden, gömülü varsayılana düşmeden.** Düşerse üretimde yanlış
      veritabanına sessizce bağlanmak mümkün olur — açılışta durun.
- [ ] **Multi-stage Dockerfile**, son aşamada `USER 1000:1000`.
- [ ] **Migration kendi şemasına.** EF Core'da `HasDefaultSchema` **ve**
      `MigrationsHistoryTable(..., "<proje>")` — ikincisi unutulunca geçmiş tablosu `public`'e düşer.
- [ ] **Kullanıcının yüklediği dosyalar** konteynerde `/yukleme` yolunda (yedek betiği bu bağlamayı
      `docker inspect` ile okur), üretilen çıktıdan **ayrı kökte**.

---

## ADIM 3 — Yerelde prova edin

Sunucuya çıkmadan önce kendi makinenizde çalıştırın. Projenizin kendi `deploy/docker-compose.yml`
dosyası varsa onunla, yoksa yığına ekleyip yerelde kaldırın.

```bash
docker compose -p <proje> -f deploy/docker-compose.yml up -d --build
curl -s http://localhost:8083/api/saglik; echo    # surum alanı dolu mu?
docker ps --filter name=tti-envanter-server       # (healthy) yazıyor mu?
```

> ⚠️ **Compose proje adı klasör adından türer.** Bu depoda iki projenin de klasörü `deploy/`
> olduğu için ikisi de `deploy` projesi sayılıyor ve biri diğerinin konteynerini "orphan" görüp
> **değiştirebiliyor**. Yerelde çalıştırırken `-p <proje>` ile adı açıkça verin, ya da compose
> dosyasına `name: <proje>` satırı ekleyin.

---

## ADIM 4 — Veritabanını ve rolünü açın

```bash
cd /srv/kod/tti-sunucu
cp provizyon/SABLON.sql provizyon/03-envanter.sql
nano provizyon/03-envanter.sql     # <proje> -> envanter, parola -> .env ile AYNI
```

Sonra **bakım (superuser) rolüyle bir kez** çalıştırın:

```bash
sudo docker exec -i tti-db psql -U tti_hakedis -d tti -v ON_ERROR_STOP=1 \
  < provizyon/03-envanter.sql
```

> ⚠️ Bu adım **elle** yapılır. Konteynerin `initdb.d` betikleri yalnızca veritabanı **ilk kez**
> oluşturulurken çalışır; volume'de veri olduğu için hiç çalışmazlar.
>
> ⚠️ Şablondaki `revoke connect ... from public` satırını **silmeyin**. PostgreSQL yeni bir
> veritabanına bağlanma yetkisini varsayılan olarak PUBLIC'e verir; o satır olmadan makinedeki
> her rol sizin veritabanınıza bağlanabilir.

---

## ADIM 5 — Platform dosyalarına ekleyin (**altı yer**)

YENI-PROJE.md "dört yer" diyor; proxy sonradan eklendiği için liste eksik kalmış. Web arayüzü
olan bir servis için doğru sayı **altı**:

### 5.1 `docker-compose.yml` — servis bloğu

```yaml
  tti-envanter-server:
    image: tti-envanter-server:${SURUM_ENVANTER:?tanimli olmali}
    build:
      context: ../tti-envanter
      dockerfile: deploy/Dockerfile
    container_name: tti-envanter-server
    restart: unless-stopped
    mem_limit: 640m
    depends_on:
      db:
        condition: service_healthy
    environment:
      ConnectionStrings__Varsayilan: "Host=db;Port=5432;Database=envanter;Username=envanter;Password=${ENVANTER_DB_PAROLA:?tanimli olmali};Search Path=envanter;Maximum Pool Size=10"
      SURUM: ${SURUM_ENVANTER}
      TZ: Europe/Istanbul
    volumes:
      - ${ENVANTER_YUKLEME_KOK:?tanimli olmali}:/yukleme:Z   # :Z = RHEL SELinux
    ports:
      - "127.0.0.1:8083:8080"        # ⚠️ 127.0.0.1'e bağlı — dışarı AÇILMAZ
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:8080/api/saglik || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 12
      start_period: 60s
```

### 5.2 `Caddyfile` — iki blok

```
http://envanter.{$DOMAIN} {
	redir https://{host}{uri} permanent
}

https://envanter.{$DOMAIN} {
	tls /etc/caddy/certs/fullchain.pem /etc/caddy/certs/privkey.pem
	reverse_proxy tti-envanter-server:8080
}
```

> ✅ **Yeni sertifika GEREKMEZ.** Sunucudaki sertifika `*.turktelekomint.com` wildcard'ıdır;
> bir seviye alt alan adının hepsini kapsar, yani `envanter.` de kendiliğinden kapsanır.
> Sertifikanın son geçerliliği **2027-02-27** — yenileme yordamı [CERT.md](CERT.md)'de.

### 5.3 `.env` — dört satır

```
SURUM_ENVANTER=2026-08-17
ENVANTER_DB_PAROLA=<provizyon SQL ile AYNI>
ENVANTER_PORT=8083
ENVANTER_YUKLEME_KOK=/srv/veri/tti-envanter/yukleme
```

Klasörü de açın: `sudo mkdir -p /srv/veri/tti-envanter/yukleme && sudo chown -R 1000:1000 /srv/veri/tti-envanter`

### 5.4 `yedek.sh` — iki listeye birer giriş

```bash
VERITABANLARI="${VERITABANLARI:-tti:tti_hakedis rapor:rapor envanter:envanter}"
YUKLEMELER="${YUKLEMELER:-tti:tti-hakedis-server rapor:tti-rapor-server envanter:tti-envanter-server}"
```

### 5.5 `dogrula.sh` — proje listesi ve havuz toplamı

```bash
PROJELER="${PROJELER:-tti:tti-hakedis-server:8081 rapor:tti-rapor-server:8082 envanter:tti-envanter-server:8083}"
BEYAN_EDILEN_HAVUZ="${BEYAN_EDILEN_HAVUZ:-30}"    # 20 -> 30
```

### 5.6 `.env.example` — havuz bütçesi tablosuna satır ekleyin

---

> ## ⚠️ Güvenlik duvarında uygulama portunu AÇMAYIN
>
> YENI-PROJE.md'nin 8. maddesinde `firewall-cmd --add-port=<port>/tcp` yazıyor. **Bu satır
> eskidir** — proxy eklenmeden önce yazılmış. Bugün doğru olan:
>
> - Uygulama portu (8083) `127.0.0.1`'e bağlıdır ve **açılmaz**
> - Dışarı açık kapılar yalnızca proxy'nin **80** ve **443**'ü, ikisi de zaten açık
> - Yeni bir web servisi için **hiç firewall değişikliği gerekmez**
>
> Portu açmak `127.0.0.1` bağını anlamsız kılmaz ama savunmayı tek katmana indirir.

---

## ADIM 6 — IT'den tek şey isteyin: DNS A kaydı

```
envanter.turktelekomint.com   A   <sunucu-ip>
```

İsmi IP'ye çözmek **Docker'ın işi değildir**; proxy yalnızca *gelmiş* bir isteği `Host` başlığına
göre içeride yönlendirir. DNS kaydı olmadan hiçbir kullanıcı adrese ulaşamaz.

Kayıt gelene kadar test için istemcinin `hosts` dosyasına geçici satır eklenebilir — ölçeklenmez,
yalnızca doğrulama içindir.

---

## ADIM 7 — Kaynağı sunucuya taşıyın

```bash
# Mac'te — SADECE yeni projeyi paketleyin
cd ~/Desktop/tti-projeler
zip -r tti-envanter.zip tti-envanter \
  -x '*/build/*' '*/bin/*' '*/obj/*' '*/.git/*' '*/node_modules/*' '*.DS_Store' '*/.env'
shasum -a 256 tti-envanter.zip
scp tti-envanter.zip aurun@<sunucu-ip>:/tmp/
```

```bash
# Sunucuda
cd /tmp && sha256sum tti-envanter.zip     # Mac'teki hash ile aynı mı?
unzip -o tti-envanter.zip -d /srv/kod
```

> ⚠️ `unzip -o` **üzerine yazar ama silmez.** Depodan kaldırdığınız bir dosya sunucuda kalır ve
> derlemeyi düşürebilir. Dosya silen her güncellemede açılıştan sonra elle kontrol edin.
>
> ⚠️ Zip'ten `*/certs/*` ve `*/.env` **daima hariç tutulur**: özel anahtar pakette dolaşmasın ve
> `unzip -o` sunucudaki gerçek sertifikayı ezmesin.

---

## ADIM 8 — Derleyin ve başlatın

```bash
cd /srv/kod/tti-sunucu && sudo bash yedek.sh        # önce yedek

sudo docker compose build tti-envanter-server       # ⚠️ SERVİS ADIYLA
sudo docker compose up -d tti-envanter-server
sudo docker compose up -d proxy                     # Caddyfile değiştiği için proxy de yenilenir
```

> ⚠️ **Servis adını yazmayı unutmayın.** Adsız `docker compose build`, çalışan diğer projeleri de
> yeniden derler ve onların sürüm etiketlerinin altına o an depoda ne varsa yazar — geri dönüş
> imkânsızlaşır.
>
> ⚠️ Build anı **internet gerekir** (Maven/npm/NuGet). Kapalı ağda build'i dev'de koşup imajı
> `docker save` / `docker load` ile taşıyın; Dockerfile aynı kalır.

---

## ADIM 9 — Doğrulayın

```bash
# a) Migration ve hata kontrolü
sudo docker logs tti-envanter-server 2>&1 | grep -iE "Applying|migration|ERROR"

# b) Uygulama ayakta ve doğru sürümde
curl -s http://localhost:8083/api/saglik; echo

# c) Proxy üzerinden, sertifika doğrulamalı (-k YOK)
curl -sSI --resolve envanter.turktelekomint.com:443:127.0.0.1 \
  https://envanter.turktelekomint.com/api/saglik

# d) Platform sınırları — yeni proje listeye eklendiği için onu da sınar
cd /srv/kod/tti-sunucu && sudo bash dogrula.sh

# e) Yedek yeni projeyi kapsıyor mu
sudo bash yedek.sh && sudo bash yedek.sh dogrula
```

| Kontrol | Olması gereken |
|---|---|
| a | ERROR yok |
| b | `"surum":"2026-08-17"`, `veritabani` ayakta |
| c | `HTTP/2 200` |
| d | 8083 **dışarı açık değil**, proxy yönlendiriyor, havuz bütçesi tutuyor |
| e | `envanter` veritabanı ve yükleme kökü yedekte görünüyor |

Son test **başka bir bilgisayardan tarayıcıyla**: `https://envanter.turktelekomint.com` yeşil
kilitle, uyarısız açılmalı.

---

## ADIM 10 — Geri dönüş

```bash
cd /srv/kod/tti-sunucu
sudo nano .env                                   # SURUM_ENVANTER=<önceki tarih>
sudo docker compose up -d tti-envanter-server
```

Servisi tamamen kaldırmak: compose'daki bloğu ve Caddyfile bloklarını silin, `sudo docker compose
up -d --remove-orphans`, sonra konteyneri silin. **Veritabanı ve yükleme kökü kalır** — silmek
ayrı ve bilinçli bir karardır.

---

## Sık yapılan hatalar

| Belirti | Gerçek sebep |
|---|---|
| Konteyner sonsuza kadar `unhealthy`, uygulama çalışıyor | Taban imajda `curl`/`wget` yok, healthcheck hiç geçemiyor |
| Proxy 502, ama `curl localhost:8083` çalışıyor | Caddyfile'da **host portu** yazılmış; oraya **konteyner adı + iç port** gelmeli |
| Diğer projeler `sorry, too many clients` veriyor | Yeni projenin havuz sınırı yazılmamış (Npgsql varsayılanı 100) |
| Uygulama açılıyor, sağlık geçiyor, ilk dosya yazımında hata | Bind mount'ta `:Z` yok (RHEL SELinux) |
| Migration geçmiş tablosu `public`'te | EF Core'da `MigrationsHistoryTable` şema argümanı verilmemiş |
| Site hiç açılmıyor, sertifika uyarısı bile yok | DNS A kaydı yok ya da kurumsal ağda 443 kapalı — sunucuyla ilgisi yok |
| Tarayıcı "güvenli değil" diyor | Sertifika sorunu değilse: `envanter.` iki seviye alt alan adı mı? Wildcard bir seviye kapsar |
| `dogrula.sh` "veritabanına ulaşılamadı" diyor ama DB sağlıklı | Betik `sudo`suz çalıştırılmış; `aurun` docker grubunda değil |

---

## Başka bir yapay zekâya verilebilecek hazırlık prompt'u

Aşağıdaki metni, uygulamanızın kaynak koduyla birlikte bir yapay zekâya verirseniz kodu bu
platformun sözleşmesine uyarlamasını isteyebilirsiniz. Köşeli parantezleri kendi değerlerinizle
doldurun.

````text
Bir uygulamayı, halihazırda iki uygulama çalıştıran paylaşımlı bir sunucuya eklemeye
hazırlıyorum. Uygulamamı aşağıdaki platform sözleşmesine uyarlaman gerekiyor. Kodu incele,
eksikleri listele ve gereken değişiklikleri yap.

## Uygulamam
- Dil / çatı: [ör. .NET 8 Web API · Spring Boot · Node.js]
- Veritabanı: PostgreSQL kullanıyor / kullanmıyor: [...]
- Kullanıcı dosya yüklüyor mu: [evet/hayır]
- Web arayüzü var mı: [evet/hayır]

## Hedef ortam
- RHEL 9 sunucu, Docker Engine + Compose eklentisi, SELinux **enforcing**
- Tek paylaşımlı PostgreSQL 17 konteyneri; her proje KENDİ veritabanı ve KENDİ ayrıcalıksız rolü
- Tek Caddy ters vekili; dışarı açık tek kapı 443 (ve 80 -> 443 yönlendirme)
- Uygulamalar host'ta `127.0.0.1`'e bağlanır, ağdan yalnızca proxy üzerinden erişilir
- Toplam 6 GB RAM, `max_connections=50` (tüm projelerce PAYLAŞILIR)

## Uyman gereken sözleşme
1. `GET /api/saglik` ucu: sağlıklıysa 200, değilse 503. Gövde en az:
   {"durum":"ayakta","surum":"<SURUM env değişkeninden>","veritabani":"ayakta"}
   `surum` MUTLAKA ortam değişkeninden okunmalı — çalışan sürümü dışarıdan doğrulamanın
   başka yolu yok.
2. Tüm ayarlar (bağlantı dizesi, yollar, sırlar) ortam değişkeninden gelir. Gömülü
   varsayılana DÜŞÜLMEZ; değer yoksa uygulama açılışta anlaşılır bir hatayla DURMALI.
3. Veritabanı bağlantı havuzu sınırı AÇIKÇA yazılmalı (en fazla 10). Varsayılanlar
   aldatıcıdır: Npgsql 100'dür ve tek başına makinenin bütçesini tüketip DİĞER projeleri
   düşürür.
4. Migration'lar uygulama açılışında kendi şemasına uygulanır; elle SQL çalıştırılmaz.
   EF Core ise: HasDefaultSchema("<proje>") VE
   MigrationsHistoryTable("__EFMigrationsHistory", "<proje>") — ikincisi unutulursa geçmiş
   tablosu `public` şemasına düşer.
5. Çok aşamalı (multi-stage) Dockerfile yaz: build aşaması SDK ile derler, runtime aşaması
   yalnızca çalışma zamanını taşır. Son aşamada `USER 1000:1000` (kök DEĞİL).
   Taban imajda `curl` yoksa KUR — healthcheck onu çağıracak.
   (`mcr.microsoft.com/dotnet/aspnet` imajında ne curl ne wget vardır.)
6. Kullanıcının yüklediği dosyalar konteynerde `/yukleme` yolunda olmalı; uygulamanın
   ÜRETTİĞİ çıktı ayrı bir kökte. Yükleme kökü yedeklenir, üretilen çıktı yeniden üretilir.
7. Uygulama başka bir projenin veritabanına ya da `public` şemasına DOKUNMAZ.

## İstediğim çıktı
1. Kodun sözleşmeyi ihlal ettiği yerlerin listesi (dosya + satır + neden).
2. Gereken kod değişiklikleri.
3. Yukarıdaki kurallara uyan bir `deploy/Dockerfile`.
4. Paylaşımlı yığına eklenecek bir compose servis bloğu: `mem_limit`, `depends_on` ile
   veritabanı sağlık koşulu, healthcheck, `127.0.0.1:<port>:<iç port>` bağı, `:Z` etiketli
   bind mount, `${SURUM_...}` ile etiketlenmiş imaj.
5. Uygulama portunun ASLA dışarı açılmaması gerektiğini not düş.

Emin olmadığın bir şey varsa varsayım yapma, sor. Özellikle: veritabanı şeması adı, havuz
sınırı ve yükleme klasörü yolu yanlış tahmin edilirse hata vermeden bozulur.
````

---

**İlgili belgeler:** [`tti-sunucu/YENI-PROJE.md`](tti-sunucu/YENI-PROJE.md) (sözleşme) ·
[`tti-sunucu/KURULUM.md`](tti-sunucu/KURULUM.md) (platformun sıfırdan kurulumu) ·
[`ServerDeploy.md`](ServerDeploy.md) (canlı dağıtım kaydı ve geçmiş tuzaklar) ·
[`CERT.md`](CERT.md) (sertifika yenileme) ·
[`DOCKER_DOKUMAN.md`](DOCKER_DOKUMAN.md) (Docker bilmeyen ekip arkadaşları için)
