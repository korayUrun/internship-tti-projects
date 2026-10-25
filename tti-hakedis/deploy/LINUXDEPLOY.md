# Linux Sunucuya Grafik Arayüz (GUI) Erişimi — RHEL 9

Arayüzsüz (headless) kurulmuş bir Red Hat sunucusuna, Windows'un **yerleşik Uzak Masaüstü
Bağlantısı** (`mstsc`) ile grafik masaüstü erişimi. 7 Ağustos 2026'da `example-host.tti.intra`
(RHEL 9.8, kullanıcı `deploy-user`) üzerinde uygulanmış ve çalıştığı doğrulanmış adımlardır.

> Hangi rehber?
> - **Bu dosya** — sunucuya grafik arayüz kurma ve uzaktan bağlanma
> - `RedHatKurulum.md` — Red Hat sunucuda TTI yığınını konteynerle ayağa kaldırma
> - `KURULUM.md` — yalnızca TTI'nin kendi ayarları (`.env`, arşiv klasörleri, kable içe aktarımı)

⚠️ **GUI zorunlu değildir.** TTI yığınının hiçbir parçası grafik arayüz istemez — PostgreSQL,
Spring Boot jar'ı, Docker ve **PDF üretimi** (openhtmltopdf saf Java, fontlar jar'ın içinde
gömülü, X sunucusu gerekmez) tamamen headless çalışır. Web arayüzü de yöneticinin **kendi**
bilgisayarının tarayıcısından açılır. GUI yalnızca sunucuyu grafik araçlarla yönetmek istendiğinde
gerekir ve ~1–1,5 GB RAM tüketir. Hafif alternatif: `cockpit` (RHEL'in kendi web yönetim paneli,
port 9090).

---

## 0. Sonuç ve seçim gerekçesi

| | Seçilen | Neden |
|---|---|---|
| Masaüstü | **GNOME** (`Server with GUI`) | RHEL 9'da desteklenen tek masaüstü. KDE Plasma RHEL 8'de depolardan çıkarıldı. |
| Uzak erişim | **xrdp** + Windows `mstsc` | Windows tarafına **hiçbir şey kurulmuyor**; RDC her Windows'ta yerleşik. |
| Bağlantı yolu | **SSH tüneli** | Güvenlik duvarında port açılmıyor; trafik kurumsal ağdan çıkmıyor. |

**Neden AnyDesk/TeamViewer değil:** bağlantı üreticinin dış sunucuları üzerinden geçer (ekran
görüntüsü kurumsal ağdan çıkar), sunucunun sürekli internet erişimi gerekir ve ücretsiz sürüm
kişisel kullanımla sınırlıdır. NOC sunucusunda bu üç maddenin her biri ayrı bir itiraz sebebidir.

**Neden VNC değil:** `tigervnc-server` Red Hat deposunda ve desteklenir — ama Windows'ta yerleşik
VNC istemcisi **yoktur**, iş istasyonuna ek program indirmek gerekir. xrdp'nin tek dezavantajı
(EPEL'de olması) buna tercih edildi.

---

## 1. Ön kontroller

```bash
cat /etc/os-release                  # RHEL sürümü — /etc/redhat-release bazı imajlarda yok
sudo dnf makecache && sudo dnf repolist
df -h /                              # en az 5 GB boş gerekli
free -h
```

`repolist` çıktısında şu ikisi görünmelidir:

```
rhel-9-for-x86_64-baseos-rpms
rhel-9-for-x86_64-appstream-rpms
```

> `subscription-manager status` çıktısı **`Overall Status: Registered`** olabilir; RHEL 9'da Simple
> Content Access varsayılan açık olduğu için `Current` yerine bunu yazar. Sorun değil — asıl kanıt
> `dnf repolist`'tir.

EPEL erişimini de baştan doğrulayın (3. adımda gerekecek):

```bash
curl -sI --max-time 10 -o /dev/null -w 'HTTP %{http_code}\n' \
  https://dl.fedoraproject.org/pub/epel/epel-release-latest-9.noarch.rpm
```

`HTTP 200` veya `302` bekleniyor. `000` çıkarsa sunucu dışarıya çıkamıyor; RPM'leri elle taşımak
gerekir.

---

## 2. GNOME masaüstünü kur

```bash
sudo dnf group install -y "Server with GUI"
```

10–20 dakika sürer, ~2 GB indirir. `Complete!` beklenir. RHEL'in kurulum profillerinden biridir
(`graphical-server-environment`); "Workstation" profili LibreOffice/medya araçları da getirdiği
için sunucuda tercih edilmez.

Bu komut hiçbir servisi başlatmaz, hiçbir paketi kaldırmaz — sistem hâlâ metin modunda açılır.

---

## 3. EPEL'i ekle, hemen kapat, xrdp'yi kur

xrdp Red Hat depolarında yoktur. EPEL'i kalıcı olarak açık bırakmak, ileriki `dnf update`'lerde
RHEL paketlerinin yerine topluluk sürümlerinin geçmesine yol açar — bu yüzden depo **kapalı**
tutulur ve yalnızca kurulum işlemi boyunca açılır.

```bash
sudo dnf install -y https://dl.fedoraproject.org/pub/epel/epel-release-latest-9.noarch.rpm
sudo dnf config-manager --set-disabled epel
sudo dnf config-manager --set-disabled epel-cisco-openh264
```

Doğrulama — `epel` satırının sonunda `disabled` yazmalı:

```bash
dnf repolist --all | grep -i epel
```

Kurulum (iki paket **birlikte**):

```bash
sudo dnf install -y --enablerepo=epel xrdp xorgxrdp
```

⚠️ **`xorgxrdp`'yi ayrıca yazmak zorunludur.** xrdp onu yalnızca "önerilen" (weak) bağımlılık
olarak tanımlar ve gelmeyebilir. Gelmezse bağlantı kurulur ama oturum
**`X server could not be started`** ile düşer ve hata xrdp'yi işaret etmez.

Doğrulama:

```bash
rpm -q xrdp xorgxrdp
```

---

## 4. GDM'de Wayland'i kapat

xrdp **yalnızca Xorg** ekranını yakalayabilir; RHEL 9'da GNOME varsayılan olarak Wayland ile açılır.
Bu adım atlanırsa bağlantı kurulur ve **siyah/boş ekran** gelir.

Dosya Red Hat'in kendi önerisini yorum olarak zaten taşır (*"Uncomment the line below to force the
login screen to use Xorg"*), yaptığımız o yorumu açmaktır:

```bash
sudo cp /etc/gdm/custom.conf /etc/gdm/custom.conf.yedek
sudo sed -i 's/^#WaylandEnable=false/WaylandEnable=false/' /etc/gdm/custom.conf
grep Wayland /etc/gdm/custom.conf
```

Beklenen: başında `#` **olmayan** bir `WaylandEnable=false` satırı.

---

## 5. xrdp'nin PAM yığınını GNOME'a çevir

`/etc/pam.d/xrdp-sesman` iki hazır blok içerir ve dosyanın kendi yorumu şunu söyler: *"Uncomment
this block and comment out the generic block above if you are running xrdp sessions into a GNOME
desktop."* GNOME kullandığımız için GDM'nin PAM yığınına geçiyoruz — oturumu `systemd-logind`'e
kaydeder ve GNOME Keyring'i otomatik açar.

```bash
sudo cp /etc/pam.d/xrdp-sesman /etc/pam.d/xrdp-sesman.yedek
printf '%s\n' '#%PAM-1.0' \
  '# GNOME masaustu icin GDM PAM yigini kullaniliyor (paketin kendi onerisi).' \
  '# Onceki hali: /etc/pam.d/xrdp-sesman.yedek' \
  'auth       include      gdm-password' \
  'account    include      gdm-password' \
  'password   include      gdm-password' \
  'session    include      gdm-password' | sudo tee /etc/pam.d/xrdp-sesman > /dev/null
cat /etc/pam.d/xrdp-sesman
```

---

## 6. ⛔ `pam_systemd` kontrolü — bu adım atlanamaz

Bu sunucuda **`pam_systemd.so`, `system-auth` ve `password-auth` dosyalarından çıkarılmıştı.**
Standart RHEL 9'da bu satır bulunur. Yokluğunun sonucu xrdp'ye özgü değil, **sistem genelidir**:
hiçbir giriş — SSH dahil — `systemd-logind`'e kaydedilmez, `systemd --user` örneği hiç başlamaz ve
GNOME Shell kendi oturum kimliğini bulamayıp çöker.

Belirti (RDC'de gördükleriniz):

```
Oh no! Something has gone wrong.
A problem has occurred and the system can't recover.
```

Log'daki karşılığı:

```
gnome-session-binary: WARNING: Could not get session id for session.
  Check that logind is properly installed and pam_systemd is getting used at login.
gnome-shell: JS ERROR: TypeError: this._userProxy.Display is null
gnome-session-binary: Unrecoverable failure in required component org.gnome.Shell.desktop
```

**Teşhis** (üçü birlikte kesin sonuç verir):

```bash
loginctl list-sessions        # "No sessions." → SSH oturumu bile kayıtlı değil
echo "[$XDG_SESSION_ID]"      # boş → kayıt yok
grep pam_systemd /etc/pam.d/system-auth /etc/pam.d/password-auth /etc/pam.d/postlogin
                              # hiç çıktı vermiyorsa satır eksik
```

`grep -r pam_systemd /etc/pam.d/` **kullanmayın** — bu dosyalar bazı kurulumlarda sembolik bağdır
ve `grep -r` onları atlayarak yanlış sonuç verir.

**Düzeltme** — eksik satır geri konur (yeni bir şey eklenmiyor, RHEL varsayılanına dönülüyor):

```bash
sudo cp /etc/pam.d/password-auth /etc/pam.d/password-auth.yedek
sudo cp /etc/pam.d/system-auth   /etc/pam.d/system-auth.yedek
sudo sed -i '/pam_limits\.so/a -session    optional      pam_systemd.so' \
  /etc/pam.d/password-auth /etc/pam.d/system-auth
grep -A1 pam_limits /etc/pam.d/password-auth /etc/pam.d/system-auth
```

Baştaki `-` işareti "modül bulunamazsa sessizce atla" demektir; Red Hat'in kendi dosyasında da
böyle yazar.

**Ucuz doğrulama** — RDP'yi denemeden önce bunu yapın. PAM ayarı yalnızca **yeni** girişlerde
devreye girdiği için yeni bir SSH bağlantısı açmak gerekir:

```bash
# yeni bir SSH oturumu açıp:
loginctl list-sessions        # artık bir satır görülmeli
```

⚠️ **Kalıcılık uyarısı:** `example-host`'de bu iki dosya 5 Ağustos 2026 tarihliydi ve
`# This file is auto-generated. User changes will be destroyed the next time authconfig is run.`
başlığını taşıyordu — yani bir kurulum/sıkılaştırma otomasyonu yazmış. **O otomasyon tekrar
koşarsa satır silinir ve masaüstü aynı şekilde çöker.** `authselect` bu sunucuda kullanılmıyor
("No existing configuration detected"), dosyalar doğrudan yönetiliyor. Masaüstü bir gün sebepsiz
"Oh no!" vermeye başlarsa **ilk bakılacak yer burasıdır.** Kalıcı çözüm satırı kurumsal baseline'a
eklemektir; BT'ye bildirilecek kalem budur.

---

## 7. Servisleri başlat

```bash
sudo systemctl set-default graphical.target
sudo systemctl enable --now xrdp
systemctl is-active xrdp xrdp-sesman
sudo firewall-cmd --state
sudo reboot
```

Beklenen: `active`, `active`, `running`.

`firewall-cmd --state` → `running` olması **istenen durumdur**: 3389 dışarıya kapalı kalır.
**Güvenlik duvarında port açılmaz.**

Reboot sonrası:

```bash
systemctl is-active xrdp xrdp-sesman gdm     # üçü de active
```

---

## 8. Windows'tan bağlanma

**Windows tarafında kurulacak hiçbir şey yoktur.**

**1.** Bir PowerShell penceresi açın, tüneli kurun ve **pencereyi kapatmayın**:

```powershell
ssh -L 13389:localhost:3389 deploy-user@example-host
```

Bu, sunucudaki 3389 portunu iş istasyonundaki 13389'a bağlar. Trafik SSH'in şifreli tünelinden
geçer; ağda hiçbir port açılmaz. Pencere kapanırsa tünel de kapanır.

**2.** `mstsc` (Uzak Masaüstü Bağlantısı) → **Bilgisayar:**

```
localhost:13389
```

**3.** Sertifika uyarısı → **Evet**. (xrdp kendi imzalı sertifika kullanır; trafik zaten SSH
tünelinde şifreli.)

**4.** xrdp giriş ekranı:

| Alan | Değer |
|---|---|
| Session | `Xorg` |
| username | Linux kullanıcı adı (`deploy-user`) |
| password | Linux parolası |

---

## 9. Sorun giderme

Kurulum sırasında karşılaşılan üç hatanın tamamı ve nedenleri:

| Belirti | Neden | Çözüm |
|---|---|---|
| `user does not exist` (parola doğru olsa da) | Mesaj yanıltıcı — log'da `password check failed for user` yazar, yani **parola** yanlış gelmiştir. En sık neden xrdp giriş ekranının US klavye düzeni kullanması: Türkçe-Q'da yazılan `.` `,` `-` `*` gibi işaretler ve `ı ö ü ç ş ğ` farklı gider | Caps Lock'u kapatın, rakamları sayı adasından değil üst sıradan yazın. Teşhis: giriş ekranının **username** kutusuna parolanızdaki özel karakterleri yazıp ne göründüğüne bakın |
| `X server could not be started` | `xorgxrdp` kurulmamış | `sudo dnf install -y --enablerepo=epel xorgxrdp`, sonra `sudo systemctl restart xrdp xrdp-sesman` |
| `Oh no! Something has gone wrong` | `pam_systemd` PAM yığınında yok → oturum `logind`'e kaydedilmiyor | **6. adım** |
| Siyah/boş ekran | GDM Wayland ile açılmış | **4. adım** |

Log okuma sırası:

```bash
sudo journalctl -u xrdp-sesman -n 40 --no-pager          # kimlik doğrulama / oturum açma
tail -40 ~/.xsession-errors                              # oturum başlangıcı
sudo journalctl --since "5 min ago" --no-pager | grep -i -E 'gnome-shell|gnome-session' | tail -40
```

`~/.xsession-errors` içindeki `Failed to import environment` ve
`Environment variable $XAUTHORITY not set` satırları **zararsız uyarılardır**, asıl hata değil.
`xrdp` günlüğündeki `You are running xrdp as uid=0 ... not recommended` satırı da EPEL paketinin
varsayılan yapılandırmasında normaldir.

Bozuk bir oturum kaldıysa yeni deneme öncesi temizlenir:

```bash
sudo systemctl restart xrdp xrdp-sesman
```

---

## 10. Lisans ve destek durumu

| Bileşen | Lisans | Ücret | Red Hat desteği |
|---|---|---|---|
| GNOME (`Server with GUI`) | GPL — Red Hat deposundan | Abonelikte dahil | **Var** |
| EPEL deposu | Fedora Projesi, ücretsiz | Yok | — (depo, paket değil) |
| `xrdp`, `xorgxrdp` | **Apache License 2.0** | Yok — kurumsal kullanım serbest, kullanıcı başına lisans yok | **Yok** |
| Remote Desktop Connection | Windows lisansının parçası | Dahil | — |

- **Microsoft RDS CAL gerekmez.** CAL'lar Windows Server host'a bağlanırken gerekir; RDP sunucusu
  Linux olduğunda devreye girmez.
- Tek gri alan: `xrdp`/`xorgxrdp` EPEL'den geldiği için **o iki paket** Red Hat destek kapsamı
  dışındadır. Lisans ihlali değildir; sistemin geri kalanı kapsamda kalır. EPEL kapalı tutulduğu
  için Red Hat dışından gelen paket sayısı ikiyle sınırlıdır.

---

## 11. Geri alma

```bash
sudo systemctl disable --now xrdp xrdp-sesman
sudo dnf remove -y xrdp xorgxrdp epel-release
sudo systemctl set-default multi-user.target
sudo cp /etc/gdm/custom.conf.yedek     /etc/gdm/custom.conf
sudo cp /etc/pam.d/xrdp-sesman.yedek   /etc/pam.d/xrdp-sesman   # paket kaldırıldıysa gereksiz
sudo cp /etc/pam.d/system-auth.yedek   /etc/pam.d/system-auth
sudo cp /etc/pam.d/password-auth.yedek /etc/pam.d/password-auth
```

Masaüstünü de kaldırmak için: `sudo dnf group remove "Server with GUI"`.

⚠️ `pam_systemd` satırını geri almak **sunucuyu standart RHEL 9 davranışının dışına geri
döndürür** (oturumlar `logind`'e kaydedilmez). Bu satırı yalnızca kurumsal baseline bilinçli
olarak böyle istiyorsa geri alın.

---

## 12. BT'ye bildirim metni

Aşağıdaki İngilizce özet, yapılan değişikliklerin tamamını ve dikkat çekilmesi gereken tek kalemi
içerir.

```
Changes made on example-host (RHEL 9.8)

1. Installed the GNOME desktop - "Server with GUI" package group, from our subscribed
   Red Hat repos. Within Red Hat support scope.
2. Added the EPEL repository, then disabled it immediately. It is only enabled
   per-transaction with --enablerepo=epel.
3. Installed xrdp and xorgxrdp from EPEL. Apache License 2.0 - free, commercial use
   permitted, no per-seat licensing. These are the only packages on the host from outside
   Red Hat repos, and the only ones outside Red Hat support scope.
4. Set the default systemd target to graphical.target.
5. /etc/gdm/custom.conf - set WaylandEnable=false (uncommented the line Red Hat ships
   commented). Required because xrdp cannot capture a Wayland session. Backup: .yedek
6. /etc/pam.d/xrdp-sesman - switched to the GNOME block the package itself documents
   (include gdm-password). Backup: .yedek
7. /etc/pam.d/system-auth and /etc/pam.d/password-auth - added
   "-session optional pam_systemd.so" after pam_limits.so. Backups: .yedek
   See the note below.
8. No firewall changes. TCP/3389 is not exposed. Access is via SSH local port forwarding
   (ssh -L 13389:localhost:3389), so session traffic stays inside the corporate network
   and is encrypted by SSH. No third-party relay service is involved.
9. Enabled at boot: xrdp, xrdp-sesman.

Note on item 7 - needs your awareness

On this host, pam_systemd.so was absent from system-auth and password-auth. It is present
in stock RHEL 9. The consequence was system-wide, not xrdp-specific: no login was
registered with systemd-logind, including SSH. "loginctl list-sessions" reported
"No sessions", XDG_SESSION_ID was empty, and per-user "systemd --user" instances never
started. GNOME Shell depends on this and crashed with "Oh no! Something has gone wrong."

Two things to check on your side:
- Both files are dated 5 Aug 2026 and carry an auto-generated header referencing
  authconfig, so they were written by a provisioning/hardening tool. If that tooling
  re-runs it will remove the line again and the desktop will break the same way. If the
  line belongs in your baseline, please add it there.
- Was the omission of pam_systemd intentional? authselect is not configured on this host
  ("No existing configuration detected"), so these files are managed directly.
```
