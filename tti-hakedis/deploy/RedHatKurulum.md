# Red Hat Sunucu Kurulumu — İki Proje, Konteynerle

Kurumsal bir Red Hat (RHEL 8/9) sunucusunda TTI Saha Hakediş ve ikinci backend'in konteynerle
ayağa kaldırılması. Motor **Docker Engine (docker-ce)**; Apache 2.0 lisanslıdır ve lisans sorusu
doğurmaz. **Docker Desktop kurulmuyor** — ödemeli olan odur ve zaten bir masaüstü uygulamasıdır.

> ⚠️ **Paylaşılan yığının (PostgreSQL, ortak compose, provizyon, yedekleme) otoritesi artık
> `tti-sunucu/KURULUM.md` dosyasıdır.** Ortak compose ve `yedek.sh` hiçbir projeye ait olmadığı
> için ayrı bir platform deposuna taşındı; bu rehberdeki ilgili bölümler oradaki hâliyle
> güncellenir. Burada TTI'ye özgü adımlar (jar üretimi, arşiv/yükleme kökleri, kable içe
> aktarımı) kalıyor.
>
> Hangi rehber?
> - **Bu dosya** — Red Hat sunucu, konteynerle
> - `WindowsDeploy.md` — Windows makine, WSL2 + Docker Engine ile
> - `WindowsDockersiz.md` — Windows makine, konteynersiz (her şey doğrudan kurulu)
> - `KURULUM.md` — yalnızca TTI'nin kendi ayarları (`.env`, arşiv klasörleri, kable içe aktarımı)

---

## 0. Ne kuracağız

Beş konteyner (son ikisi paylaşılan yığından — `tti-sunucu`):

| Konteyner | Ne işe yarar | Port | Ağdan erişim |
|---|---|---|---|
| `tti-db` | PostgreSQL 17 — **iki proje de bunu paylaşır**, ayrı veritabanlarında | 5432 | ❌ sadece sunucunun kendisi |
| `tti-hakedis-server` | TTI backend'i **+ web arayüzü** (jar'ın içinde gömülü) | 8081 (127.0.0.1) | ❌ yalnızca proxy üzerinden |
| `tti-rapor-server` | TTIDataRapor backend'i **+ arayüzü** (.NET 8, `wwwroot`'tan servis edilir) | 8082 (127.0.0.1) | ❌ yalnızca proxy üzerinden |
| `tti-proxy` | Ters vekil (Caddy) — domain adını doğru konteynere yönlendirir | **80** | ✅ kullanıcılar (domain adıyla) |
| `tti-portainer` | IT'nin Docker yönetim arayüzü | 9443 | ✅ **yalnızca IT subnet** |

> ℹ️ **TTI için ayrı bir web konteyneri yoktur.** `web/` klasörü derlenip jar'ın içine gömülür ve
> 8081'den servis edilir. İkinci proje .NET olduğu için arayüzünü de kendisi servis eder; ayrı bir
> statik-dosya sunucusu gerekmez. `tti-proxy` bir web sunucusu değil, yalnızca **yönlendiricidir**:
> kullanıcı IP:port yerine `hakedis.<DOMAIN>` yazsın diye vardır (paylaşılan yığın, `tti-sunucu`).

**Sunucu gereksinimi:** 6 GB RAM, 100 GB disk. Yığın boşta ~2,5 GB kullanır.

**Nereden ne geliyor:**

| | Kaynak |
|---|---|
| Taban imajlar (`postgres:17`, `temurin:17-jre/jdk`, `dotnet/sdk+aspnet`, `caddy`, `portainer-ce`) | Sunucu **kendisi internetten indirir** |
| Build bağımlılıkları (Maven/Gradle/npm/NuGet) | Sunucu **build anında internetten çeker** (§3a doğrular) |
| **Üç kaynak depo** (tti-hakedis, tti-rapor, tti-sunucu) | **Elle taşınır** (zip / WhatsApp / USB) — imaj sunucuda kaynaktan derlenir |

---

## 1. ⛔ Ön kontrol — bu adım geçilmeden devam edilmez

### 1a. İkinci proje Linux'ta çalışıyor mu?

**.NET Framework Linux'ta çalışmaz.** Bunu kuruluma başlamadan önce öğrenmek zorundasınız, yoksa
yarım günü boşa harcarsınız. İkinci projenin `.csproj` dosyasını açın:

```xml
<TargetFramework>net8.0</TargetFramework>                <!-- ✅ .NET Core ailesi — devam edin -->
<TargetFrameworkVersion>v4.8</TargetFrameworkVersion>    <!-- ⛔ .NET Framework — aşağıyı okuyun -->
```

Komut satırından da bakılabilir:

```bash
grep -i "TargetFramework" *.csproj
```

| Çıkan değer | Anlamı |
|---|---|
| `net6.0`, `net8.0`, `net9.0` | ✅ Linux konteynerinde çalışır. Bu rehberin tamamı uygulanabilir. |
| `v4.5` … `v4.8` (ya da `net48`) | ⛔ **Yalnızca Windows'ta çalışır.** |

**`v4.x` çıktıysa ne olur:** .NET Framework yalnızca Windows'ta koşar. Windows konteyneri ise
Windows Server host ister; Red Hat bunu sağlayamaz — ara katman, sanal makine ya da uyumluluk
paketi de bu durumu değiştirmez. İki seçeneğiniz var:

1. **Karma kurulum:** Bu rehberdeki her şeyi uygulayın ama `tti-rapor-server` servisini compose'dan
   çıkarın. RHEL'de TTI + PostgreSQL koşar; ikinci proje Windows'ta kalır ve TTI'nin
   veritabanına ağ üzerinden bağlanır. (Bu durumda 6. adımda 5432 portunu yalnızca o Windows
   makinesinin IP'sine açın, dünyaya değil.)
2. **Tek makinede toplayın:** `WindowsDockersiz.md` rehberine geçin — orada iki proje de çalışır.

### 1b. Sunucu hazır mı?

```bash
cat /etc/os-release | head -3    # RHEL 8 mi 9 mu — 4. adımda lazım
free -h                          # en az 6 GB
getenforce                       # büyük olasılıkla "Enforcing" — normaldir, 7. adım bunu ele alıyor
```

### 1c. ⚠️ Disk **nerede** boş — toplam boş alana bakmak yetmez

Docker imajları, konteynerleri ve veritabanı verisini `/var/lib/docker` altına yazar. Kurumsal RHEL
kurulumları sertleştirme gereği diski **ayrı bölmelere** böler ve `/var` genellikle küçük tutulur —
`df -h /` bol alan gösterirken motorun yazacağı yerde 2–3 GB kalmış olabilir.

```bash
lsblk                            # bölme düzeni
df -h                            # her bölmede ne kadar boş
sudo vgs                         # VFree: LVM'de dağıtılmamış boş alan var mı
```

Gereken: motorun veri klasörü için **en az 25 GB**. `/var` küçükse iki yol var:

- **`VFree` > 0 ise** bölmeyi büyüt: `sudo lvextend -L +25G -r /dev/<VG>/varLV` (canlıda çalışır).
- **`VFree` 0 ise** (example-host'de böyleydi) motoru boş alana yönlendir — 2. adımın sonundaki
  `data-root` bölümü. Root bölmesini küçültmek **XFS'te mümkün değildir**, o yolu denemeyin.

> 💡 `/var` dolarsa yalnızca konteynerler durmaz; sistem log'ları ve PostgreSQL de yazamaz hâle
> gelir. Bu kontrolü kurulumdan sonra yapmak, üç ay sonra bozulan bir sunucu demektir.

---

## 2. Konteyner motoru — Docker Engine (docker-ce)

Apache 2.0 lisanslıdır, ücretsizdir ve kısıtsızdır — ödemeli olan Docker **Desktop**'tır, o bir
masaüstü uygulamasıdır ve sunucuda zaten çalışmaz. Tek kaydı: Red Hat destek kapsamına girmez.

> **TTI'nin kararı (9 Ağustos 2026).** Gerekçe: geliştirme ortamıyla birebir aynı compose dosyası,
> açılışta kalkmanın tek satır olması (`systemctl enable --now docker`) ve Docker'ın tek somut
> güvenlik itirazının (firewalld'yi atlaması) bu topolojide etkisiz kalması — yığın yalnızca
> 8081'i yayımlıyor, veritabanı `127.0.0.1`'e bağlı. podman değerlendirilip **elendi**: asıl
> üstünlüğü Red Hat desteğiydi, IT ekibinin böyle bir destek ilişkisi olmadığı için devre dışı
> kaldı. Bu rehberdeki adımlar RHEL 9.8'de (`example-host`) uygulanmış ve doğrulanmıştır.
>
> Kararı değiştirecek tek koşul: güvenlik tarafının "kök yetkili üçüncü parti servis istemiyoruz"
> demesi. O durumda podman'a geçiş yarım günlük iştir — compose dosyası aynen çalışır, tek fark
> `podman generate systemd` ile bir systemd birimi üretmektir.

**Sıra önemlidir:** servis, `daemon.json` yazılmadan başlatılırsa veri klasörünü `/var` altında
oluşturur ve sonradan taşımak gerekir.

```bash
# 1) Çakışan paketleri kaldır. `runc` kurulu olmayabilir — "No match" normaldir.
#    ⚠️ podman'ı BURADA kaldırıyoruz çünkü docker-ce ile çakışır; bu adım atlanırsa kurulum bozulur.
sudo dnf remove --assumeno podman buildah runc     # önce NE gideceğini gör
sudo dnf remove -y podman buildah

# 2) Depoyu tanıt (kurulum değil, yalnızca adres)
sudo dnf install -y dnf-plugins-core
sudo dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
sudo dnf repolist | grep docker                    # docker-ce-stable görünmeli

# 3) Kur — HENÜZ BAŞLATMA
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

**Silme listesindeki iki kalem şaşırtmasın:** `cockpit-podman` gider (Cockpit'in konteyner sekmesi
kaybolur) ve `container-selinux` "kullanılmayan bağımlılık" diye listelenir — onu `docker-ce`
kurulumu geri getirir.

#### Veri klasörü: `/var` küçükse zorunlu adım

1c'de `/var` dar çıktıysa motoru boş bölmeye yönlendirin. `/srv` ayrı bir bölme değilse root
üzerindedir, yani oradaki boş alanı kullanır.

```bash
sudo dnf install -y policycoreutils-python-utils   # `semanage` bunun içinde
sudo mkdir -p /srv/docker /etc/docker

# SELinux'a "burası da /var/lib/docker gibidir" de. `setenforce 0` ÇÖZÜM DEĞİLDİR.
sudo semanage fcontext -a -e /var/lib/docker /srv/docker
sudo restorecon -R -v /srv/docker

sudo tee /etc/docker/daemon.json >/dev/null <<'EOF'
{
  "data-root": "/srv/docker",
  "log-driver": "json-file",
  "log-opts": { "max-size": "50m", "max-file": "3" }
}
EOF
```

`log-opts` ayrı bir güvenlik önlemidir: varsayılanda konteyner log'ları **sınırsız** büyür ve diski
sessizce doldurur. Konteyner başına 150 MB tavan koyuyoruz.

#### Başlat ve doğrula

```bash
sudo systemctl enable --now docker
systemctl is-enabled docker && systemctl is-active docker
sudo docker info | grep -E 'Docker Root Dir|Cgroup Version|Storage Driver'

sudo usermod -aG docker $USER                      # sonra oturumu kapatıp açın
docker run --rm hello-world                        # motor + internet erişimi testi
docker compose version
```

Beklenen: `enabled` · `active` · **`Docker Root Dir: /srv/docker`** · `Cgroup Version: 2`.
`Docker Root Dir` hâlâ `/var/lib/docker` ise `daemon.json` okunmamıştır, devam etmeyin.

**Şaşırtabilecek üç çıktı:**
- `Storage Driver: overlayfs` — Docker 29 containerd tabanlı imaj deposunu kullanıyor; `overlay2`
  ile aynı teknoloji, ad değişti.
- `cat /etc/docker/daemon.json` → *Permission denied*. Sertleştirilmiş sunucularda `umask 077`
  ayarlıdır, `sudo tee` dosyayı `0600` yazar. Docker root olduğu için okur; siz `sudo cat` kullanın.
- `dnf install docker-ce` sonrası GPG parmak izi sorulur; `060A 61C5 1B55 8A7F 742B 77AA C52F EB6B
  621E 9F35` olmalı.

> RHEL sürümünüz için `docker-ce.repo` bulunamazsa `linux/rhel` yerine `linux/centos` deneyin —
> paketler aynıdır.

Yukarıdaki `docker run --rm hello-world` aynı zamanda **internet erişimini de test eder**.
Çalışmıyorsa kurumsal proxy arkasında olabilirsiniz; IT'den proxy adresini alıp
`/etc/systemd/system/docker.service.d/proxy.conf` içine yazın, sonra
`sudo systemctl daemon-reload && sudo systemctl restart docker`.

---

## 3. Proje dosyalarını sunucuya taşıyın

İmajlar **sunucuda, kaynaktan** derlenir — Dockerfile'lar çok aşamalıdır (multi-stage). Bu yüzden
dev'de elle `bootJar`/`publish` üretmeye gerek yok; taşınan şey **üç kaynak depodur**. Derleyiciyi
(TTI: JDK 17 + Node 22 · Rapor: .NET SDK) Docker build imajı getirir — sunucu **host'una** hiçbir SDK
kurulmaz, yalnızca `docker compose build` sırasında build imajının içinde çalışır.

### 3a. ⚠️ Ön koşul — sunucu build bağımlılıklarına erişebiliyor mu

build aşaması derleme anında bağımlılık çeker. Taşımadan önce **sunucuda** doğrulayın (hepsi
`200`/`301`/`302` olmalı):

```bash
for u in https://repo.maven.apache.org/maven2/ https://services.gradle.org/distributions/ \
         https://plugins.gradle.org/ https://registry.npmjs.org/ \
         https://deb.nodesource.com/setup_22.x https://deb.debian.org/debian/ \
         https://api.nuget.org/v3/index.json; do
  printf '%-45s ' "$u"; curl -fsS -o /dev/null -w '%{http_code}\n' --max-time 10 "$u" || echo ERISIM-YOK
done
```

Biri bloklanırsa build sunucuda koşamaz → **dev'de** derleyip imajı taşıyın: `docker compose build`
→ `docker save tti-hakedis-server:$SURUM tti-rapor-server:$SURUM -o imajlar.tar` → sunucuda
`docker load -i imajlar.tar`. Dockerfile değişmez, yalnızca build yeri değişir.

### 3b. Üç depoyu paketle ve taşı

`.env` bilinçli olarak dışarıda (sunucununki `.env.example`'dan üretilecek — §4). Build çıktıları,
`node_modules` ve `local.properties` de hariç (imaj içinde yeniden üretilir; `local.properties`
girse Android modülünü tetikler).

```bash
cd ~/Desktop/tti-projeler
zip -r tasima.zip tti-hakedis tti-rapor tti-sunucu \
  -x '*/build/*' '*/bin/*' '*/obj/*' '*/yayin/*' '*/.git/*' '*/node_modules/*' \
     '*/.gradle/*' '*/.kotlin/*' '*/web/dist/*' '*.DS_Store' \
     '*/.env' '*/deneme/*' '*/local.properties'
shasum -a 256 tasima.zip        # bu değeri not edin
```
> ⚠️ **`*/arsiv/*` ya da `*/yukleme/*` ile dışlama YAPMAYIN.** Arşiv ve yükleme *veri* kökleri repoda
> değil, dışarıdadır (`/srv/veri/...`); bu kalıplar bunun yerine `server/.../server/arsiv/` **kaynak
> paketini** siler ve build `Unresolved reference 'arsiv'` ile çöker. `web/dist` gibi build çıktıları
> için yolu tam ver (`*/web/dist/*`), çıplak `*/dist/*` değil.

Sunucuda açarken kod ağacına gider — **üçü SIBLING olmalı** (compose imajları `../tti-hakedis`,
`../tti-rapor` bağlamından derler):

```bash
sudo mkdir -p /srv/kod && sudo unzip -q tasima.zip -d /srv/kod
ls /srv/kod                     # tti-hakedis  tti-rapor  tti-sunucu
```

> ⚠️ **`sha256sum` karşılaştırmasını atlamayın.** Elle taşımada dosya sessizce bozulabilir.

İmajlar §5'te **`docker compose build`** ile burada üretilir; ayrıca `docker build` komutu yok.

---

## 4. Klasörleri ve `.env`'i hazırlayın

Depo 3b'de `/srv/kod/tti-hakedis`'ye açıldı (ya da `git clone` ile alındı). Kendi hazırlık betiğini çalıştırın:

```bash
cd /srv/kod/tti-hakedis
sudo bash deploy/kur.sh
```

Betik `/srv/veri/tti-hakedis/arsiv` ve `/srv/veri/tti-hakedis/yukleme` klasörlerini açar.
**Dolu bir değeri ezmez** — çalışan bir kurulumun arşivini sessizce başka klasöre taşımamak için.

> ⚠️ **İKİ AYRI `.env` VAR, karıştırmayın.**
>
> | Dosya | Kimin | Ne zaman kullanılır |
> |---|---|---|
> | `tti-hakedis/deploy/.env` | Yalnızca TTI'nin **tek başına** compose'u | Geliştirme |
> | `/srv/kod/tti-sunucu/.env` | **Paylaşılan yığın** — sunucuda çalışan tanım | ÜRETİM |
>
> Sunucuda parolaları ve `SURUM` değerlerini **`tti-sunucu/.env`** içinde ayarlayın
> (`tti-sunucu/KURULUM.md` §1). `kur.sh`'ın ürettiği dosya üretimde okunmaz.

⚠️ **İki klasör vardır ve ayrı kalmalıdır:**

| Değişken | İçerik | Kaybolursa |
|---|---|---|
| `ARSIV_KOK` | Sunucunun **ürettiği** PDF/Excel | Yeniden üretilir |
| `YUKLEME_KOK` | Kullanıcının **yüklediği** EK1/EK2 dosyaları | **Geri gelmez** |

---

## 5-6. Ortak yığın: compose, provizyon, başlatma

⚠️ **Bu bölümün içeriği `tti-sunucu` deposuna taşındı.** Buradaki adımları tek tek izlemek yerine:

> **`tti-sunucu/KURULUM.md` §1-5**

Neden taşındı: ortak compose ve provizyon SQL'leri **iki projeyi birden** tanır, dolayısıyla
hiçbir projenin deposuna ait değildir. Burada bir kopyası daha durduğunda iki metin zamanla
ayrışıyor — nitekim ayrıştı da: bu rehberdeki eski compose bloğunda `external` volume,
`SURUM` etiketi, `Maximum Pool Size` ve ayrıcalıksız uygulama rolü **yoktu**. Onu izleyen biri
bugün eklenen güvencelerin hiçbirini almadan bir yığın kurardı.

Özet olarak orada yapılanlar:

| Adım | Nerede |
|---|---|
| `/srv/kod` ve `/srv/veri` klasörleri, `.env` | `tti-sunucu/KURULUM.md` §1 |
| Veritabanı volume'ü (**external**) | §2 |
| `docker compose up -d db` | §3 |
| Proje başına veritabanı + ayrıcalıksız rol | §4 · `tti-sunucu/provizyon/` |
| İmajları **tarihli etiketle** üretip yığını başlatma | §5 |

⚠️ İki tuzak orada ayrıntılı anlatılıyor, atlanırsa sessizce bozulur:
- **`POSTGRES_USER` bir uygulama hesabı değildir** — imaj onu SUPERUSER açar ve superuser tüm
  yetki kontrollerini atlar; o rolle bağlanan uygulama her projenin verisini okur.
- **Volume `external` olmalı** — compose klasörü/proje adı değiştiğinde Docker yeni ve boş bir
  volume yaratır, veri "kaybolmuş" görünür.

Bu rehberde TTI'ye özgü kalan adımlar: 1 (ön kontrol), 2 (Docker Engine), 3 (jar üretimi ve
taşıma), 4 (TTI'nin `.env`'i), 11 (günlük işler), 13 (sorun giderme).

---

## 7. SELinux — `:Z` etiketi ne yapıyor

RHEL'de SELinux varsayılan olarak `Enforcing` durumdadır. Konteyner, host'tan bağlanan
(bind mount) bir klasöre **etiketlenmeden yazamaz**. Compose dosyasındaki `:Z` soneki motora
"bu klasörü bu konteynere özel olarak etiketle" der ve sorun ortadan kalkar. (`:Z` Docker'a özgü
bir eksiklik değil, ortak bir compose seçeneğidir; `container-selinux` paketi `docker-ce` ile
birlikte kurulduğu için burada çalışır.)

Bu neden önemli: etiket olmadan uygulama **açılır**, sağlık kontrolü **geçer**, ama ilk PDF
üretiminde arşive yazamaz. Hata geç ve alakasız görünür.

Yazma sorunu yaşarsanız teşhis:

```bash
sudo ausearch -m avc -ts recent
```

⚠️ **`setenforce 0` bir çözüm değildir.** SELinux'u kapatmak kurumsal bir sunucuda güvenlik
politikasının ihlalidir ve zaten yeniden başlatmada geri gelir. Doğru çözüm `:Z` etiketidir;
zaten compose dosyasında duruyor.

---

## 8. Güvenlik duvarı

RHEL'de `firewalld` gelen bağlantıları varsayılan olarak engeller. Erişim artık **proxy (port 80)**
üzerinden domain adıyla olur; uygulama portları (8081/8082) `127.0.0.1`'e bağlı olduğundan açılmaz.
Portların ve Portainer'ın (9443, yalnızca IT subnet) tam listesi paylaşılan yığındadır:
**`tti-sunucu/KURULUM.md` §8**.

```bash
sudo firewall-cmd --permanent --add-service=http    # port 80 → proxy
sudo firewall-cmd --reload
sudo firewall-cmd --list-all
```

**8081, 8082 ve 5432'yi AÇMAYIN.** İlk ikisine proxy içeriden erişir; veritabanı `127.0.0.1`'e bağlı.

Sunucunun IP adresini öğrenin (DNS A kaydı için IT'ye vereceksiniz):

```bash
ip -4 addr show | grep inet
```

> 💡 IT ekibinden bu IP'ye **sabit adres** ayırmalarını **ve** `hakedis.<DOMAIN>` / `rapor.<DOMAIN>`
> için **DNS A kaydı** eklemelerini isteyin. Sabit IP yoksa sunucu yeniden başladığında IP değişir ve
> DNS kaydı yanlış yeri gösterir; isim çözümlemesi Docker'ın değil, kurumsal DNS'in işidir.

---

## 9. ⚠️ Yeniden başlatmada kendiliğinden kalkma

Linux'ta konteyner motoru bir sistem servisidir — Windows'taki WSL sorunu burada **yoktur**. Yine de
`restart: unless-stopped` tek başına yetmez; motoru sisteme tanıtmak gerekir.

```bash
sudo systemctl enable --now docker
systemctl is-enabled docker          # "enabled" demeli
```

Bu kadar. Docker daemon bir sistem servisidir; `enable` edildikten sonra `restart: unless-stopped`
gerisini halleder ve konteynerler açılışta kendiliğinden kalkar.

### Kurulum bittikten sonra mutlaka test edin

```bash
sudo reboot
```

Sunucu geri geldikten **3 dakika sonra**, başka bir bilgisayardan `http://hakedis.<DOMAIN>`
adresini açın. Açılıyorsa bu adım tamamdır. Açılmıyorsa demo sabahı da açılmayacak demektir.
(İsim çözülmüyorsa `http://<sunucu-ip>` ile proxy'yi doğrulayın — açılan varsa sorun DNS'tedir.)

---

## 10. Doğrulama

**Sunucudan:**

```bash
curl -s http://localhost:8081/api/saglik
docker ps                                   # hepsi Up / healthy
```

**Başka bir bilgisayardan** — asıl test budur (URL'de **port yok**):

```
http://hakedis.<DOMAIN>        → TTI (giriş ekranı)
http://rapor.<DOMAIN>          → ikinci proje
```

**Uygulama düzeyi doğrulama** (depo sunucuda duruyorsa):

```bash
bash deploy/dogrula.sh          # yetki süzgeci, kable araması, para hassasiyeti
```

---

## 11. Günlük işler

Hepsi `/srv/kod/tti-sunucu` klasöründe:

```bash
cd /srv/kod/tti-sunucu

docker ps                            # ne çalışıyor
docker logs -f tti-hakedis-server            # canlı log (Ctrl+C ile çık)
docker restart tti-hakedis-server            # tek servisi yeniden başlat
docker compose stop                  # hepsini durdur (veri kalır)
docker compose up -d                 # hepsini başlat
docker stats --no-stream             # bellek kullanımı
```

**Yeni sürüm çıkarma:** güncel **kaynağı** taşıyın (3. adım — jar/publish değil), `.env`'de `SURUM`
(ve/veya `SURUM_RAPOR`) değerini yeni tarihe çevirin, sonra:

```bash
cd /srv/kod/tti-sunucu
docker compose build                 # imajı kaynaktan yeniden derler, yeni SURUM ile etiketler
docker compose up -d                 # değişen imajın konteyneri yeniden oluşturulur
```

Geri dönüş: `.env`'de `SURUM`'u önceki tarihe çevir, `docker compose up -d` (eski imaj hâlâ
duruyorsa ~20 sn; yoksa o sürümün kaynağından `docker compose build`).

---

## 12. Yedekleme

Tek sunucu, tek disk. Yedek yoksa disk arızası her iki projeyi birden götürür.

Yedeği **elle komut dizerek almayın** — depodaki `tti-sunucu/yedek.sh` (platform deposu) bunu yapar ve elle yazılan
komutların iki sessiz kusurunu kapatır (12.2'de anlatılıyor).

### 12.1 Ne yedekleniyor, ne yedeklenmiyor

| Ne | Nerede | Kaybı |
|---|---|---|
| `hakedis` şeması | `db/hakedis-<tarih>.dump` | **Geri gelmez** |
| `rapor` şeması | `db/rapor-<tarih>.dump` | **Geri gelmez** |
| `/srv/veri/tti-rapor/yukleme` | `yukleme-rapor-<tarih>.tar.zst` | **Geri gelmez** — saha fotoğrafları |
| Roller ve parolaları | `roller/roller-<tarih>.sql` | Şema dump'ı rolleri **içermez** |
| `YUKLEME_KOK` (EK1/EK2) | `yukleme/<tarih>/` | **Geri gelmez — asıl veri** |
| `/srv/kod/tti-sunucu` (compose + `.env`) | `sunucu-ayarlari/` | Hiçbir depoda yok, tek kopya |
| ~~`ARSIV_KOK`~~ | **yedeklenmez** | Veritabanından yeniden üretilir |

`ARSIV_KOK` bilinçli olarak dışarıda: üretilen PDF/Excel'in tek yönlü aynasıdır, doğruluğun kaynağı
veritabanıdır (`docs/uretim-notlari.md`). Yedeklemek aynı veriyi iki kez saklamak olurdu.

### 12.2 Neden betik — elle yazılan komutun iki sessiz kusuru

1. **`pg_dump ... > dosya` yönlendirmesi dosyayı komut çalışmadan ÖNCE yaratır.** `pg_dump` düşse
   bile diskte bugünün tarihini taşıyan 0 baytlık bir `.dump` kalır ve "yedek var" sanılır. Betik
   önce geçici dosyaya yazar, **yalnızca doğrulamayı geçerse** asıl adına taşır; başarısızlıkta
   dünün iyi yedeği yerinde kalır.
2. **`docker exec -t` TTY ayırır** ve `-Fc` ikili arşivine `\r` karışır — dump **sessizce bozulur**,
   ancak geri yükleme günü anlaşılır. Betik `-t` kullanmaz.

   > ⚠️ İnce ayrım: `docker exec` için doğru olan **`-t` yokluğu**, `docker compose exec` için ise
   > **`-T` varlığı**dır (compose varsayılan olarak TTY ayırır). Biri diğerine bakıp "düzeltmesin".

Betik ayrıca her dump'ı **arşivin tamamını okuyarak** doğrular (`pg_restore -f /dev/null`); yalnızca
`--list` bakmak yetmez, çünkü o sadece baştaki içindekiler tablosunu okur ve sondan kırpılmış bir
dosyayı "sağlam" sayar.

### 12.3 Ön koşullar

```bash
sudo dnf install -y rsync
sudo mkdir -p /srv/veri/yedek
sudo chown root:root /srv/veri/yedek
sudo chmod 750 /srv/veri/yedek
```

Parola gerekmez: `docker exec` komutu konteynerin **içinde** koşar, oradaki `pg_hba.conf` yerel
soket için `trust`tır. Doğrulayın — parola sormadan `1` dönmeli:

```bash
docker exec tti-db psql -U tti_hakedis -d tti -c 'select 1'
```

**SELinux:** yedek dizini bir bind mount **değildir**, compose'a eklenmez ve `:Z` verilmez. Oraya
yazan tek şey root olarak koşan systemd birimidir; `Enforcing` modda sorun çıkmaz.

### 12.4 Bir kez elle çalıştırın

```bash
sudo bash /srv/kod/tti-sunucu/yedek.sh
```

Beklenen çıktı:

```
… BASLADI TTI yedek — 2026-08-10
… TAMAM  hakedis dump — 178836 bayt, 152 girdi
… ATLANDI rapor şeması yok (ilgili proje henüz kurulmamış)
… TAMAM  roller
… TAMAM  makineye özel ayarlar
… TAMAM  yükleme anlık görüntüsü — 259 dosya
… BITTI  durum=TAMAM sure=12s bos=41000MB
```

`.NET projesi henüz kurulmamışsa `rapor` için **ATLANDI** görürsünüz; bu normaldir ve genel sonucu
başarısız yapmaz.

| Çıkış kodu | Anlamı |
|---|---|
| 0 | Başarılı |
| 1 | Ön koşul eksik (dizin, `rsync`, `docker`) |
| 2 | Veritabanı süresi içinde hazır olmadı |
| 3 | Bir şema dump'ı alınamadı ya da doğrulamayı geçemedi |
| 4 | Disk yetersiz — **hiçbir şey silinmedi/yazılmadı** |
| 5 | Yükleme anlık görüntüsü alınamadı (**en ağırı: asıl veri**) |
| 6 | Başka bir çalışma sürüyor |
| 7 | `dogrula`: yedek eski, eksik ya da bozuk |

### 12.5 Her gece otomatik çalıştırma (systemd timer)

```bash
sudo tee /etc/systemd/system/tti-yedek.service >/dev/null <<'EOF'
[Unit]
Description=TTI gunluk yedek (veritabani + yuklenen dosyalar)
Wants=docker.service
After=docker.service

[Service]
Type=oneshot
ExecStart=/bin/bash /srv/kod/tti-sunucu/yedek.sh
# ⚠️ Varsayilan TimeoutStartSec 90 sn'dir ve TUM betigi kapsar. Saha fotograflari buyudukce
#    systemd yedegi yarida SIGTERM ile keser ve geriye yarim is kalir.
TimeoutStartSec=3600
Nice=10
IOSchedulingClass=idle
SyslogIdentifier=tti-yedek
EOF

sudo tee /etc/systemd/system/tti-yedek.timer >/dev/null <<'EOF'
[Unit]
Description=TTI gunluk yedegini her gece 23:30'da calistirir

[Timer]
OnCalendar=*-*-* 23:30:00 Europe/Istanbul
Persistent=true
AccuracySec=1m
Unit=tti-yedek.service

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now tti-yedek.timer
systemctl list-timers tti-yedek.timer      # NEXT / LEFT dolu görünmeli
```

`.service` dosyasında `[Install]` **yoktur** — servis doğrudan etkinleştirilmez, onu timer tetikler.

> `OnCalendar` **host'un** saat dilimini kullanır (konteynerdeki `TZ` değil). Sonekli yazım systemd
> 240+ ister; RHEL 9'da sorun yok. Hata verirse soneki silin ve
> `sudo timedatectl set-timezone Europe/Istanbul` yapın.

### 12.6 Sunucu yeniden başlarsa

- `Persistent=true` sayesinde timer reboot'tan sonra kendiliğinden gelir ve makine kapalıyken
  **kaçırılan çalışma açılışta koşar**.
- ⚠️ `After=docker.service` yalnızca *daemon*'ın kalktığını garanti eder; `tti-db` konteyneri ve
  PostgreSQL'in kurtarması 10–60 sn sürebilir. Kaçırılmış yedek açılışta **hemen** ateşlendiği için
  bu yarışa her reboot'ta girilir. Betik `pg_isready` ile 5 sn arayla en çok 3 dakika bekler.

Reboot testi (9. adımdaki testle birlikte yapın):

```bash
sudo reboot
# geri geldikten sonra:
systemctl list-timers tti-yedek.timer
journalctl -t tti-yedek -b            # açılışta koştuysa "veritabanı bekleniyor" satırı görünür
```

O satır, yarışın gerçekten yaşandığının **ve kazanıldığının** kanıtıdır.

### 12.7 Günlük kontrol

```bash
sudo bash /srv/kod/tti-sunucu/yedek.sh dogrula     # tek komut, hiçbir şey yazmaz
```

Bu komut dump'ları tam okuyarak doğrular, anlık görüntünün yaşına bakar ve **veritabanındaki her ek
dosyasının anlık görüntüde gerçekten bulunduğunu ve boyutunun tuttuğunu** çapraz kontrol eder.

Hızlı bakış: `head -1 /srv/veri/yedek/SON-DURUM.txt` · `journalctl -t tti-yedek -n 50`

> **Bu dosyanın tarihi dünden eskiyse yedeğiniz yok demektir.** Yedeğin klasik ölüm şekli sessizce
> bir daha hiç koşmamaktır.

### 12.8 Geri yükleme — üç ayrı senaryo, karıştırmayın

**(a) Tek dosya kurtarma** (en sık gereken): kullanıcı bir eki yanlışlıkla sildi.

```bash
cp /srv/veri/yedek/yukleme/2026-08-09/BURSA/2026/BURSA_2026_HAKEDIS_001/EK1_8_guzergah.kml \
   /srv/veri/tti-hakedis/yukleme/BURSA/2026/BURSA_2026_HAKEDIS_001/
```

**(b) PROVA** — yedeğin gerçekten geri yüklenebildiğini kanıtlar, **canlıya dokunmaz**:

```bash
docker exec tti-db psql -U tti_hakedis -d postgres -c "create database tti_prova owner tti_hakedis"
docker exec -i tti-db pg_restore -U tti_hakedis -d tti_prova --no-owner --no-privileges \
  < /srv/veri/yedek/db/hakedis-2026-08-10.dump

# Canlıyla karşılaştırın — iki sayı eşit olmalı
docker exec tti-db psql -U tti_hakedis -d tti       -tAc "select count(*) from hakedis.belge"
docker exec tti-db psql -U tti_hakedis -d tti_prova -tAc "select count(*) from hakedis.belge"
docker exec tti-db psql -U tti_hakedis -d tti_prova -tAc \
  "select version from hakedis.flyway_schema_history order by installed_rank desc limit 1"

docker exec tti-db psql -U tti_hakedis -d postgres -c "drop database tti_prova"
```

⚠️ `-d tti_prova` yazmak, prova ile felaket arasındaki **tek** farktır. Komuttan önce veritabanı
adını gözle doğrulayın.

**(c) GERÇEK FELAKET KURTARMA** — yalnızca canlı veri gerçekten kaybolduğunda:

```bash
# 1) Uygulamaları DURDURUN. Bu adım atlanırsa Flyway ve açık oturumlar geri yükleme sırasında
#    yazar; sonuç yarı-restore edilmiş, tutarsız bir veritabanıdır.
docker stop tti-hakedis-server tti-rapor-server

# 2) Bozuk şemayı adli kopya olarak saklayın (silmeyin — nedenini sonra araştıracaksınız)
docker exec tti-db psql -U tti_hakedis -d tti -c \
  "alter schema hakedis rename to hakedis_bozuk_$(date +%Y%m%d)"

# 3) Geri yükleyin
docker exec -i tti-db pg_restore -U tti_hakedis -d tti --clean --if-exists \
  < /srv/veri/yedek/db/hakedis-2026-08-10.dump

# 4) Yükleme dosyalarını da geri getirin (veritabanı bunları İÇERMEZ)
rsync -a /srv/veri/yedek/yukleme/2026-08-10/ /srv/veri/tti-hakedis/yukleme/

docker start tti-hakedis-server tti-rapor-server
```

### 12.9 Rotasyon, disk ve dış kopya

Şema dump'ları ve roller **14 gün**, yükleme anlık görüntüleri **7 gün** saklanır; ayın 1'ine denk
gelen kopyalar daha uzun tutulur. Rotasyon **her zaman başarılı bir yedekten sonra** çalışır — ters
sırası "yer açayım" diye son iyi yedeği silip ardından bozuk bir yedek yazmak olurdu.

Yükleme anlık görüntüleri `rsync --link-dest` ile alınır: değişmeyen dosya kopyalanmaz, bir önceki
güne **sabit bağlantı** verilir. Yedi günlük görüntü tek kopya kadar yer kaplar. (`du` her klasörü
tam boyutunda gösterebilir; toplam için `du -sh /srv/veri/yedek/yukleme` bakın.) Saha
fotoğrafları JPEG olduğu için sıkıştırmanın kazancı yoktur, o yüzden `tar` kullanılmıyor.

Boş disk 5 GB'ın altına inerse betik **hiçbir şey silmeden ve yazmadan** durur (kod 4). Yer açmak
operatörün bilinçli kararıdır, betiğin değil.

⚠️ **Yedekler aynı diskte.** Yanlış silme ve bozulmaya karşı korur, **disk arızasına karşı korumaz.**
Haftada bir başka bir makineye kopyalayın:

```bash
rsync -a /srv/veri/yedek/ <kullanici>@<baska-makine>:/yedek/tti/
```

> `roller/` ve `sunucu-ayarlari/` **parola içerir** (`chmod 600` ile yazılır). Açık erişimli bir
> paylaşıma göndermeyin.

---

## 13. Sorun giderme

| Belirti | Sebep | Çözüm |
|---|---|---|
| Başka bilgisayardan site açılmıyor, sunucuda açılıyor | firewalld | 8. adım |
| Uygulama açılıyor ama PDF üretiminde "yazılamadı" hatası | SELinux etiketi yok | compose'da `:Z`, 7. adım |
| Yeniden başlatmadan sonra hiçbir şey çalışmıyor | `systemctl enable docker` yapılmamış | 9. adım |
| Konteyner sürekli yeniden başlıyor | Bellek yetmiyor (OOM) | `docker stats`; `mem_limit` düşürün |
| `mem_limit` etkisiz görünüyor | cgroups v1 | 5. adımdaki cgroup notu |
| `tti-hakedis-server` başlamıyor, log'da veritabanı hatası | Parola uyuşmuyor | `/srv/kod/tti-sunucu/.env` ile `deploy/.env` aynı olmalı |
| Taban imaj indirilemiyor | Kurumsal proxy | 2. adımın sonundaki proxy notu |
| Jar açılışta anlamsız hata veriyor | Taşımada bozulmuş | 3b'deki `sha256sum` |
| Sunucunun IP'si değişti | DHCP | IT'den sabit IP isteyin |
| `pg_restore: input file does not appear to be a valid archive` | Dump `docker exec -t` ile alınmış (TTY `\r` sokmuş) | `-t` kullanmayın; `tti-sunucu/yedek.sh` zaten kullanmıyor (12.2) |
| Yedek klasöründe 0 baytlık `.dump` | Elle `pg_dump > dosya` yazılmış, komut düşmüş | Elle komut dizmeyin, `yedek.sh` kullanın (12.2) |
| Yedekte `no matching schemas were found` | `rapor` şeması yok | .NET projesi kurulmamış — normaldir, `ATLANDI` yazar |
| Timer görünüyor ama gece koşmuyor | Host'un saat dilimi | `timedatectl`; 12.5'teki `OnCalendar` notu |
| Yedek 90 saniyede kesiliyor | `TimeoutStartSec` varsayılanı | 12.5'teki `TimeoutStartSec=3600` |
| `başka bir yedek çalışması sürüyor` | Bayat `.kilit` dizini | 6 saat sonra kendiliğinden devralınır; acele ise `rmdir /srv/veri/yedek/.kilit` |

**Log dosyaları:** `docker logs <konteyner>`. Motorun kendi log'u: `journalctl -u docker`.

---

## Kurulum kontrol listesi

Sırayla işaretleyin:

- [ ] İkinci projenin `TargetFramework` değeri kontrol edildi: `____________` (1a)
- [ ] RHEL sürümü, RAM, disk kontrol edildi
- [ ] **Motorun veri klasöründe en az 25 GB boş var** (1c — `df -h`, `vgs`)
- [ ] Konteyner motoru kuruldu, `hello-world` çalıştı (internet erişimi doğrulandı)
- [ ] `Docker Root Dir` doğru bölmede: `________________`
- [ ] Kurulum ÖNCESİ açık port listesi not edildi (`ss -tlnp`) — sonrasında yalnızca 8081 eklenmeli
- [ ] Jar geliştirici makinesinde üretildi ve taşındı
- [ ] **`sha256sum` karşılaştırması yapıldı**
- [ ] `sudo bash tti-hakedis/deploy/kur.sh` çalıştırıldı, TÜM parolalar değiştirildi (`degistir-beni` kalmadı)
- [ ] `/srv/kod/tti-sunucu/docker-compose.yml` ve `.env` yerinde
- [ ] İmajlar **TARİHLİ etiketle** oluşturuldu (`:dev` / `:latest` DEĞİL — geri dönüş buna bağlı)
- [ ] `docker compose up -d` → hepsi ayakta
- [ ] Her proje için **ayrı veritabanı + ayrıcalıksız rol** açıldı (`tti-sunucu/provizyon/`)
- [ ] **Hiçbir uygulama rolü superuser DEĞİL** — `POSTGRES_USER` yalnızca bakım hesabı
- [ ] `revoke connect ... from public` her veritabanında uygulandı
- [ ] Veritabanı volume'ü **external** olarak oluşturuldu (`docker volume create`)
- [ ] `Cgroup Version: 2` doğrulandı (yoksa bellek sınırı ayarlandı)
- [ ] Güvenlik duvarında **80 (proxy)** açıldı, **9443 yalnızca IT subnet'ine** açıldı, 8081/8082/5432 kapalı
- [ ] `.env`'de `DOMAIN` ayarlandı; IT `hakedis.<DOMAIN>` / `rapor.<DOMAIN>` için DNS A kaydı ekledi
- [ ] Portainer'a IT subnet'inden girildi, admin parolası kuruldu, konteynerler listeleniyor
- [ ] Sunucunun sabit IP'si alındı ve not edildi: `________________`
- [ ] **Başka bir bilgisayardan** her iki site de **domain adıyla (portsuz)** açıldı
- [ ] `bash tti-sunucu/dogrula.sh` → **tüm platform kontrolleri geçti**
- [ ] `/api/saglik` uçları doğru `surum` değerini bildiriyor
- [ ] **Sunucu yeniden başlatıldı, her şey kendiliğinden ayağa kalktı**
- [ ] `dnf install -y rsync` yapıldı, `/srv/veri/yedek` açıldı (12.3)
- [ ] `sudo bash tti-sunucu/yedek.sh` bir kez elle çalıştırıldı, çıkış kodu 0
- [ ] `systemctl enable --now tti-yedek.timer` → `list-timers` NEXT/LEFT dolu (12.5)
- [ ] `sudo bash tti-sunucu/yedek.sh dogrula` → **yedek güvenilir**
- [ ] **Geri yükleme provası `tti_prova` üzerinde yapıldı, satır sayıları tuttu** (12.8b)

Son beş madde işaretlenmeden kurulum bitmiş sayılmaz. Yedeği olmayan bir kurulum, ilk disk
arızasında geri dönüşü olmayan bir kurulumdur.
