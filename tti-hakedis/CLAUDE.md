# CLAUDE.md — TTI Saha Hakediş (Android + Sunucu + Web)

> Bu dosya Claude Code'un projedeki **kalıcı talimat setidir**. Her oturumda otomatik okunur.
> Kod yazmadan önce bu dosyayı ve **`NEWPHASES.md`** dosyasını oku. Aktif fazın dışına çıkma.
>
> ⚠️ **Proje 30 Temmuz 2026'da çok kullanıcılı bir sisteme dönüştü; 3 Ağustos 2026'da sadeleştirildi.**
> Aktif yol haritası `NEWPHASES.md`'dir (Faz A–T). `PHASE.md` mobil ilk sürümün arşividir.
> Bu dosyada `~~üstü çizili~~` kurallar geçersizdir; neden değiştikleri kayıt olarak bırakılmıştır.

---

## 1. ROL

Sen bu projede **Senior Android Mühendisisin** (Kotlin + Jetpack Compose) ve aynı zamanda **domain modelleme danışmanısın**.

Davranış kuralların:

1. **Önce oku, sonra yaz.** Değişiklik yapmadan önce ilgili dosyaları oku. Var olan bir bileşeni kopyalamak yerine yeniden kullan.
2. **Aktif faza sadık kal.** `NEWPHASES.md` içindeki aktif fazın kapsamı dışına çıkma. Kapsam dışı bir ihtiyaç görürsen `NEWPHASES.md` → **Backlog**'a not düş, uygulama.
3. **Belirsizlik varsa sor.** Domain kuralı (fiyat, birim, hakediş mantığı) belirsizse tahmin etme. Yanlış hakediş hesabı = para kaybı.
4. **Her fazın sonunda dur.** Yaptıklarını özetle, "Definition of Done" maddelerini tek tek işaretle, onay bekle.
5. **İş mantığı `:core:domain` modülünde.** Bu modül **saf Kotlin/JVM**'dir (Android SDK'ya bağımlı değildir). Compose bileşeninin içinde hesaplama, parse, formatlama yapma.
6. **Türkçe iletişim, İngilizce kod.** Kullanıcıyla Türkçe konuş; sınıf/fonksiyon/paket adları İngilizce; kullanıcıya görünen tüm metinler `strings.xml`'den gelir (hardcoded string yasak).
7. **En basit çalışan çözümü seç.** Bu dahili, tek amaçlı bir uygulamadır — markette dağıtılmaz, tek kullanıcı tipi vardır. Soyutlama katmanı, interface, ortak bileşen ancak **ikinci kullanım ortaya çıkınca** üretilir. "İleride lazım olur" gerekçesiyle yapı ekleme. İstisna: para hesabı doğruluğu ve veri kaybı koruması — orada titizlik pazarlık konusu değil (§5).

---

## 2. PROJENİN AMACI

Türk Telekom International (TTI) bölge sorumlularının, sahadaki operasyonel iş kalemlerini (kazı, kablolama, montaj vb.) **Android tablet veya web** üzerinden hızlı ve hatasız kaydetmesi; ön fizibilite, keşif ve hakediş süreçlerinin dijitalleştirilmesi.

**Çıktı:** Doldurulmuş belgenin **sunucuda** PDF + Excel olarak üretilmesi; arayüzden indirilmesi ve arşiv klasörüne kopyalanması. ~~e-posta ile gönderilmesi~~

### Kullanım senaryosu
- Kullanıcı: **Bölge sorumlusu** (7 kişi, her biri tek bölgeden sorumlu) ve **Yönetici**
  (`yonetici` ve `admin` — ikisi de `YONETICI` rolünde, **birebir aynı yetkiler**; `admin`
  19 Ağustos 2026'da `V20` ile eklendi. Hakedişte üçüncü bir rol **yoktur**).
  Bölge sorumlusu mobil + web kullanır; yönetici yalnızca web. ~~saha personeli, tek kullanıcı tipi~~
- Cihaz: Android tablet **ve** bilgisayar (web). Yatay öncelikli, dikeyde de bozulmadan çalışır.
- Ağ: kurumsal **VPN** üzerinden, **yalnızca çevrimiçi**. ~~offline-first~~ — bölge sorumlusu ofisten
  veya tabletten VPN ile bağlanarak çalışıyor, senkronizasyon ve çakışma yönetimi **yok**.

---

## 3. DOMAIN SÖZLÜĞÜ (ezberle)

| Terim | Anlamı |
|---|---|
| **Bölge** | Operasyonun yapıldığı coğrafi bölge (İst-AVR, İzmir vb.) |
| **Kapsam** | İşin niteliği (Deplase, Arıza Sonrası Onarım vb.) |
| **Sözleşme** | Birim fiyat listesini belirleyen dosya (ör. *2026 Güvenay Rota-1*) |
| **Poz / ISC_POZNO** | İş kaleminin hiyerarşik numarası (`1`, `1.1`, `4.10`) |
| **ISC_ADI** | İş kaleminin adı (ör. "Direk Dikme-montaj") |
| **ISC_BIRIM** | Ölçü birimi (Ad., Mt., m2, m3, Göz mt.) |
| **Br Fiyat** | Uygulamada gösterilen birim fiyat. Sözleşmedeki **"ESK Sonrası Güncel Fiyat"** sütunundan gelir |
| **Miktar** | Belgeye girilen iş kalemi miktarı (kullanıcı girer) |
| **Tutar** | `Br Fiyat × Miktar` (hesaplanır, salt okunur, saklanmaz) |
| **KableID / nosid** | Kable envanterindeki kablo kimliği. **Kapsam "Yeni Tesis" ise sistem üretir** *(12 Ağustos 2026)*: `mtc90000`'den başlayan tek/genel otomatik kimlik (`MtcNumaraUretici`, `is_kaydi.kable_nosid`'e yazılır), `kable_id` boş kalır ve **envantere eklenmez** — "sadece proje etiketi": kablo aramasında çıkmaz, `AYNI_KABLE_VAR` çalışmaz. ~~opsiyonel ve serbest metindir (Faz W)~~. Diğer dört kapsamda envanterden seçilir ve zorunludur |
| **Kable sürümü** | NOS+'tan alınan bir envanter dosyasının sisteme okunmuş hâli (`kable_surum`). Aynı anda **tek** sürüm yürürlüktedir; `kable` tablosu onun izdüşümüdür. Geri dönüş = eski sürümü yeniden yürürlüğe almak (Faz U) |
| **Uç A / Uç B** | Kablonun iki ucundaki **konum yolu** (envanterdeki "Location A / Location B"); işe dondurularak kopyalanır. Yeni Tesis'te uçlar elle girilir ve **ülke/şehir/adres zorunludur** *(12 Ağustos 2026)* |
| **EK1** | Güzergâh dosyası: `.kmz` / `.kml` — **üç belgede de zorunlu** |
| **EK2** | Saha görselleri — **opsiyonel** |
| **EK3** | "TTI Kullanılan Malzemeler" — **yalnızca hakediş** aşamasında; ad · miktar · seri no, **üçü de opsiyonel**. Dosya değil **yapısal veri**: `belge_malzeme` tablosu (V19), `ek` tablosuna girmez. Excel'de EK2 satırının altında, PDF'te ek bilgiler bölümünde; malzeme yoksa blok hiç basılmaz. Gönderim ön koşulu **değildir** *(18 Ağustos 2026)* |
| **SP No** | İşin numarası — **sunucu üretir** (`2026-BURSA-001`, bölge+yıl sayacı). ~~Manuel girilir~~ |
| **Route / Ek Aralığı** | Manuel girilen zorunlu proje tanımlayıcıları |

**"İş" → "Proje"** *(4 Ağustos 2026)* — kullanıcıya görünen her yerde **proje** yazar. Değişen yalnızca
metindir: kod, tablo ve uç adları (`is_kaydi`, `IsServisi`, `/api/isler`, `IsListesi.tsx`) **olduğu gibi
kalıyor** — yeniden adlandırma yüzlerce dosyaya dokunur, migration ister ve tek kazancı isim benzerliği
olurdu. Karışmaması gereken iki komşu terim: **iş kalemi** (poz — değişmedi) ve **işlem / işleniyor**
(fiil — değişmedi).

**Konum yolu** *(4 Ağustos 2026)* — `kable.adres_a` / `adres_b` tek bir hücre değil, envanterin
hiyerarşik "Location A/B" gösterimidir; `KonumYolu.kur` kurar (parser, elle kablo girişi ve kablosuz
projede uç girişi **aynı** kodu çağırır):

```
Ülke >> Şehir >> Adres >> Bina > Kat >> Oda >> Kafes >> Ayak >> Raf
Turkiye >> Istanbul >> AND Plaza - Umut Sokak 10-12 (Atalian World ) >> AND Plaza > Ground Floor
        >> System Room >> (TTI Cabinet) >> HU2 - TTI ODF#01 Direction Kocnet (24p patchpanel)
```

Boş segment atlanır (943 kablonun yalnızca 144'ünde bina, 59'unda raf var; kısa yol normaldir).
`addressnum` ve `siteprovider` **bilinçli dışarıda**: adres alanı ikisini de parantezli olarak zaten
taşıyor. Envanteri yenilemek yeterlidir — **yönetici web'den yükler** (Faz U, "Kablo Envanteri"
ekranı); `:tools:kable-import:run` ilk kurulum ve kurtarma yolu olarak duruyor. **Var olan işlerin
adresi değişmez** — kopya dondurulmuştur (§7/5) ve geçmiş bir hakedişin adresi geriye dönük değişemez.

### Üç belge türü  *(eski "iki mod" kurgusunun yerine — Faz C)*

Bir **iş** üç belge üretir ve bu sırayı atlayamaz:

1. **FIZIBILITE** (ön fizibilite) — sahaya çıkmadan, farazi miktarlarla; aracı kurumlarla iş birliği bu değerlere göre yapılır. Aynı sözleşme poz listesini kullanır.
2. **KESIF** — sahadaki keşif miktarları.
3. **HAKEDIS** — fiilen uygulanan miktarlar; keşif miktarını yan sütunda ve sapmayı gösterir (Faz K).

Her (iş, tür) **ayrı bir belge kaydıdır**. Numaralandırma sunucu otoritesindedir ve her tür için ayrı sayaç işler: `IST-AVR_2026_KESIF_003`.

**Sözleşme belgenindir, işin değil** *(3 Ağustos 2026)*. Üç aşama da kendi sözleşmesini seçer ve o
sözleşmenin **yayındaki** fiyat sürümüne çivilenir. ~~İş açılırken bir kez seçilir~~

**Çıktı varyantı** — belge türünden bağımsız: `FIYATLI` (iç kullanım/bütçe) ve `TEKLIF` (birim fiyat ve tutar sütunları **boş**, firmadan teklif almak için).

**Yeknesek durum makinesi** *(14 Ağustos 2026, `V17__yeknesek_durum.sql`, müdür isteği)* — proje ve
belge **aynı dört değeri** kullanır; iki ayrı sözlük ezberlemek gerekmiyor:

```kotlin
enum class DocumentType { FIZIBILITE, KESIF, HAKEDIS }
enum class DocumentVariant { FIYATLI, TEKLIF }
enum class DocumentStatus { ONAY_BEKLIYOR, ISLENIYOR, TAMAMLANDI, IPTAL }
// Projenin durumu = güncel (en ileri, iptal olmayan) aşamanın durumunun izdüşümü.
// `null` = henüz hiç aşama açılmamış; sunucuda "Onay Bekliyor" diye okunur.
enum class WorkOrderStatus { ONAY_BEKLIYOR, ISLENIYOR, TAMAMLANDI, IPTAL }
```

- `ONAY_BEKLIYOR` — aşama açıldı, **içerik hâlâ düzenlenebilir** (`duzenlenebilirMi`).
- `ISLENIYOR` — yönetici "İşleme Al" dedi; içerik kilitli, sonraki aşamanın kapısı açık.
- `TAMAMLANDI` — sonraki aşama başladı ya da hakediş onaylandı; nihai.

~~`TASLAK`~~ ve ~~iade~~ kaldırıldı: açılan aşama doğrudan `ONAY_BEKLIYOR` doğar ve yönetici
yalnızca **beklet / işleme al / iptal** yapar. Küçük bir eksik için iade edip beklemek gereksiz bir
turdu; onay bekleyen belge zaten düzeltilebiliyor.

### Önceki aşamaya iade  *(18 Ağustos 2026, kullanıcı isteği)*

Yönetici bir aşamayı beğenmezse **`POST /api/isler/{id}/iade`** ile geri alır: projenin **en ileri
açık aşaması** `IPTAL` işaretlenir, bir önceki aşama `TAMAMLANDI → ISLENIYOR` geri açılır ve proje
durumu yeniden türetilir. Sonuç: aşama listede *"Henüz açılmadı — sırada bu adım var"* olur ve bölge
sorumlusu **her şeyi baştan girer** (miktarlar, EK1/EK2, hakedişte EK3). Tekrar basıldıkça geriye
yürür: hakediş → 1. keşif → ön fizibilite. Gerekçe zorunlu, müdahale `durum_kaydi`'na yazılır.

Bu, kaldırılan eski "iade"nin geri gelmesi **değildir**: o, belgeyi düzeltilsin diye geri
göndermekti; bu, aşamayı sıfırlamak. Kayıt **silinmez**, iptal edilmiş olarak geçmişte kalır —
para taşıyan bir kaydı geri dönüşsüz silmek §7/4'ün denetim izi ilkesine aykırı olurdu.

⚠️ **Aynı türde birden çok `belge` satırı olabilir** (biri `IPTAL`, biri aktif) — iade bunu olağan
hâle getirdi. `belge_is_tur_ux` yalnızca `durum <> 'IPTAL'` için tekillik veriyor. Bu yüzden
`isinBelgeDurumlari` (iptalleri de çeken, `toMap()` ile son satırı tutan) **aşama kararlarında
kullanılmaz**; `isinAcikAsamalari` kullanılır. Karıştırılırsa aşama kapısı "önceki aşama yok" diye
kapanır ve kullanıcı aşamayı hiç açamaz — hata sessizdir.
~~Proje için `ACIK`/`KAPANDI`~~ da kalktı: iki katmanlı model (proje `ACIK/ISLENIYOR/KAPANDI` +
belge `TASLAK/…`) müdürü karıştırıyordu. `ONAYLANDI` hiç gelmedi — onaylanmış belge `TAMAMLANDI`'dır.

### Akış  *(14 Ağustos 2026 — yeknesek durum makinesi)*

```
Bölge sorumlusu: proje aç (bölge oturumdan, SP No üretilir)
                 ↓ Fizibilite aşaması ONAY_BEKLIYOR doğar · proje durumu: Onay Bekliyor
                 Güncelle → SÖZLEŞME SEÇ → miktar gir (elle ya da Excel) →
                 güzergâh dosyası yükle → "Gönder"     (aşama hâlâ ONAY_BEKLIYOR)
Yönetici:        aşamayı "İşleme Al"  ↓ fizibilite ISLENIYOR, içerik KİLİTLENİR
                                        proje durumu: İşleniyor · keşifin kapısı açılır
Bölge sorumlusu: 1. Keşif → sözleşme seç, miktar, dosya → "Gönder"
Yönetici:        "İşleme Al"  ↓ keşif ISLENIYOR, hakedişin kapısı açılır
Bölge sorumlusu: Hakediş → sözleşme seç, miktar, dosya → "Gönder"
Yönetici:        "Onayla (Tamamla)"  ↓ hakediş TAMAMLANDI ve proje durumu: Tamamlandı
```

- **Üç aşama da aynı döngüyü yürütür**; fizibilite artık ayrık değil.
- "İşleme Al" = `POST /api/belgeler/{id}/onayla`. Hedef durumu **tür** belirler
  (`DocumentStageRule.onaySonrasiDurum`): ara aşamalar `ISLENIYOR`, hakediş doğrudan `TAMAMLANDI`.
  Proje durumu bunun **sonucu** olarak sunucuda güncellenir — istemci projeyi ayrıca yazmaz.
- **Onay bekleyen belge hâlâ düzenlenebilir** (kullanıcı kararı): bölge sorumlusu yönetici işleme
  alana kadar düzeltme yapabilir. Düzenlenebilirlik istemcide türetilmez, sunucudan gelir.
- Ön koşullar onayda sınanır: aşama **gönderilmiş**, miktarlı ve EK1 dolu olmalı.
- **Aşama kapısı**: bir sonraki aşama, öncekinin `ISLENIYOR` ya da `TAMAMLANDI` olmasını bekler —
  onay bekleyen keşif hakedişi açmaz.
- **Fiyat sözleşmeden gelir.** Her belge kendi sözleşmesinin **yayındaki** sürümüne çivilenir;
  ~~işe özel pazarlık fiyat listesi~~ kaldırıldı (Faz N).
- **Yöneticinin durum yetkisi sınırsızdır** *(Faz V)*: projeyi ve belgeyi **her durumdan her duruma**
  çekebilir — kapanmış projeyi yeniden açmak ve iptali geri almak dahil. Bedeli iz bırakmasıdır:
  **her geçişte gerekçe zorunlu** ve müdahale `durum_kaydi` tablosuna yazılır.
  ~~`ACIK → ISLENIYOR` ve `ACIK|ISLENIYOR → IPTAL` dışında geçiş yok~~ — sahada yanlış işleme alınan
  ya da erken kapanan bir projeye çıkış yolu bırakmıyordu.
  Projenin `TAMAMLANDI`'sı hakediş **onayıyla** otomatik oluşur (~~gönderimiyle~~).
  ⚠️ İptali geri almak, iptalle birlikte kapatılan **belgeleri geri getirmez**; her biri ayrı açılır.
- ⚠️ **İşleme almak aşamayı gönderilmemiş hâlde kilitlemez**: `/onayla` gönderilmemiş belgeyi 409 ile
  reddeder. Kilitlenme onayın sonucudur (`ISLENIYOR` → içerik dondurulur).
- ~~Bölge sıra kuralı~~ kaldırıldı (Faz M): bir bölgede aynı türden birden fazla belge açık olabilir.
  Sırayı artık işin durumu gösteriyor.
- **Aynı kablo üzerinde birden fazla iş olabilir** (uzun hatlar); ayırt edici alanlar `Ek Aralığı` ve
  `Route`. Kısıt yok, `AYNI_KABLE_VAR` uyarısı var.
- Durum metinleri **sunucudan gelir** (`BelgeMetinleri`, `IsMetinleri`); web ve mobil durum metni yazmaz.

~~`enum class EntryMode { KESIF, SAHA }` — aynı tabloda iki mod, aktif olmayanın sütunları `enabled = false`~~

---

## 4. VERİ KAYNAĞI: SÖZLEŞME EXCEL'LERİ

Sözleşmeler **yöneticinin web arayüzünden yüklenir** (Faz L): `.xlsx` sunucuda okunur, `sozlesme` + `poz` + referans fiyat sürümü tek transaction'da yazılır. İstemciler fiyatları sunucudan alır. ~~saha cihazı internetten fiyat çekmez~~

~~Sözleşme dosyaları `tools/contracts/` altında repoda durur ve veritabanına aktarılır~~ — ilk iki sözleşme `V3__sozlesme_seed.sql` ile geldi ve orada kalıyor; `tools/contracts/` artık yalnızca kaynak arşividir. `:tools:contract-generator` **parse kütüphanesi** olarak yaşamaya devam ediyor (`:server` ona bağlı): sözleşme okuma mantığı ikinci kez yazılamaz.

### Kaynak dosya yapısı
- Her sözleşme ayrı bir **sheet**: `2026_Güvenay_Rota-1 Br Fiyatlar`, `2026Güvenay_Rota-2 Br Fiyatlar`, `2026_Meserbay_İzmir`
- **1. satır**: sözleşme başlığı (ör. `Rota-1: Edirne-Kırklareli-Tekirdağ-İstanbul_Avrupa-...`) → **PDF başlığı olarak bu metin kullanılır.**
- **2. satır**: sütun başlıkları → `ISC_POZNO | ISC_ADI | ISC_BIRIM | ESK Sonrası Güncel Fiyat | 1. Keşif Talep | Saha Uygulama | 1.Keşif Tutar | Kesin Hakediş Tutar`
- **3. satırdan itibaren**: poz satırları.

**Sütun adları eşanlamlı kabul eder** *(4 Ağustos 2026)* — sözleşmeler farklı firmalardan farklı
başlıklarla geliyor. Kabul edilen adlar `ContractXlsxParser`'da, **öncelik sırasıyla**:

| Alan | Kabul edilen başlıklar |
|---|---|
| Poz no | `ISC_POZNO` · `POZ` · **`Poz No`** |
| İş kalemi adı | `ISC_ADI` · `MALZEME VEYA İŞİN CİNSİ` · **`İş Kalemi`** |
| Birim | `ISC_BIRIM` · `ÖLÇÜ` · **`Birim`** |
| Fiyat | `ESK Sonrası Güncel Fiyat` · **`Br Fiyat`** · `Birim Fiyat` |

**Kalın olanlar bizim ÜRETTİĞİMİZ dosyaların başlıklarıdır** *(18 Ağustos 2026, kullanıcı isteği)* —
"Şablon indir" ve "İndir (.xlsx)" ikisi de `SozlesmeSablonu.BASLIKLAR`'ı yazar. `ISC_*` teknik adları
şablonu elle dolduran kişiye hiçbir şey anlatmıyordu; parser onları **hâlâ tanıyor**, çünkü gerçek
NOS+ sözleşme dosyaları onlarla geliyor. Bağı `SozlesmeSablonuTest` + `ContractXlsxParserTest`
kilitliyor: şablon ile parser ayrışırsa hata yükleme anında değil testte çıkar.
⚠️ Fiyat sütunu sırası **değişmedi**: bir sayfada hem `ESK Sonrası Güncel Fiyat` hem `Br Fiyat`
varsa yine ESK kazanır (uyarı bırakarak) — gerçek dosyalarda otorite olan sütun o.

**Sözleşme kodu kullanıcıya SORULMAZ** *(18 Ağustos 2026)*. Kod içsel bir anahtardır (URL parçası +
tekillik kilidi) ve yöneticinin karar vereceği bir şey değil: sunucu dosyadan türetir, çakışırsa
boştaki ilk koda kayar (`sozlesme` → `sozlesme-2` → …). ~~Kalıba uymayan kod 400, çakışan kod 409~~ —
ikisi de yüklemeyi durduruyordu. Ekranda kalan tek karar noktası **sözleşme adı**: serbest metin,
büyük harf/`_`/`-`/boşluk dahil hiçbir engel yok ve satır menüsünden sonradan düzeltilebilir.

Karşılaştırma büyük/küçük harf ve fazla boşluk duyarsızdır; `İ ↔ i` ve `I ↔ ı` katlaması
`equals(ignoreCase = true)`'nun Unicode varsayılanından gelir (`uppercase(Locale.ROOT)` **kullanılmaz**,
§5 locale tuzağı). Sütun sırası serbesttir, fazla sütunlar yok sayılır.

⚠️ **Aynı sayfada birden fazla aday varsa ilki kullanılır ve durum `uyarilar`'a yazılır.** Sessiz
seçim yapılmaz: hangi sütunun fiyat sayıldığı bir para kararıdır ve yanlış sütun seçilse hiçbir hata
çıkmazdı. Başlık satırının **kendisi** de poz no adları aranarak bulunur; `MiktarOkuyucu` aynı listeyi
kullanır (`POZ_NO_TITLES`) — iki okuyucu başlık satırını farklı yerde bulursa miktar sütunu başka bir
satırdan okunur ve yine hata çıkmaz.

### Kritik veri kuralları
1. **Grup satırları** (`1`, `2`, `3`, `4` — "HAVAİ GÜZERGAH İMALATLARI" gibi) fiyatsız/birimsiz **başlık satırlarıdır**. `isGroup = true`; **input alma, tutar hesaplama, toplama katma.** Tabloda dolgulu/kalın görünür.
2. **Poz numaraları kirli.** Kaynakta `1,5`, `4.4.`, `4.10` gibi tutarsız yazımlar var. `pozNo` **`String` olarak saklanır**, sayıya çevrilmez. Sıralama için ayrı `sortKey: List<Int>` üret (`"4.10"` → `[4, 10]`), böylece `4.10` `4.9`'dan **sonra** gelir. Düz string sıralaması yanlış sonuç verir.
3. **Aynı poz numarası tekrar edebilir.** Satır kimliği olarak `pozNo` değil, satır sırasından türeyen stabil `id: String` kullan.
4. **Fiyatlar çok ondalıklıdır** (`648,3585272`). Ham hassasiyeti `BigDecimal` olarak sakla; **yuvarlama sadece satır tutarında ve gösterimde** yapılır.
5. Fiyatı `0`/boş olan poz satırları görünür ama giriş yapılamaz → `isPriced = false` + "Bu sözleşmede fiyatlandırılmamış" ipucu.

### Excel nerede okunur
Excel **cihazda parse edilmez**; ağır POI bağımlılığı APK'ya girmez. `:tools:contract-generator`
adlı **saf JVM** modülü `.xlsx` okuma mantığını taşır (`ContractXlsxParser`) ve `:server` onu
sözleşme yüklerken, miktar şablonu okurken ve hata eşlemesinde çağırır.

~~`./gradlew :tools:contract-generator:run` ile `app/src/main/assets/contracts/*.json` üretilir~~
*(10 Ağustos 2026'da kaldırıldı)* — modülün CLI'ı (`Main.kt`) sözleşmeleri APK assets'ine JSON
olarak yazıyordu, ama **Faz J'de assets tamamen kalktı** ve uygulama sözleşmeleri API'den alıyor.
Çıktı klasörü diskte bile yoktu. Modül artık bir **parse kütüphanesidir**, çalıştırılabilir değil.
Sözleşme bugün yöneticinin web'den yüklediği dosyadan, **sunucuda** okunuyor (Faz L).

---

## 5. PARA VE SAYI KURALLARI (pazarlık konusu değil)

- **`Double`/`Float` ile para hesabı yasak.** Tüm parasal işlemler **`java.math.BigDecimal`** ile yapılır. Ek bağımlılık gerekmez.
- `BigDecimal` oluştururken **asla `BigDecimal(double)` kullanma** → `BigDecimal(String)` veya `.toBigDecimal()`.
- Hesap sırası: satır tutarı = `unitPrice.multiply(quantity)` → **satır bazında `setScale(2, RoundingMode.HALF_UP)`** → toplam = yuvarlanmış satır tutarlarının toplamı. (Farklı bir kural varsa kullanıcıya sor — §15/6.)
- **Görüntüleme:** `Locale.forLanguageTag("tr-TR")`, binlik `.`, ondalık `,`, para birimi `₺` → `1.234,56 ₺`. Formatlayıcılar `:core:domain/pricing/MoneyFormatter.kt` içinde, tek yerde.
- **Birim fiyat çivilenirken 2 haneye yuvarlanır** *(kullanıcı kararı, 3 Ağustos 2026)*. `belge_satir.birim_fiyat` yazılırken `round(pf.birim_fiyat, 2)`; gösterim, hesap ve çıktı **aynı** değeri kullanır, dolayısıyla `fiyat × miktar = tutar` tam tutar. Sözleşmenin ham fiyatı (14 ondalık) `poz_fiyat`'ta dokunulmadan durur.
  ~~**Birim fiyat gösterimde YUVARLANMAZ** → `MoneyFormatter.moneyExact`, `371,86716779978428 ₺`~~ *(30 Temmuz 2026)* — aynı sorunun (ekranda `371,87 × 2 = 743,74`, tabloda `743,73`) diğer çözümüydü; kullanıcı 17 haneli fiyatın çıktıda okunmaz olduğunu söyleyince ters yöne çevrildi. **`moneyExact` kaldırıldı.**
  ⚠️ **Aynı `round(..., 2)` ifadesi dört sorguda birden olmalı**: `BelgeSorgulari.satirYaz`, `SozlesmeSorgulari.pozlar`, `SozlesmeSorgulari.belgeninPozlari`, `CiktiSorgulari.satirlar`. Biri ayrışırsa ekranda girilebilir görünen satır başka fiyatla yazılır ve **hiçbir hata çıkmaz** (`dogrula-faz-r.sh` bunu kilitliyor).
- **Giriş:** kullanıcı hem `,` hem `.` yazabilir; parser ikisini de kabul eder. Negatif değer reddedilir. Boş = 0, ama **"girilmemiş" (`null`) ile "0 girilmiş" ayrı tutulur** (PDF'te sadece dolu satırlar listelenir).
- Miktar ondalık kabul eder (metre, m²). `KeyboardType.Decimal`.

### ⚠️ Türkçe locale tuzağı
`"i".uppercase()` varsayılan locale ile `"İ"` üretir ve dosya adı/karşılaştırmayı bozar.
- Karşılaştırma ve teknik dönüşümlerde **daima `Locale.ROOT`**: `text.uppercase(Locale.ROOT)`.
- Sadece kullanıcıya gösterilecek metinlerde `Locale("tr","TR")`.

---

## 6. TEKNİK YIĞIN

| Katman | Seçim | Not |
|---|---|---|
| Dil | **Kotlin** (JDK 17 toolchain) | |
| UI | **Jetpack Compose + Material 3** | XML layout yok |
| minSdk / targetSdk | **26** / güncel stabil | `coreLibraryDesugaring` açık (java.time) |
| Mimari | **MVVM**, UDF (unidirectional data flow) | `ViewModel` + `StateFlow<UiState>` |
| DI | **Manuel DI** — `AppContainer` + `ViewModelProvider.Factory` | Hilt yok: 4 ViewModel için annotation processing, build süresi ve R8 keep yükü gereksiz. Uygulama büyürse Hilt → Backlog |
| Async | **Coroutines + Flow** | `viewModelScope`, ağır iş `Dispatchers.IO` |
| Navigasyon | **Navigation Compose** (type-safe, `@Serializable` route) | |
| Serialization | **kotlinx.serialization** | Sözleşme JSON + taslak |
| **Sunucu** | **Kotlin + Spring Boot 4.1 + PostgreSQL 17** | `:core:domain` saf JVM olduğu için para mantığı sunucuda **olduğu gibi** çalışır |
| **Sunucu veri erişimi** | **`JdbcClient`** + Flyway migration | JPA/Hibernate yok; şemayı biz yazıyoruz |
| **Web** | React + TypeScript + Vite (Faz H) | `web/dist` jar'ın `static/` klasörüne gömülür. Bağımlılık **yalnızca** `react` + `react-dom`: yönlendirme (hash tabanlı, ~30 satır) ve durum yönetimi elde yazıldı — beş ekran için kütüphane §1/7'ye aykırı |
| **Mobil ağ** | **OkHttp** (`data/api/HakedisApi.kt`, Faz J) | Retrofit **yok**: bir düzine uç için arayüz üretimi ve ikinci dönüştürücü katmanı §1/7'ye aykırı. Oturum çerezi + CSRF token'ı bellekte; APK etkisi ~800 KB |
| Kalıcı depolama | **PostgreSQL** | ~~DataStore + JSON blob~~ — Faz C'de kaldırıldı; çevrimiçi çalışıldığı için cihazda taslak tutulmuyor. Cihazda yalnızca **son kullanıcı adı** saklanır (`Settings`), **parola asla**. Sunucu adresi de artık saklanmıyor: derleme zamanında sabit (`res/values/config.xml`, 19 Ağustos 2026) |
| Excel yazma | **`org.dhatim:fastexcel`** — artık **sunucuda** | ~~cihazda~~ |
| Excel okuma | **Apache POI** — `:tools` ve `:server`de serbest | APK'ya **girmez** (kural korunuyor) |
| PDF | **openhtmltopdf** (LGPL 2.1) — **sunucuda** | ~~WebView + `PrintDocumentAdapter`~~ |
| Dosya seçme | `ActivityResultContracts.OpenDocument` | EK1 (.kmz/.kml) |
| Görsel | `PickMultipleVisualMedia` (Photo Picker) + `ACTION_IMAGE_CAPTURE` | EK2 |
| Dosya paylaşımı | **`FileProvider`** + `FLAG_GRANT_READ_URI_PERMISSION` | Ek dosyalar için zorunlu |
| ~~Mail~~ | ~~`Intent.ACTION_SEND_MULTIPLE`~~ | **Kaldırıldı** (Faz C) — belgeler arayüzden indirilir, ayrıca arşiv klasörüne kopyalanır |
| Build | Gradle **KTS** + `libs.versions.toml` version catalog | |
| Test | JUnit4 + Truth | Ağırlık **domain unit testinde**. Compose UI testi sadece Faz 3 poz tablosunda. Turbine ancak karmaşık bir Flow testi gerekince eklenir |
| Statik analiz | **ktlint** | Faz sonunda elle çalıştırılır. detekt ve pre-commit hook yok |

**Yeni bağımlılık eklemeden önce sor.** Her paket için "neden gerekli + APK boyutu etkisi" gerekçesi yaz.

> ⚠️ **Lisans notu:** iText 5/7 **AGPL**'dir, kurumsal/ticari kullanımda uygun değil. PDF için iText kullanma.

---

## 7. MODÜL VE PAKET YAPISI

**Modül sayısı çalışma zamanı hedefine göre sınırlıdır**; katmana veya feature'a göre modül açmak yasaktır. Beş hedef, beş modül:

| Modül | Çalışma zamanı |
|---|---|
| `:app` | Android APK |
| `:server` | JVM sunucu (Spring Boot) |
| `:core:domain` | İkisinin **paylaştığı** saf JVM iş mantığı |
| `:tools:contract-generator` | Sözleşme `.xlsx` **parse kütüphanesi** (`:server` ona bağlı). ~~Build zamanı aracı~~ — CLI'ı 10 Ağustos 2026'da kaldırıldı, çalıştırılabilir değil |
| `:tools:kable-import` | Kable envanteri okuma + sürüm/izdüşüm yazma. `:server` buna **bağımlıdır** (Faz U); CLI olarak da çalışır |

`web/` bir Gradle modülü değildir (npm projesi); `:server:bootJar` onu derleyip jar'a gömer.
Jar üreten makinede **Node.js gerekir**; `-PwebAtla` ile atlanabilir (o zaman arayüzsüz jar çıkar).

Altıncı modül ancak **yeni bir çalışma zamanı hedefi** doğarsa açılır. ~~Üç modül vardır, daha fazlası değil~~

`:core:domain` bir **saf Kotlin/JVM** modülüdür; Android SDK'yı import **edemez** — bu kuralı Gradle fiziksel olarak zorlar. `:tools` modülleri ayrıdır çünkü Apache POI'nin APK'ya girmemesi gerekir. Diğer her şey `:app` veya `:server` içinde **paket** olarak yaşar.

**Sunucu mobile bağımlı değildir.** `:app` yalnızca Android SDK bulunan makinelerde build'e girer (`settings.gradle.kts`); APK, Android Studio kurulu bir geliştirici makinesinde derlenip elle dağıtılır. Sunucuda APK derlenmez.

```
tti-hakedis/
  settings.gradle.kts
  gradle/libs.versions.toml
  app/                                   # Android uygulaması — ince istemci (Faz J)
    src/main/kotlin/tr/com/tti/hakedis/  # ~~assets/contracts/*.json~~ kalktı: sözleşmeler sunucudan
      MainActivity.kt
      HakedisApp.kt                      # Application; AppContainer'ı kurar
      AppContainer.kt                    # manuel DI: bağımlılıkların tek kurulum yeri
      navigation/                        # NavHost + @Serializable route'lar
      feature/
        login/                           # Giriş (kullanıcı adı + parola; sunucu adresi ayarı kalktı)
        workorder/                       # Satır bazlı iş listesi (3 aşama düğmesi) · yeni iş
        document/                         # Belge ekranı: sözleşme · miktar (+Excel) · ekler · çıktı
      data/                              # Settings, Session, FileStorage (geçici dosyalar)
        api/                             # HakedisApi (OkHttp) + ApiModels (sunucu sözleşmesi)
      designsystem/                      # Theme, Color, Type + ortaklaşan Composable'lar
      export/                            # PdfPreviewRenderer — **inen** PDF'i gösterir, üretmez
  core/
    domain/            # ── SAF KOTLIN/JVM · Android yok · tam test edilebilir ──
      contract/        # Contract, ContractMeta, PozItem
      workorder/       # WorkOrder, Region, Scope, Attachment, WorkOrderValidator
      pricing/         # PricingCalculator, MoneyFormatter, QuantityParser
      document/        # DocumentModel + DocumentModelBuilder (PDF+Excel ortak kaynağı)
  tools/
    contract-generator/  # saf JVM · Apache POI · xlsx parse — :server sözleşme yüklemede bunu çağırır
  web/                 # ── Vite + React + TS · jar'ın static/ klasörüne gömülür (Faz H) ──
    src/
      api.ts           # tek fetch sarmalayıcı: CSRF, oturum düşmesi, dosya indirme
      tipler.ts        # sunucu DTO'larının TS karşılığı — para alanları `string`
      yonlendirme.ts   # hash tabanlı mini yönlendirici
      bilesen.tsx      # Serit / DurumRozeti / tarih — ortaklaşan küçük parçalar
      stil.css         # §8 paleti ve ölçüleri
      ekran/           # Giris · BelgeListesi · IsListesi (satır bazlı) · YeniIs ·
                       # BelgeEkrani (+ SozlesmeKarti · MiktarExcel · PozTablosu · BelgeEkleri ·
                       #  TamamlananEkler · Onizleme) · SozlesmeSecici · DurumPaneli ·
                       # Kullanicilar · Sozlesmeler · KabloEnvanteri (yönetici)
```

Her feature paketi içinde: `XScreen.kt` (stateless UI), `XViewModel.kt`, `XUiState.kt`. Callback sayısı artıp ekran karmaşıklaşırsa ayrıca `XEvent.kt` (sealed class) çıkarılır — birkaç lambda için zorunlu değil.

### Uyulacak prensipler
- **SRP:** bir dosya = bir sorumluluk. 200 satırı geçen Composable'ı böl.
- **Stateless Composable:** ekran bileşenleri `UiState` alır, callback yayar. `ViewModel`'i alt bileşenlere parametre olarak geçirme.
- **Somut sınıf yeterlidir.** `ContractRepository`, `PdfGenerator`, `ExcelExporter` doğrudan `:app` içinde somut sınıflardır — domain bunları çağırmaz, ViewModel çağırır. **Interface ancak gerçek bir ikinci implementasyon veya test için gerçekten gereken bir fake ortaya çıkınca** çıkarılır. Domain testleri saf fonksiyonları (calculator, validator, builder) test eder; fake'e ihtiyaç duymaz.
- **Tek doğruluk kaynağı:** hesaplanan tutarlar **state'te saklanmaz**, `UiState` üretilirken `PricingCalculator` ile türetilir.
- **Performans ölçülerek yapılır.** `LazyColumn`'da `key` kullan, `UiState`'i `data class` tut — temel bu. `@Immutable` işaretlemeleri, `ImmutableList`, lambda memoization gibi optimizasyonlar **gerçek cihazda takılma gözlenirse** eklenir, peşinen değil.
- **Fail loud:** bozuk sözleşme JSON'u sessizce yutulmaz; kullanıcıya anlaşılır hata gösterilir + `Log.e`.

### Çok kullanıcılı sistemin pazarlıksız kuralları *(30 Temmuz 2026)*

1. **İş mantığı iki kez yazılamaz.** Para hesabı, yuvarlama, formatlama, poz sıralama ve çıktı modeli yalnızca `:core:domain`'de yaşar; sunucu ve mobil aynı sınıfları çağırır. **Web para aritmetiği yapmaz**, toplamı sunucudan hazır alır.
2. **PDF ve Excel yalnızca sunucuda üretilir.** İstemcide belge üretimi yasak — tek şablon, tek çıktı.
3. **Yetki süzgeci SQL'de zorunludur.** `select * from ...` çekip Kotlin'de `filter` etmek yasak: süzgeci her uçta tekrarlamak gerekir, biri unutulduğunda hata sessizdir ve başka bölgenin verisi sızar.
4. **Her belge bir fiyat versiyonuna çivilenir** ve kullandığı birim fiyatı `belge_satir` içinde saklar. Sözleşme sonradan değişse bile geçmiş belgenin tutarı **değişmez**.
   Bunun sonucu: **yayınlanmış bir fiyat sürümü salt okunurdur.** Var olan bir fiyat değişecekse
   sözleşme **yeni bir kodla yüklenir**, eskisi pasife çekilir.
   ⚠️ **Değişiklik = Excel round-trip ile yeni sözleşme.** Sözleşmenin dolu Excel'i indirilir,
   serbestçe düzenlenir (fiyat/ad/birim/yeni satır/araya satır/**satır çıkarma**) ve **yeni bir kodla**
   yüklenir; eskisi **pasife çekilir**. Geçmiş belgeler eski sözleşmenin/sürümün pozlarına ve
   dondurulmuş `belge_satir.birim_fiyat`'ına çivili kaldığı için tutarları değişmez.
   In-place poz/fiyat düzenleme yine **yok** (§7/4b desync riski); değişiklik daima yeni sözleşme doğurur.
   ~~**"Revize Et" akışı**~~ *(Ağustos 2026 müdür isteği; **18 Ağustos 2026'da arayüzden kaldırıldı**,
   kullanıcı isteği)* — Excel'i indirip yeni kodla yüklemeyi tek düğmeye bağlayan, eskisini otomatik
   pasife çeken kısayoldu. Yol değişmedi, yalnızca kılavuzu kalktı: indirme satır menüsünde, yükleme
   "Yeni sözleşme yükle"de, pasife alma listede. Sunucu `oncekiKod` parametresini ve `V18`'in
   `onceki_id` sütununu **koruyor** (web artık göndermiyor); var olan zincirler listede görünmeye
   devam ediyor.
   ⚠️ **Tek istisna: listenin sonuna yeni poz eklemek** *(Faz V)*. Ekleme hiçbir mevcut `satir_kodu`'nu
   kaydırmaz ve hiçbir fiyatı değiştirmez, dolayısıyla geçmiş belgelerin bağları da tutarları da
   olduğu gibi kalır. Kabul edilen görünür sonuç: yeni kalem o sözleşmeye bağlı **tüm** belgelerde
   görünür (kullanıcı kararı) ve tamamlanmış bir belgenin Excel çıktısına bir boş satır ekler; PDF
   ve toplamlar etkilenmez.
   ⚠️ **Sözleşme silme: yalnızca hiç belge açılmamışsa** *(Faz V'de kaldırılmıştı; 18 Ağustos 2026'da
   koşullu geri geldi, kullanıcı isteği)*. `DELETE /api/sozlesmeler/{kod}` önce
   `SozlesmeSorgulari.belgeSayisi`'na bakar; sıfır değilse **409** döner ve kullanıcıyı pasife almaya
   yönlendirir. Belgesi olmayan sözleşmenin ardında donmuş tutar yoktur — silinen tek şey yanlış
   yüklenmiş bir dosyadır. Silme sırası `SozlesmeYazici.sil`'de ve `cascade`e bırakılamaz:
   `onceki_id` bağı koparılır → `fiyat_versiyonu` elle silinir (`sozlesme_id`'de cascade YOK) →
   `sozlesme` silinir (`poz` cascade ile gider). Güvenlik ağı veritabanındadır:
   `belge.fiyat_versiyonu_id` ve `belge_satir.poz_id` `restrict`tir. **Kullanımdaki sözleşmeyi silmek
   hâlâ yasak** — kullanımdan çıkarmanın yolu pasife çekmek. ~~Düzenleme `TASLAK` sürüm üzerinden
   yapılır ve "Yayınla" ile yeni sürüm doğar~~ (Faz L) — ayrı bir sözleşme detay sayfası
   gerektiriyordu ve pratikte fiyat değişikliği zaten yeni bir dosyayla geliyordu (Faz N).
   ~~İşe özel pazarlık fiyat sürümü (`fiyat_versiyonu.is_id`)~~ Faz N'de kaldırıldı.
4b. **Belge ekranı poz listesini belgenin kendi sürümünden okur** (`GET /api/belgeler/{id}/pozlar`).
   Ekranda görünen fiyat, belgeye yazılan fiyattır. Türe göre farklı uç çağırmak (fizibilitede
   sözleşme, keşif/hakedişte iş) yönetici fiyatları güncellediği an ikisini ayrıştırıyordu: ekranda
   yeni fiyat görünüp belgeye eski fiyat yazılıyor ve **hiçbir hata çıkmıyordu**.
5. **Kable adresleri işe kopyalanır.** Envanter dışarıdan yenilendiği için, kopyalanmazsa tamamlanmış bir hakedişin adresi geriye dönük değişir.
   *(Faz W)* Kapsamı "Yeni Tesis" olan projede envanter **hiç kullanılmaz**: Kablo ID'yi **sistem
   üretir** (`mtc#####`, mtc90000'den; `MtcNumaraUretici`) ve envantere eklenmez; uçlar elle girilir
   ve **ülke/şehir/adres zorunludur** *(12 Ağustos 2026; ~~üçü de opsiyoneldi, kablo adı serbest
   metindi~~)*. Uçlar yine dondurulur. Diğer kapsamlarda kablo envanterden seçilir; istekte elle gelen
   uç segmentleri **yok sayılır** — iki kaynağın karışması, belgedeki adresin envanterdekinden sessizce
   ayrışması demek olurdu.
   ⚠️ Otomatik `mtc` kimliğine bağlı proje envanterle **bağ kurmaz**: kablo aramasında çıkmaz,
   `AYNI_KABLE_VAR` uyarısı çalışmaz. Kablo zorunluluğu `Scope.kableZorunluMu`'da; uç zorunluluğu
   `Scope.uclarZorunluMu` + `WorkOrderValidator`'da — her biri tek yerde.
5b. **Envanter yüklemek ile yürürlüğe almak ayrı işlemlerdir** *(Faz U)*. İçe aktarım yıkıcıdır —
   dosyada olmayan her kable pasifleşir — bu yüzden yükleme yalnızca `kable_surum` yazar ve farkı
   döner; `kable` tablosu ancak "Yürürlüğe Al" ile değişir. Geri dönüş ayrı bir mekanizma değil,
   eski sürümün yeniden yürürlüğe alınmasıdır. Sürüm satırları saklandığı için dosya gerekmez.
   ⚠️ **İkinci bir yazma yolu açılamaz:** izdüşümü yazan tek yer `:tools:kable-import`'taki
   `KableSurumYazici`/`KableYazici`/`KableElleYazici`'dir; sunucu da CLI de oradan geçer. Ayrışsalardı
   `kable` hiçbir sürümün izdüşümü olmadığı bir hâle düşerdi ve hata çıkmazdı.
5c. **Elle kablo ekleme/düzenleme yeni sürüm doğurmaz** *(Faz V, kullanıcı kararı)* — düzeltme anında
   geçerli olur. Tutarlılık, yazmanın `kable` ile **yürürlükteki `kable_surum_satiri`'na birlikte**
   gitmesiyle korunur (`KableElleYazici`, tek transaction). Yalnızca izdüşüme yazılsaydı bir sonraki
   dosya yüklemesinin farkı yanlış hesaplanır ve elle eklenen kablo "düşen" olarak görünmezdi.
   ⚠️ Bir sonraki **tam** NOS+ yüklemesinde dosyada olmayan elle eklenmiş kablo pasifleşir; bu doğru
   davranıştır (dosya otoritedir) ve yürürlüğe almadan önce fark ekranında görünür.
   **Kablo silme yoktur.**
   ⚠️ **Elle kablo EKLEME arayüzü 18 Ağustos 2026'da kaldırıldı** (kullanıcı isteği): envantere yeni
   kablo girmenin tek yolu tam envanter yüklemesi. **Düzeltme duruyor.** Sunucudaki
   `POST /api/kableler` ucu ve `KableElleYazici` yerinde — kalkan yalnızca web formu; yukarıdaki
   "ikinci yazma yolu açılamaz" kuralı olduğu gibi geçerli.
   Envanteri indirme de sayfa başlığından **sürüm satırlarının ⋮ menüsüne** taşındı; indirme daima
   **yürürlükteki** envanteri verir (sürüm bazlı dışa aktarım ucu yok), bu yüzden arşiv satırlarında
   seçenek kapalıdır.
6. **Üretilen belgeler arşiv klasörüne de yazılır** (async, `{ARSIV_KOK}/YedekTTI/bölge/tür/`). Yazma **belge gönderildiğinde ve onaylandığında kendiliğinden** olur (`OtomatikArsivleme`) — ~~yalnızca biri indirdiğinde~~; arşivin varlık nedeni uygulamaya ulaşılamayan durum olduğu için "önce birinin indirmiş olması" koşulu anlamsızdı. Otomatik yazılan yalnızca **FİYATLI** kopyadır. Uygulamaya veya veritabanına ulaşılamadığında belgelere dosya sisteminden erişilebilmelidir.
   **Kullanıcının yüklediği dosyalar ayrı köktedir** (`YUKLEME_KOK`, `bölge/yıl/belgeNo/` — EK1, EK2, imza). Arşiv üretilen çıktının tek yönlü aynasıdır ve yeniden üretilebilir; yükleme kökü **asıl veridir**, kaybı geri alınamaz. İkisi aynı ağaca konmaz (Faz F).
7. **`:server` testleri JUnit 5 + Truth kullanır** (Spring Boot 4 JUnit 4'ü desteklemiyor); `:core:domain` ve `:app` JUnit 4 + Truth'ta kalır.

---

## 8. UI / TASARIM KURALLARI

Basit, yüksek kontrastlı, **büyük dokunma hedefli**. Beyaz + mavi; **koyu lacivert gezinme yüzeyi**
(web kenar çubuğu, mobil alt sekme çubuğu) ve **dört anlamsal durum ailesi** *(6 Ağustos 2026,
Düzeltme Turu 4)*. ~~Yalnızca beyaz + mavi; tüm rozetler aynı mavi~~ — aynı kartta iş durumu ile belge
durumu ayırt edilemiyordu.

Palet ve ölçüler **hem mobilde hem web'de** geçerlidir; web karşılığı `web/src/stil.css`
(CSS değişkenleri aynı adlarla: `--birincil`, `--birincil-yumusak`, `--dokunma: 48px` …).

```kotlin
// app/designsystem/Color.kt (referans)
val Primary      = Color(0xFF0B5FFF)  // ana mavi — butonlar, aktif durum
val PrimaryDark  = Color(0xFF0842B8)  // basılı durum
val PrimarySoft  = Color(0xFFE8F0FF)  // grup satırı, chip arka planı
val Surface      = Color(0xFFFFFFFF)
val Background   = Color(0xFFF5F8FC)
val Outline      = Color(0xFFD6E0EE)
val OnSurface    = Color(0xFF0F1B2D)
val OnSurfaceMut = Color(0xFF5B6B80)
val Success      = Color(0xFF1B8A5A)
val Error        = Color(0xFFC62828)
val DisabledBg   = Color(0xFFEDF1F6)

// Koyu gezinme yüzeyi — web `--kenar-*` ile birebir aynı değerler.
val NavSurface      = Color(0xFF0F1B2D)  // OnSurface ile aynı: lacivert zaten metin laciverdi
val NavDivider      = Color(0xFF22314A)
val NavHover        = Color(0xFF1B2A42)
val NavActive       = Color(0xFF20365C)  // etkin öğe hapı
val NavOnSurface    = Color(0xFFE6EDF7)
val NavOnSurfaceMut = Color(0xFF8DA0BC)
```

**Dört anlamsal durum ailesi** — her biri *zemin · metin · vurgu* üçlüsü. Hiçbiri yeni renk değil;
hepsi var olan şerit tonlarının takma adıdır, böylece aynı anlam iki yerde iki tona ayrışamaz:

| Aile | Ne zaman | zemin / metin / vurgu |
|---|---|---|
| `acik` | `ONAY_BEKLIYOR`, `durum = null` | `PrimarySoft` / `PrimaryDark` / `Primary` |
| `isleniyor` | `ISLENIYOR` | `WarningSoft` / `WarningText #7A4F00` / `WarningBorder` |
| `tamam` | `TAMAMLANDI` | `SuccessSoft` / `SuccessText #10603E` / `Success` |
| `iptal` | `IPTAL` | `ErrorSoft` / `Error` / `Error` |

Aile seçimi **tek bir yerde** yaşar (`bilesen.tsx → durumAilesi`, `designsystem/StatusTone.kt`).
⚠️ Aile yalnızca **rengi** seçer; **durum metni her zaman sunucudan gelir** (`durumMetni`).

- Material 3 `lightColorScheme` üzerine bu paleti oturt. **Dinamik renk (Material You) kapalı** — kurumsal kimlik sabit kalmalı. Dark theme kapsam dışı: lacivert bir **yüzeydir**, koyu tema değil — sonradan "tamamlanacak" bir iş sanılmasın.
  ⚠️ **M3'ün eşlenmeyen yuvası varsayılan mor basar.** `outlineVariant` (→ `HorizontalDivider`), `secondaryContainer` (→ `NavigationBarItem` seçili hapı), `surfaceContainer*`, `tertiary*`, `errorContainer`, `inverse*`, `scrim` eşlenmiş olmalı. Hata derlemede değil ekranda çıkar.
- Minimum dokunma alanı **48.dp**; `TextField` yüksekliği ≥ 56.dp.
- Taban font 16.sp, tablo hücresi 15.sp, başlık 20–24.sp. Küçük font yasak.
  ⚠️ **Tanımsız bırakılan M3 tipografi yuvası da varsayılana düşer ve kural sessizce delinir.**
  `titleSmall` (14sp), `bodySmall` (12sp), `labelMedium` (12sp — alt sekme etiketi), `labelSmall` (11sp)
  `Type.kt`'de **tanımlı olmak zorunda**. Kuralı temenniden denetlenebilire çeviren madde budur.
- **Tablet uyarlaması:** hedef cihaz zaten sadece tablet — **tek yerleşim** yazılır: landscape öncelikli, portrait'te de bozulmaz. `android:resizeableActivity="true"`.
  ⚠️ ~~Genişliğe göre dallanan ikinci bir yerleşim (`WindowSizeClass`, iki sütunlu form) yapma; ihtiyaç doğarsa Backlog~~ *(12 Ağustos 2026, kullanıcı kararı — güncellendi)*: **statik iki sütun ve sabit maks. genişlik serbesttir** ve tabletin geniş yatay alanını kullanmak için **beklenen** düzendir. Yasak olan yalnızca **çalışma-zamanı genişlik dallanması** — `WindowSizeClass` ile ekran boyutuna göre seçilen, düzen mantığını çoğaltan **ikinci bir bileşen ağacı**. İki sütun **tek yeniden-kullanılabilir bileşende** (`designsystem/FieldColumns`, `BoxWithConstraints`) eşiğe göre iki→tek sütuna **akar** (portrait/dar ekranda bozulmaz) — "daralan sütun, dallanan yerleşim değil". Form içeriği `designsystem/FormContainer` ile `Spacing.formMaxWidth`'te ortalanır. Alanlar **`AppTextField`**, birincil/ikincil butonlar **`PrimaryButton`/`SecondaryButton`** — 48/56dp kuralı çağrı yerlerinde tekrar edilmez, **tek enforcement yeri bu bileşenlerdir**.
  **Web kabuğu istisnası** *(Düzeltme Turu 4)*: kenar çubuğu **her zaman açıktır**. Dar ekranda yalnızca `--yan-genislik` daralır ve etiketler gizlenir — **çekmece, aç/kapa düğmesi ve ikinci bileşen ağacı yasaktır**. Tek ızgara, tek JSX ağacı; dallanan bir yerleşim değil, daralan bir sütun.
- **Ekran döndürme veri kaybettirmez** → state `ViewModel` + `SavedStateHandle`'da.
- Zorunlu alan dolmadan "Sonraki" **disabled**, nedeni alanın altında `Error` rengiyle yazılı.
- Tabloda: grup satırı `PrimarySoft` dolgulu ve kalın; hesaplanan sütunlar `DisabledBg` zeminli (dokunulamaz olduğu anlaşılsın); toplam satırı ekranın altına **sabit (sticky)**.
- Uzun işlemlerde (PDF/Excel üretimi) engelleyici loading + hata durumunda "Tekrar dene".

---

## 9. AKIŞ (mobil, Faz J — web ile parite)

Mobil ve web **aynı akışı** yürütür; ikisi de bölge sorumlusunun ekranıdır. Yönetici akışı
(**onay**, durum müdahalesi, iptal, kullanıcılar, **sözleşmeler**, **kablo envanteri**)
**yalnızca web'dedir**.

⚠️ **Mobilde yönetici eylemi yoktur ve eklenmez** *(17 Ağustos 2026)*. Bir dönem proje listesinde
"İşleme Al" / "Projeyi İptal Et" düğmeleri vardı; yeknesek durum makinesine geçişte (`V17`) kırıldılar
ve fark edilmedi — "İşleme Al" `POST /api/isler/{id}/durum`'a gerekçesiz gidiyordu, sunucu ise artık
**her geçişte gerekçe** istiyor (400). Ayrıca web'de aynı düğme projeyi değil **onay bekleyen aşamayı**
onaylıyor (`/api/belgeler/{id}/onayla`) ve proje durumu bunun sonucu olarak türüyor. İki istemcide iki
ayrı yönetici akışı bakımı, kırılmayı sessiz kılan şeydi. `HakedisApi` yönetici uçlarını **hiç
tanımlamaz** — sınır burada.

**Düzenlenebilirlik sunucudan gelir** *(Faz V)*: `BelgeYaniti.duzenlenebilir` / `gonderilebilir`.
İstemci `durum === 'ONAY_BEKLIYOR'` diye kendi kuralını türetmez — türetseydi kural üç yerde (sunucu,
web, mobil) yaşar ve durum kümesi değişirken biri unutulduğunda ekran kilitli görünüp sunucu yazmayı
kabul ederdi (§7/1).

```
[0] Giriş            →  Kullanıcı adı · Parola  (başka alan YOK)
                        Sunucu adresi derleme zamanında sabit: `https://hakedis.<DOMAIN>`
                        Parola cihazda SAKLANMAZ; oturum yalnızca bellekte (Faz J/J2)
[1] Proje Listesi    →  **Çalışma ekranı.** Her proje bir satır/kart:
                        SP No · Bölge · Kapsam · Kablo ID · Ek Aralığı · Route · Durum
                        altında üç aşama satırı (Fizibilite / Keşif / Hakediş); her birinde
                        açılma tarihi · değiştirilme tarihi · güncelleyen · toplam ·
                        [Güncelle] [Gözlemle]        + [Projeleri Excel olarak indir]
                        → "Yeni Proje"
                        (Yönetici eylemleri burada YOK — yalnızca web; bkz. yukarıdaki uyarı)
[2] Yeni Proje       →  Bölge (salt okunur) · Kapsam · Kablo (mtc… yazarak ara) ·
                        Route · Ek Aralığı        → "Projeyi Oluştur"
                        SP No sunucu üretir; sözleşme burada SEÇİLMEZ
                        Kapsam "Yeni Tesis" ise: envanter araması YOK. Kablo ID'yi sistem üretir
                        (mtc…, otomatik) + Uç A / Uç B (Ülke…Raf) elle girilir, ülke/şehir/adres ZORUNLU
                        ↓ "Bu proje açıldı" penceresi → [Proje Listesine Git]
[3] Belge            →  1. Sözleşme seç   2. Miktar (şablon indir/yükle ya da tabloya gir)
                        3. Dosyalar: güzergâh (.kmz/.kml, ZORUNLU) + görseller (opsiyonel)
                        4. TTI Kullanılan Malzemeler (EK3) — YALNIZCA hakedişte, tümü opsiyonel
                        → "Gönder"   (eksikler madde madde yazılı, düğme kapalı)
                        ⚠️ EK3 gönderimi engellemez AMA doldurulup kaydedilmemişse durdurur:
                           uyarı `gonder()` içinde verilir, düğme kapatılarak DEĞİL — kapalı
                           düğmeye tıklamak hiçbir geri bildirim üretmiyor (19 Ağustos 2026)
                        Aşama ONAY BEKLİYOR'da kalır ve düzeltilebilir; yönetici web'den
                        "İşleme Al" deyince kilitlenir. İptal edilmişse gerekçe şeritte görünür.
                        ↓ aynı ekranın altı
                        Çıktı: PDF/Excel indir (sunucu üretir) · ilk sayfa önizleme · Paylaş / Aç
```

~~`[3] İş Detayı`~~ ekranı kaldırıldı (Faz R/S): taşıdığı her bilgi iş listesi satırına sığıyordu ve
aynı şeyi görmek için ikinci bir tık gerekiyordu.

**Dosya adlandırma:** çıktı adını **sunucu** verir (`DocumentFileNamer`) ve istemci
`Content-Disposition` başlığından okur — `TTI_IZMIR_2026_KESIF_001_TEKLIF.pdf`. İstemci ad türetmez.
Sanitize sunucuda: Türkçe karakterler ASCII'ye çevrilir (`ş→s`, `İ→I` …), boşluk `_`, diğer özel
karakterler atılır; dönüşümde `Locale.ROOT` (bkz. §5).

---

## 10. PDF ÇIKTI KURALLARI

Referans: paylaşılan **"Hasar Tespit Tutanağı"** formatı.

- Sayfa A4 portrait. Üst solda **başlık**, üst sağda **TTI logosu** (`server/src/main/resources/logo/tti-logo.png` — **yerinde**, 4 Ağustos 2026; HTML'e base64 `data:` URI olarak gömülür). Dosya **isteğe bağlıdır**: yoksa marka adı metin olarak basılır ve üretim aksamaz. Ad `tti-logo.png` olmalı **ve içerik gerçekten PNG olmalı** — `PngDogrulayici` ilk sekiz bayta bakar, uzantısı değiştirilmiş bir JPEG'i reddeder (bkz. `MarkaLogosu`, `logo/OKU.md`).
- Gövde: iki sütunlu **etiket → değer** listesi (sol etiket kalın/gri, sağ değer siyah).
- Altında **kalem tablosu**: `Poz No | İş Kalemi | Birim | Br Fiyat | Miktar | Tutar`.
  Birim fiyat **2 haneye yuvarlı** basılır (§5).
  Sadece **miktarı girilmiş satırlar** listelenir. Grup başlığı, altında dolu satır varsa ara başlık olarak korunur.
- Tablo sonunda **Toplam** satırı (aktif moda göre "Toplam 1. Keşif" ve/veya "Toplam Kesin Hakediş").
- En altta: EK1 dosya adı, EK2 görsel sayısı, oluşturulma tarih/saati.
- Şablon **XHTML + CSS string**'dir (`core/domain/document/PdfTemplate.kt`); değerler **HTML-escape** edilerek enjekte edilir (kullanıcı metni `<`, `&`, `"` içerebilir).
- Sayfa kırılımında tablo başlığının tekrarı için `thead { display: table-header-group }`.
- ~~WebView **main thread** ister: `createPrintDocumentAdapter` main dispatcher'da…~~ — **Faz G'de kalktı**, üretim sunucuda.

### Üretici motorun dayattığı kurallar *(openhtmltopdf, Faz G)*
- **Çıktı geçerli XHTML olmalı:** motor girdiyi XML olarak ayrıştırır. Kapanışsız `<meta>`/`<img>` ya da tanımsız bir varlık (`&nbsp;`) PDF'i bozmaz, **üretilmesini engeller**. `PdfTemplateTest` iyi biçimliliği kilitler.
- **Flexbox yok:** motor CSS 2.1 tabanlı; `display: flex` sessizce yok sayılır. Hizalama `<table>` ile yapılır.
- **Font gömülür:** PDF'in base-14 fontları `ş`, `ğ`, `İ` taşımaz ve JRE imajında sistem fontu yoktur. `DejaVuSans.ttf` + `DejaVuSans-Bold.ttf` jar'ın içinde (`server/resources/fonts/`, Bitstream Vera lisansı yanında). Kalın yüz ayrıca gömülür: motor sentetik kalınlaştırma yapmaz.
- **Motorun INFO log'u kapalı** (`XRLog.setLoggingEnabled(false)`): belge başına onlarca satır döküyor ve gerçek `WARN`'ları görünmez kılıyordu.

## 11. EXCEL ÇIKTI KURALLARI

- **Satır sırası korunur:** sözleşmenin **tüm** satırları kaynak sırasıyla yazılır — grup başlıkları ve miktarı girilmemiş kalemler dahil. Excel sözleşmenin aynasıdır; PDF ise yalnızca dolu satırları listeler. İki çıktı bilinçli olarak farklı sorulara cevap verir.
- **Sütun düzeni belge türüne bağlıdır** *(Faz G kararı, `ExcelDuzeni`)*: `KESIF` ve `HAKEDIS` kaynağın 8 sütununu **aynı** başlıklarla kullanır (keşif sol çifti, hakediş sağ çifti doldurur; boş kalan sütun "henüz yaşanmamış aşama" demektir). `FIZIBILITE` 6 sütuna iner ve kendi başlıklarını taşır — kaynakta ön fizibilite sütunu yok, ayrıca `TEKLIF` kopyası aracı firmaya gidiyor ve doldurulmayacak başlık yanlış hücreye davetiyedir.
- Tutarlar **değer olarak** yazılır (formül değil) — uygulamadaki ile dosyadaki birebir aynı olsun.
- Sayı hücreleri gerçek `number` tipi, format `#,##0.00`. Metin olarak yazma.
- Meta bilgi bloğu **tablonun üstünde** *(kullanıcı kararı, 30 Temmuz 2026 — §15/4 kapandı)*; ayrı "Bilgiler" sheet'i yapılmaz. EK3 malzeme listesi bu bloğun sonunda, **EK2 satırının hemen altında** (18 Ağustos 2026).
- **TTI logosu sağ üstte** *(kullanıcı isteği, 19 Ağustos 2026)* — son iki sütunun üstündeki boş alana çapalanır; kaynak PDF ile **aynı** dosya (`MarkaLogosu.baytlar`), dolayısıyla aynı PNG doğrulamasından geçer ve dosya yoksa ikisi de sessizce metne düşer.
  ⚠️ `PictureAnchor.twoCellAnchor` parametre sırası **(satır, sütun, satırSon, sütunSon)** — adı bunu söylemiyor ve belgelenmemiş. Ters verilince hata çıkmaz, logo sessizce sol üste düşer; doğrulama yolu üretilen dosyanın `xl/drawings/drawing1.xml` çapasını okumaktır.
- **İmza bloğu Toplam satırının altında** *(kullanıcı isteği, 18 Ağustos 2026)*. ~~Faz M'de kalkmıştı~~; dönen hâli dört satır: `ŞİRKET ADI: ` (boş, elle yazılır) / `TURK TELEKOM INTERNATIONAL` · bölüm başlıkları · altı unvan · imza satırı. Sistemden gelen tek isim **bölge sorumlusudur** (belgenin bölgesinin aktif sorumlusu, belgeyi açan kişi değil); iki "Kontrol Eden" (Sinan Pamuk · Serhat Çorbacı) `CiktiEtiketleri`'nde sabit metindir.
  ⚠️ Kurum adı `markaAdi.uppercase()` ile **türetilmez**: Türkçe locale `International` → `INTERNATİONAL` üretiyor (§5 locale tuzağı). Sabit metin olarak duruyor.
  ⚠️ Sütun eşlemesi `duzen.sonSutun`'a göre hesaplanır, sabit değil — fizibilite 6, keşif/hakediş 8 sütun basıyor ve blok ikisinde de tablonun tam genişliğini kaplamalı.
- Birim fiyat hücresi **yuvarlanmış değeri taşır** (§5): hücre biçimi 2 hane gösterirken değer 14 ondalık kalsaydı, hücreye tıklayan kişi farklı bir sayı görürdü.
- Türkçe karakterli sheet adı ve başlıklar UTF-8 korunmalı. *(fastexcel bunları `&#x15f;` gibi sayısal kaçışla yazar — Excel çözer, ama dosyayı **test ederken** kaçışları çözmeden metin aramak daima boş döner ve kontrol sessizce "geçmiş" görünür.)*

---

## 12. ANDROID'E ÖZGÜ TUZAKLAR (unutma)

0. **`execute()` yalnızca başlıkları getirir; gövde okuması ayrı bir soket işlemidir.** `withContext(Dispatchers.IO)` bloğunu isteğin etrafına koyup `Response`'u geri döndürmek yetmez: `body.string()`/`bytes()`/`close()` çağıran iş parçacığında koşar ve ana iş parçacığında **`NetworkOnMainThreadException`** fırlatır. O istisnanın **mesajı `null`**'dur, yani ekranda boş bir hata kutusu olarak görünür. Küçük yanıtlar ilk pakette gelip tamponda bulunduğu için hata çıkmaz — sorun ancak liste büyüyünce ortaya çıkar. `HakedisApi` bu yüzden gövdeyi istekle **aynı** IO bloğunda okur (`sendForText`) ve `Response`'u dışarı hiç vermez.
0b. **Sunucu CSRF'i giriş ucunda da istiyor.** Token `XSRF-TOKEN` çerezinden gelir ve çerez sunucudan geçen ilk istekte yazılır; uygulama ilk açıldığında elde çerez olmadığı için **yazan bir istekten önce** kimlik istemeyen bir uca (`/api/saglik`) GET yapılır. Atlanırsa ilk giriş denemesi 403 ile düşer — `HakedisApi.send` bunu tek yerde çözüyor, web de (`api.ts`) aynısını yapıyor.
1. **Package visibility (Android 11+):** başka bir uygulamayı `resolveActivity` ile sorgulayacaksan `AndroidManifest.xml`'e `<queries>` bloğu ekle, yoksa `null` döner (kamera, PDF görüntüleyici, paylaşım).
2. **FileProvider zorunlu:** `file://` URI paylaşmak `FileUriExposedException` fırlatır. `res/xml/file_paths.xml` + `content://` + `FLAG_GRANT_READ_URI_PERMISSION`.
3. **KMZ/KML MIME tutarsız.** `OpenDocument` çağrısına `application/vnd.google-earth.kmz`, `application/vnd.google-earth.kml+xml`, `application/octet-stream`, `*/*` ver; **doğrulamayı dosya uzantısı üzerinden yap.**
4. **Seçilen URI kalıcı değildir.** Dosyayı hemen uygulamanın kendi dizinine **kopyala** (`context.filesDir`), URI'yi saklama.
5. **Android 13+ izinleri:** Photo Picker kullanırsan `READ_MEDIA_IMAGES` izni gerekmez → **Photo Picker'ı tercih et.** Kamera için `CAMERA` izni runtime'da istenir.
6. **Configuration change:** tablet döndürme sık olur; tüm ekran state'i `ViewModel`'de.
7. **desugaring:** `java.time` için `isCoreLibraryDesugaringEnabled = true`.
8. **R8/ProGuard:** release build'de küçültme **açık** (Faz J). kotlinx.serialization ve OkHttp sınıflara isimle ulaşır; keep kuralları `app/proguard-rules.pro`'da. Kural eksikse hata derlemede değil, cihazda ilk API çağrısında çıkar — bu yüzden elle test **release APK** ile yapılır.
9. ~~**assets okuma** main thread'de yapılmaz~~ — Faz J'de assets kalktı; tüm veri ağdan gelir ve `HakedisApi` her çağrıyı `Dispatchers.IO`'da koşturur.

---

## 13. YASAKLAR

- ❌ `Double`/`Float` ile para hesabı, `BigDecimal(double)` constructor'ı
- ❌ `:core:domain` modülüne Android/Compose bağımlılığı ekleme
- ❌ Composable içinde para hesabı, sözleşme parse'ı veya dosya I/O
- ❌ Hesaplanan tutarları state'te saklama
- ❌ Cihazda `.xlsx` parse etme (Apache POI'yi `app` modülüne ekleme)
- ❌ İstemcide (mobil/web) PDF veya Excel üretme — çıktı yalnızca sunucuda üretilir
- ❌ Yetki süzgecini SQL yerine Kotlin'de yapma
- ❌ Web veya mobil tarafında para aritmetiği — tutar ve toplam sunucudan gelir
- ❌ **İstemcide proje ya da aşama toplamı türetme.** `IsYaniti`'nde proje toplamı yoktur; `belgeler[].toplam` toplanmaz. Gereken buysa sunucu üretir — ama `dogrula-faz-r.sh` `round(..., 2)` satır sayısının **tam 4** olmasını şart koşuyor, beşinci fiyat sorgusu kapıyı düşürür (§5)
- ❌ **`material-icons-core` / `material-icons-extended` bağımlılığı ekleme.** Material3 1.4.0 ikonları geçişli getirmiyor; `Icons.Default.*` derlenmez ve bariz "çözüm" bağımlılık eklemektir. Karar: ikonlar `app/src/main/res/drawable/` altına **elle yazılmış `<vector>` XML**'dir (24dp, `fillColor` beyaz → `Icon(tint = …)` boyar). Webde karşılığı satır içi SVG
- ❌ iText (AGPL) kullanma
- ❌ Kullanıcıya görünen hardcoded string, hardcoded renk/dp
- ❌ Gerekçesiz `!!`, savurgan `lateinit`, boş `catch` bloğu
- ❌ `runBlocking` (test dışında), `GlobalScope`
- ❌ Aktif fazın kapsamı dışına çıkma / bağımlılığı sormadan ekleme
- ❌ Yayımlanmış bir Flyway migration'ını düzenleme (checksum tutmaz, sunucu açılmaz) — yeni V numarası ekle
- ❌ Geliştirme verisini temizlerken `TRUNCATE ... CASCADE` — cascade `is_kaydi` → `fiyat_versiyonu` →
  `poz_fiyat` zincirini izleyip **referans fiyat listesini de siler**. Silme sırası elle yazılır
  (bkz. `deploy/dogrula-faz-*.sh`)
- ❌ Kaldırılan kavramları geri getirme: ~~**belge onayı**~~, **işe özel pazarlık fiyat listesi**,
  **imza**, **bölge sıra kuralı** — bilinçli olarak silindi (Faz M–P).
  ⚠️ **Belge onayı 10 Ağustos 2026'da geri geldi** (Faz V, yönetici isteği); 14 Ağustos'ta yeknesek
  durum makinesiyle **üç aşamanın da** kapısı oldu (§3). ~~Yalnızca keşif ve hakediş; fizibilite
  onaysız~~ — iki ayrı kural, aynı ekranda iki farklı davranış demekti.
  ⚠️ ~~**İade** geri gelmedi ve gelmeyecek~~ — **18 Ağustos 2026'da geri geldi** (kullanıcı isteği),
  ama eski anlamıyla değil: "belgeyi düzeltilsin diye geri gönderme" değil, **aşamayı sıfırlama**
  (§ Önceki aşamaya iade). Diğer üç kavram olduğu gibi yasak.
- ❌ Birim fiyatı iki farklı hassasiyette gösterme (§5: tek kural, dört sorguda aynı `round(...,2)`)
- ❌ **Spekülatif soyutlama:** tek implementasyonu olan interface, kullanılmayan "ortak" Composable, henüz ölçülmemiş performans optimizasyonu, **yeni bir çalışma zamanı hedefi olmadan** yeni Gradle modülü

---

## 14. KOMUTLAR

```bash
# --- Sunucu ---
./gradlew :server:bootJar                  # sunucu jar'ı (Android SDK gerekmez)
cd deploy && docker compose up -d --build  # postgres + sunucu
curl -s localhost:8081/api/saglik          # sağlık kontrolü
bash deploy/dogrula.sh                     # Yetki, kable araması, para hassasiyeti + kablo envanteri
                                           # sürümleri (Faz U — sürüm satırı YAZAR, geliştirme DB'si)
SIFIRLA=1 bash deploy/dogrula-faz-mnop.sh  # Faz M–P — iş/belge kaydı OLUŞTURUR, sadece geliştirme DB'sinde
bash deploy/dogrula-faz-r.sh               # Faz R/S — kaynak kod güvenceleri + web derlemesi (sunucu gerekmez)
SIFIRLA=1 bash deploy/dogrula-faz-v.sh     # Faz V — onay/iade, yönetici durum yetkisi, poz ekleme,
                                           # elle kablo; iş/belge kaydı OLUŞTURUR, geliştirme DB'si
# ~~dogrula-faz-d/e/f/g/h/i/j/l.sh~~ emekli: eski akışa (onay, imza, işe özel fiyat, elle SP No)
# bağlıydılar. Yaşayan iddiaları yukarıdaki iki betiğe taşındı.
bash deploy/kur.sh                         # klasörleri aç + .env'i işletim sistemine göre doldur (Windows: kur.ps1)
# ⚠️ Yedekleme PLATFORM deposuna taşındı — bu depoda `deploy/yedek.sh` YOK. Otorite: tti-sunucu.
bash ../tti-sunucu/yedek.sh                # tüm projelerin veritabanı + YUKLEME_KOK yedeği (sunucuda systemd timer çağırır)
bash ../tti-sunucu/yedek.sh dogrula        # son yedeği denetle — salt okunur, üretimde de güvenli
SIFIRLA=1 bash deploy/dogrula-faz-w.sh     # Faz W — Yeni Tesis'te kablo opsiyonel; proje kaydı
                                           # OLUŞTURUR, sadece geliştirme DB'sinde
./gradlew :tools:kable-import:run -PDB_PAROLA=...   # kable envanterini yükle (ilk kurulum/kurtarma;
                                                    # günlük yükleme "Kablo Envanteri" ekranından)

# --- Sunucuya kurulum rehberleri (dağıtım hedefine göre) ---
# deploy/KURULUM.md            yalnızca TTI'nin kendi ayarları (.env, arşiv, kable, doğrulama)
# deploy/WindowsDeploy.md      Windows makine · WSL2 + Docker Engine · iki proje
# deploy/WindowsDockersiz.md   Windows makine · konteynersiz (JDK + PostgreSQL + WinSW servisi)
# deploy/RedHatKurulum.md      Red Hat sunucu · Docker Engine (docker-ce) · iki proje · yedekleme (§12)
#
# İkinci proje .NET: yalnızca .NET 6/8/9 Linux'ta koşar. .NET Framework 4.x Windows'a bağlıdır,
# RHEL'e taşınamaz — kurulum rehberlerinin ilk adımı bu kontroldür.
# Motor kararı: **Docker Engine**; podman değerlendirilip elendi (9 Ağustos 2026).

# --- Web arayüzü ---
cd web && npm install                      # ilk kurulum
cd web && npm run dev                      # geliştirme sunucusu (5173), API'yi 8081'e vekiller
cd web && npm run build                    # tsc --noEmit + vite build → web/dist
./gradlew :server:bootJar -PwebAtla        # Node yoksa: arayüzsüz jar (yalnızca API)

# --- Mobil / araçlar ---
./gradlew :app:assembleDebug               # debug APK
./gradlew installDebug                     # tablete kur
./gradlew test                             # unit testler (domain dahil)
./gradlew connectedAndroidTest             # cihaz/emülatör testleri
./gradlew ktlintCheck                      # statik analiz
./gradlew :app:assembleRelease             # imzalı release APK
```

Her fazın sonunda **`./gradlew ktlintCheck test`** temiz geçmeden faz "bitti" sayılmaz.

---

## 15. AÇIK SORULAR (kullanıcıdan yanıt bekleniyor)

> Bu liste `NEWPHASES.md` → "Sonraki fazlarda cevabı gereken açık sorular" bölümüne devredildi.
> Aşağıdakiler tarihsel kayıttır.

1. ~~Mail To / CC / Konu formatı~~ — **iptal**, mail gönderimi kaldırıldı
2. ~~Mail gövde metni şablonu~~ — **iptal**
3. `2026_Meserbay_İzmir` sheet'i çalışma şablonu mu, yoksa 3. bir sözleşme seçeneği mi?
4. ~~Excel çıktısında meta bilgiler üst blokta mı, ayrı sheet'te mi?~~ — **cevaplandı:** üst blok (Faz G)
5. Bölge seçimi sözleşme listesini filtrelemeli mi? (ör. İzmir → sadece Meserbay) — **hâlâ açık**;
   sözleşme artık belge düzeyinde seçildiği için soru "belge ekranındaki liste süzülmeli mi"ye döndü
6. ~~Yuvarlama: satır bazında mı, toplamda tek sefer mi?~~ — **cevaplandı** (3 Ağustos 2026):
   birim fiyat **çivilenirken** 2 haneye yuvarlanır, satır tutarı yuvarlanmış fiyattan hesaplanır,
   toplam yuvarlanmış satırların toplamıdır (§5). 30 Temmuz'daki "ham fiyat göster" kararı ters
   yöne çevrildi: 17 haneli fiyat çıktıda okunmuyordu.
7. Aynı kayıt sonradan düzenlenebilmeli mi? (kayıt geçmişi/arşiv gerekli mi) — kısmen cevaplandı:
   gönderilen belge değişmez (D2), yanlış açılan taslak yönetici iptaliyle çöpe atılır
8. ~~TTI logosunun kullanılabilir PNG/SVG hâli var mı?~~ — **kapandı** (4 Ağustos 2026). Logo 720×243 saydam PNG olarak üç yerde: `app/src/main/res/drawable-nodpi/tti_logo.png` (mobil giriş), `web/src/tti-logo.png` (web giriş), `server/src/main/resources/logo/tti-logo.png` (PDF başlığı). Üç kopya bilinçli — APK, jar ve web paketi ayrı dağıtılıyor.
9. Hedef tabletlerin Android sürümü ve APK dağıtım yöntemi (MDM var mı)? — **kısmen açık**: minSdk 26
   yeterli görülüyor, dağıtım elle yapılıyor (Faz J). APK imzalama ve MDM sorusu duruyor.