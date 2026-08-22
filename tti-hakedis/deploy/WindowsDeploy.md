# Windows Sunucu Kurulumu — İki Proje Tek Makinede *(WSL2 + Docker Engine)*

Şirket içi ağda tek bir Windows makinesinde çalışacak yığının kurulum rehberi.
Hedef: yönetici ve IT ekibi tarayıcıdan her iki projeye de ulaşabilsin.

> **Hangi rehber?**
> - **Bu dosya** — Windows makine, **WSL2 + Docker Engine** ile
> - `WindowsDockersiz.md` — Windows makine, **konteynersiz** (Docker yok, WSL yok; §9'daki
>   yeniden başlatma karmaşası ortadan kalkar)
> - `RedHatKurulum.md` — **Red Hat sunucu**, Docker Engine ile
> - `KURULUM.md` — yalnızca TTI'nin kendi ayarları (`.env`, arşiv klasörleri, kable içe aktarımı,
>   doğrulama betikleri)

---

## 0. Ne kuracağız

Altı konteyner:

| Konteyner | Ne işe yarar | Port | Ağdan erişim |
|---|---|---|---|
| `tti-db` | PostgreSQL 17 — **iki proje de bunu paylaşır**, ayrı veritabanlarında | 5432 | ❌ sadece makinenin kendisi |
| `tti-hakedis-server` | TTI Saha Hakediş backend'i **+ web arayüzü** (jar'ın içinde gömülü) | 8081 | ✅ |
| `tti-rapor-server` | TTIDataRapor backend'i **+ arayüzü** (.NET 8) | 8082 | ✅ |

| `portainer` | Docker'ı bilmeyen IT ekibinin arayüzden yönetmesi için | 9443 | ✅ |

> ℹ️ **TTI için ayrı bir web konteyneri yoktur.** `web/` klasörü derlenip jar'ın içine gömülür ve
> 8081'den servis edilir. TTIDataRapor da arayüzünü kendi `wwwroot`'undan servis eder; ayrı bir web
> gerekir; backend'in içine gömülüyse o satırı silin.

**Makine gereksinimi:** 6 GB RAM, 100 GB disk. Yığın boşta ~4,5 GB kullanır; yani sığar ama
bol yeri yoktur. Bu yüzden 2. ve 6. adımdaki bellek sınırları **atlanamaz**.

---

## 0b. Önce şunu anlayın: iki ayrı terminal var

Kurulum boyunca kafa karıştıran tek şey budur, baştan netleştirelim.

**Docker bir Linux yazılımıdır.** Windows'ta çalışması için makinede küçük bir Linux gerekir; bunu
WSL2 sağlar ve `wsl --install` komutu varsayılan olarak **Ubuntu** kurar. Docker Engine işte o
Ubuntu'nun içine kurulur ve konteynerler orada çalışır.

> Daha önce Docker Desktop kullandıysanız bu size garip gelebilir — Desktop bu Linux'u sizden
> gizliyor, kendi dağıtımını kurup Docker'ı orada çalıştırıyordu. Desktop'ı kullanmadığımız için
> (lisans) Linux tarafı görünür hale geliyor. İşin özü değişmiyor.

Bu yüzden iki terminaliniz olacak:

| Terminal | Nasıl açılır | Ne için |
|---|---|---|
| **PowerShell** (Windows) | Başlat → PowerShell | WSL kurulumu, güvenlik duvarı, görev zamanlayıcı |
| **Ubuntu** (Linux) | Başlat → Ubuntu, ya da PowerShell'de `wsl` | **Tüm `docker` komutları** |

Aşağıda her komut bloğunun başında hangisinde çalıştırılacağı yazıyor.

**Dosyalar nerede duracak:** Docker'ın verisi ve uygulama klasörleri Linux tarafında olacak
(`/srv/...`). Bunlara Windows'tan da bakabilirsiniz — Dosya Gezgini'nin adres çubuğuna:

```
\\wsl.localhost\Ubuntu\srv\tti-hakedis
```

---

## 1. WSL2 kurulumu

**PowerShell (yönetici olarak):**

1. Başlat → PowerShell → sağ tık → "Yönetici olarak çalıştır"
2. Tek komut hem WSL'i hem Ubuntu'yu kurar:

   ```powershell
   wsl --install
   ```

3. **Makineyi yeniden başlatın.**
4. Açılışta Ubuntu penceresi gelir; bir kullanıcı adı ve parola ister. **İkisini de not edin** —
   parola `sudo` komutlarında lazım olacak.
5. Doğrulayın:

   ```powershell
   wsl --version
   ```

   `WSL sürümü: 2.x` görmelisiniz. Sürüm numarası **0.67.6'dan büyük olmalı** (9. adımdaki
   systemd bunu gerektiriyor). Eskiyse: `wsl --update`.

---

## 2. WSL'in belleğini ve ağını ayarlayın

⚠️ **Bu adım atlanırsa iki şey olur:** WSL makinenin RAM'inin yarısını kapar, ve konteynerlere
ağdan erişilemez.

**PowerShell:**

1. Dosya Gezgini'nde adres çubuğuna `%USERPROFILE%` yazıp Enter'a basın.
2. Orada `.wslconfig` adında **uzantısız** bir dosya oluşturun (Not Defteri → Farklı Kaydet →
   dosya adını tırnak içinde yazın: `".wslconfig"`).
3. İçine şunu yazın:

   ```ini
   [wsl2]
   memory=4GB
   processors=2
   swap=2GB
   autoMemoryReclaim=gradual
   networkingMode=mirrored
   ```

4. Ayarların geçerli olması için:

   ```powershell
   wsl --shutdown
   ```

**Satırların anlamı:**

- `memory=4GB` — WSL en fazla 4 GB alır, kalan 2 GB Windows'a kalır.
- `autoMemoryReclaim=gradual` — WSL boşalan belleği Windows'a geri verir (yoksa sürekli şişer).
- `networkingMode=mirrored` — **Bu satır kritik.** Docker Engine konteyner portlarını Linux'un
  içinde açar; bu ayar olmadan o portlar Windows'un ağ arayüzünde görünmez ve yönetici siteye
  ulaşamaz.

> ⚠️ `mirrored` yalnızca **Windows 11 22H2 ve sonrasında** çalışır. Sürümünüzü `winver` ile
> kontrol edin. Windows 10 ise bu satırı silin ve 4b bölümündeki yönlendirmeyi uygulayın.

---

## 3. Docker Engine kurulumu

Docker Desktop **kurmuyoruz** — 250+ çalışanlı şirketlerde ticari kullanımı ücretli. Docker Engine
(Apache 2.0) ücretsiz ve kısıtsızdır; arayüz ihtiyacını Portainer karşılayacak.

**Ubuntu terminalinde** (Başlat → Ubuntu):

```bash
# Depoyu ekle
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Kur
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

**Her komutta `sudo` yazmamak için** kendinizi docker grubuna ekleyin:

```bash
sudo usermod -aG docker $USER
```

Bunun geçerli olması için Ubuntu penceresini kapatın, PowerShell'de `wsl --shutdown` deyip Ubuntu'yu
yeniden açın.

**Doğrulayın (Ubuntu):**

```bash
docker version
docker compose version
docker run --rm hello-world
```

Üçü de çalışıyorsa Docker hazır.

---

## 4. Güvenlik duvarı — ağdan erişimi açın

Windows Defender dışarıdan gelen bağlantıları varsayılan olarak engeller. Bu adım yapılmadan
yönetici hiçbir şey göremez.

**PowerShell (yönetici):**

```powershell
New-NetFirewallRule -DisplayName "TTI Backend"     -Direction Inbound -LocalPort 8081 -Protocol TCP -Action Allow -Profile Domain,Private
New-NetFirewallRule -DisplayName "Proje2 Backend"  -Direction Inbound -LocalPort 8082 -Protocol TCP -Action Allow -Profile Domain,Private
New-NetFirewallRule -DisplayName "Proje2 Web"      -Direction Inbound -LocalPort 8083 -Protocol TCP -Action Allow -Profile Domain,Private
New-NetFirewallRule -DisplayName "Portainer"       -Direction Inbound -LocalPort 9443 -Protocol TCP -Action Allow -Profile Domain,Private
```

`-Profile Domain,Private` bilinçlidir: yalnızca şirket ağında açılır, halka açık Wi-Fi'de kapalı kalır.

**Makinenin IP adresini öğrenin** (yöneticiye vereceğiniz adres):

```powershell
ipconfig | findstr IPv4
```

Örneğin `192.168.1.50` çıktıysa yönetici `http://192.168.1.50:8081` adresine gidecek.

> 💡 IT ekibinden bu IP'ye **sabit adres** (DHCP rezervasyonu) ayırmalarını isteyin. Yoksa makine
> yeniden başladığında IP değişir ve verdiğiniz bağlantılar ölür.

### 4b. Windows 10 iseniz — port yönlendirmesi

`mirrored` ağ modu Windows 10'da yok. Konteyner portlarını elle Windows'a taşımanız gerekir.

**PowerShell (yönetici):**

```powershell
$ip = (wsl hostname -I).Trim().Split()[0]
foreach ($port in 8081,8082,8083,9443) {
  netsh interface portproxy delete v4tov4 listenport=$port listenaddress=0.0.0.0 2>$null
  netsh interface portproxy add v4tov4 listenport=$port listenaddress=0.0.0.0 connectport=$port connectaddress=$ip
}
```

⚠️ **WSL'in IP adresi her açılışta değişir**, yani bu komutların her yeniden başlatmada tekrar
çalışması gerekir. 9. adımdaki başlangıç betiğine ekleyeceğiz.

---

## 5. Klasörleri ve ayar dosyasını hazırlayın

TTI deposunu Ubuntu tarafına alın. **Ubuntu terminalinde:**

```bash
sudo mkdir -p /srv && cd /srv
# Depoyu buraya kopyalayın (git clone ya da Windows'tan: cp -r /mnt/c/... /srv/kod/tti-hakedis)
cd /srv/kod/tti-hakedis
```

> Windows'taki bir klasörü Ubuntu'dan görmek için `/mnt/c/...` yolunu kullanın. Örneğin
> masaüstündeki bir dosya: `/mnt/c/Users/<kullanıcı>/Desktop/dosya.zip`

Sonra TTI'nin kendi hazırlık betiğini çalıştırın — klasörleri açar ve `.env`'i doldurur:

```bash
sudo bash deploy/kur.sh
```

Bu betik `/srv/veri/tti-hakedis/arsiv` ve `/srv/veri/tti-hakedis/yukleme` klasörlerini oluşturur.

**`.env` dosyasındaki `DB_PAROLA`'yı değiştirin.** Varsayılan parola bırakılmaz:

```bash
nano deploy/.env
```

(`nano`'da kaydetmek için `Ctrl+O`, Enter, sonra `Ctrl+X`.)

### Bu klasörlere Windows'tan bakmak

Dosya Gezgini'nin adres çubuğuna:

```
\\wsl.localhost\Ubuntu\srv\tti-hakedis
```

Üretilen PDF/Excel'ler ve yüklenen dosyalar buradan görünür. Sık bakacaksanız bu yolu sağ tıklayıp
"Hızlı erişime sabitle" deyin.

⚠️ **`yukleme` klasörü asıl veridir** — EK1/EK2 ve imza dosyaları orada durur ve kaybolursa geri
gelmez. `arsiv` ise üretilen çıktının aynasıdır, yeniden üretilebilir.

⚠️ **Bu klasörleri `/mnt/c/...` altına taşımayın.** Linux'tan Windows dosya sistemine yazmak hem
yavaştır hem izin sorunları çıkarır. `/srv` altında kalsınlar; yukarıdaki `\\wsl.localhost` yolu
zaten Windows'tan erişimi sağlıyor.

---

## 6. compose dosyasını oluşturun

**Ubuntu terminalinde:**

```bash
sudo mkdir -p /srv/kod/tti-sunucu
sudo nano /srv/kod/tti-sunucu/docker-compose.yml
```

Bu dosya iki projeyi birden kapsadığı için **hiçbir projenin deposuna ait değildir**; makineye
özeldir.

```yaml
services:
  db:
    image: postgres:17
    container_name: tti-db
    restart: unless-stopped
    mem_limit: 512m
    environment:
      POSTGRES_DB: ${DB_ADI}
      POSTGRES_USER: ${DB_KULLANICI}
      POSTGRES_PASSWORD: ${DB_PAROLA}
      TZ: Europe/Istanbul
      POSTGRES_INITDB_ARGS: "--locale=C --encoding=UTF8"
    command:
      - postgres
      - -c
      - shared_buffers=256MB
      - -c
      - max_connections=50
    volumes:
      - tti_db_verisi:/var/lib/postgresql/data
    ports:
      - "127.0.0.1:5432:5432"      # dışarı AÇILMAZ
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_KULLANICI} -d ${DB_ADI}"]
      interval: 5s
      timeout: 5s
      retries: 10

  tti-hakedis-server:
    image: tti-hakedis-server:dev
    container_name: tti-hakedis-server
    restart: unless-stopped
    mem_limit: 900m               # JVM heap'i buna göre hesaplar — 8. adımdaki nota bakın
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_URL: jdbc:postgresql://db:5432/${DB_ADI}
      DB_KULLANICI: ${DB_KULLANICI}
      DB_PAROLA: ${DB_PAROLA}
      ARSIV_KOK: /arsiv
      YUKLEME_KOK: /yukleme
      TZ: Europe/Istanbul
    volumes:
      - ${ARSIV_KOK}:/arsiv
      - ${YUKLEME_KOK}:/yukleme
    ports:
      - "8081:8081"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8081/api/saglik || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 12
      start_period: 60s

  tti-rapor-server:
    image: tti-rapor-server:dev        # ← TTIDataRapor'un imaj adı
    container_name: tti-rapor-server
    restart: unless-stopped
    mem_limit: 640m
    depends_on:
      db:
        condition: service_healthy
    environment:
      # .NET bağlantı dizesi — JDBC DEĞİL. Şema adı Search Path ile veriliyor.
      ASPNETCORE_URLS: http://+:8080
      ConnectionStrings__Varsayilan: "Host=db;Port=5432;Database=${DB_ADI};Username=rapor;Password=${RAPOR_DB_PAROLA};Search Path=rapor"
      RAPOR_YUKLEME_KOK: /yukleme
      TZ: Europe/Istanbul
    volumes:
      - ${RAPOR_YUKLEME_KOK}:/yukleme      # saha fotoğrafları — asıl veri, yedeklenmeli
    ports:
      - "8082:8080"                # ← sol taraf dışarısı, sağ taraf konteynerin portu
    healthcheck:
      # wget DEĞİL curl: aspnet taban imajında ikisi de yok, projenin Dockerfile'ı curl kuruyor.
      test: ["CMD-SHELL", "curl -fsS http://localhost:8080/api/saglik || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 12
      start_period: 60s

  # TTIDataRapor arayüzünü kendi wwwroot'undan servis eder; ayrı bir web konteyneri gerekmez.

  portainer:
    image: portainer/portainer-ce:latest
    container_name: portainer
    restart: unless-stopped
    mem_limit: 256m
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - portainer_verisi:/data
    ports:
      - "9443:9443"

volumes:
  tti_db_verisi:
  portainer_verisi:
```

`.env` dosyasını compose'un yanına kopyalayın ve ikinci projenin parolasını ekleyin:

```bash
sudo cp /srv/kod/tti-hakedis/deploy/.env /srv/kod/tti-sunucu/.env
echo 'RAPOR_DB_PAROLA=<güçlü bir parola>' | sudo tee -a /srv/kod/tti-sunucu/.env
```

**Bellek toplamı:** 512 + 900 + 640 + 128 + 256 = ~2,4 GB. WSL'e verdiğimiz 4 GB'ın içinde rahat kalır.

---

## 7. İkinci proje için veritabanı kullanıcısı açın

İki proje aynı PostgreSQL'i paylaşır ama **birbirinin verisini görmemelidir.** Ayrı şema, ayrı
kullanıcı — bu sayede ikinci projenin migration'ı yanlışlıkla TTI'nin şemasına dokunamaz.

**Ubuntu terminalinde**, veritabanı ayağa kalktıktan sonra (8. adım):

```bash
cd /srv/kod/tti-sunucu
docker compose exec db psql -U tti_hakedis -d tti
```

Açılan istemciye:

```sql
create user rapor with password '<RAPOR_DB_PAROLA ile aynı>';
grant create on database tti to rapor;   -- şemayı EF'in ilk migration'ı açar
revoke all on schema public from public;
\q
```

⚠️ Bu adımı **elle** yapmak gerekir. Docker'ın otomatik başlatma betikleri (`initdb.d`) yalnızca
veritabanı **ilk kez** oluşturulurken çalışır; sizde veri zaten varsa hiç çalışmazlar.

İkinci projenin migration ayarı kendi şemasını hedeflemeli (`rapor`), `public`'i
kullanmamalıdır.

---

## 8. İmajları makineye taşıyın ve yığını başlatın

Bu makinede Gradle/Node kurulu olmasına gerek yok — imajlar geliştirici makinesinde üretilir.

**Geliştirici makinesinde:**

```bash
./gradlew :server:bootJar
cd deploy && docker compose build
docker save tti-hakedis-server:dev rapor-server:dev postgres:17 portainer/portainer-ce:latest \
  | gzip > imajlar.tar.gz
```

Dosyayı ağ paylaşımı veya USB ile Windows makinesine taşıyın; diyelim ki `C:\imajlar.tar.gz`.

**Ubuntu terminalinde:**

```bash
cd /srv/kod/tti-sunucu
docker load -i /mnt/c/imajlar.tar.gz
docker compose up -d
docker compose ps
```

Hepsi `healthy` ya da `running` görünmeli.

> ℹ️ **JVM bellek notu:** TTI'nin `Dockerfile`'ı `-XX:MaxRAMPercentage=75.0` ile çalışır; yani
> heap'i konteynerin sınırının %75'i kadar olur. `mem_limit: 900m` verdiğimiz için heap ~675 MB,
> üstüne heap dışı bellek biner ve sığar. **`mem_limit` satırını silerseniz JVM tüm makineyi görür,
> kendine 3 GB ayırır ve yığın çöker.** Daha az bellek vermek isterseniz oranı da düşürün:
> compose'daki `environment` bloğuna `_JAVA_OPTIONS: "-XX:MaxRAMPercentage=55"` ekleyin.

**Doğrulama — Ubuntu'dan:**

```bash
curl http://localhost:8081/api/saglik
```

**Başka bir bilgisayardan** (asıl test budur):

```
http://<makine-ip>:8081
```

Açılmıyorsa 2. adımdaki `mirrored` satırına ve 4. adımdaki güvenlik duvarı kurallarına dönün.

---

## 9. ⚠️ Yeniden başlatma sorunu — en kritik adım

**Sorun:** Linux'ta Docker bir sistem servisidir, makine açılınca kendiliğinden başlar. Windows'ta
ise WSL kendiliğinden başlamaz — biri Ubuntu penceresini açana kadar Linux uyur, dolayısıyla Docker
da uyur.

> Makine yeniden başlar → WSL uyanmaz → Docker çalışmaz → tüm siteler kapalı kalır.

Windows Update gece zorla yeniden başlattığında sabah her şeyin kapalı olmasının sebebi budur.
Üç katmanla çözüyoruz.

### Katman 1 — WSL içinde Docker otomatik başlasın

**Ubuntu terminalinde:**

```bash
sudo nano /etc/wsl.conf
```

İçine ekleyin:

```ini
[boot]
systemd=true
```

Kaydedin (`Ctrl+O`, Enter, `Ctrl+X`). **PowerShell'de** WSL'i kapatın:

```powershell
wsl --shutdown
```

Ubuntu'yu yeniden açıp Docker servisini kalıcı olarak etkinleştirin:

```bash
sudo systemctl enable --now docker
systemctl is-enabled docker      # "enabled" demeli
```

Artık WSL her uyandığında Docker da başlar, `restart: unless-stopped` sayesinde konteynerler de.

### Katman 2 — Windows açılınca WSL uyansın

WSL'i uyandıracak birinin oturum açması gerekir. İki parça:

**a) Windows otomatik giriş yapsın:**

1. `Win + R` → `netplwiz` → Enter
2. "Kullanıcıların bu bilgisayarı kullanmak için kullanıcı adı ve parola girmeleri gerekir"
   kutusundaki işareti **kaldırın**
3. Uygula → parolayı iki kez girin → Tamam

> Bu kutu görünmüyorsa (Windows 11'de bazen gizlidir) Microsoft'un
> [Autologon](https://learn.microsoft.com/sysinternals/downloads/autologon) aracını kullanın.

⚠️ **Güvenlik:** Makine artık açıldığında parolasız masaüstüne düşer. Fiziksel olarak kilitli bir
odada durmalı. IT ekibiyle bu durumu konuşun.

**b) Oturum açılınca WSL'i başlatan görev:**

Windows tarafında `C:\TTI-Sunucu\baslat.bat` dosyasını oluşturun:

```bat
@echo off
REM WSL'i uyandirir, Docker systemd ile kalkar, konteynerleri de garantiye alir.
wsl -d Ubuntu -u root -e /bin/bash -lc "cd /srv/kod/tti-sunucu && docker compose up -d" >> C:\TTI-Sunucu\baslat.log 2>&1

REM --- YALNIZCA WINDOWS 10 ISE: port yonlendirmesini tazele (WSL'in IP'si degisir) ---
REM for /f "tokens=1" %%i in ('wsl hostname -I') do set WSLIP=%%i
REM for %%p in (8081 8082 8083 9443) do (
REM   netsh interface portproxy delete v4tov4 listenport=%%p listenaddress=0.0.0.0 >nul 2>&1
REM   netsh interface portproxy add v4tov4 listenport=%%p listenaddress=0.0.0.0 connectport=%%p connectaddress=%WSLIP%
REM )
```

> Windows 10 kullanıyorsanız `REM` ile başlayan satırlardaki `REM ` önekini silin.

Sonra Görev Zamanlayıcı'da (`Win + R` → `taskschd.msc`):

1. Sağdaki panelden **"Görev Oluştur"**
2. **Genel** sekmesi: Ad = `TTI Sunucu Baslat`, ✅ *En yüksek ayrıcalıklarla çalıştır*
3. **Tetikleyiciler** sekmesi → Yeni:
   - Görevi başlat: **Oturum açıldığında**
   - ✅ Görevi ertele: **1 dakika**
4. **Eylemler** sekmesi → Yeni → Program: `C:\TTI-Sunucu\baslat.bat`
5. **Koşullar** sekmesi: "Yalnızca bilgisayar AC gücündeyse çalıştır" işaretini **kaldırın**
6. Tamam

### Katman 3 — Emniyet kemeri

Aynı görevi bir de periyodik çalıştırın: 3. adımdaki tetikleyiciye dönüp
✅ *Görevi her **10 dakikada** bir yinele, süre: **Süresiz*** kutusunu işaretleyin.

Bir konteyner herhangi bir sebeple düşerse en geç 10 dakika içinde geri gelir.

### Kurulum bittikten sonra mutlaka test edin

**PowerShell:**

```powershell
Restart-Computer
```

Makine geri geldikten **5 dakika sonra**, başka bir bilgisayardan `http://<makine-ip>:8081`
adresini açın. Açılıyorsa bu adım tamamdır. Açılmıyorsa demo sabahı da açılmayacak demektir.

---

## 10. Portainer — IT ekibi için arayüz

Docker komutu bilmeyen birinin konteynerleri görmesi, yeniden başlatması ve log okuması için.
Portainer CE ücretsizdir.

**İlk açılış:**

1. Tarayıcıdan `https://<makine-ip>:9443` (dikkat: **https**)
2. Sertifika uyarısı çıkar — kendi imzalı sertifika kullanıyor, normaldir.
   "Gelişmiş" → "Devam et"
3. Yönetici kullanıcı adı ve parolası belirleyin
4. "Get Started" → yerel Docker ortamını seçin

⚠️ **Portainer, ilk açılıştan sonraki 5 dakika içinde yönetici parolası belirlenmezse güvenlik
gereği kendini kilitler.** Kilitlenirse Ubuntu'da `docker restart portainer` deyip tekrar deneyin.

**IT ekibinin yapabilecekleri:**

| İhtiyaç | Portainer'da |
|---|---|
| Bir şey çalışıyor mu? | **Containers** — yeşil `running`, kırmızı `stopped` |
| Uygulama takıldı, yeniden başlat | Konteyneri seç → **Restart** |
| Hata var, log lazım | Konteyneri seç → **Logs** |
| Ne kadar bellek kullanıyor? | Konteyneri seç → **Stats** |

**IT ekibinin yapmaması gerekenler:** konteyner **silmek** (Remove), volume silmek, imaj silmek.
`tti_db_verisi` volume'ü silinirse **tüm veritabanı gider.**

---

## 11. Günlük işler

Hepsi **Ubuntu terminalinde**, `/srv/kod/tti-sunucu` klasöründe:

```bash
cd /srv/kod/tti-sunucu

docker compose ps                    # ne çalışıyor
docker compose logs -f tti-hakedis-server    # canlı log (Ctrl+C ile çık)
docker compose restart tti-hakedis-server    # tek servisi yeniden başlat
docker compose stop                  # hepsini durdur (veri kalır)
docker compose up -d                 # hepsini başlat
docker stats --no-stream             # bellek kullanımı
```

> 💡 PowerShell'den çıkmadan komut vermek isterseniz başına `wsl` ekleyin:
> `wsl -d Ubuntu -e docker ps`

**Yeni sürüm çıkarma:** geliştirici makinesinde yeni imajı üretip taşıyın, sonra Ubuntu'da:

```bash
docker load -i /mnt/c/imajlar.tar.gz
docker compose up -d                 # değişen imajın konteyneri yeniden oluşturulur
```

---

## 12. Yedekleme

Tek makine, tek disk. Yedek yoksa disk arızası her iki projeyi birden götürür.

Yedeği **elle komut dizerek almayın** — `tti-sunucu/yedek.sh` (platform deposu) bunu yapar, çıktıyı doğrular ve elle
yazılan komutların iki sessiz kusurunu kapatır (0 baytlık sahte dump, TTY'nin bozduğu ikili arşiv).
Ne yedeklendiği, çıkış kodları ve geri yükleme yordamları **`RedHatKurulum.md §12`**'de; komutlar
aynen geçerlidir.

**Ubuntu terminalinde:**

```bash
sudo apt install -y rsync
sudo mkdir -p /srv/veri/yedek
sudo bash /srv/kod/tti-sunucu/yedek.sh
sudo bash /srv/kod/tti-sunucu/yedek.sh dogrula
```

### Zamanlama — WSL'de systemd timer çalışmayabilir

RHEL'den ayrılan tek nokta budur. WSL2'de systemd **varsayılan olarak kapalıdır**, dolayısıyla
`RedHatKurulum.md §12.5`'teki timer doğrudan uygulanamaz. İki yol var:

**a) systemd'yi açın** (Windows 11 + güncel WSL) — sonra §12.5 aynen geçerlidir:

```ini
# /etc/wsl.conf
[boot]
systemd=true
```

Ardından PowerShell'de `wsl --shutdown`, sonra Ubuntu'yu yeniden açın (`systemctl list-timers`
çalışıyorsa hazırsınız).

**b) Windows Görev Zamanlayıcı'dan çağırın** (systemd açılmıyorsa):

- Program: `wsl.exe`
- Bağımsız değişkenler: `-d Ubuntu -u root bash /srv/kod/tti-sunucu/yedek.sh`
- Tetikleyici: her gün 23:30
- ⚠️ "Kullanıcı oturum açmamış olsa da çalıştır" **işaretlenmelidir**, yoksa görev yalnızca siz
  oturumdayken koşar.

⚠️ **WSL'e özgü tuzak:** yedekler WSL'in kendi sanal diskinin (`ext4.vhdx`) içindedir. Distro
kaldırılır ya da bozulursa **yedekler de gider** — bu, RHEL'deki "aynı disk" uyarısından daha
ciddidir. Windows tarafından `\\wsl.localhost\Ubuntu\srv\tti-hakedis\yedek` yoluyla erişip düzenli
olarak `C:` sürücüsüne ya da bir ağ paylaşımına kopyalayın.

---

## 13. Sorun giderme

| Belirti | Sebep | Çözüm |
|---|---|---|
| Başka bilgisayardan site açılmıyor, makinede açılıyor | `mirrored` yok ya da güvenlik duvarı | 2. ve 4. adım |
| Hiçbir şey açılmıyor, makine yeni yeniden başladı | WSL uyanmamış | 9. adım; `wsl -d Ubuntu -e true` |
| `docker` komutu bulunamıyor | PowerShell'de yazıyorsunuz | Ubuntu terminalinde çalıştırın (0b) |
| `permission denied` / docker socket hatası | Grup üyeliği aktif değil | `wsl --shutdown` sonra tekrar açın |
| Konteyner sürekli yeniden başlıyor | Bellek yetmiyor (OOM) | `docker stats`; `mem_limit` düşürün |
| `tti-hakedis-server` başlamıyor, log'da veritabanı hatası | Parola uyuşmuyor | `/srv/kod/tti-sunucu/.env` ile `deploy/.env` aynı olmalı |
| Makinenin IP'si değişti | DHCP | IT'den sabit IP isteyin |
| Windows çok yavaşladı | WSL bellek sınırı yok | 2. adımdaki `.wslconfig` |

**Mirrored moda rağmen dışarıdan bağlanılamıyorsa** Hyper-V güvenlik duvarı engelliyor olabilir.
PowerShell (yönetici):

```powershell
Set-NetFirewallHyperVVMSetting -Name '{40E0AC32-46A5-438A-A0B2-2B479E8F2E90}' -DefaultInboundAction Allow
```

**Log dosyaları:** Ubuntu'da `docker compose logs <servis>`, ya da Portainer → Containers → Logs.
Başlangıç görevinin log'u: `C:\TTI-Sunucu\baslat.log`.

---

## Kurulum kontrol listesi

Sırayla işaretleyin:

- [ ] `winver` ile Windows sürümü kontrol edildi (11 22H2+ değilse 4b gerekli)
- [ ] WSL2 + Ubuntu kuruldu (`wsl --version` → 2.x, 0.67.6+)
- [ ] `.wslconfig` yazıldı (`memory`, `mirrored`), `wsl --shutdown` yapıldı
- [ ] Docker Engine kuruldu, `docker run hello-world` çalıştı
- [ ] Kullanıcı `docker` grubuna eklendi
- [ ] Güvenlik duvarı kuralları eklendi (8081, 8082, 8083, 9443)
- [ ] Makinenin sabit IP'si alındı ve not edildi: `________________`
- [ ] `sudo bash deploy/kur.sh` çalıştırıldı, `DB_PAROLA` değiştirildi
- [ ] `docker-compose.yml` ve `.env` `/srv/kod/tti-sunucu` altında
- [ ] İmajlar `docker load` ile yüklendi
- [ ] `docker compose up -d` → hepsi healthy
- [ ] İkinci proje için şema + kullanıcı açıldı
- [ ] **Başka bir bilgisayardan** her iki site de açıldı
- [ ] Portainer'a girildi, yönetici parolası belirlendi
- [ ] `/etc/wsl.conf` → `systemd=true`, `systemctl enable docker`
- [ ] Otomatik oturum açma ayarlandı
- [ ] Görev Zamanlayıcı görevi eklendi (oturum açılışı + 10 dk yineleme)
- [ ] **Makine yeniden başlatıldı, her şey kendiliğinden ayağa kalktı**
- [ ] Bir kez yedek alındı

Son madde işaretlenmeden kurulum bitmiş sayılmaz.
