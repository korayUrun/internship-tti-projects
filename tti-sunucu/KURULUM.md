# Paylaşılan Yığın — Kurulum

Red Hat (RHEL 8/9) sunucusunda, Docker Engine ile. Bu döküman **platformun** otoritesidir:
motor, veritabanı, ortak compose, provizyon, güvenlik duvarı, yedekleme, doğrulama.

Projelere özgü adımlar (jar/publish üretimi, arşiv klasörleri, kable içe aktarımı) kendi
depolarındadır:
- `../tti-hakedis/deploy/RedHatKurulum.md`
- `../tti-rapor/deploy/KURULUM.md`

---

## 0. Ön kontrol

```bash
cat /etc/os-release | head -3    # RHEL 8 mi 9 mu
free -h                          # en az 6 GB
df -h; sudo vgs                  # motorun veri klasörü için ≥25 GB — /var genellikle DAR
getenforce                       # büyük olasılıkla Enforcing — normaldir, `:Z` bunu ele alıyor
docker info | grep -i 'Cgroup Version'   # 2 olmalı; 1 ise mem_limit SESSİZCE yok sayılır
```

**.NET projeleri için:** `grep -i TargetFramework *.csproj` → `net6.0`/`net8.0`/`net9.0` olmalı.
`v4.x` (.NET Framework) yalnızca Windows'ta koşar ve RHEL'e taşınamaz.

Docker Engine kurulumu ve `/var` dar ise `data-root` taşıması: `../tti-hakedis/deploy/RedHatKurulum.md` §2.

---

## 1. Klasörler ve `.env`

```bash
sudo mkdir -p /srv/kod/tti-sunucu \
              /srv/veri/tti-hakedis/{arsiv,yukleme} \
              /srv/veri/tti-rapor/yukleme \
              /srv/veri/yedek
sudo chown -R 1000:1000 /srv/veri

# bu depoyu /srv/kod/tti-sunucu'ya açın, sonra:
cd /srv/kod/tti-sunucu
cp .env.example .env && chmod 600 .env
sudo nano .env          # PAROLALAR, SURUM ve DOMAIN değerlerini doldurun
```

⚠️ `.env`'deki `degistir-beni` parolalarını **mutlaka** değiştirin.
`DOMAIN` değerini kurumunuzun intranet alan adına göre ayarlayın (ör. `tti.local`, `sirket.local`);
kullanıcılar `hakedis.<DOMAIN>` / `rapor.<DOMAIN>` yazacak (6. adım).

---

## 2. Veritabanı volume'ü

```bash
docker volume create tti_db_verisi
```

> ⚠️ Volume compose'da **external** tanımlıdır. İki nedeni var: `docker compose down -v`
> veritabanını silemez, ve compose proje adı/klasörü değiştiğinde Docker yeni ve **boş** bir
> volume yaratıp veriyi "kaybolmuş" göstermez.
>
> Var olan bir kurulumdan geçiyorsanız `.env`'de `DB_VOLUME` ile **eski adı** verin
> (`docker volume ls` ile bulunur), yeni bir tane oluşturmayın.

---

## 3. Yığını başlatın

```bash
cd /srv/kod/tti-sunucu
docker compose up -d db
docker compose ps        # db healthy olmalı
```

---

## 4. Projelerin veritabanlarını açın

⚠️ **Bu adım elle yapılır.** Konteynerin `initdb.d` betikleri yalnızca veritabanı **ilk kez**
oluşturulurken çalışır; volume'de veri varsa hiç çalışmazlar.

```bash
docker exec -i tti-db psql -U tti_hakedis -d tti -v ON_ERROR_STOP=1 < provizyon/01-tti.sql
docker exec -i tti-db psql -U tti_hakedis -d tti -v ON_ERROR_STOP=1 < provizyon/02-rapor.sql
```

⚠️ **Şemaları elle AÇMAYIN.** TTI'nin `hakedis` şemasını Flyway, TTIDataRapor'un `rapor` şemasını
EF Core'un ilk migration'ı kendisi açar. Elle açılan şema sürüm tablosuyla çakışabilir.

---

## 5. İmajları üretin ve servisleri başlatın

İmajlar **kaynaktan, burada** derlenir — Dockerfile'lar çok aşamalıdır (multi-stage): build aşaması
derleyiciyi taşır (TTI: JDK 17 + Node 22 · Rapor: .NET SDK) ve derler, runtime aşaması yalnızca
çalışma zamanını. Ne dev'de ne sunucuda SDK kurmak gerekir; taşınan şey **kaynak**tır (§3).

```bash
docker compose build     # iki app imajını kaynaktan derler, .env'deki SURUM ile etiketler
docker compose up -d
docker compose ps        # db + iki app healthy; ayrıca proxy ve portainer up (6. adım)
```

> ⚠️ **Build anı internet gerekir.** build aşaması Maven/Gradle/npm (TTI) ve NuGet (Rapor)
> bağımlılıklarını çeker. Sunucunun eriştiği §3'te `curl` ile doğrulanır. Kapalı ağdaysa: build'i
> **dev'de** koşup imajı `docker save`/`load` ile taşıyın — Dockerfile aynı kalır, yalnızca build
> yeri değişir.

> **Sürüm etiketi `dev` OLMAZ.** İmaj `.env`'deki `SURUM` ile etiketlenir (`YYYY-AA-GG`). Yeni
> sürüm çıkarmak: `SURUM`'u yeni tarihe çevir → `docker compose build` → `up -d`. `dev`/`latest`
> hareketli işaretçidir, üstüne yazılır ve geri dönüşü imkânsız kılar; **kullanılmaz**.

---

## 6. Ters vekil (domain adları) ve Portainer

`docker compose up -d` (5. adım) veritabanı ve iki uygulamanın yanında **iki platform servisini
daha** başlatır: `tti-proxy` (Caddy) ve `tti-portainer`. İkisi de hazır imajdır — üretmek ya da
etiketlemek gerekmez.

### 6.1 Ters vekil — kullanıcı IP:port değil domain yazar

`proxy` servisi port 80'i dinler ve gelen `Host` başlığına göre yönlendirir (`Caddyfile`):

```
http://hakedis.<DOMAIN>  →  tti-hakedis-server
http://rapor.<DOMAIN>    →  tti-rapor-server
```

`<DOMAIN>` `.env`'deki değerden gelir. App portları artık `127.0.0.1`'e bağlıdır; ağdan tek erişim
proxy üzerindendir (bu yüzden güvenlik duvarında 8081/8082 **açılmaz**, yalnızca 80 — 8. adım).

> ⚠️ **İsmi IP'ye çözmek Docker'ın işi DEĞİL.** `hakedis.<DOMAIN>` adının sunucunun IP'sine gitmesi
> **kurumsal DNS**'in işidir. IT'den şu iki A kaydını isteyin (Docker'ı ya da bu depoyu görmelerine
> gerek yok — 30 saniyelik, projeden bağımsız bir iş):
> ```
> hakedis.<DOMAIN>   A   <sunucu-ip>
> rapor.<DOMAIN>     A   <sunucu-ip>
> ```
> DNS yoksa geçici çözüm: her istemcinin hosts dosyasına aynı iki satır — ölçeklenmez.

**HTTPS aktiftir** (2026-08-13). `Caddyfile` `https://` bloklarıyla kurumsal CA sertifikasını
(`*.turktelekomint.com` wildcard) `certs/` altından `tls` ile sunar; 80 → 443'e yönlendirir.
`auto_https off` **kalır** (manuel sertifika; Caddy ACME denemesin). Ayrıntı ve yenileme (son
2027-02-27): `ServerDeploy.md` §14. Sertifika/anahtar yalnızca sunucuda, git/zip dışında (`certs/`).

### 6.2 Portainer — IT'nin Docker yönetim arayüzü

Teslimden sonra IT, compose dosyası okumadan konteynerleri **web'den** görür, yeniden başlatır,
log'lara bakar. Ayar/sürüm değişikliği yine geliştiricidedir; IT'nin rolü gözlem ve temel
operasyondur.

> ⚠️ **Portainer Docker soketini bağlar = host'ta KÖK yetkisine denk güç.** Erişimi bu yüzden ayrı
> porta (9443) ve güvenlik duvarında **yalnızca IT subnet'ine** kısıtlanır (8. adım).

**İlk açılış:** IT ilk kez `https://<sunucu-ip>:9443` adresine girdiğinde (self-signed sertifika
uyarısı normaldir) **admin parolasını birkaç dakika içinde** koymalıdır; yoksa Portainer güvenlik
gereği kilitlenir ve `docker restart tti-portainer` ile sıfırlamak gerekir.

**⚠️ SELinux + docker.sock (RHEL Enforcing).** Soketi bağlamak Enforcing modda engellenebilir;
belirti Portainer arayüzünde **konteyner listesinin boş gelmesi ya da erişim hatası**dır.

```bash
docker compose up -d portainer
sudo ausearch -m avc -ts recent      # engelleniyorsa AVC denial görünür
```

Engelleniyorsa `docker-compose.yml`'deki `portainer` servisinde şu satırı açın, sonra
`docker compose up -d portainer`:

```yaml
    security_opt: ["label:disable"]   # yalnızca bu konteynerin SELinux etiket sınırını kaldırır
```

`setenforce 0` **DEĞİLDİR** (kurumsal politikayı ihlal etmez, reboot'ta kalıcıdır) ve yalnızca
Portainer konteynerini etkiler. Portainer zaten kök-eşdeğeri olduğundan marjinal risk düşüktür.

---

## 7. SELinux

RHEL'de SELinux `Enforcing`'dir; konteyner bind mount'a **etiketlenmeden yazamaz**. Compose
dosyasındaki `:Z` soneki bunu çözer.

Bu neden önemli: etiket olmadan uygulama **açılır**, sağlık kontrolü **geçer**, hata ancak ilk
dosya yazımında (TTI'de PDF üretimi, Rapor'da fotoğraf yükleme) çıkar — belirti nedene benzemez.

Teşhis: `sudo ausearch -m avc -ts recent`

⚠️ `setenforce 0` çözüm değildir: kurumsal politikayı ihlal eder ve yeniden başlatmada geri gelir.

---

## 8. Güvenlik duvarı

Dışarıya açık kapılar **proxy'nin 443 (HTTPS)** ve **80 (→443 yönlendirme)** portlarıdır. 8081/8082
uygulama portları `127.0.0.1`'e bağlı olduğundan **açılmaz**; proxy onlara içeriden erişir.

```bash
sudo firewall-cmd --permanent --add-service=https   # port 443 → proxy (TLS; hakedis./rapor.<DOMAIN>)
sudo firewall-cmd --permanent --add-service=http    # port 80  → 443'e yönlendirme (kullanıcı http yazarsa)
sudo firewall-cmd --reload
sudo firewall-cmd --list-all
```

> ⚠️ Sunucu firewall'ında 443 açık olması yetmez: kullanıcı ile sunucu arasındaki **kurumsal ağ** da
> 443'ü geçirmeli (IT, port 80 için yaptığının aynısını 443 için de yapmalı). Erişilemiyorsa http→https
> yönlendirmesi ölü uca düşer; geçici çözüm §14 "geri dönüş / B modu".

**Portainer (9443) yalnızca IT subnet'ine açılır** — dünyaya değil. IT'nin ağ bloğunu (`<IT_SUBNET>`)
onlardan alın:

```bash
sudo firewall-cmd --permanent --add-rich-rule='rule family="ipv4" \
  source address="<IT_SUBNET>/24" port port="9443" protocol="tcp" accept'
sudo firewall-cmd --reload
```

**8081, 8082 ve 5432'yi AÇMAYIN.** İlk ikisine proxy içeriden erişir; veritabanı zaten `127.0.0.1`'e
bağlı. Bir port açmak `127.0.0.1` bağını anlamsız kılmaz ama savunmayı tek katmana indirir.

---

## 9. Açılışta kendiliğinden kalkma

```bash
sudo systemctl enable --now docker
systemctl is-enabled docker      # "enabled"
```

`restart: unless-stopped` gerisini halleder. **Kurulum bittikten sonra `sudo reboot` ile test
edin** — demo sabahı öğrenmeyin.

---

## 10. Yedekleme

```bash
bash yedek.sh            # elle bir kez
bash yedek.sh dogrula    # denetle (salt okunur)
```

systemd timer ile günlük çalıştırın (`Persistent=true` — kapalı kalınan günü telafi eder).

Yedeklenen: her projenin veritabanı, roller, her projenin yükleme kökü, `/srv/kod/tti-sunucu`
ayarları (compose + `Caddyfile` + `.env`). Yedeklenmeyen: TTI'nin arşiv klasörü, proxy'nin
(`tti_caddy_verisi`) ve Portainer'ın (`tti_portainer_verisi`) volume'leri — hepsi yeniden
üretilebilir. Portainer volume'ü kaybolursa yalnızca Portainer yeniden kurulur (admin parolası
tekrar konur); uygulama verisi orada değildir.

> ⚠️ **Yedekler makine dışına da kopyalanmalı.** Hepsi aynı diskte durduğu sürece tek bir disk
> arızası her şeyi götürür.
>
> ⚠️ **Geri yükleme provası yapın.** Test edilmemiş yedek, yedek değildir.

---

## 11. Doğrulama

```bash
bash dogrula.sh                             # platform sınırları (yeni §6: proxy + port yüzeyi)
curl -s http://localhost:8081/api/saglik    # TTI    — surum alanı .env ile aynı olmalı (localhost bağı)
curl -s http://localhost:8082/api/saglik    # Rapor
curl -sSI --resolve hakedis.<DOMAIN>:443:127.0.0.1 https://hakedis.<DOMAIN>/api/saglik   # proxy HTTPS (200, geçerli sertifika)
```

`ss -tlnp` çıktısında **80**, **443** ve **9443** dinliyor, **8081/8082 yalnızca `127.0.0.1`**'de olmalı.

Başka bir bilgisayardan — asıl test budur (URL'de **port yok**):

```
https://hakedis.<DOMAIN>    → TTI      (yeşil kilit, uyarısız)
https://rapor.<DOMAIN>      → TTIDataRapor
http://hakedis.<DOMAIN>     → 443'e yönlenir (301)
```

Açılmıyorsa: önce isim çözülüyor mu bakın (`ping hakedis.<DOMAIN>` sunucunun IP'sini vermeli) —
vermiyorsa DNS A kaydı eksik (IT). Bağlanamıyorsa (sertifika uyarısı değil, hiç açılmıyorsa)
kurumsal ağda **443 kapalı** olabilir — IT'ye 80 gibi 443'ü de açtırın (§8, ServerDeploy §14).

**Portainer:** IT subnet'inden `https://<sunucu-ip>:9443` açılır (self-signed uyarısı kabul edilir);
IT subnet'i **dışından** bağlantı reddedilmeli. Arayüzde konteynerler listelenmeli (boşsa 6.2'deki
SELinux notu).

---

## 12. Yeni proje ekleme

`YENI-PROJE.md` — katılım sözleşmesi. Sekiz madde, hepsi atlandığında sessizce bozulan bir şeyi
kapatıyor.
