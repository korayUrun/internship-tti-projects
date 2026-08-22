# Windows Kurulumu — Konteynersiz, İki Proje Tek Makinede

Windows 10/11 bir makinede TTI Saha Hakediş ve ikinci backend'in **Docker olmadan**, doğrudan
işletim sistemine kurularak çalıştırılması.

> Hangi rehber?
> - **Bu dosya** — Windows makine, konteynersiz (Docker yok, WSL yok)
> - `WindowsDeploy.md` — Windows makine, WSL2 + Docker Engine ile
> - `RedHatKurulum.md` — Red Hat sunucu, konteynerle
> - `KURULUM.md` — yalnızca TTI'nin kendi ayarları (`.env`, arşiv klasörleri, kable içe aktarımı)

**Bu yolun avantajı:** `WindowsDeploy.md` §9'un tamamı — WSL'in yeniden başlatmada uyanmaması,
otomatik oturum açma, Görev Zamanlayıcı, port yönlendirmesi — WSL yüzünden vardır. Docker olmayınca
o katmanın hepsi düşer; yerini sıradan bir Windows Servisi alır. Operasyonel olarak en basit
senaryodur.

**Karşılığında:** işletim sistemi kirlenir (üç ayrı kurulum, üç ayrı sürüm takibi) ve iki proje
birbirinden yalıtılmaz. Tek makinede birkaç kullanıcıya hizmet verecek bir kurulum için kabul
edilebilir bir takas.

---

## 0. Ne kuracağız

Dört bileşen, hepsi **Windows Servisi** olarak:

| Servis | Ne işe yarar | Port | Ağdan erişim |
|---|---|---|---|
| `postgresql-x64-17` | PostgreSQL 17 — **iki proje de bunu paylaşır**, ayrı şemalarda | 5432 | ❌ sadece makinenin kendisi |
| `tti-hakedis` | TTI backend'i **+ web arayüzü** (jar'ın içinde gömülü) | 8081 | ✅ |
| `proje2` | İkinci projenin backend'i (.NET) | 8082 | ✅ |

Kurulmayacaklar: Docker, WSL, Node.js, Gradle, .NET SDK. Bunların hepsi **geliştirici makinesinde**
gerekir, sunucuda değil.

**Makine gereksinimi:** 8 GB RAM, 100 GB disk.

> ⚠️ **Windows 10 kullanıyorsanız:** desteği **Ekim 2025'te sona erdi**, yeni güvenlik yaması
> almıyor. Kurulum çalışır, ama makine kurumsal ağda duracaksa IT ekibine bu durumu bildirin ve
> orta vadede Windows 11 veya Windows Server planlayın. Rehberdeki adımların hiçbiri Windows 11'de
> değişmez.

---

## 1. ⛔ Ön kontrol — ikinci proje hangi .NET?

Bu rehber her iki .NET sürümünde de çalışır, ama **6. adım ikiye ayrılır**. Şimdiden öğrenin.
İkinci projenin `.csproj` dosyasını açın:

```xml
<TargetFramework>net8.0</TargetFramework>                <!-- .NET Core ailesi -->
<TargetFrameworkVersion>v4.8</TargetFrameworkVersion>    <!-- .NET Framework -->
```

PowerShell'den:

```powershell
Select-String -Path *.csproj -Pattern "TargetFramework"
```

| Çıkan değer | 6. adımda izlenecek yol |
|---|---|
| `net6.0`, `net8.0`, `net9.0` | **6a** — Kestrel, servis olarak. IIS kurulmaz. |
| `v4.5` … `v4.8`, `net48` | **6b** — IIS zorunlu. |

Not edin: `____________`

---

## 2. PostgreSQL 17

[EDB installer](https://www.enterprisedb.com/downloads/postgres-postgresql-downloads) ile kurulur.

### ⚠️ Kurulumun tek kritik ekranı: **Locale**

Kurulum sihirbazı "Advanced Options" adımında bir **Locale** sorar. Buraya **`C`** seçin.

Neden: TTI'nin Docker kurulumu veritabanını `--locale=C --encoding=UTF8` ile açar
(`deploy/docker-compose.yml`). Türkçe locale ile kurulursa sıralama ve karşılaştırma farklı davranır
— `I ↔ ı`, `İ ↔ i` katlaması değişir, poz sıralaması ve arama sonuçları sessizce kayar. **Hiçbir
hata çıkmaz**, sadece sonuçlar yanlış olur. Bu yüzden bu ekran geçiştirilmez.

Diğer adımlar:

| Ekran | Değer |
|---|---|
| Installation Directory | varsayılan (`C:\Program Files\PostgreSQL\17`) |
| Data Directory | varsayılan |
| Password | `postgres` süper kullanıcısının parolası — **not edin** |
| Port | `5432` |
| Locale | **`C`** ⚠️ |
| Stack Builder | **atlayın**, gerekmiyor |

### Kurulumu doğrulayın

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -c "show lc_collate;"
```

`C` demeli. Başka bir şey diyorsa kurulumu kaldırıp locale'i `C` seçerek baştan kurun — sonradan
değiştirilemez.

### Veritabanını ağa kapatın

`C:\Program Files\PostgreSQL\17\data\postgresql.conf` dosyasını Not Defteri'yle (yönetici olarak)
açın ve şu satırı bulun:

```ini
listen_addresses = 'localhost'
```

Zaten `localhost` ise dokunmayın. `'*'` yazıyorsa `'localhost'` yapın — veritabanı ağa açılmaz,
ona yalnızca aynı makinedeki iki backend erişir.

Sonra servisi yeniden başlatın:

```powershell
Restart-Service postgresql-x64-17
```

### Veritabanını, kullanıcıları ve şemaları oluşturun

```powershell
cd "C:\Program Files\PostgreSQL\17\bin"
.\psql.exe -U postgres
```

Açılan istemciye:

```sql
create database tti;
create user tti_hakedis with password '<TTI icin guclu bir parola>';
grant all privileges on database tti to tti_hakedis;
\c tti
create user proje2 with password '<PROJE2 icin guclu bir parola>';
create schema proje2 authorization proje2;
revoke all on schema public from public;
\q
```

⚠️ **TTI'nin `hakedis` şemasını elle açmayın.** Flyway kendisi açar
(`server/src/main/resources/application.yml` → `flyway.create-schemas: true`). Elle açılan şema
Flyway'in sürüm tablosuyla çakışabilir.

İkinci projenin migration ayarı kendi şemasını (`proje2`) hedeflemeli, `public`'i kullanmamalıdır.

---

## 3. Java 17

TTI bir Spring Boot uygulamasıdır ve JDK 17 ister.
[Eclipse Temurin 17 (LTS)](https://adoptium.net/temurin/releases/?version=17) MSI paketini indirip
kurun. Kurulumda **"Set JAVA_HOME variable"** ve **"Add to PATH"** seçeneklerini işaretleyin.

Doğrulayın — **yeni** bir PowerShell penceresinde:

```powershell
java -version
```

`openjdk version "17.x.x"` görmelisiniz.

> Makinede başka bir Java sürümü de varsa sorun değil; 5. adımda servis tanımına Java'nın **tam
> yolunu** yazacağız, PATH'e güvenmeyeceğiz.

---

## 4. Klasörler ve dosyaların taşınması

### 4a. Klasörleri açın

Depoyu makineye alın ve hazırlık betiğini çalıştırın:

```powershell
powershell -ExecutionPolicy Bypass -File deploy\kur.ps1
```

Betik `%USERPROFILE%\TTI-Hakedis\arsiv` ve `...\yukleme` klasörlerini açar.

> Betik ayrıca bir `deploy\.env` dosyası üretir. **Bu senaryoda `.env` okunmaz** — o dosya Docker
> Compose içindir. Değerleri 5. adımda servis tanımına yazacağız. Betiği yine de çalıştırın:
> klasörleri doğru yerde açıyor ve yolları ekrana basıyor.

⚠️ **İki klasör vardır ve ayrı kalmalıdır:**

| Klasör | İçerik | Kaybolursa |
|---|---|---|
| `arsiv` | Sunucunun **ürettiği** PDF/Excel | Yeniden üretilir |
| `yukleme` | Kullanıcının **yüklediği** EK1/EK2 dosyaları | **Geri gelmez** |

### 4b. Jar'ı geliştirici makinesinde üretin

Bu makinede derleme yapılmaz. Geliştirici makinesinde:

```bash
./gradlew :server:bootJar          # → server/build/libs/tti-hakedis-server.jar  (~53 MB)
Get-FileHash server\build\libs\tti-hakedis-server.jar -Algorithm SHA256   # değeri not edin
```

> Jar'ı derleyen makinede **Node.js bulunmalıdır** — `bootJar` web arayüzünü de derleyip jar'ın
> içine gömer. Node yoksa `-PwebAtla` ile atlanır, ama çıkan jar'da **API çalışır, arayüz
> bulunmaz**.

İkinci proje .NET Core ise (6a yolu):

```bash
dotnet publish -c Release -o yayin
```

### 4c. Sunucuya kopyalayın ve **doğrulayın**

```powershell
New-Item -ItemType Directory -Force -Path C:\TTI-Sunucu
# jar'ı ve ikinci projenin çıktısını buraya kopyalayın
Get-FileHash C:\TTI-Sunucu\tti-hakedis-server.jar -Algorithm SHA256
```

⚠️ **Çıkan değeri 4b'de not ettiğinizle karşılaştırın.** Elle taşımada (özellikle mesajlaşma
uygulamaları üzerinden) dosya sessizce bozulabilir; jar bozuksa hata ancak servis başlarken,
anlaşılmaz bir stack trace olarak çıkar.

---

## 5. TTI'yi Windows Servisi yapın

`java -jar` komutunu bir pencerede elle çalıştırmak **çözüm değildir** — pencere kapanınca uygulama
ölür, makine yeniden başlayınca geri gelmez. Servis olması gerekir.

Bunun için [WinSW](https://github.com/winsw/winsw/releases) kullanacağız: MIT lisanslı, tek `.exe`,
kurulum gerektirmez ve ortam değişkenlerini servis tanımının içinde tutar.

> **Ortam değişkenlerini neden makine geneline yazmıyoruz:** `DB_URL`, `DB_PAROLA` gibi adlar
> geneldir. Makine geneline yazılırsa iki proje birbirinin değerini görür ve biri diğerinin
> veritabanına bağlanmaya çalışır. Servis tanımının içinde tutulunca bu mümkün değildir.

### 5a. WinSW'yi yerleştirin

`WinSW-x64.exe` dosyasını indirip `C:\TTI-Sunucu\` altına **`tti-hakedis.exe`** adıyla kopyalayın.
(WinSW, kendi adıyla aynı adı taşıyan `.xml` dosyasını okur.)

### 5b. Servis tanımını yazın

`C:\TTI-Sunucu\tti-hakedis.xml`:

```xml
<service>
  <id>tti-hakedis</id>
  <name>TTI Saha Hakedis</name>
  <description>TTI Saha Hakedis backend ve web arayuzu</description>

  <executable>C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe</executable>
  <arguments>-XX:MaxRAMPercentage=40 -jar C:\TTI-Sunucu\tti-hakedis-server.jar</arguments>

  <env name="DB_URL"       value="jdbc:postgresql://localhost:5432/tti"/>
  <env name="DB_KULLANICI" value="tti_hakedis"/>
  <env name="DB_PAROLA"    value="&lt;2. adimda verdiginiz TTI parolasi&gt;"/>
  <env name="ARSIV_KOK"    value="C:/Users/&lt;kullanici&gt;/TTI-Hakedis/arsiv"/>
  <env name="YUKLEME_KOK"  value="C:/Users/&lt;kullanici&gt;/TTI-Hakedis/yukleme"/>
  <env name="TZ"           value="Europe/Istanbul"/>

  <logpath>C:\TTI-Sunucu\log</logpath>
  <log mode="roll-by-size">
    <sizeThreshold>10240</sizeThreshold>
    <keepFiles>8</keepFiles>
  </log>

  <onfailure action="restart" delay="10 sec"/>
  <startmode>Automatic</startmode>
  <delayedAutoStart>true</delayedAutoStart>
  <depend>postgresql-x64-17</depend>
</service>
```

Doldurulacak yerler:

| Satır | Ne yazılacak |
|---|---|
| `<executable>` | 3. adımda kurduğunuz Java'nın **tam yolu**. Bulmak için: `where.exe java` |
| `DB_PAROLA` | 2. adımda `tti_hakedis` kullanıcısına verdiğiniz parola |
| `ARSIV_KOK` / `YUKLEME_KOK` | 4a'da betiğin ekrana bastığı yollar |

Ayrıntılar, sonradan "acaba neden böyle" dedirtmemesi için:

- **Değişken adları uydurulmamıştır** — `server/src/main/resources/application.yml` dosyasındaki
  `${DB_URL}`, `${DB_KULLANICI}`, `${DB_PAROLA}`, `${ARSIV_KOK}`, `${YUKLEME_KOK}` ile birebir
  aynıdır. Yeni bir değişken eklemeniz gerekirse önce o dosyaya bakın.
- **`MaxRAMPercentage=40`** — Docker kurulumunda bu değer `75`'tir, çünkü orada JVM konteynerin
  sınırını görür. Burada konteyner yok: JVM **makinenin tamamını** görür ve `75` verilirse 8 GB'lık
  bir makinede kendine 6 GB ayırıp PostgreSQL'i ve ikinci projeyi aç bırakır. `40` ≈ 3,2 GB.
- **`ARSIV_KOK` ileri eğik çizgi (`/`) ile yazılmıştır** — `kur.ps1` betiği de böyle üretir. Java
  Windows'ta her iki biçimi de kabul eder; ters eğik çizgi ise XML içinde kaçış sorunu çıkarabilir.
- **`<depend>`** — PostgreSQL'den önce başlarsa TTI veritabanına bağlanamaz ve düşer. Bu satır
  Windows'a "önce onu başlat" der.
- **`delayedAutoStart`** — açılışta disk yoğunken sıraya girer; ilk denemede düşüp yeniden başlamak
  zorunda kalmaz.

### 5c. Servisi kurun ve başlatın

**PowerShell (yönetici olarak):**

```powershell
cd C:\TTI-Sunucu
.\tti-hakedis.exe install
Start-Service tti-hakedis
Get-Service tti-hakedis          # Status: Running
```

Başlamazsa `C:\TTI-Sunucu\log\tti-hakedis.err.log` dosyasına bakın.

---

## 6. İkinci projeyi çalıştırın

1. adımda not ettiğiniz değere göre **6a** veya **6b**.

### 6a. `.NET 6/8/9` ise — Kestrel, servis olarak *(IIS kurulmaz)*

ASP.NET Core uygulamaları kendi web sunucularını (Kestrel) taşır; IIS'e ihtiyaçları yoktur ve
IIS'siz kurulum hem daha basit hem de aşağıdaki 10-bağlantı sınırından muaftır.

1. [ASP.NET Core Runtime](https://dotnet.microsoft.com/download/dotnet) kurun (SDK değil, Runtime
   yeter). Sürüm `.csproj`'daki `TargetFramework` ile eşleşmeli.
2. `dotnet publish` çıktısını `C:\Proje2\` altına kopyalayın.
3. WinSW'yi ikinci kez, `C:\Proje2\proje2.exe` adıyla yerleştirin.
4. `C:\Proje2\proje2.xml`:

```xml
<service>
  <id>proje2</id>
  <name>Proje2 Backend</name>
  <executable>C:\Program Files\dotnet\dotnet.exe</executable>
  <arguments>C:\Proje2\Proje2.dll</arguments>

  <env name="ASPNETCORE_URLS" value="http://+:8082"/>
  <env name="ASPNETCORE_ENVIRONMENT" value="Production"/>
  <env name="ConnectionStrings__Default"
       value="Host=localhost;Port=5432;Database=tti;Username=proje2;Password=&lt;PROJE2 parolasi&gt;;SearchPath=proje2"/>

  <logpath>C:\Proje2\log</logpath>
  <onfailure action="restart" delay="10 sec"/>
  <startmode>Automatic</startmode>
  <delayedAutoStart>true</delayedAutoStart>
  <depend>postgresql-x64-17</depend>
</service>
```

> `ConnectionStrings__Default` adı örnektir — ikinci projenin `appsettings.json` dosyasındaki
> gerçek anahtar adını kullanın. `SearchPath=proje2` kısmı **önemlidir**: onsuz uygulama `public`
> şemasına yazmaya çalışır ve iki projenin ayrımı bozulur.

```powershell
cd C:\Proje2
.\proje2.exe install
Start-Service proje2
```

### 6b. `.NET Framework 4.x` ise — IIS

.NET Framework kendi başına HTTP servis edemez; IIS zorunludur.

1. **Denetim Masası → Programlar → Windows özelliklerini aç/kapat**
   - ✅ Internet Information Services
   - ✅ World Wide Web Services → Uygulama Geliştirme Özellikleri → **ASP.NET 4.8**
2. `inetmgr` → Siteler → **Web Sitesi Ekle**
   - Site adı: `proje2`
   - Fiziksel yol: `C:\Proje2`
   - Bağlama: Tür `http`, Port **`8082`**
3. **Uygulama Havuzu** → `proje2` → Gelişmiş Ayarlar
   - .NET CLR sürümü: `v4.0`
   - Kimlik: veritabanına erişebilecek bir hesap (`ApplicationPoolIdentity` yeterliyse dokunmayın —
     bağlantı dizesinde kullanıcı adı/parola veriyorsanız zaten yeter)
   - **Boşta Zaman Aşımı (Idle Time-out): `0`** — varsayılan 20 dakikadır ve site kimse girmediğinde
     kapanır; ilk giren kişi uzun bir bekleme yaşar.
   - **Geri Dönüştürme → Düzenli Aralık: `0`** — varsayılan 29 saatte bir havuzu yeniden başlatır.
4. `C:\Proje2\Web.config` içindeki bağlantı dizesi `proje2` şemasını hedeflemeli.

> ⚠️ **Windows'un istemci sürümlerinde (10/11 Pro dâhil) IIS aynı anda 10 isteği işler.** 11.
> istek sıraya girer. Birkaç kullanıcı için sorun değildir, ama sınırın varlığını bilin: yük
> altında sebebi anlaşılmayan bir yavaşlama olarak görünür ve hiçbir log'da "sınır" yazmaz. Bu
> sınır Windows **Server**'da yoktur.

---

## 7. Güvenlik duvarı

Windows Defender dışarıdan gelen bağlantıları varsayılan olarak engeller. Bu adım yapılmadan
kimse sitelere ulaşamaz.

**PowerShell (yönetici):**

```powershell
New-NetFirewallRule -DisplayName "TTI Backend"    -Direction Inbound -LocalPort 8081 -Protocol TCP -Action Allow -Profile Domain,Private
New-NetFirewallRule -DisplayName "Proje2 Backend" -Direction Inbound -LocalPort 8082 -Protocol TCP -Action Allow -Profile Domain,Private
```

`-Profile Domain,Private` bilinçlidir: yalnızca şirket ağında açılır, halka açık Wi-Fi'de kapalı
kalır.

**5432 için kural EKLEMEYİN.** Veritabanı 2. adımda zaten `localhost`'a kapatıldı.

**Makinenin IP adresini öğrenin** (kullanıcılara vereceğiniz adres):

```powershell
ipconfig | findstr IPv4
```

> 💡 IT ekibinden bu IP'ye **sabit adres** (DHCP rezervasyonu) ayırmalarını isteyin. Yoksa makine
> yeniden başladığında IP değişir ve verdiğiniz bağlantılar ölür.

---

## 8. Yeniden başlatmada kendiliğinden kalkma

Bu senaryonun en rahat kısmı burasıdır. Windows Servisleri açılışta kendiliğinden başlar; WSL'i
uyandırmak, otomatik oturum açmak ya da Görev Zamanlayıcı kurmak **gerekmez**.
(`WindowsDeploy.md` §9'un tamamı bu senaryoda geçersizdir — o adımlar WSL yüzünden vardı.)

Yine de iki şeyi doğrulayın. **PowerShell (yönetici):**

```powershell
# Üçü de Automatic (Delayed Start) olmalı
Get-Service postgresql-x64-17, tti-hakedis, proje2 | Select-Object Name, Status, StartType

# Hata durumunda yeniden başlatma — WinSW zaten ayarlıyor, PostgreSQL için elle:
sc.exe failure postgresql-x64-17 reset= 86400 actions= restart/10000/restart/30000/restart/60000
```

IIS kullandıysanız (6b): IIS siteleri zaten `W3SVC` servisiyle otomatik başlar, ek ayar gerekmez.

### Kurulum bittikten sonra mutlaka test edin

```powershell
Restart-Computer
```

Makine geri geldikten **3 dakika sonra**, başka bir bilgisayardan `http://<makine-ip>:8081`
adresini açın. Açılıyorsa bu adım tamamdır. Açılmıyorsa demo sabahı da açılmayacak demektir.

---

## 9. Doğrulama

**Makineden:**

```powershell
curl.exe http://localhost:8081/api/saglik
Get-Service postgresql-x64-17, tti-hakedis, proje2
```

**Başka bir bilgisayardan** — asıl test budur:

```
http://<makine-ip>:8081        → TTI (giriş ekranı)
http://<makine-ip>:8082        → ikinci proje
```

**Uygulama düzeyi doğrulama** (depo makinede duruyorsa):

```bash
bash deploy/dogrula.sh          # yetki süzgeci, kable araması, para hassasiyeti
```

> Bu bir bash betiğidir ve `curl` ile `python3` ister; Windows'ta **Git Bash** içinde, Python
> kuruluyken çalışır. İkisi de yoksa bu adımı atlayabilirsiniz — kurulumun doğruluğunu yukarıdaki
> tarayıcı testi de gösterir. Betiği geliştirici makinesinden bu makineye yönelterek de
> koşturabilirsiniz: `KOK=http://<makine-ip>:8081 bash deploy/dogrula.sh`.

---

## 10. Günlük işler

**PowerShell (yönetici):**

```powershell
Get-Service tti-hakedis, proje2, postgresql-x64-17     # ne çalışıyor
Restart-Service tti-hakedis                            # yeniden başlat
Stop-Service tti-hakedis                               # durdur
Start-Service tti-hakedis                              # başlat
Get-Content C:\TTI-Sunucu\log\tti-hakedis.out.log -Tail 50 -Wait   # canlı log
```

**Yeni sürüm çıkarma:**

```powershell
Stop-Service tti-hakedis
# yeni jar'ı C:\TTI-Sunucu\tti-hakedis-server.jar üzerine kopyalayın (hash'ini doğrulayın)
Start-Service tti-hakedis
```

Servis dosyayı kilitlediği için **önce durdurmak zorunludur**; çalışırken kopyalama "dosya
kullanımda" hatası verir.

---

## 11. Yedekleme

Tek makine, tek disk. Yedek yoksa disk arızası her iki projeyi birden götürür.

`C:\TTI-Sunucu\yedek.ps1`:

```powershell
$tarih = Get-Date -Format "yyyy-MM-dd"
$yedek = "C:\TTI-Sunucu\yedek"
$pg    = "C:\Program Files\PostgreSQL\17\bin"
$env:PGPASSWORD = "<tti_hakedis parolasi>"

New-Item -ItemType Directory -Force -Path $yedek | Out-Null

# Veritabani — her sema ayri dosyaya (biri geri yuklenirken digeri bozulmasin)
& "$pg\pg_dump.exe" -U tti_hakedis -d tti -n hakedis -Fc -f "$yedek\hakedis-$tarih.dump"
& "$pg\pg_dump.exe" -U tti_hakedis -d tti -n proje2  -Fc -f "$yedek\proje2-$tarih.dump"

# Yuklenen dosyalar — VERITABANINDA DEGILDIR, ayrica yedeklenmeli
Compress-Archive -Path "$env:USERPROFILE\TTI-Hakedis\yukleme\*" `
                 -DestinationPath "$yedek\yukleme-$tarih.zip" -Force
```

Günlük çalıştırmak için Görev Zamanlayıcı'da (`taskschd.msc`) bir görev oluşturun: tetikleyici
"Her gün 23:00", eylem `powershell.exe -ExecutionPolicy Bypass -File C:\TTI-Sunucu\yedek.ps1`.

Geri yükleme:

```powershell
& "C:\Program Files\PostgreSQL\17\bin\pg_restore.exe" -U tti_hakedis -d tti --clean --if-exists "C:\TTI-Sunucu\yedek\hakedis-2026-08-01.dump"
```

⚠️ Yedek dosyalarını **bu makineden başka bir yere** kopyalayın — ağ paylaşımına ya da harici diske.
Aynı diskteki yedek, disk bozulduğunda yedek değildir.

---

## 12. Sorun giderme

| Belirti | Sebep | Çözüm |
|---|---|---|
| Başka bilgisayardan site açılmıyor, makinede açılıyor | Güvenlik duvarı | 7. adım |
| Servis başlamıyor, hemen duruyor | Log'a bakılmamış | `C:\TTI-Sunucu\log\tti-hakedis.err.log` |
| Log'da `Connection refused` / veritabanı hatası | PostgreSQL henüz kalkmamış ya da parola yanlış | 5b'deki `<depend>` satırı; `DB_PAROLA` |
| Açılıştan sonra TTI çalışmıyor ama elle başlayınca çalışıyor | `<depend>` satırı eksik | 5b |
| Sıralama/arama sonuçları Docker kurulumundan farklı | Locale `C` değil | 2. adım — PostgreSQL yeniden kurulmalı |
| Makine yavaşladı, her şey takılıyor | JVM çok bellek almış | `MaxRAMPercentage` değerini düşürün (5b) |
| Site ilk açılışta çok yavaş, sonra normal (IIS) | Uygulama havuzu boşta kapanmış | 6b — Idle Time-out `0` |
| Yük altında sebepsiz yavaşlama (IIS) | İstemci Windows'un 10 istek sınırı | 6b'deki uyarı — Windows Server gerekir |
| Jar başlarken anlamsız hata | Taşımada bozulmuş | 4c'deki `Get-FileHash` |
| Yeni jar kopyalanamıyor, "dosya kullanımda" | Servis çalışıyor | Önce `Stop-Service` (10. adım) |
| Makinenin IP'si değişti | DHCP | IT'den sabit IP isteyin |

---

## Kurulum kontrol listesi

Sırayla işaretleyin:

- [ ] İkinci projenin `TargetFramework` değeri not edildi: `____________` (1. adım)
- [ ] PostgreSQL 17 kuruldu, **`show lc_collate;` → `C`** doğrulandı
- [ ] `listen_addresses = 'localhost'` kontrol edildi
- [ ] `tti` veritabanı, `tti_hakedis` ve `proje2` kullanıcıları, `proje2` şeması oluşturuldu
- [ ] JDK 17 kuruldu, `java -version` çalıştı
- [ ] `deploy\kur.ps1` çalıştırıldı, klasör yolları not edildi
- [ ] Jar taşındı ve **`Get-FileHash` karşılaştırması yapıldı**
- [ ] `tti-hakedis` servisi kuruldu ve başladı
- [ ] İkinci proje çalışıyor (6a Kestrel servisi **veya** 6b IIS sitesi)
- [ ] Güvenlik duvarında 8081 ve 8082 açıldı, 5432 kapalı bırakıldı
- [ ] Makinenin sabit IP'si alındı ve not edildi: `________________`
- [ ] **Başka bir bilgisayardan** her iki site de açıldı
- [ ] Üç servis de `Automatic (Delayed Start)`, hata kurtarması ayarlı
- [ ] **Makine yeniden başlatıldı, her şey kendiliğinden ayağa kalktı**
- [ ] `yedek.ps1` bir kez elle çalıştırıldı, Görev Zamanlayıcı'ya eklendi

Son madde işaretlenmeden kurulum bitmiş sayılmaz.
