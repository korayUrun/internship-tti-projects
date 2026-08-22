# Docker Dokümantasyonu

> Bu doküman, Docker'ı hiç bilmeyen ekip arkadaşları için hazırlanmıştır.
> Amacımız Docker'ı en basit haliyle anlatmak ve sunucuda hangi komutun ne işe yaradığını göstermektir.

---

## İçindekiler

1. [Docker Nedir?](#1-docker-nedir)
2. [Docker Ne İçin Kullanılır?](#2-docker-ne-için-kullanılır)
3. [Container ve Image Nedir?](#3-container-ve-image-nedir)
4. [Sunucuda Hangi Komut Ne İşe Yarar?](#4-sunucuda-hangi-komut-ne-işe-yarar)
5. [Sık Karşılaşılan Durumlar](#5-sık-karşılaşılan-durumlar)
6. [Özet Komut Tablosu](#6-özet-komut-tablosu)

---

## 1. Docker Nedir?

Docker, bir uygulamayı **çalışması için gereken her şeyle birlikte tek bir paket** haline getiren bir araçtır.

Bir uygulamanın çalışması için genellikle şunlar gerekir:
- Uygulamanın kendi kodu
- Programlama dili (örneğin Python, Node.js, Java)
- Kütüphaneler ve ayarlar
- Bazen bir veritabanı

Normalde bunların hepsini her bilgisayara **tek tek kurmanız** gerekir. Bu hem zaman alır hem de "benim bilgisayarımda çalışıyordu ama sunucuda çalışmadı" gibi sorunlara yol açar.

Docker bu sorunu çözer. Uygulamayı ve gereken her şeyi **kutu (container)** içine koyar. Bu kutu her yerde **aynı şekilde** çalışır: sizin bilgisayarınızda da, sunucuda da, başka bir çalışanın bilgisayarında da.

### Basit Bir Benzetme

Docker'ı **hazır yemek kabı** gibi düşünün:

- Bir yemeği (uygulama) pişirir, gereken tüm malzemelerle birlikte kaba koyarsınız.
- Bu kabı istediğiniz mutfağa (bilgisayar/sunucu) götürebilirsiniz.
- Hangi mutfağa götürürseniz götürün, yemek **aynı tada** sahip olur çünkü içindeki her şey hazır ve sabittir.

> **Kısacası:** Docker, "Bende çalışıyor ama sende çalışmıyor" sorununu ortadan kaldırır.

---

## 2. Docker Ne İçin Kullanılır?

Docker temel olarak şu amaçlarla kullanılır:

### ✅ Her Yerde Aynı Şekilde Çalıştırmak
Uygulama, geliştirici bilgisayarında da sunucuda da tıpatıp aynı çalışır. Kurulum farklılıklarından kaynaklanan hatalar ortadan kalkar.

### ✅ Kolay Kurulum
Yeni bir sunucuya uygulamayı kurmak için saatlerce program yüklemek yerine, **tek bir komutla** her şey hazır hale gelir.

### ✅ Temiz ve Düzenli Sistem
Uygulamalar birbirinden **izole (ayrı)** çalışır. Bir uygulama diğerini bozmaz. Sunucunuz gereksiz kurulumlarla kirlenmez.

### ✅ Hızlı Taşıma ve Yedekleme
Uygulamayı bir sunucudan diğerine taşımak çok kolaydır. Kutuyu alır, diğer sunucuda çalıştırırsınız.

### ✅ Birden Fazla Uygulamayı Yan Yana Çalıştırmak
Aynı sunucuda farklı uygulamaları (örneğin web sitesi + veritabanı + arka plan servisi) birbirine karışmadan çalıştırabilirsiniz.

---

## 3. Container ve Image Nedir?

Bu ikisi Docker'ın en temel kavramlarıdır. Karıştırılması çok normaldir, o yüzden basit bir benzetmeyle açıklayalım.

### 📦 Image (İmaj) = Kalıp / Tarif

Image, uygulamanın **kalıbıdır**. İçinde uygulama ve gereken her şey vardır ama **henüz çalışmıyordur**. Sadece hazır durur.

**Benzetme:** Image bir **yemek tarifidir** ya da bir **kek kalıbıdır**. Tarif tek başına yenmez; ondan yemek yapmanız gerekir.

### 🚀 Container (Konteyner) = Çalışan Kutu

Container, bir image'in **çalışan halidir**. Image'i çalıştırdığınızda ortaya bir container çıkar. Asıl işi yapan şey budur.

**Benzetme:** Tarifi (image) kullanarak pişirdiğiniz **gerçek yemek** container'dır. Aynı tariften birden fazla yemek yapabileceğiniz gibi, aynı image'den birden fazla container çalıştırabilirsiniz.

### İlişkileri

```
   IMAGE  (Kalıp / Tarif - hazır durur, çalışmaz)
     │
     │  çalıştırılınca →
     ▼
 CONTAINER  (Çalışan uygulama - asıl iş burada döner)
```

| Kavram | Nedir? | Durumu | Benzetme |
|--------|--------|--------|----------|
| **Image** | Uygulamanın hazır paketi/kalıbı | Durur, çalışmaz | Yemek tarifi |
| **Container** | Image'in çalışan hali | Aktif çalışır | Pişmiş yemek |

> **Not:** Bir başka kavram da **Dockerfile**'dır. Dockerfile, image'in nasıl oluşturulacağını anlatan **talimat dosyasıdır** (yani tarifin yazıldığı kağıt). Genellikle projeyi hazırlayan geliştirici tarafından yazılır; siz sunucuda çoğunlukla hazır image'leri çalıştırırsınız.

---

## 4. Sunucuda Hangi Komut Ne İşe Yarar?

Aşağıdaki komutları sunucuda **terminal (komut satırı)** üzerinden çalıştırırsınız.

> ⚠️ **Önemli:** Bazı sunucularda komutların başına `sudo` yazmanız gerekebilir (yönetici yetkisi için). Örnek: `sudo docker ps`

### 🔹 Çalışan Uygulamaları Görmek

```bash
docker ps
```
**Ne yapar:** Şu anda **çalışan** container'ları (uygulamaları) listeler. "Uygulamam ayakta mı?" diye kontrol etmek için kullanılır.

```bash
docker ps -a
```
**Ne yapar:** Çalışan + **durmuş** tüm container'ları listeler. Bir uygulama durduysa/çöktüyse burada görünür.

---

### 🔹 Uygulamaları Başlatmak ve Durdurmak

```bash
docker start <isim>
```
**Ne yapar:** Durmuş bir container'ı **başlatır**.
Örnek: `docker start web-uygulamasi`

```bash
docker stop <isim>
```
**Ne yapar:** Çalışan bir container'ı **durdurur** (nazikçe kapatır).
Örnek: `docker stop web-uygulamasi`

```bash
docker restart <isim>
```
**Ne yapar:** Container'ı **kapatıp tekrar açar**. Uygulama takıldığında veya ayar değiştiğinde en sık kullanılan komuttur.
Örnek: `docker restart web-uygulamasi`

---

### 🔹 Sorun Çözmek İçin (Logları / Kayıtları İncelemek)

```bash
docker logs <isim>
```
**Ne yapar:** Uygulamanın ürettiği **kayıtları (log)** gösterir. "Uygulama neden hata veriyor?" sorusunun cevabı genellikle buradadır.
Örnek: `docker logs web-uygulamasi`

```bash
docker logs -f <isim>
```
**Ne yapar:** Logları **canlı olarak** akıtır (yeni kayıtlar geldikçe ekranda görürsünüz). Çıkmak için `Ctrl + C` yapın.

---

### 🔹 Container İçine Girmek

```bash
docker exec -it <isim> bash
```
**Ne yapar:** Çalışan container'ın **içine girmenizi** sağlar (sanki o kutunun içindeki bir bilgisayara bağlanmış gibi). İçeride komut çalıştırabilir, dosyalara bakabilirsiniz. Çıkmak için `exit` yazın.
Örnek: `docker exec -it web-uygulamasi bash`

---

### 🔹 Image'leri Görmek ve İndirmek

```bash
docker images
```
**Ne yapar:** Sunucuda bulunan tüm **image'leri (kalıpları)** listeler.

```bash
docker pull <image-adı>
```
**Ne yapar:** İnternetten bir image **indirir**.
Örnek: `docker pull nginx`

---

### 🔹 Docker Compose (Birden Fazla Uygulamayı Birlikte Yönetmek)

Çoğu projede birden fazla parça vardır (örneğin: web + veritabanı). Bunları tek tek yönetmek yerine `docker-compose` ile **hepsini birlikte** yönetiriz. Bu komutlar, içinde `docker-compose.yml` dosyası olan klasörde çalıştırılır.

```bash
docker-compose up -d
```
**Ne yapar:** Projedeki **tüm uygulamaları arka planda başlatır**. `-d` = arka planda çalıştır demektir.
> Bu, bir projeyi ayağa kaldırmak için en sık kullanılan komuttur.

```bash
docker-compose down
```
**Ne yapar:** Projedeki **tüm uygulamaları durdurur** ve container'ları kaldırır.

```bash
docker-compose ps
```
**Ne yapar:** Bu projeye ait uygulamaların durumunu gösterir.

```bash
docker-compose logs -f
```
**Ne yapar:** Tüm projenin loglarını canlı gösterir.

```bash
docker-compose restart
```
**Ne yapar:** Projedeki tüm uygulamaları yeniden başlatır.

---

### 🔹 docker-compose.yml Dosyası Nedir?

`docker-compose.yml`, projedeki **tüm uygulamaların (servislerin) tek bir dosyada tanımlandığı ayar dosyasıdır**. "Bu projede hangi uygulamalar var, hangi image'leri kullanıyor, hangi portlardan erişiliyor, veriler nerede saklanıyor" gibi bilgilerin hepsi buradadır.

**Benzetme:** Bir projenin **kurulum planı / krokisi** gibidir. Siz `docker-compose up -d` dediğinizde, Docker bu dosyayı okur ve içindeki her şeyi otomatik olarak kurup çalıştırır.

Basit bir örnek dosya şöyle görünür:

```yaml
services:
  web:                        # "web" adında bir uygulama
    image: nginx              # nginx image'ini (kalıbını) kullanır
    ports:
      - "80:80"               # dışarıdan 80 portundan erişilir

  veritabani:                 # "veritabani" adında bir uygulama
    image: postgres           # postgres (veritabanı) image'ini kullanır
    environment:
      POSTGRES_PASSWORD: sifre123
    volumes:
      - db_verileri:/var/lib/postgresql/data   # veriler burada saklanır

volumes:
  db_verileri:                # verilerin kalıcı olarak tutulduğu alan
```

**Dikkat edilmesi gerekenler:**
- Bu dosyayı **genellikle geliştirici hazırlar**. Siz sunucuda çoğunlukla sadece çalıştırırsınız.
- Dosyada değişiklik yaptıysanız, değişikliğin geçerli olması için projeyi yeniden başlatın:
  ```bash
  docker-compose down
  docker-compose up -d
  ```
- `volumes` (birim) kısmı çok önemlidir: **Veritabanı verileri burada saklanır.** Bu alan silinirse veriler de gider (bir sonraki bölüme bakın).

---

### 🔹 Temizlik Komutları (prune) — ⚠️ DİKKATLİ KULLANIN

Zamanla sunucuda **kullanılmayan** container'lar, image'ler ve dosyalar birikir ve disk alanını doldurur. `prune` komutları bu **çöpleri temizlemek** için kullanılır.

> ⚠️ **ÇOK ÖNEMLİ UYARI:** Bu komutlar **kalıcı olarak siler** ve geri alınamaz. Özellikle veri silen komutlar için önce mutlaka **yedek alın** ve emin değilseniz **sorun**. Yanlış komut, veritabanındaki tüm verileri silebilir.

#### Güvenli Temizlik (verilere dokunmaz)

```bash
docker image prune
```
**Ne yapar:** Hiçbir yerde **kullanılmayan** image'leri (kalıpları) siler. Disk alanı açmak için güvenli bir başlangıçtır. Çalışan uygulamalarınıza dokunmaz.

```bash
docker container prune
```
**Ne yapar:** **Durmuş** (çalışmayan) container'ları siler. Çalışan uygulamalar etkilenmez.

```bash
docker system prune
```
**Ne yapar:** Kullanılmayan tüm container, image ve ağları **birlikte** temizler. Genel bir disk temizliği için kullanılır.
> Not: Komut çalışınca "Are you sure? (y/N)" diye onay ister. Devam etmek için `y`, iptal için `n` yazın.

#### ⛔ Veri Silen Komut (EN TEHLİKELİSİ)

```bash
docker volume prune
```
**Ne yapar:** Hiçbir container tarafından **kullanılmayan volume'leri (veri alanlarını) siler.**

> 🛑 **BURADA DURUN!** Volume'ler **veritabanı verilerinin saklandığı yerdir.** Eğer veritabanı container'ınız o an durmuşsa veya yanlışlıkla kaldırılmışsa, bu komut **veritabanınızdaki tüm verileri kalıcı olarak silebilir.**
>
> Bu komutu **yalnızca** ne yaptığınızdan tamamen eminseniz ve **yedeğiniz varsa** çalıştırın. Emin değilseniz **asla çalıştırmayın, önce sorun.**

#### Her Şeyi Silen Komut (En Agresif)

```bash
docker system prune -a --volumes
```
**Ne yapar:** Kullanılmayan **her şeyi** siler — image'ler, container'lar, ağlar ve **volume'ler (veriler dahil).**
> 🛑 Bu komut son çare içindir ve **çok risklidir.** Ekipten onay almadan çalıştırmayın.

---

## 5. Sık Karşılaşılan Durumlar

Aşağıda, günlük hayatta karşılaşabileceğiniz durumlar ve çözümleri var:

### ❓ "Uygulama çalışıyor mu, nasıl kontrol ederim?"
```bash
docker ps
```
Listede uygulamanızın adını görüyorsanız çalışıyordur.

### ❓ "Uygulama çöktü / açılmıyor, ne yapmalıyım?"
Önce loglara bakın, sorunu anlayın:
```bash
docker logs <isim>
```
Sonra yeniden başlatmayı deneyin:
```bash
docker restart <isim>
```

### ❓ "Yeni bir güncelleme geldi, nasıl uygularım?"
Projenin klasöründe genellikle şu iki komut yeterlidir:
```bash
docker-compose down
docker-compose up -d
```

### ❓ "Tüm projeyi baştan ayağa kaldırmam gerekiyor"
Proje klasörüne girin ve:
```bash
docker-compose up -d
```

### ❓ "Sunucunun diski doldu, yer açmam lazım"
Önce **güvenli** temizlikle başlayın (verilere dokunmaz):
```bash
docker image prune
docker container prune
```
Bu yeterli gelmezse `docker system prune` kullanabilirsiniz. Ancak **`docker volume prune` komutuna dokunmayın** — veritabanı verilerini silebilir. Emin değilseniz sorun.

---

## 6. Özet Komut Tablosu

| Komut | Ne İşe Yarar? |
|-------|---------------|
| `docker ps` | Çalışan uygulamaları listeler |
| `docker ps -a` | Tüm uygulamaları (durmuş dahil) listeler |
| `docker start <isim>` | Uygulamayı başlatır |
| `docker stop <isim>` | Uygulamayı durdurur |
| `docker restart <isim>` | Uygulamayı yeniden başlatır |
| `docker logs <isim>` | Uygulamanın kayıtlarını (log) gösterir |
| `docker logs -f <isim>` | Kayıtları canlı gösterir |
| `docker exec -it <isim> bash` | Uygulamanın içine girer |
| `docker images` | Mevcut image'leri (kalıpları) listeler |
| `docker pull <image>` | İnternetten image indirir |
| `docker-compose up -d` | Projedeki tüm uygulamaları başlatır |
| `docker-compose down` | Projedeki tüm uygulamaları durdurur |
| `docker-compose ps` | Proje uygulamalarının durumunu gösterir |
| `docker-compose logs -f` | Proje loglarını canlı gösterir |
| `docker-compose restart` | Proje uygulamalarını yeniden başlatır |
| `docker image prune` | Kullanılmayan image'leri siler (güvenli) |
| `docker container prune` | Durmuş container'ları siler (güvenli) |
| `docker system prune` | Kullanılmayan container/image/ağları temizler |
| `docker volume prune` | ⚠️ Kullanılmayan veri alanlarını siler — TEHLİKELİ |
| `docker system prune -a --volumes` | ⛔ Veriler dahil her şeyi siler — ÇOK TEHLİKELİ |

---

## Son Notlar

- Bu komutları çalıştırmak için sunucuya **terminal** üzerinden bağlanmanız gerekir.
- `<isim>` yazan yerlere, `docker ps` komutuyla gördüğünüz **gerçek container adını** yazın.
- Emin olmadığınız bir komutu çalıştırmadan önce sormaktan çekinmeyin; özellikle `down`, `stop` gibi durdurma komutları uygulamayı geçici olarak erişilemez yapar.
- En çok kullanacağınız 3 komut: **`docker ps`** (kontrol), **`docker logs`** (sorun bulma), **`docker restart`** (çözme).

---

*Bu doküman ekip içi bilgilendirme amacıyla hazırlanmıştır.*

---
---

# 📌 EK NOT: BİZİM SUNUCUMUZ — EN SIK KULLANILACAKLAR

> Yukarıdaki bölümler Docker'ı **genel olarak** anlatır. Bu son bölüm ise **bizim sunucumuza özeldir**:
> gerçek container adlarımız ve günlük hayatta kopyala-yapıştır kullanabileceğiniz komutlar.

### Önce bilinmesi gereken 3 şey

| | |
|---|---|
| **Sunucu** | `TRLPVNOCBO01` — SSH ile bağlanılır, kullanıcı `aurun` |
| **Proje klasörü** | `/srv/kod/tti-sunucu` — compose komutları **buradan** çalışır |
| **`sudo` şart** | `aurun` docker grubunda değil → **her komutun başına `sudo`** |

> ⚠️ **Bizde `docker-compose` değil, `docker compose` (arada boşluk).** Sunucuda Compose eklentisi
> kurulu, tireli eski komut çalışmaz. Yukarıdaki 4. bölümdeki `docker-compose ...` örneklerini
> sunucuda **`sudo docker compose ...`** diye yazın.

### Bizim container adlarımız

| Container adı | Nedir? | Durursa ne olur? |
|---|---|---|
| `tti-hakedis-server` | TTI Saha Hakediş uygulaması | `hakedis.turktelekomint.com` açılmaz |
| `tti-rapor-server` | TTIDataRapor uygulaması | `rapor.turktelekomint.com` açılmaz |
| `tti-db` | PostgreSQL veritabanı (**ikisi de bunu kullanır**) | **Her iki uygulama da** çalışmaz |
| `tti-proxy` | Caddy — domain adlarını ve HTTPS'i yöneten kapı | **Hiçbir site açılmaz** (ikisi de) |
| `tti-portainer` | IT'nin web yönetim arayüzü (`:9443`) | Sadece o arayüz kapanır, siteler etkilenmez |

---

## 1️⃣ Ne çalışıyor? (en sık kullanılan komut)

```bash
sudo docker ps
```

**Beklenen:** yukarıdaki **5 container da** listede ve `STATUS` sütununda `Up` yazıyor olmalı.
Uygulamaların yanında ayrıca `(healthy)` görürsünüz — bu "sadece açık değil, gerçekten cevap
veriyor" demektir.

Listede olması gereken biri **yoksa**, durmuş demektir — durmuşları da görmek için:

```bash
sudo docker ps -a          # durmuş olanlar "Exited" olarak görünür
```

---

## 2️⃣ Tek bir projeyi yeniden başlatmak

Sadece o uygulama ~10 saniye kesintiye uğrar, **diğerleri ve veritabanı etkilenmez.**

```bash
# Hakediş uygulaması takıldıysa:
sudo docker restart tti-hakedis-server

# Rapor uygulaması takıldıysa:
sudo docker restart tti-rapor-server

# Siteler hiç açılmıyorsa (domain/HTTPS sorunu):
sudo docker restart tti-proxy

# Portainer arayüzü açılmıyorsa:
sudo docker restart tti-portainer
```

Yeniden başlattıktan sonra **mutlaka kontrol edin**:

```bash
sudo docker ps                                    # STATUS "Up" mı?
sudo docker logs --tail 50 tti-hakedis-server     # hata satırı var mı?
```

> ⚠️ **`tti-db`'yi tek başına restart etmekten kaçının.** Veritabanı **iki uygulama tarafından
> ortak** kullanılır; onu yeniden başlatmak ikisinin de bağlantısını koparır. Gerçekten gerekiyorsa
> sonrasında iki uygulamayı da restart edin.

---

## 3️⃣ Bütün projeleri baştan başlatmak

**Önce klasöre girmeniz şart** — compose komutları `docker-compose.yml` dosyasının olduğu yerden çalışır:

```bash
cd /srv/kod/tti-sunucu
```

**Hafif yöntem (önce bunu deneyin)** — container'lar aynı kalır, sadece yeniden başlar:

```bash
sudo docker compose restart
```

**Tam yöntem** — container'lar silinip yeniden oluşturulur (ayar dosyası değiştiyse gerekir):

```bash
sudo docker compose down
sudo docker compose up -d
sudo docker compose ps        # hepsi Up / healthy mi?
```

> ✅ **`down` verilerimizi silmez.** Veritabanımız `external` bir volume'de tutulur; `down` ona
> dokunamaz. **Ancak `down -v` ASLA yazmayın** — `-v` volume silme anlamına gelir.
>
> ⚠️ `down` çalıştığı andan `up -d` bitene kadar (~1 dakika) **siteler erişilemez** olur. Mesai
> saatinde yapmadan önce haber verin.

---

## 4️⃣ Sorun çözerken bakılacak loglar

```bash
sudo docker logs --tail 100 tti-hakedis-server   # son 100 satır
sudo docker logs -f tti-rapor-server             # canlı akış (çıkmak için Ctrl+C)
sudo docker compose logs -f                      # hepsi birden (klasörün içinden)
```

---

## 5️⃣ Her şey yolunda mı? (tek komutla toplu kontrol)

```bash
cd /srv/kod/tti-sunucu
bash dogrula.sh
```

Bu betik container'ları, portları, domain adlarını ve HTTPS sertifikasını birlikte kontrol eder;
yeşil/kırmızı satırlarla sonucu söyler.

---

### 🚦 Hızlı karar tablosu

| Durum | Ne yapmalı |
|---|---|
| Tek bir site açılmıyor | O uygulamanın log'una bak → `sudo docker restart <o-container>` |
| **İki site birden** açılmıyor | Önce `sudo docker ps` → `tti-proxy` veya `tti-db` durmuş olabilir |
| Ayar dosyası (`.env`, `Caddyfile`) değişti | `cd /srv/kod/tti-sunucu && sudo docker compose up -d` |
| Sertifika yenilenecek | `docker restart` yetmez → **[CERT.md](CERT.md)** dosyasını takip edin |
| Yeni sürüm kurulacak | Kendi başınıza yapmayın → `ServerDeploy.md` §7 |

> **Unutulmaması gereken:** `restart` güvenlidir, veriye dokunmaz, her zaman denenebilir.
> Silme içeren komutlar (`down -v`, `prune`, `rm`) **sorulmadan çalıştırılmaz.**
