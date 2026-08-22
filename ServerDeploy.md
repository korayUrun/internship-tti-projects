# TTI Platformu — Sunucu Dağıtım Kaydı

> Bu dosya **canlı dağıtım kaydıdır**: sunucuda ne yapıldı, ne kaldı, hangi tuzaklara düşüldü.
> Sonraki oturumlarda buradan devam edilir. Servis/port özeti: [SERVISLER.md](SERVISLER.md) ·
> Kimlik bilgileri: [KULLANICILAR-VE-PAROLALAR.md](KULLANICILAR-VE-PAROLALAR.md)

**Durum (2026-08-17):** 🟢 **Yığın ayakta ve doğrulandı** (5 konteyner healthy, proxy domain'e göre
yönlendiriyor, uygulamalar DB'ye ayrıcalıksız rolle bağlı). **HTTPS canlı** — `*.turktelekomint.com`
wildcard sertifikasıyla 443 açık, http→https yönlendirme çalışıyor, kullanıcı ağından doğrulandı (bkz. §14).
**Her iki proje de `2026-08-17` sürümünde**; hakediş **temiz başlangıç** yapıldı (bkz. §16).
Kalan işler: Portainer admin parolası, 9443'ü IT subnet'ine açma, **sunucu saat dilimi**, yedek timer,
`/var` temizliği, **hakediş sözleşme + kablo envanteri yükleme**, **sertifika yenileme (son geçerlilik
2027-02-27, bkz. CERT.md)**. RDP/klavye faslı kapatıldı (SSH asıl yol; bkz. §10).

---

## 1. Sunucu

| | |
|---|---|
| Hostname | `TRLPVNOCBO01` |
| İşletim sistemi | RHEL 9.x |
| Kullanıcı | `aurun` (sudo var; **docker grubunda değil** → tüm `docker` komutları `sudo` ile) |
| Motor | Docker Engine (docker-ce) + compose eklentisi |
| Storage driver | **overlay2** (containerd imaj deposu kapalı — aşağıya bak) |
| Docker veri kökü | **`/srv/docker`** (`/` üzerinde, 66 GB boş) |
| cgroups | v2 (mem_limit çalışır) |

**Disk yerleşimi:**
- `/` (SystemVG-rootLV): 71 GB, ~66 GB boş → Docker + kod + veri burada.
- `/var` (SystemVG-varLV): **4 GB, dar** → eski containerd verisi (~2.3 GB) hâlâ orada, temizlenecek.

---

## 2. Dizin yerleşimi (sunucuda)

| Yol | İçerik | Sahip |
|---|---|---|
| `/srv/kod/{tti-hakedis,tti-rapor,tti-sunucu}` | Kaynak kod (imajlar buradan derlenir) | `aurun` |
| `/srv/kod/tti-sunucu/.env` | Parolalar, SURUM, DOMAIN (chmod 600) | `aurun` |
| `/srv/veri/tti-hakedis/{arsiv,yukleme}` · `/srv/veri/tti-rapor/yukleme` · `/srv/veri/tti-data/yukleme` · `/srv/veri/yedek` | Uygulama verisi (bind-mount) | `1000:1000` |
| `/srv/docker` | Docker veri kökü (imaj, volume, build cache) | root |
| Volume `tti_db_verisi` (external) | PostgreSQL verisi | — |

---

## 3. Mimari (çalışan konteynerler)

```
port 80 ── kullanıcılar ──► tti-proxy (caddy) ──► hakedis.<DOMAIN> → tti-hakedis-server (127.0.0.1:8081)
                                              ├─► rapor.<DOMAIN>   → tti-rapor-server   (127.0.0.1:8082→8080)
                                              └─► data.<DOMAIN>    ─┬ /api,/admin,/docs → tti-data-server (127.0.0.1:8083→8000)
                                                                    └ geri kalanı       → tti-data-panel  (127.0.0.1:8084→8080)
tti-db (postgres:17, 127.0.0.1:5432)  ·  tti-portainer (0.0.0.0:9443, IT için)
```
- **İmajlar:** `tti-hakedis-server:2026-08-11`, `tti-rapor-server:2026-08-11`,
  `tti-data-server:2026-08-19`, `tti-data-panel:2026-08-19` (multi-stage, **sunucuda kaynaktan** derlendi).
- ⚠️ `tti-data` **tek depodan iki imaj** üretir. Güncellemede iki servis adı da verilir:
  `docker compose build tti-data-server tti-data-panel`.
- **DB rolleri:** `tti_hakedis` = bakım superuser · `tti_uygulama` + `rapor` = uygulama rolleri (**ayrıcalıksız**).
- Ayrıntı: [SERVISLER.md](SERVISLER.md).

---

## 4. Yapıldı ✅

- [x] Docker Engine kurulu, `enabled`+`active`, compose eklentisi, cgroups v2.
- [x] Docker veri kökü `/srv/docker`'da (küçük `/var` yerine büyük disk).
- [x] Kaynak `/srv/kod`'a taşındı (WhatsApp/scp + sha256 doğrulama), `aurun`'a chown'landı.
- [x] Veri klasörleri `/srv/veri/...` açıldı, `1000:1000`'e chown'landı.
- [x] `.env` dolduruldu (3 DB parolası, SURUM, DOMAIN=`tti.local` geçici, DB_VOLUME=`tti_db_verisi`).
- [x] Provizyon parolaları `.env`'den `sed` ile eşlendi; `provizyon/01-tti.sql` + `02-rapor.sql` çalıştı.
- [x] Volume oluşturuldu, DB bootstrap + provizyon: roller ve `tti`/`rapor` veritabanları kuruldu.
- [x] İmajlar `docker compose build` ile **sunucuda** derlendi.
- [x] `docker compose up -d` → 5 konteyner healthy.
- [x] Doğrulandı: `localhost:8081/8082/api/saglik` → `veritabani:true/ayakta`; proxy `Host: hakedis.tti.local` → 200.
- [x] Güvenlik duvarında **port 80 (http)** açıldı.

## 5. Kalan ⏳

- [ ] **Portainer admin parolası** — `https://<sunucu-ip>:9443`'ten birkaç dakika içinde kur (yoksa kilitlenir → `sudo docker restart tti-portainer`). Erişim için 9443 açık olmalı (aşağı).
- [ ] **9443'ü IT subnet'ine aç** (şu an kapalı; geçici olarak `--add-port=9443/tcp`, kalıcı için rich-rule ile IT subnet'i):
      `sudo firewall-cmd --permanent --add-rich-rule='rule family="ipv4" source address="<IT_SUBNET>/24" port port="9443" protocol="tcp" accept'`
- [ ] **DNS (IT)** — `hakedis.<domain>` ve `rapor.<domain>` → sunucu IP. IT tam adı verince `.env`'de `DOMAIN`'i değiştir → `sudo docker compose up -d` (proxy yeniden kurulur). Ad `hakedis.`/`rapor.` ön ekinden farklıysa `tti-sunucu/Caddyfile` düzenlenir.
- [ ] **Başka ağdan / VPN erişim testi** — kullanıcıların ağından sunucunun 80 portuna yol var mı (IT'nin routing'i).
- [x] ~~**Reboot testi**~~ — **2026-08-17'de geçti**: reboot sonrası 5 konteyner de kendiliğinden kalktı (`Up 14 minutes`, üçü healthy), `systemctl is-enabled docker` → `enabled`, hepsinde `RestartCount=0`.
- [ ] ⚠️ **Sunucu saat dilimi** — host saati İstanbul'un **8 saat gerisinde** (yedek log'u `02:10` yazarken gerçek saat `10:10`; `unzip` dosya damgaları da 8 saat geri). Konteynerler etkilenmiyor (compose `TZ: Europe/Istanbul` geçiyor), etkilenen host tarafı: `yedek.sh` log/dosya adları. **Yedek timer'ından ÖNCE düzeltilmeli** — `OnCalendar` host saat dilimine göre çalışır, "gece 02:00" dediğinizde yedek mesai içinde 10:00'da koşar. Kontrol: `timedatectl`; düzeltme: `sudo timedatectl set-timezone Europe/Istanbul`.
- [ ] **Yedek** — `tti-sunucu/yedek.sh` için systemd timer (günlük) + makine dışına kopya + geri yükleme provası. Bkz. `tti-hakedis/deploy/RedHatKurulum.md` §12. (Önce saat dilimi — üstteki madde.)
- [ ] **`/var` temizliği** — eski containerd verisi (~2.3 GB) `/var`'ı doldurdu; overlay2'ye geçtiğimiz için artık gereksiz. Dikkatli temizle (containerd runtime'ı bozmadan).

---

## 6. Karşılaşılan sorunlar ve çözümleri (gelecek için)

1. **RDP bağlanmıyordu** → xrdp servisi/firewall (3389). Servis `enabled`+`active`, port açık olunca çözüldü.

2. **Build "no space left on device" (`/var/lib/containerd`)** → Docker `data-root` `/srv/docker`'a taşınmış görünüyordu ama **containerd imaj deposu (Storage Driver: overlayfs) küçük `/var`'ı** kullanıyordu; `data-root` taşıması onu kapsamıyor. **Çözüm:** `/etc/docker/daemon.json`:
   ```json
   { "data-root": "/srv/docker", "features": { "containerd-snapshotter": false } }
   ```
   → `overlay2`, her şey `/srv/docker`'da (66 GB). `systemctl restart docker`.

3. **DB kullanıcı modeli hatası** → compose tek `DB_KULLANICI`'yi hem `POSTGRES_USER` (bootstrap superuser) hem uygulama bağlantısı için kullanıyordu; sıfır kurulumda uygulama **superuser olarak** bağlanır ve projeler-arası sınır sessizce delinirdi. **Çözüm:** ayrıştırıldı — `DB_BAKIM_KULLANICI`/`DB_BAKIM_PAROLA` (superuser) ayrı, `DB_KULLANICI=tti_uygulama`/`DB_PAROLA` (ayrıcalıksız app). Sıfır-DB testinde izolasyon doğrulandı.

4. **Kotlin build "Unresolved reference 'arsiv'"** → taşıma zip'i `*/arsiv/*` ile çalışma-zamanı veri klasörünü dışlarken **`server/.../server/arsiv/` KAYNAK paketini** de siliyordu (Mac'te `.dockerignore` kullanıldığı için orada görünmedi). **Çözüm:** zip'ten `*/arsiv/*` ve `*/yukleme/*` çıkarıldı; `web/dist` için tam yol verildi. `RedHatKurulum.md §3b`'de uyarı eklendi.

---

## 7. Güncelleme prosedürü (yeni sürüm)

Kod değişince:
1. **Mac'te** doğru zip (yukarıdaki 4. tuzağa dikkat — `arsiv`/`yukleme` dışlanmaz):
   ```bash
   cd ~/Desktop/tti-projeler
   zip -r tti-tasima.zip tti-hakedis tti-rapor tti-sunucu \
     -x '*/build/*' '*/bin/*' '*/obj/*' '*/yayin/*' '*/.git/*' '*/node_modules/*' \
        '*/.gradle/*' '*/.kotlin/*' '*/web/dist/*' '*.DS_Store' '*/.env' '*/certs/*' '*.bak' \
        '*/deneme/*' '*/local.properties'
   shasum -a 256 tti-tasima.zip
   ```
2. Sunucuya taşı → `sha256sum` doğrula → `unzip -o tti-tasima.zip -d /srv/kod`.
3. `.env`'de `SURUM`/`SURUM_RAPOR`'u yeni tarihe çevir.
4. `cd /srv/kod/tti-sunucu && sudo docker compose build && sudo docker compose up -d`.
   - Yalnızca değişen proje derlenir/yeniden oluşturulur; DB'ye dokunulmaz.
5. Geri dönüş: `.env`'de `SURUM`'u eski tarihe çevir → `up -d` (eski imaj duruyorsa).

> ⚠️ `unzip -o` provizyon SQL'lerini placeholder'a geri yazar; DB zaten kurulu olduğu için sorun
> değil. Yeniden provizyon gerekirse `sed`'le parolaları `.env`'den tekrar eşle.
>
> ⚠️ **TLS sertifikası zip'e girmez** (`-x '*/certs/*'`). İki nedenle: (1) özel anahtar taşınan
> pakette dolaşmasın; (2) `unzip -o` sunucudaki gerçek CA sertifikasını Mac'teki bir self-signed
> ile **ezmesin**. `certs/` yalnızca sunucuda durur; `unzip -o` orada olmayan dosyayı silmez, dokunmaz.

---

## 8. IT'ye verilecekler (onlar projeyi bilmez)

IT sunucu IP'sini biliyor. Onlardan tek gereken **isim→IP** eşlemesi:

> Sunucuda (IP: `<sunucu-ip>`) iki iç web uygulaması çalışıyor, ikisi de 80 portundaki tek proxy'nin
> arkasında. İki DNS A kaydı ekler misiniz, ikisi de bu IP'ye:
> - `hakedis.<domain>` → `<sunucu-ip>`
> - `rapor.<domain>`   → `<sunucu-ip>`
>
> Hangi domain son ekini (`.sirket.local` vb.) kullandığınızı bana iletin. Ayrıca VPN'den bağlanan
> saha kullanıcıları dahil, kullanıcıların bulunduğu ağlardan sunucunun **80** portuna erişim açık
> olsun. Yönetim paneli için **9443**'ü yalnızca sizin ağınıza açacağım — IT subnet'inizi paylaşın.

**Geri gelince:** tam domain adları → `.env` `DOMAIN`; IT subnet → 9443 firewall kuralı.

---

## 9. Kimlik bilgileri

- **DB servis parolaları:** sunucuda `/srv/kod/tti-sunucu/.env` (`DB_BAKIM_PAROLA`, `DB_PAROLA`, `RAPOR_DB_PAROLA`).
- **Uygulama giriş hesapları** (hakediş `TTI111111…`, rapor `TTIrapor2026`, Portainer admin): [KULLANICILAR-VE-PAROLALAR.md](KULLANICILAR-VE-PAROLALAR.md).

---

## 10. RDP / xrdp kurtarma (kara ekran, "Error connecting to Xorg", çift giriş)

> **Önce şunu bil:** RDP sadece masaüstü (GUI) erişimi. **Docker servisleri RDP'den bağımsız çalışır** —
> sen masaüstüne hiç giremesen de konteynerler ayakta. Yönetim işlerini **SSH** ile yap; kara ekran seni
> engellemez. Aşağıdaki kill/restart komutları yalnızca **GUI oturumu** bozulunca gerekir.

**Kök neden (bu ortamda gözlenen):** `xrdp-chansrv` oturum kapanışında bazen takılıyor → oturum
`closing` durumunda kalıyor → tekrar bağlanınca ölü Xorg'a bağlanmaya çalışıyor → **kara ekran /
"Error connecting to Xorg"**. Zamanla artık (orphan) Xorg süreçleri birikebiliyor.

### Kurtarma merdiveni — en hafiften en sertine (sırayla dene)

**0. Önce sadece yeniden bağlan.** Oturum kalıcıysa (KillDisconnected=false) masaüstüne geri döner.
Çoğu zaman yeter.

**1. Kara ekran / greeter takıldı → xrdp'yi yeniden başlat** (SSH'tan):
```bash
sudo systemctl restart xrdp
```
xrdp + sesman'ı tazeler. **Çalışan GUI oturumunu öldürmez** (en güvenli ilk adım). Sonra yeniden bağlan.
→ **Ne zaman:** giriş ekranında/bağlanırken kara ekran, greeter cevap vermiyor.

**2. Takılı `closing` oturum → oturumu sonlandır:**
```bash
loginctl                          # oturumları listele: state=closing olan xrdp oturumunu bul
loginctl session-status <id>      # doğrula (leader = xrdp-chansrv, display :10 gibi)
loginctl terminate-session <id>   # takılı oturumu kapat
sudo systemctl restart xrdp
```
→ **Ne zaman:** "Error connecting to Xorg", bağlanınca hemen ikinci kez giriş sorması, `loginctl`'de
`closing` görünen bir oturum var.

**3. Son çare — artık (orphan) süreçleri temizle:**
```bash
ps aux | grep -E 'Xorg|xrdp' | grep -v grep     # takılı Xorg/chansrv'leri gör
sudo pkill -u aurun xrdp-chansrv
sudo pkill -u aurun Xorg
sudo systemctl restart xrdp
```
→ **Ne zaman:** 1 ve 2 işe yaramadı, `ps`'te ölü Xorg/chansrv süreçleri duruyor.
⚠️ `pkill Xorg` **açık GUI oturumunu kapatır** (kaydedilmemiş GUI işi gider) — ama **Docker/servisler
etkilenmez**. Terminal işlerini SSH'ta yaptığın için pratikte kayıp olmaz.

### Bilinmesi gerekenler / karar kaydı
- **Klavye Türkçe Q:** masaüstü/kilit ekranı Türkçe (GNOME `setxkbmap tr` + autostart). **xrdp login
  kutusu** oturum öncesi olduğu için ayrı: klavyesi `/etc/xrdp/km-<layoutid>.ini`'den ve istemcinin
  bildirdiği **aktif Windows giriş dilinden** gelir; panoya (kopyala-yapıştır) **kapalı** (henüz oturum yok).
  → **Pratik çözüm:** Windows'ta `mstsc` → "Kimlik bilgilerimi kaydetmeme izin ver", parolayı **Windows
  kimlik penceresine yapıştır** (orada Ctrl+V çalışır); xrdp login kutusuna hiç yazma. Türkçe km dosyası
  (`km-0000041f.ini`) sunucuda **yok**; istenirse masaüstü oturumunda `setxkbmap tr && xrdp-genkeymap
  /tmp/km-tr.ini` ile üretilip kopyalanabilir (opsiyonel, kozmetik).
- **`xrdp_keyboard.ini`** oturum (masaüstü) klavyesini ayarlar; login kutusunu **değil**. `[default]`
  bölümü force_tr'ye çevrildi (US bildiren istemci de masaüstünde tr alsın diye) — zararsız.
- **KillDisconnected:** `false` önerildi (`/etc/xrdp/sesman.ini`) → kopunca oturum canlı kalır, yeniden
  bağlanınca giriş sormadan döner. `true` ise her koptuğunda oturum kapanır (chansrv takılma riski artar).
- **Oturum içinde** kopyala-yapıştır çalışır (chansrv aktif): terminalde `Ctrl+Shift+C/V`.

---

## 11. Oturum günlüğü — 2026-08-12

- **RDP/klavye faslı kapatıldı.** xrdp kara ekran + Türkçe klavye + kopyala-yapıştır sorunları
  tekrarladı; kök neden ve kurtarma adımları §10'a dökümante edildi. Karar: **SSH asıl yönetim yolu**,
  GUI bozulursa §10 merdiveni. Kalıcı "her şey kusursuz" uğraşı bırakıldı (getirisi düşük).
- **HTTPS:** şirket **turktelekomint.com** domainini verdi (`hakedis.turktelekomint.com`). ✅ **2026-08-13'te
  tamamlandı** — support PFX yerine PEM (sertifika + zincir + ayrı özel anahtar) verdi; PFX→PEM çevirmeye
  gerek kalmadı. Caddy'ye elle `tls` ile tanıtıldı, 443 açıldı, http→https yönlendirme kuruldu. Ayrıntılı
  runbook + tuzaklar: **§14**.
- **IT entegrasyonu:** AD grupları / SSO / LDAPS için ATOS'a Türkçe ticket taslağı hazırlandı (ayrı belge).
- **Lisans notu:** Caddy = Apache 2.0, Apache httpd = Apache 2.0, nginx OSS = BSD — hepsi ücretsiz; yalnızca
  **nginx Plus** ücretli. Portainer CE = zlib. Reverse proxy tercihinde lisans engeli yok.
- **Kablo verisi:** DB'ye yüklenmemiş; yükleme prosedürü §12'de (aşağıda).
- **Android (app tarafı, prod'u etkilemez):** tablet UI iki-sütun çalışması + "Yeni Proje" çökme düzeltmesi
  emülatörde doğrulandı; commit bekliyor (server dağıtımından bağımsız).

---

## 12. Kablo verisini (envanter) DB'ye yükleme

**Amaç:** `hakedis.kable` tablosunu doldurmak. Proje (iş emri) eklerken KableID/nosid buradan aranır;
envanter yüklenmeden kablolu proje eklenemez. Kabul edilen **tek format: iki sayfalı `.xlsx`**
(`cable` sayfası = kablo kayıtları, `Saha` sayfası = "Technical Consultant" → saha eşlemesi). CSV/JSON/SQL
yok. Yükleme **idempotent** (`on conflict (nosid) do update`); dosyada olmayan kablolar silinmez, `aktif=false` olur.

### Yol A — Web arayüzünden yükle (ÖNERİLEN, dosyayı Linux'a taşımana GEREK YOK)
Dosya Windows'ta kalır; tarayıcıdan yüklersin, HTTP ile sunucuya gider.
1. Windows'ta `.xlsx` hazır olsun (içinde **`cable` ve `Saha`** sayfaları bulunmalı — örnek: `cable_data_28072026.xlsx`).
2. Tarayıcıdan hakediş web arayüzüne gir, **yönetici** hesabıyla giriş yap.
   - DNS henüz hazır değilse Windows'ta `C:\Windows\System32\drivers\etc\hosts`'a geçici satır ekle:
     `<sunucu-ip>  hakedis.<domain>` → sonra `http://hakedis.<domain>`.
3. **"Kablo Envanteri"** ekranı → dosyayı yükle. Bu bir **sürüm** oluşturur; canlı `kable` tablosuna
   **henüz dokunmaz** (`POST /api/kable-surumleri`).
4. **"Fark"** ile önizle (kaç eklenecek/güncellenecek), sonra **"Yürürlüğe al"** → sürüm canlıya yansır
   (`POST /api/kable-surumleri/{id}/yururluk`). Artık kablolar `kable` tablosunda.
5. Proje ekle (Yeni Proje) → KableID artık bulunur.

> Import **fail-loud**: eksik sayfa/sütun, boş/tekrar eden `nosid`, ya da `Saha`'daki bir danışman adı
> `hakedis.bolge.xlsx_saha_adi` ile eşleşmiyorsa **hiçbir şey yazmadan** durur. Eşleşmeyen saha adı için:
> `UPDATE hakedis.bolge SET xlsx_saha_adi = '<xlsx'teki ad>' WHERE kod = '<bölge kodu>';` (DB'ye
> `sudo docker exec -it tti-db psql -U tti_hakedis -d tti` ile bağlan).

### Yol B — CLI ile yükle (dosyayı sunucuya taşımayı gerektirir; ilk kurulum/kurtarma)
Web erişimi yoksa. Dosyayı sunucuya taşı + `:tools:kable-import` görevini çalıştır:
1. Windows → sunucu dosya transferi (kod taşımada kullandığın yöntem — scp/WhatsApp):
   `scp cable_data_YYYYMMDD.xlsx aurun@<sunucu-ip>:/srv/kod/tti-hakedis/tools/kable/`
2. `sha256sum` ile bütünlüğü doğrula.
3. Import görevini çalıştır (JDK/Gradle gerektirir; DB env değişkenleri `.env`'den):
   ```bash
   cd /srv/kod/tti-hakedis
   ./gradlew :tools:kable-import:run -PDB_PAROLA=<DB_PAROLA> \
     --args="tools/kable/cable_data_YYYYMMDD.xlsx"
   ```
   (`DB_URL`/`DB_KULLANICI`/`DB_PAROLA` env; ayrıntı ve saha-adı düzeltmesi: `tti-hakedis/deploy/KURULUM.md:126-146`.)
   > Not: prod sunucuda imajlar multi-stage **konteyner içinde** derlendi; host'ta Gradle/JDK olmayabilir.
   > O yüzden prod'da **Yol A (web) tercih edilir**; CLI daha çok ilk kurulum/lokal içindir.

**Özet karar:** Prod sunucuda → **Yol A (web yükleme)**. Dosyayı Linux'a taşımaya, Gradle çalıştırmaya
gerek yok; yönetici olarak "Kablo Envanteri"nden yükle → fark'ı gör → yürürlüğe al → proje ekle.

---

## 13. Uygulama güncellemesi — Yeni Tesis mtc + zorunlu uçlar + UI (2026-08-13)

**Ne değişti (tti-hakedis):**
- **Yeni Tesis** projesinde Kablo ID artık **sistem üretir** (`mtc90000`'den; `MtcNumaraUretici`),
  envantere eklenmez; Uç A/Uç B'de **ülke/şehir/adres zorunlu**. Yeni migration **V16** (`mtc_sayac`).
- Web "Yeni Proje" formu (serbest Kablo ID kalktı, uçlar zorunlu), "Ek Aralığı" yerleşim düzeltmesi.
- Android: arka plan tek düz açık mavi; aynı Yeni Tesis mantığı mobilde de.

**Sunucuya nasıl gider (server + web BİRLİKTE, §7 prosedürü):**
1. Mac'te zip → sunucuya taşı → sha256 → `/srv/kod`'a aç (§7'deki komut).
2. `.env`'de `SURUM`'u `2026-08-13` yap.
3. `cd /srv/kod/tti-sunucu && sudo docker compose build && sudo docker compose up -d`.
   - Yalnızca `tti-hakedis` yeniden derlenir; **web jar'a gömülür** (build'de Node çalışır —
     `-PwebAtla` KULLANMA); açılışta Flyway **V16**'yı otomatik uygular.
4. **V16 güvenli:** sadece yeni `mtc_sayac` tablosu; mevcut veriye dokunmaz. Eski Yeni Tesis projeleri
   (varsa) eski kimliğini korur; yalnızca yeni açılanlar `mtc…` alır.
5. Doğrula: web'de **Yeni Proje → Kapsam: Yeni Tesis** → Kablo ID "Sistem atayacak (mtc…)", Uç A/B
   zorunlu; kaydedince liste satırında `mtc90000…`.

**Android APK (AYRI, elle):** `./gradlew :app:assembleRelease` (imzalı) → tablete kur (adb/MDM).
Sunucudan bağımsız; server güncellemesini beklemez.

---

## 14. HTTPS / TLS (2026-08-13 — tamamlandı)

**Sonuç:** 443 açık, `*.turktelekomint.com` wildcard sertifikası ile hem `hakedis.` hem `rapor.`
şifreli; port 80 → 443'e yönlendiriyor (301). Kullanıcı ağından `https://hakedis.turktelekomint.com`
yeşil kilitle açıldı; `curl` loopback testi zincir doğrulamalı **HTTP/2 200** verdi.

### Support ne verdi (PFX değil, PEM)
- `_.turktelekomint.com.pem` — 3 blok: leaf (`CN=*.turktelekomint.com`) + `RapidSSL TLS RSA CA G1`
  + `DigiCert Global Root G2` = **tam zincir, leaf başta** (Caddy'nin istediği sıra).
- `_.turktelekomint.com-certificate-chain.pem` — yalnızca ara/kök CA (fullchain'de zaten var, kullanılmadı).
- `_.turktelekomint.com-private-key.key` — **özel anahtar** (`BEGIN PRIVATE KEY`). Ayrı istendi.
- İmzalayan DigiCert/RapidSSL. Geçerlilik **2026-08-13 → 2027-02-27** (~6.5 ay, kısa).

> **Tuzak / ders:** CA sertifika dosyalarının içinde özel anahtar YOK — bu normaldir. Anahtar CSR'ın
> üretildiği yerde (burada: support) kalır ve ayrıca istenmelidir. "3 tane BEGIN CERTIFICATE" = zincir,
> anahtar değil. Anahtar tanınır: içinde `BEGIN PRIVATE KEY` geçer (PEM) ya da `.pfx`/`.p12` (ikili).

### Yapılan adımlar (sunucuda, `/srv/kod/tti-sunucu`)
1. `fullchain.pem` (= `_.turktelekomint.com.pem`) + `privkey.pem` (= `_...-private-key.key`) Windows'tan
   **scp** ile taşındı → `certs/`. `chmod 600 privkey.pem`, `644 fullchain.pem`, `sed -i 's/\r$//'` (CRLF).
2. **Eşleşme doğrulandı** (deploy'dan önce şart): cert ve anahtarın public kısmı aynı →
   `openssl x509 -pubkey` vs `openssl pkey -pubout` diff = eşit.
3. `Caddyfile`: `http://` blokları → `https://` + `tls certs/fullchain.pem certs/privkey.pem`; ayrı bir
   `http://…` bloğu `redir https://{host}{uri} permanent`. **`auto_https off` KALDI** (ACME denemesin;
   manuel `tls` ondan bağımsız).
4. `docker-compose.yml` proxy: `- "443:443"` portu + `- ./certs:/etc/caddy/certs:Z,ro` mount (`:Z` = RHEL SELinux).
5. `.env`: `DOMAIN=tti.local` → `DOMAIN=turktelekomint.com`.
6. Firewall: `sudo firewall-cmd --permanent --add-service=https && --reload`.
7. `sudo docker compose up -d` → proxy yeniden oluştu (build gerekmedi).

### `certs/` git/zip DIŞINDA
`.gitignore`'a `certs/` eklendi; §7 zip komutuna `-x '*/certs/*'` eklendi. Özel anahtar pakette dolaşmaz
ve `unzip -o` sunucudaki gerçek sertifikayı Mac'teki self-signed ile **ezmez**. Dev makinede proxy'yi
çalıştırmak için yerel self-signed üretilir (README "Yerel prova").

### Doğrulama komutları
```bash
ss -tlnp | grep -E ':(80|443|9443) '                       # 80,443,9443 dinliyor
curl -sSI --resolve hakedis.turktelekomint.com:443:127.0.0.1 https://hakedis.turktelekomint.com/api/saglik   # HTTP/2 200
curl -sSI --resolve hakedis.turktelekomint.com:80:127.0.0.1  http://hakedis.turktelekomint.com/             # 301 → https
openssl s_client -connect hakedis.turktelekomint.com:443 -servername hakedis.turktelekomint.com </dev/null 2>/dev/null | openssl x509 -noout -subject -dates
bash dogrula.sh                                            # 6a artık HTTPS + yönlendirme sınar
```

### 🔁 Sertifika yenileme (SON 2027-02-27 — dolmadan önce yap)
Dolmadan önce support'tan **yeni sertifika + yeni özel anahtar** iste (yenileme sürecini/lead-time'ı
önceden netleştir — kim başlatıyor?). Geldiğinde:
1. Yeni `fullchain.pem` + `privkey.pem`'i `certs/`'e koy (aynı adlar), `chmod 600 privkey.pem`, CRLF temizle.
2. **Eşleşmeyi doğrula** (yukarıdaki adım 2) — yanlış anahtar gelmişse dur.
3. `sudo docker restart tti-proxy` (Caddy dosyayı yeniden okur; compose değişmediyse `up -d` yeniden
   oluşturmaz, o yüzden **restart**). Doğrulama komutlarıyla teyit et.
> Anahtar dolmadan yenilenmezse tarayıcılar "güvenli değil" der, kullanıcılar giremez.

### Geri dönüş
`cp Caddyfile.bak Caddyfile && cp docker-compose.yml.bak docker-compose.yml && sudo docker compose up -d`.
(Ara mod — 443 ağdan erişilemezse yönlendirmeyi kaldırıp http+https'i birlikte sunmak: §"443" / B modu.)

---

## 15. Uygulama güncellemesi — Yeknesek durum + saha geri bildirimleri + poz revizyonu (2026-08-14)

**Ne değişti (tti-hakedis):**
- **Yeknesek durum makinesi:** üç aşama tek döngüde — Onay Bekliyor → İşleniyor → Tamamlandı
  (+ İptal Edildi). Taslak/İade kalktı; yönetici onayı proje listesinde; proje durumu güncel aşamadan
  türetilir; Projeler ↔ Belgeler aynı durum filtresi. Yeni migration **V17** (`yeknesek_durum`).
- **Sözleşme revizyonu** (Excel round-trip): yeni sürüm **pasif** doğar, eskiye dokunmaz, `onceki_id`
  ile bağlanır. Yeni migration **V18** (`sozlesme_revizyon`).
- Sorumlu = bölge sorumlusu; kullanıcı yönetimi (ekle/düzenle/pasife al); belge ekranında
  kaydedilmemiş değişiklik uyarısı; proje kartında Durum Geçmişi (log); "Siparişleri Excel olarak
  indir" (hakedişi tamamlanmış projeler: SP No + Hakediş−1.Keşif farkı + Toplam).

**Sunucuya nasıl gitti (§7 prosedürü):** Mac'te zip → sha256 → `/srv/kod`'a aç → `.env` `SURUM=2026-08-14`
→ `cd /srv/kod/tti-sunucu && sudo docker compose build && sudo docker compose up -d`. Sunucu geride
olduğu için **V16 + V17 + V18 birlikte** uygulandı (log: *"Successfully applied 3 migrations ... now at
version v18"*). Deploy temiz; web'de doğrulandı.

**⚠️ Bu turdaki DERSLER (gelecek güncellemeler için):**
1. **V17/V18 mevcut veriyi DÖNÜŞTÜRÜR** (V16'nın aksine — o sadece tablo eklemişti). V17 `belge` ve
   `is_kaydi` durumlarını yeni kümeye eşler; V18 `sozlesme.onceki_id` ekler. Bu yüzden **güncelleme
   öncesi `yedek.sh` ŞART** (§0/§7 backup adımı atlanmaz).
2. **`SURUM` geri almak yeterli DEĞİL:** eski tarihe çevirmek **kodu** eskitir ama **V17/V18 veri
   göçünü GERİ ALMAZ**. Şema-seviyesi bir sorunda tek kurtarma yolu **yedekten DB geri yükleme**dir
   (RedHatKurulum.md §12). Kod-seviyesi rollback yalnızca davranış hatası içindir.
3. **Doğrulama iki komut:** (a) `docker logs tti-hakedis-server | grep -iE "Successfully applied|ERROR"`
   → "now at version v18", ERROR yok; (b) `/api/saglik` → `"surum":"2026-08-14"` (çalışan sürüm dışarı
   dönüyor). Sürüm alanı yanlışsa `.env` `SURUM` güncellenmemiş demektir.
4. **Dev test verisi prod'a gitmez:** `deploy/dogrula-faz-*.sh` betikleri geliştirme DB'sine test
   kayıtları (ör. `mtc-dogrulama-v` kablosu) yazar ama bunlar **migration'da değildir**; prod temiz
   DB'de yalnızca migration çalıştığı için oraya asla ulaşmaz. Paniğe gerek yok — dev'de görülüp
   prod'da olmayan `dogrulama`/test kayıtları normaldir.

---

## 16. 2026-08-17 — reboot testi, iki deploy, hakediş temiz başlangıcı

Tek günde dört iş: reboot testi kapandı, **rapor** ve **hakediş** ayrı ayrı `2026-08-17` sürümüne
çıktı, hakediş verisi sıfırlandı. Sonuç: her iki uygulama da ayakta ve doğrulandı.

### 16.1 Reboot testi ✅
Reboot sonrası 5 konteynerin hepsi kendiliğinden kalktı (`Up 14 minutes`, üçü `healthy`).
`systemctl is-enabled docker` → `enabled`; beş konteynerin de `RestartPolicy=unless-stopped`,
`RestartCount=0`. Kurulumdan bu yana hiçbiri çökmemiş.

> ⚠️ `RestartCount` konteyner **yeniden oluşturulduğunda sıfırlanır**; "0" mutlak bir geçmiş değil,
> son deploy'dan bu yanadır.

### 16.2 Rapor deploy'u — kablo kataloğu (943)
**Sorun:** sunucudaki rapor `2026-08-11` sürümündeydi; 943 kayıtlık katalog `KatalogTohumu943`
migration'ıyla **15 Ağustos'ta** geldi, yükleme ucu ve yönetici butonu da 15–16 Ağustos'ta. Yani
"web'den Excel yüklerim" **mümkün değildi** — yükleme özelliğinin kendisi deploy edilecek kodun içindeydi.

**Çözüm:** yalnızca `tti-rapor` zip'lendi (2.1 MB değil, 157 KB), `SURUM_RAPOR=2026-08-17`,
`docker compose build tti-rapor-server` + `up -d tti-rapor-server`. Migration açılışta koştu
(`Applying migration '20260815120000_KatalogTohumu943'`), katalog **943** oldu. Excel yüklemeye
gerek kalmadı.

> **Ders:** `docker compose build` servis adı **verilmeden** çalıştırılırsa diğer projeyi de yeniden
> derler ve onun sürüm etiketinin altına yarım kalmış dev kodunu yazar — geri dönüş imkânsızlaşır.
> Deploy komutları daima servis adıyla: `build tti-rapor-server`, `up -d tti-rapor-server`.

### 16.3 Hakediş deploy'u — üç arayüz değişikliği (müdür isteği)
1. Kenar çubuğunda **kullanıcı adı yerine bölge adı** (yöneticide "Yönetici"). Oturum yanıtına
   `bolgeAdi` eklendi (`KimlikDenetleyici`) — istemci ayrıca `/api/bolgeler` çekmesin diye.
2. **"Siparişleri Excel olarak indir" tamamen kaldırıldı** — buton, uç (`/api/isler/siparisler.xlsx`),
   servis metodu, `SiparisExceli.kt` ve üç etiket silindi.
3. **Projeler Excel'i**: alttaki toplam satırı kalktı, tablonun sonuna iki sütun geldi —
   *Fizibilite / 1. Keşif Farkı* ve *1. Keşif / Hakediş Farkı* (ikisi de **sonraki − önceki**).

Migration **yok** (V18'de kalındı), yani bu deploy kod-seviyesiydi ve `SURUM` geri alma gerçekten
güvenliydi — §15'teki V17/V18 durumunun aksine.

> **Tuzak:** `unzip -o` üzerine yazar ama **silmez**. Depodan kaldırılan `SiparisExceli.kt` sunucuda
> kalsaydı, artık var olmayan `etiketler.sutunSiparisler` alanını arayıp derlemeyi düşürürdü. Dosya
> silen her deploy'da açılıştan sonra elle kontrol gerekir.
>
> **Karar:** toplam satırı kaldırmak yalnızca istek değil, doğru olandı — tabloda `setAutoFilter`
> var ve sabit toplam satırı süzgeçle güncellenmiyordu; süzülmüş listeye bakan kişi yanlış toplam
> görüyordu. Excel'in kendi seçim toplamı süzgece uyar.

### 16.4 Hakediş temiz başlangıcı
Müdür isteğiyle proje/belge/sözleşme/kablo verisi sıfırlandı: **7 proje, 18 belge, 4 sözleşme,
212 poz, 943 kablo** silindi; **8 kullanıcı ve 7 bölge korundu**. Betik: `tti-hakedis/deploy/temiz-baslangic.sh`
(`ONAY=EVET` kapısı, tek transaction, öncesi/sonrası sayım).

```bash
cd /srv/kod/tti-sunucu && sudo bash yedek.sh            # önce yedek
cd /srv/kod/tti-hakedis
ONAY=EVET DOSYALAR=EVET DOCKER="sudo docker" \
  ARSIV_KOK=/srv/veri/tti-hakedis/arsiv \
  YUKLEME_KOK=/srv/veri/tti-hakedis/yukleme \
  bash deploy/temiz-baslangic.sh
```

`TRUNCATE ... CASCADE` **kullanılmaz** (CLAUDE.md §13): cascade `is_kaydi` → `fiyat_versiyonu` →
`poz_fiyat` zincirini izleyip referans fiyat listesini de siler. Silme sırası betikte elle yazılı.
`bolge`/`kapsam` korunur (silinseydi `EnumSenkronDogrulayici` uygulamayı hiç açmazdı), `kullanici`
korunur (silinseydi kimse giremezdi; çıkarmanın yolu arayüzden pasife almak).

**Dosyalar ayrı adım:** DB boşalınca arşivdeki PDF'ler ve yüklenen EK'ler kayıtsız kalır.
`/srv/veri/tti-hakedis/arsiv` (2.1 MB) ve `.../yukleme` (76 KB) `sudo find … -mindepth 1 -delete`
ile boşaltıldı — `-mindepth 1` şart, klasörün kendisi silinirse bind mount hedefi kaybolur ve hata
ancak ilk PDF üretiminde çıkar.

**Temizlikten sonra iki adım zorunlu** (yapılmadan proje açılamaz): yönetici → **Sözleşmeler**'den
`.xlsx` yükle, sonra **Kablo Envanteri**'nden `cable_data_28072026.xlsx` → Fark → **Yürürlüğe Al**.

### 16.5 ⚠️ Bu turda öğrenilen üç işletim gerçeği

1. **`aurun` uid 8001, `atosadm` uid 1000.** Uygulama konteyneri `USER 1000:1000` ile çalışır
   (`Dockerfile`), `/srv/veri/*` ise `atosadm:atosadm` sahipli ve **750** izinli. Yani konteyner
   yazabilir ama `aurun` o klasörlere **bakamaz** — `/srv/veri` altındaki her işlem `sudo` ister.
   Dosya sahipliği diskte **sayı** olarak durur; `atosadm` yalnızca 1000'in `/etc/passwd`'deki adıdır.
   ⚠️ Sonucu: `atosadm` silinip ileride başka bir kullanıcı uid 1000 alırsa, hiçbir komut
   çalıştırmadan **tüm uygulama verisinin sahibi olur**.
2. **`dogrula.sh` `sudo`suz çalıştırılınca yanıltıcı hata veriyor:** `docker exec`'i `2>/dev/null`
   ile çağırdığı için soket izin hatasını yutup *"Veritabanı konteynerine ulaşılamadı: tti-db"*
   diyor — oysa `tti-db` sapasağlam. Doğrusu **`sudo bash dogrula.sh`**.
3. **Sunucu saati 8 saat geride** (§5'te açık madde). Konteynerler doğru (`TZ` geçiliyor), host
   tarafı değil. Yedek timer'ı kurulmadan düzeltilmeli.

---

## 17. 2026-08-19 — hakediş EK3 + imza bloğu, rapor kullanıcı yönetimi (planlanan deploy)

Bu tur **canlı veri üzerine** çıkıyor: hakediş tarafında gerçek projelerle işlem başladı. Bu yüzden
deploy sırası ve doğrulama, önceki turlardan farklı olarak **veri koruma** üzerine kurgulandı.

### 17.1 Neden hakediş için risk yok

İki yeni migration da **eklemeli** (additive); var olan hiçbir satıra dokunmuyorlar:

| Migration | Ne yapıyor | Var olan veriye etkisi |
|---|---|---|
| `V19__belge_malzeme.sql` | `create table belge_malzeme` + indeks | Yok — yeni tablo |
| `V20__admin_yoneticisi.sql` | `insert … on conflict do nothing` | Yok — tek yeni satır (`admin`) |

`update`/`delete`/`drop` **hiç yok**. Kod tarafındaki davranış değişiklikleri de geçmişe dönük
çalışmıyor: `projeDurumunuTazele`'nin yeni eşlemesi ancak bir durum geçişi olduğunda,
`asamaGeriAc` ancak iade basıldığında koşuyor.

⚠️ **Rapor tarafı farklı:** `KapsamTablosuVeGuzergahDosyasi` migration'ı `kablo_talep`'ten
`konum_a_lat/lon` ve `konum_b_lat/lon` sütunlarını **düşürüyor** (GPS alanları kaldırıldı, yerine
.kml/.kmz geldi). Geri dönüşü yalnızca yedektir. Rapor verisi 18 Ağustos'ta sıfırlandığı için
pratikte kayıp beklenmiyor — ama sıra bu yüzden **önce hakediş, sonra rapor**.

### 17.2 Deploy sırası

1. **Dağıtım öncesi sayım** — `is_kaydi`, `belge`, `belge_satir`, `ek`, `sozlesme`, `kable`,
   `kullanici` sayıları ve `max(version)` not edilir.
2. Paketle → `sha256sum` karşılaştır → `unzip -o -d /srv/kod`.
3. **`yedek.sh` + `yedek.sh dogrula`** — temiz çıkmadan devam yok.
4. `.env`: `SURUM` ve `SURUM_RAPOR` → yeni tarih.
5. `docker compose build tti-hakedis-server` → `up -d tti-hakedis-server` (**servis adıyla**).
6. **Sayım tekrar** ve 1. adımla karşılaştırma. Beklenen: şema sürümü **20**,
   `Successfully applied 2 migrations`, `kullanici` **+1** (yeni `admin`), diğer bütün sayılar
   **aynı**. Fark varsa geri dönülür.
7. Ancak bundan sonra `build tti-rapor-server` → `up -d tti-rapor-server`.
8. `sudo bash dogrula.sh`.

### 17.3 Bu turda çıkan yenilikler

**Hakediş:** EK3 "TTI Kullanılan Malzemeler" (yalnızca hakediş aşaması, üç alanı da opsiyonel;
Excel'de EK2'nin altında, PDF'te ek bilgilerde) · Excel'de **Toplam altında imza bloğu** ve
**sağ üstte TTI logosu** · **Önceki Aşamaya İade Et** (aşamayı sıfırlar, gerekçesiyle proje durum
geçmişine yazılır) · sözleşme adı düzenleme ve koşullu silme · kablo envanterinde elle ekleme
kaldırıldı, indirme satır menüsüne taşındı · ikinci yönetici hesabı `admin`.

**Rapor:** `admin` sistem yöneticisi + **Admin Girişi** sekmesi ve kullanıcı yönetimi (giriş adı ve
parola değiştirme) · yenilenen şifre pencereleri · katalog yüklemede başarı animasyonu · GPS yerine
.kml/.kmz güzergah dosyası · zorunlu güzergah uzunluğu (metre) · yöneticinin kapsam ekleyebilmesi.

### 17.4 ⚠️ Geri dönüşün sınırı

`SURUM`/`SURUM_RAPOR` eskitilip `up -d` demek **imajı** geri alır, **veritabanını almaz.**

- **Hakediş:** sorun değil — V19/V20 eklemeli, eski imaj yeni tabloyu yok sayar.
- **Rapor:** düşen GPS sütunları geri gelmez; oradaki tek gerçek geri dönüş 3. adımdaki yedektir.

⚠️ Üretimde `dogrula-faz-*.sh` betiklerinden `SIFIRLA=1` isteyenler **çalıştırılmaz** — proje/belge
kaydı oluştururlar, yalnızca geliştirme veritabanı içindir.

---

## 18. 2026-08-19 — TTI Merkezi Veri (`tti-data`) platforma katılıyor (planlanan deploy)

Üçüncü proje. Sunucuda **hiç bulunmadı**, yani bu bir güncelleme değil **ilk kurulum**.
Hakediş ve rapor'un kaynağına dokunulmadı; onlar için yeniden derleme **gerekmez**.

### 18.1 Ne gidiyor

| Depo | Durum |
|---|---|
| `tti-data/` | **Tamamen yeni** |
| `tti-sunucu/` | `docker-compose.yml` (2 servis bloğu + proxy `depends_on`), `Caddyfile` (site), `provizyon/03-data.sql` (yeni), `yedek.sh` (2 liste), `dogrula.sh` (portlar artık `.env`'den, port sızıntısı kontrolü konteynerlere bakıyor), `.env.example`, `YENI-PROJE.md`, `README.md` |
| `tti-hakedis/`, `tti-rapor/` | **Değişmedi** — pakete girmiyor |

```bash
cd ~/Desktop/tti-projeler
zip -rq tti-tasima.zip tti-data tti-sunucu \
  -x '*/.venv/*' '*/venv/*' '*/node_modules/*' '*/dist/*' '*/backups/*' \
     '*/__pycache__/*' '*.pyc' '*/.pytest_cache/*' '*/.ruff_cache/*' \
     '*/api_keys.txt' '*/.env' '*/certs/*' '*/yerel-veri/*' '*/deploy/yukleme/*' \
     '*.DS_Store' '*/.git/*' '*.bak'
shasum -a 256 tti-tasima.zip
```
⚠️ `zip -r` `.gitignore`'a **bakmaz** — dışlamalar elle verilir. `backups/` (76 MB kullanıcı
verisi), `.venv` (227 MB, üstelik Windows'ta üretilmiş), `api_keys.txt` ve `.env` kasıtlı dışarıda.
Paket ~1.4 MB / 130 dosya olmalı; çok daha büyükse bir dışlama tutmamıştır.

### 18.2 Sunucuda elle yapılacaklar (unzip bunları yapmaz)

```bash
sudo mkdir -p /srv/veri/tti-data/yukleme && sudo chown -R 1000:1000 /srv/veri/tti-data
sudo nano /srv/kod/tti-sunucu/.env          # .env.example'daki "Proje 3" bloğu — 9 satır
sudo nano /srv/kod/tti-sunucu/provizyon/03-data.sql   # parolayı DATA_DB_PAROLA ile EŞİTLE
```
⚠️ `SURUM` ve `SURUM_RAPOR`'a **DOKUNULMAZ** (2026-08-11 kalır) — hakediş/rapor değişmedi.
Yalnızca `SURUM_DATA=2026-08-19` eklenir.

### 18.3 Deploy sırası

```bash
cd /srv/kod/tti-sunucu
sudo bash yedek.sh                                      # 1) ÖNCE YEDEK
sudo docker exec -i tti-db psql -U tti_hakedis -d tti \
     -v ON_ERROR_STOP=1 < provizyon/03-data.sql         # 2) provizyon (bir kez)
sudo docker compose build tti-data-server tti-data-panel  # 3) ⚠️ İKİ SERVİS ADI DA
sudo docker compose up -d tti-data-server tti-data-panel  # 4)
sudo docker compose up -d proxy                          # 5) Caddyfile değişti
```
⚠️ **Çıplak `docker compose build` YAZILMAZ** — hakediş ve rapor imajlarını da yeniden derler ve
`SURUM` etiketlerini ezer; geri dönüş imkânı yok olur.

⚠️ 5. adım `tti-proxy`'yi yeniden başlatır → hakediş ve rapor kullanıcıları için **1-2 saniye**
kesinti. Uygulama konteynerleri durmaz. Mesai dışına alınabilir.

### 18.4 Doğrulama

```bash
sudo docker ps --filter name=tti-data                    # ikisi de (healthy)
curl -s http://localhost:8083/api/saglik                 # "surum":"2026-08-19"
sudo bash dogrula.sh                                     # tümü geçmeli
sudo bash yedek.sh && sudo bash yedek.sh dogrula         # `data` dump + yukleme görünmeli

# alembic_version'ın YERİ — en kolay unutulan madde
sudo docker exec -i tti-db psql -U tti_hakedis -d data -tAc \
  "select table_schema from information_schema.tables where table_name='alembic_version'"  # → data
sudo docker exec -i tti-db psql -U tti_hakedis -d data -tAc \
  "select count(*) from information_schema.tables where table_schema='public'"             # → 0

# hakediş verisi dokunulmadı mı (provizyon öncesi/sonrası AYNI çıkmalı)
sudo docker exec -i tti-db psql -U tti_hakedis -d tti -tAc \
  "select count(*) from hakedis.is_kaydi; select count(*) from hakedis.kullanici;"
```

### 18.5 Neden hakediş ve rapor için risk yok

- `provizyon/03-data.sql` `-d tti` ile açılır (çünkü `create database` için bir yere bağlı olmak
  gerekir) ama içindeki ifadelerin hiçbiri `tti`'nin İÇİNİ değiştirmez: ikisi küme düzeyi
  (`create user`, `create database`), üçü hedefi `data` olan yetki ifadesi. `\connect data`'dan
  sonrası zaten yeni veritabanının içi.
- `tti-db` yeniden başlatılmaz, `max_connections`/`shared_buffers`/`mem_limit` değişmez.
- Veritabanı volume'ü `external: true` — `down -v` bile silemez.
- `yedek.sh`/`dogrula.sh` değişiklikleri **eklemeli**; mevcut `tti`/`rapor` kayıtları aynen kalır.
- Bağlantı bütçesi 25 → 35 (47 tavanda); hakediş ve rapor'un 10'ar havuzu aynen durur.

### 18.6 ⚠️ Deploy'dan sonra kalan iki iş

1. **DNS A kaydı** — `data.<DOMAIN>` → sunucu IP'si. IT'nin işi. Kayıt olmadan deploy başarılı
   görünür, `dogrula.sh` bile geçer (`--resolve` kullanıyor), ama kullanıcı adresi yazdığında
   hiçbir yere ulaşamaz. Sertifika için ek iş yok — wildcard `data.` alt alan adını kapsar.
2. **Dataset şemaları ve API izinleri** — temiz veritabanında `datasets.schema_definition` ve
   `dataset_permissions` YOKTUR ve Excel yeniden yüklenerek geri gelmezler. Eski veritabanının
   dump'ı varsa iki tablo birlikte taşınır; yoksa `tti-data/deploy/sema-taslagi-uret.py` yedek
   dosyalarından taslak üretir. Ayrıntı: `tti-data/deploy/KURULUM.md` §5b.

### 18.7 Geri dönüş

Yeni proje olduğu için geri dönüş = eklemeyi geri almak:
```bash
sudo docker compose stop tti-data-server tti-data-panel
sudo docker compose rm -f tti-data-server tti-data-panel
# Caddyfile'daki data bloğunu kaldır → sudo docker compose up -d proxy
```
`data` veritabanı ve `/srv/veri/tti-data` yerinde kalır; hakediş/rapor hiç etkilenmez.
