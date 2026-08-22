# TLS Sertifika Yenileme — Adım Adım

> **Kime:** Sunucuyu devralan/işleten kişiye. Docker veya Caddy bilmenize gerek yok; aşağıdaki
> komutları sırayla çalıştırmak yeterli.
>
> **Ne zaman:** Mevcut sertifika **27 Şubat 2027**'de doluyor. **Ocak 2027 başında** başlayın —
> sertifikayı siz üretmiyorsunuz, kurumsal support veriyor ve ne kadar süreceği belli değil.
>
> **Yapılmazsa ne olur:** 27 Şubat 2027'den sonra `https://hakedis.turktelekomint.com`,
> `https://rapor.turktelekomint.com` ve `https://data.turktelekomint.com` tarayıcıda
> **"Bu bağlantı güvenli değil"** uyarısı verir. ⚠️ `data.` alt alan adına **Android tabletler**
> de bağlanıyor ve Android bu durumda uyarı vermez, **el sıkışmayı tamamen reddeder** — yani
> saha uygulamaları veri çekemez hale gelir.
> Uygulama çalışmaya devam eder ama kullanıcılar uyarıyı geçmeden giremez.

---

## Önce bilmeniz gereken 6 şey

| | |
|---|---|
| **Sunucu** | `TRLPVNOCBO01` (RHEL 9), SSH ile bağlanılır, kullanıcı `aurun` |
| **Çalışma klasörü** | `/srv/kod/tti-sunucu` — bütün komutlar burada çalışır |
| **Sertifika klasörü** | `/srv/kod/tti-sunucu/certs/` — içinde **sadece 2 dosya** var |
| **Dosya adları** | `fullchain.pem` (sertifika+zincir) ve `privkey.pem` (özel anahtar) — **adlar değişmeyecek** |
| **Sertifika türü** | Wildcard `*.turktelekomint.com` → tek sertifika `hakedis.`, `rapor.` ve `data.` alt alan adlarını kapsar (yeni alt alan adı için yeni sertifika GEREKMEZ; wildcard **tek** etiket kapsar) |
| **Docker komutları** | `aurun` docker grubunda **değil** → her `docker` komutunun başına **`sudo`** |

`aurun` kullanıcısı sudo yetkisine sahiptir. `certs/` klasörü git'e ve zip'e **girmez**
(`.gitignore`) — özel anahtar asla depoya/pakete/e-postaya konmaz.

---

## Adım 0 — Ne kadar kaldı? (kontrol)

Sunucuya SSH ile bağlanın ve şunu çalıştırın:

```bash
cd /srv/kod/tti-sunucu
sudo openssl x509 -in certs/fullchain.pem -noout -subject -dates
```

Çıktıdaki `notAfter=` satırı **bitiş tarihidir**. `Feb 27 ... 2027` görüyorsanız doğru dosyaya
bakıyorsunuz. 60 günden az kaldıysa Adım 1'e geçin.

---

## Adım 1 — Support'tan yeni sertifika isteyin

Kurumsal support'a (sertifikayı 2026-08-13'te veren ekip) yazın ve **iki şeyi birden** isteyin:

1. **Yeni sertifika** — `*.turktelekomint.com` wildcard, **PEM formatında, tam zincir**
   (leaf sertifika en başta, ardından ara CA'lar).
2. **Yeni özel anahtar (private key)** — PEM, içinde `BEGIN PRIVATE KEY` geçen dosya.

> ⚠️ **Geçen sefer yaşanan tuzak:** Support önce sadece sertifika dosyalarını gönderdi. Sertifika
> dosyasının içinde özel anahtar **yoktur** — bu normaldir. Dosyada "3 tane `BEGIN CERTIFICATE`"
> görmek zincir demektir, anahtar demek değildir. Anahtarı **ayrıca istemek zorundasınız**.

İstenecek metin (kopyala-yapıştır):

> "TRLPVNOCBO01 sunucusundaki `*.turktelekomint.com` wildcard sertifikamız 27.02.2027'de doluyor.
> Yenilenmesini rica ediyoruz. Lütfen **PEM formatında tam zincir sertifika (leaf başta)** ve
> **ayrı özel anahtar dosyasını** birlikte iletin. (PFX/P12 de olabilir, bu durumda parolasını da
> paylaşın.)"

**Beklerken hiçbir şey yapmayın** — mevcut sistem çalışmaya devam eder.

---

## Adım 2 — Dosyaları sunucuya taşıyın

Support size genelde 2–3 dosya gönderir. Doğru olan ikisini seçin:

| Gelen dosya | Ne yapılacak |
|---|---|
| `_.turktelekomint.com.pem` (içinde birden çok `BEGIN CERTIFICATE`) | → **`fullchain.pem`** olacak |
| `_.turktelekomint.com-private-key.key` (içinde `BEGIN PRIVATE KEY`) | → **`privkey.pem`** olacak |
| `..._certificate-chain.pem` (sadece ara/kök CA) | **Kullanılmaz**, görmezden gelin |

Dosyaları Windows bilgisayarınızdan sunucuya kopyalayın (Windows'ta PowerShell/CMD):

```powershell
scp _.turktelekomint.com.pem              aurun@TRLPVNOCBO01:/tmp/fullchain.pem
scp _.turktelekomint.com-private-key.key  aurun@TRLPVNOCBO01:/tmp/privkey.pem
```

<details>
<summary><b>Eğer support PEM değil PFX/P12 gönderdiyse</b> (tıklayın)</summary>

Sunucuda `/tmp` içinde çevirin (parolayı support verecek):

```bash
cd /tmp
openssl pkcs12 -in sertifika.pfx -nokeys  -out fullchain.pem -nodes   # sertifika + zincir
openssl pkcs12 -in sertifika.pfx -nocerts -out privkey.pem   -nodes   # özel anahtar
```
</details>

---

## Adım 3 — Dosyaları hazırlayın (Windows satır sonlarını temizleyin)

Hâlâ **`/tmp`** üzerinde çalışıyoruz; canlı dosyalara henüz dokunmuyoruz.

```bash
cd /tmp
sed -i 's/\r$//' fullchain.pem privkey.pem     # Windows'tan gelen CRLF'i temizler (şart)
```

Doğru dosyaları aldığınızı teyit edin:

```bash
grep -c 'BEGIN CERTIFICATE' /tmp/fullchain.pem   # 2 veya 3 olmalı (leaf + ara CA'lar)
grep -c 'PRIVATE KEY'       /tmp/privkey.pem     # 1 veya 2 olmalı (BEGIN/END)
openssl x509 -in /tmp/fullchain.pem -noout -subject -dates -issuer
```

Son komutun çıktısında şunlar olmalı:
- `subject= ... CN = *.turktelekomint.com`
- `notAfter=` → **yeni, ileri bir tarih** (2027 sonu / 2028)

---

## Adım 4 — ⛔ Sertifika ile anahtar birbirine uyuyor mu? (ATLAMAYIN)

Yanlış anahtar gelirse Caddy açılmaz ve **site tamamen kapanır**. Deploy'dan önce mutlaka:

```bash
openssl x509 -in /tmp/fullchain.pem -noout -pubkey | openssl md5
openssl pkey  -in /tmp/privkey.pem  -pubout        | openssl md5
```

İki komutun çıktısı **birebir aynı** olmalı.

- ✅ **Aynıysa** → Adım 5'e geçin.
- ❌ **Farklıysa** → **DURUN.** Support yanlış anahtar göndermiş. Hiçbir şeyi kopyalamayın,
  support'a geri dönün. Mevcut sistem çalışmaya devam eder.

---

## Adım 5 — Eskisini yedekleyin, yenisini yerleştirin

```bash
cd /srv/kod/tti-sunucu

# 5a. Eskiyi yedekle (geri dönüş için — silmeyin)
sudo cp certs/fullchain.pem certs/fullchain.pem.eski
sudo cp certs/privkey.pem   certs/privkey.pem.eski

# 5b. Yenileri yerine koy
sudo cp /tmp/fullchain.pem certs/fullchain.pem
sudo cp /tmp/privkey.pem   certs/privkey.pem

# 5c. İzinler (özel anahtar başkası tarafından okunamaz)
sudo chmod 644 certs/fullchain.pem
sudo chmod 600 certs/privkey.pem
sudo ls -l certs/

# 5d. /tmp'deki kopyaları temizle (özel anahtar ortalıkta kalmasın)
rm -f /tmp/fullchain.pem /tmp/privkey.pem
```

> **Not:** `Caddyfile` ve `docker-compose.yml` dosyalarına **dokunulmaz**. Onlar zaten
> `certs/fullchain.pem` ve `certs/privkey.pem` adlarını gösteriyor; sadece dosyaların içeriği değişti.

---

## Adım 6 — Proxy'yi yeniden başlatın

Caddy sertifikayı sadece açılışta okur, bu yüzden **restart** gerekir:

```bash
sudo docker restart tti-proxy
sleep 5
sudo docker ps --filter name=tti-proxy        # STATUS "Up" olmalı
sudo docker logs --tail 30 tti-proxy          # hata satırı olmamalı
```

⚠️ Bu ~5 saniyelik bir kesintidir; sadece proxy yeniden başlar, **veritabanına ve uygulamalara
dokunulmaz**. `docker compose up -d` DEĞİL, `docker restart` kullanın (compose dosyası
değişmediği için `up -d` konteyneri yeniden oluşturmaz, yani sertifikayı okumaz).

Konteyner "Up" görünmüyorsa → doğrudan **Adım 8: Geri dönüş**.

---

## Adım 7 — Doğrulayın

```bash
cd /srv/kod/tti-sunucu

# 7a. Sunucunun sunduğu sertifikanın tarihi gerçekten yenilendi mi?
openssl s_client -connect hakedis.turktelekomint.com:443 \
  -servername hakedis.turktelekomint.com </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -dates

# 7b. HTTPS gerçekten çalışıyor mu (sertifika doğrulamalı — 200 dönmeli)
curl -sSI --resolve hakedis.turktelekomint.com:443:127.0.0.1 \
  https://hakedis.turktelekomint.com/api/saglik
curl -sSI --resolve rapor.turktelekomint.com:443:127.0.0.1 \
  https://rapor.turktelekomint.com/api/saglik
curl -sSI --resolve data.turktelekomint.com:443:127.0.0.1 \
  https://data.turktelekomint.com/api/saglik
# panel dalı da sınanmalı — yalnızca /api'yi denemek yol ayrımının yarısını kanıtlar
curl -sSI --resolve data.turktelekomint.com:443:127.0.0.1 \
  https://data.turktelekomint.com/login

# 7c. http → https yönlendirmesi duruyor mu (301 dönmeli)
curl -sSI --resolve hakedis.turktelekomint.com:80:127.0.0.1 \
  http://hakedis.turktelekomint.com/

# 7d. Bütün platform kontrolü (yeşil satırlar)
bash dogrula.sh
```

**Asıl test — başka bir bilgisayardan tarayıcıyla:**

- `https://hakedis.turktelekomint.com` → **yeşil kilit, uyarısız** açılmalı
- `https://data.turktelekomint.com` → aynı; ayrıca bir tabletten API çağrısı denenmeli
- `https://rapor.turktelekomint.com` → aynı şekilde
- Kilit simgesine tıklayıp sertifika bitiş tarihinin **yeni tarih** olduğunu görün

Hepsi tamamsa **iş bitti**. Son olarak yedekleri temizleyebilirsiniz (birkaç gün bekleyin):

```bash
sudo rm -f certs/fullchain.pem.eski certs/privkey.pem.eski
```

---

## Adım 8 — Geri dönüş (bir şey ters giderse)

Eski sertifika **dolmadıysa** her an geri dönebilirsiniz:

```bash
cd /srv/kod/tti-sunucu
sudo cp certs/fullchain.pem.eski certs/fullchain.pem
sudo cp certs/privkey.pem.eski   certs/privkey.pem
sudo chmod 644 certs/fullchain.pem && sudo chmod 600 certs/privkey.pem
sudo docker restart tti-proxy
```

Bu yüzden **yenilemeyi son güne bırakmayın** — eski sertifika dolmuşsa geri dönüş de sizi kurtarmaz.

---

## Sorun giderme

| Belirti | Neden / Çözüm |
|---|---|
| `tti-proxy` açılmıyor, log'da `tls: failed to find any PEM data` | Dosya bozuk ya da yanlış dosya kopyalandı. Adım 3'teki `grep -c` kontrollerini tekrarlayın. |
| Log'da `private key does not match public key` | Sertifika ve anahtar farklı çiftlerden. Adım 4'ü atlamışsınız → Adım 8 ile geri dönün, support'tan doğru anahtarı isteyin. |
| Log'da `permission denied` / Caddy dosyayı okuyamıyor | SELinux etiketi. `ls -Z certs/` bakın, sonra `sudo chcon -Rt container_file_t certs/` çalıştırıp `sudo docker restart tti-proxy`. |
| Tarayıcı "güvenli değil" diyor ama site açılıyor | Muhtemelen zincir eksik (support sadece leaf sertifika göndermiş). `grep -c 'BEGIN CERTIFICATE' certs/fullchain.pem` **1** dönüyorsa ara CA'lar eksiktir; support'tan **tam zincir (fullchain)** isteyin. |
| Site hiç açılmıyor, sertifika uyarısı bile yok | Sertifikayla ilgisi yok: kurumsal ağda 443 kapalı ya da DNS kaydı gitmiş. `ping hakedis.turktelekomint.com` sunucunun IP'sini vermeli. Vermiyorsa IT/DNS. |
| `curl` çalışıyor ama kullanıcılar giremiyor | Sunucu tarafı sağlam; sorun ağda. IT'ye 443'ü sordurun (`KURULUM.md` §8). |

---

## Bir sonraki sefere hazırlık

Yeni sertifikayı kurduktan sonra **iki şeyi güncelleyin ki bir sonraki kişi tarih avına çıkmasın**:

1. Bu dosyanın en üstündeki tarihi yeni bitiş tarihiyle değiştirin.
2. `ServerDeploy.md` §14 ve `Caddyfile` başlığındaki "son geçerlilik" notunu güncelleyin.
3. Takviminize **yeni bitiş tarihinden 60 gün önce** hatırlatıcı koyun.

**İlgili dökümanlar:** `ServerDeploy.md` §14 (HTTPS kurulumunun tam hikâyesi ve o gün düşülen
tuzaklar) · `KURULUM.md` §6.1 ve §8 (proxy, DNS, güvenlik duvarı) · `README.md` (mimari özeti).
