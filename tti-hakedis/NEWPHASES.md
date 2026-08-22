# NEWPHASES.md — Backend + Web + Mobil Yol Haritası (Faz A–T)

> **Kullanım:** Aynı anda **tek faz** açıktır. Faz başlarken `Durum: 🚧` yap, bitince tüm DoD maddelerini
> işaretle, `Durum: ✅` yap ve **dur, onay bekle**. Bir sonraki faza kendi başına geçme.
>
> Faz dışı ihtiyaçlar en alttaki **Backlog**'a yazılır, uygulanmaz.
>
> Eski `PHASE.md` (mobil Faz 0–10) **arşivdir**: Faz 0–7 tamamlandı, Faz 8 (Mail) ve Faz 9 (Offline/Taslak)
> iptal edildi. Aktif yol haritası bu dosyadır.

| Faz | Başlık | Taraf | Durum |
|---|---|---|---|
| A | Depo Düzeni, Docker, Veritabanı İskeleti | backend + altyapı | ✅ |
| B | Kimlik Doğrulama, Yetki & Sözleşme Uçları | backend | ✅ |
| C | Domain Dönüşümü — 3 Belge Türü & Çıktı Varyantı | domain (ortak) | ✅ |
| D | İş + Belge API'leri, Numaralandırma, Aşama Kuralı | backend | ✅ |
| E | Onay Akışı, Durum Makinesi & İşe Özel Fiyat Listesi | backend | ✅ |
| F | İmza & Ek Dosyalar (EK1/EK2) | backend | ✅ |
| G | Sunucuda PDF + Excel Üretimi & Belge Aynası | backend | ✅ |
| H | Web Arayüzü — Bölge Sorumlusu | web | ✅ |
| I | Web Arayüzü — Yönetici: Onay Kuyruğu & Fiyat Listesi | web | ✅ |
| J | Mobil Uygulamanın Backend'e Bağlanması | mobil | ✅ |
| K | Hakediş Karşılaştırma & Sertleştirme | hepsi | ⬜ |
| L | Yönetici Sözleşme Yönetimi | backend + web | ✅ *(kapsamı Faz N'de daraldı)* |
| **M** | **Onay Akışı ve İmza Kaldırılıyor** | backend + domain | ✅ |
| **N** | **Sözleşme İşten Belgeye Taşınıyor** | backend + domain | ✅ |
| **O** | **İş Durum Makinesi, Otomatik SP No, Serbest Kablo** | backend + domain | ✅ |
| **P** | **2 Hane Yuvarlama, Miktar Excel'i, Zorunlu EK1** | backend + domain | ✅ |
| **R** | **Web Yeniden Düzeni — Satır Bazlı İş Ekranı** | web | ✅ |
| **S** | **Mobil Parite** | mobil | ✅ |
| **T** | **Dokümantasyon ve Doğrulama Betikleri** | hepsi | ✅ |

Legend: ⬜ başlamadı · 🚧 devam ediyor · ✅ bitti · ❌ iptal

> ⚠️ **3 Ağustos 2026 — SADELEŞTİRME TURU (Faz M–T).** Yönetici, 30 Temmuz kurgusunu sahada
> gördükten sonra "daha basit ve kullanılabilir" bir uygulama istedi. Onay akışı, imza, işe özel
> pazarlık fiyatı, bölge sıra kuralı ve iki ara ekran kaldırıldı; sözleşme belgeye taşındı, SP No
> üretilir oldu, miktar Excel'le girilebilir hâle geldi. Ayrıntı: **SADELEŞTİRME TURU KARARLARI**.
>
> ⚠️ **30 Temmuz 2026'da fazlar yeniden harflendirildi.** Müdürden gelen onay akışı güncellemesi
> (aşağıda **FAZ E KARARLARI**) yeni iki faz doğurdu ve sonrakiler kaydı:
> eski E (PDF/Excel) → **G**, eski F (web bölge) → **H**, eski G (web yönetici) → **I**,
> eski H (mobil) → **J**, eski I (hakediş karşılaştırma) → **K**. Faz A–D dokunulmadı.
> Eski fazların gövdesi ve kararları **silinmedi**, yeni harfleriyle yerinde duruyor.

---

## NEDEN YENİ YOL HARİTASI

Uygulama "tek kullanıcılı offline araç"tan "çok kullanıcılı, rol bazlı sisteme" dönüşüyor. Değişen temel kararlar:

| Konu | Eskiden | Şimdi |
|---|---|---|
| Kullanıcı | Tek tip (saha personeli) | **Bölge sorumlusu** (7 kişi, her biri tek bölge) + **Yönetici** |
| Belge türü | 2 mod: `EntryMode{KESIF, SAHA}` | 3 tür: `DocumentType{FIZIBILITE, KESIF, HAKEDIS}` |
| Ağ | Offline-first, senkronizasyon | **Çevrimiçi** (VPN içinden), senkronizasyon yok |
| Sözleşme verisi | APK içinde gömülü JSON | **PostgreSQL**, fiyat versiyonlu |
| PDF/Excel | Cihazda üretilir | **Yalnızca sunucuda** üretilir |
| Çıktı teslimi | Outlook'a Intent ile mail | Arayüzden indirme + **arşiv klasörüne async kopya** |
| Numaralandırma | Yok (dosya adında zaman damgası) | `Bölge_Yıl_Tür_SıraNo`, sunucu otoritesinde, boşluksuz |
| Arayüz | Sadece Android tablet | Android + **web** (2 rol) |

**Pazarlıksız kurallar** (CLAUDE.md §5'e ek):
1. **İş mantığı iki kez yazılamaz.** Para hesabı, yuvarlama, formatlama, poz sıralama, çıktı modeli yalnızca `:core:domain`'de yaşar; sunucu ve mobil aynı sınıfları çağırır. Web para aritmetiği **yapmaz**, sunucudan hazır sonuç alır.
2. **Her belge bir fiyat versiyonuna çivilenir** ve kullandığı birim fiyatı kendi içinde saklar. Yönetici fiyat güncellemesi geçmiş belgeyi **değiştiremez**.
3. **Hesaplanan tutar saklanmaz** — `PricingCalculator.rowAmount` tek yetkili (birim fiyat donduğu için sonuç birebir yeniden üretilebilir).
4. **Yetki süzgeci SQL'de zorunludur.** Kotlin'de liste filtrelemek yasak.
5. **Sunucu mobile bağımlı değildir.** `:app` yalnızca Android SDK bulunan makinede build'e girer; APK ayrı bir geliştirici makinesinde derlenip elle dağıtılır.

---

## FAZ A — Depo Düzeni, Docker, Veritabanı İskeleti  ·  `Durum: ✅`

**Amaç:** `docker compose up` ile ayağa kalkan, şeması kurulu, kable envanteri yüklü bir servis. Henüz uç (endpoint) yok — sağlam zemin var.

**Yapılacaklar**
- [x] `git init` + ilk commit (taşıma öncesi geri dönüş noktası)
- [x] `NEWPHASES.md` (bu dosya)
- [x] Android projesini `TTI/` köküne taşı (boşluklu klasör adı yok olur), `.idea/`, `.gradle/`, `build/`, `.kotlin/` temizlenir
- [x] `settings.gradle.kts`: `:server` eklenir; **`:app` yalnızca Android SDK varsa** dahil edilir
- [x] `gradle/libs.versions.toml`: Spring Boot 4.1.0, Flyway, PostgreSQL sürücüsü
- [x] `server/build.gradle.kts`: `:core:domain` bağımlılığı, JUnit 5
- [x] `deploy/docker-compose.yml`: `db` (postgres:17, named volume, healthcheck) + `server` (8081, `TZ=Europe/Istanbul`)
- [x] `deploy/Dockerfile`: `eclipse-temurin:17-jre` + **önceden derlenmiş** bootJar (Docker'da Gradle koşmaz)
- [x] `deploy/.env.example` + `deploy/KURULUM.md`
- [x] **Belge aynası** mount'u: `{ARSIV_KOK}` bind mount (async kopyalama Faz G'de — planlandığında Faz E idi)
- [x] `V1__init.sql`: `bolge` (7), `kapsam` (5), `kullanici` (8), `kable` (boş tablo)
- [x] `V2__sema.sql`: `sozlesme`, `poz`, `fiyat_versiyonu`, `poz_fiyat`, `is_kaydi`, `belge`, `belge_satir`, `belge_sayac`, `ek`
- [x] `EnumSenkronDogrulayici`: açılışta DB kodları ↔ Kotlin enum adları; uyuşmazsa **sunucu açılmaz**
- [x] `tools/kable-import`: xlsx → `hakedis.kable` idempotent upsert (`nosid` üzerinden)
- [x] `CLAUDE.md` güncellemesi: modül kuralı yeniden yazılır, iptal olan kurallar (offline-first, `EntryMode`, mail, cihazda PDF/Excel) işaretlenir, yeni pazarlıksız kurallar eklenir
- [x] `PHASE.md` güncellemesi: Faz 0–7 kapatılır, Faz 8/9 ❌ İPTAL, bu dosyaya yönlendirme

### Faz A — plandan sapmalar (gerekçeleriyle)

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| Spring Boot sürümü | 3.5.x | **4.1.0** | Java tabanı 17 (mevcut toolchain ile uyumlu), greenfield proje için en uzun destek süresi |
| Flyway | `flyway-core` yeterli sanıldı | **`spring-boot-flyway`** eklendi | Boot 4 otokonfigürasyonları ayrı modüllere böldü; bu modül olmadan migration'lar hiç çalışmıyor (şema bile oluşmuyor) |
| Jackson dışlaması | `spring-boot-starter-json` exclude | **Faz B'ye ertelendi** | Boot 4'te modül adı `spring-boot-starter-jackson`. Ayrıca Faz A'da para taşıyan hiçbir uç yok; kararı test edilebilir olduğu yerde (Faz B) vermek doğru |
| Şema seçimi | JDBC URL'de `currentSchema` | **`spring.datasource.hikari.schema`** | Şema adı tek yerde kalıyor; compose'daki `DB_URL` ile çoğaltılmıyor |
| AGP / Spring Boot | — | Kökte `commons-compress` **1.28.0'a sabitlendi** | AGP eski bir commons-compress getiriyor, `bootJar` `NoSuchMethodError` ile düşüyordu |
| Dockerfile kullanıcı | `useradd tti` | **sayısal `USER 1000:1000`** | Temel imajda 1000 kimliği zaten tanımlı; kullanıcı yaratmak imaj sürümüne bağımlılık yaratıyor |
| ktlint `android` kipi | — | korundu | AGP kökte kaldığı için sorun çıkmadı |

> ⚠️ **Sunucuda ilk kontrol:** `docker pull postgres:17` çalışıyor mu? Kurumsal ağda Docker Hub proxy arkasında olabilir. Kapalıysa IT'den proxy adresi istenir; alternatif `docker save | gzip` → `docker load`.

> **Birim fiyat tipi `numeric` (kısıtsız).** `numeric(18,7)` gerçek veriyi keser: kaynak fiyatlar 14 ondalık taşıyor (`648.35852723050107`).

**DoD**
- [x] `./gradlew :server:bootJar` ve `./gradlew :app:assembleDebug` ikisi de geçiyor
- [x] `local.properties` geçici olarak kaldırıldığında `./gradlew projects` çıktısında `:app` **görünmüyor**, `:server:bootJar` yine derleniyor *(sunucunun mobile bağımlı olmadığının kanıtı)*
- [x] `docker compose up -d` → iki servis `healthy`, `GET /api/saglik` → `{"durum":"AYAKTA","veritabani":true,"arsivYazilabilir":true}`
- [x] `flyway_schema_history`: V1 + V2 `success = true`, 14 tablo oluştu
- [x] `select kod, dosya_kodu, ad from hakedis.bolge` → 7 satır, `Region` enum'uyla birebir
- [x] DB'ye bilerek yanlış bir bölge kodu eklenip yeniden başlatıldığında sunucu **açılmıyor** *(`'bolge' tablosu ile Kotlin enum'u uyuşmuyor. Kotlin enum'unda karşılığı yok: [SAHTE_BOLGE]`)*
- [x] `:tools:kable-import` sonrası bölge başına kable sayıları **tam olarak**: `IST_AVR` 309 · `IST_AND` 272 · `ANKARA` 86 · `IZMIR` 86 · `EDIRNE` 83 · `BURSA` 74 · `KUTAHYA` 33 → **toplam 943**
- [x] İçe aktarım **iki kez** çalıştırıldığında satır sayısı yine 943 *(0 yeni, 943 güncellendi)*
- [x] Arşiv bind mount'u yazılabilir: konteynerden oluşturulan dosya **host klasöründe** görünüyor
- [x] `./gradlew ktlintCheck test` temiz

---

## FAZ B — Kimlik Doğrulama, Yetki & Sözleşme Uçları  ·  `Durum: ✅`

**Amaç:** Giriş çalışıyor ve **yetki sınırı ilk kez gerçek veriyle** kanıtlanıyor: bölge sorumlusu yalnızca kendi bölgesinin kablelerini görüyor.

**Yapılacaklar**
- [x] `POST /api/auth/giris`, `POST /api/auth/cikis`, `GET /api/auth/ben` — BCrypt(10) + sunucu tarafı HTTP oturumu (cookie), CSRF açık. **JWT yok** (8 kullanıcı, tek servis)
- [x] Seed kullanıcılar (parolalar yalnızca BCrypt hash'i olarak, düz metin hiçbir yerde durmaz):

  | Kullanıcı adı | Bölge | İlk parola |
  |---|---|---|
  | `ist-avr` | İstanbul Avrupa | `TTI111111` |
  | `ist-and` | İstanbul Anadolu | `TTI222222` |
  | `edirne` | Edirne | `TTI333333` |
  | `ankara` | Ankara | `TTI444444` |
  | `kutahya` | Kütahya-Şırnak | `TTI555555` |
  | `izmir` | İzmir | `TTI666666` |
  | `bursa` | Bursa | `TTI777777` |
  | `yonetici` | — (tümü) | `TTI000000` |

- [x] Yetki: tek `BolgeSuzgeci` kaynağı; süzgeç **SQL `WHERE`'inde** (`where (:bolgeId is null or ... = :bolgeId)`), yönetici → `null`
- [x] Sözleşme içe aktarımı: mevcut `app/src/main/assets/contracts/*.json` → `sozlesme` + `poz` + ilk `fiyat_versiyonu` (`YAYINDA`)
- [x] `GET /api/sozlesmeler`, `GET /api/sozlesmeler/{kod}/pozlar` (yayındaki fiyat versiyonuyla)
- [x] `GET /api/bolgeler`, `GET /api/kapsamlar`
- [x] `GET /api/kableler?ara=` — yalnızca kendi bölgesi; `nosid` + adres üzerinde `ilike` arama + `limit`
- [x] `GET /api/kableler/{nosid}` → `nosid`, `adresA`, `adresB`, `sehirA`, `sehirB`. Başka bölgenin kablesi → **404** (403 değil; varlığını da sızdırmayalım)
- [x] `HataYakalayici`: Türkçe mesajlı, tutarlı hata gövdesi (fail loud, sessiz yutma yok)
- [x] **Para değerlerinin JSON'a string olarak yazılması** (Faz A'dan devredildi). Varsayılanda Jackson `BigDecimal`'i JSON *sayısı* olarak yazar; JavaScript her JSON sayısını `double`'a çevirdiği için 17 haneli ham birim fiyat sessizce bozulur. Boot 4 Jackson 3 (`tools.jackson`) kullanıyor; global bir `BigDecimal → String` serileştirici eklenip aşağıdaki DoD ile kanıtlanacak

### Faz B — plandan sapmalar (gerekçeleriyle)

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| Para JSON'u | Jackson tamamen dışlanacak, yalnızca kotlinx.serialization | **Jackson kaldı + global `BigDecimal → String` serileştirici** | Jackson'ı dışlamak hata gövdeleri ve `Map` yanıtları gibi yolları da kırıyor (kotlinx yalnızca `@Serializable` tipleri yazabilir). Beş satırlık bir serileştirici aynı güvenceyi veriyor: `ParaJsonYapilandirmasiTest` bunu kilitliyor |
| `isGroup` / `isPriced` | — | `@get:JsonProperty` ile ad korundu | Jackson `isXxx` getter'ını `xxx` sayıp öneki kırpıyordu; alan adları `PozItem` ile aynı kalmalı ki mobil ve web şemayı kaçırmasın |
| Null parametreli süzgeç | `(:p is null or ...)` | `(cast(:p as int) is null or ...)` | PostgreSQL tek başına `is null` karşılaştırılan bağlama değişkeninin tipini çıkaramıyor, sorgu 500 ile düşüyordu |
| Oturum cookie'si | — | `SameSite=Strict` + `HttpOnly` + 8 saat | CSRF token'ının yanında ikinci savunma katmanı. TLS geldiğinde (Faz I) `secure: true` eklenecek |
| Sözleşme içe aktarımı | Ayrı bir komut/araç | **`V3__sozlesme_seed.sql`** migration | Yeni modül açmadan, her taze veritabanında birebir aynı veriyi kuruyor. Faz G'deki xlsx yükleme bu yolun yerini alacak |

**DoD**
- [x] Cookie'siz `GET /api/sozlesmeler` → **401**; girişten sonra → 200
- [x] `pozlar` yanıtında `"unitPrice":"648.35852723050107"` — **string ve 14 ondalık tam** *(Jackson dışlamasının kanıtı)*
- [x] `psql` ile aynı satırın `birim_fiyat` değeri `648.35852723050107` *(numeric hassasiyet kanıtı)*
- [x] `ist-avr` → `GET /api/kableler` **309** kayıt · `ankara` → **86** · `yonetici` → **943**
- [x] `ist-avr` oturumuyla Ankara bölgesine ait bir `nosid` istenince **404**
- [x] `./gradlew ktlintCheck test` temiz

---

## FAZ C — Domain Dönüşümü: 3 Belge Türü & Çıktı Varyantı  ·  `Durum: ✅`

**Amaç:** `:core:domain`'i yeni gerçekliğe taşımak. Bu faz **sunucu ve mobilin paylaştığı** koda dokunur; en dikkatli çalışılacak faz burasıdır.

**Yapılacaklar**
- [x] `EntryMode{KESIF, SAHA}` → `DocumentType{FIZIBILITE, KESIF, HAKEDIS}`. **Her (iş, tür) ayrı bir belge kaydıdır**; tek miktar alanı yeterli, "girilmemiş ≠ 0" ayrımı satırın **varlığıyla** korunur
- [x] `DocumentVariant{FIYATLI, TEKLIF}` — müdür isteği: aynı belgeden iki çıktı. `TEKLIF`'te birim fiyat, tutar ve toplam **boş** (firmadan teklif almak için); yeni belge türü değil, **çıktı varyantı**. Builder para alanlarını `null` bırakır, şablonda ek dallanma olmaz
- [x] `DocumentStageRule` — aşama kuralı domain'de test edilebilir biçimde: (a) bir iş `FIZIBILITE → KESIF → HAKEDIS` sırasını atlayamaz, (b) bölgede **açık (tamamlanmamış) bir KESIF varken** yeni iş için KESIF açılamaz
- [x] `DocumentHeader`'a `adresA` / `adresB` eklenir (kable envanterinden gelen uç adresleri)
- [x] `PricingCalculator`: `rowAmount` **dokunulmaz** (`setScale(2, HALF_UP)`); `Totals(kesif, hakedis)` → tek `total`, çünkü artık her tür ayrı belge
- [x] `DocumentFileNamer`: `TTI_{BolgeDosyaKodu}_{Yıl}_{Tür}_{SıraNo}_{Varyant}` → zaman damgası kalkar, ad **kararlı** olur (aynı belgenin ikinci üretimi üzerine yazar)
- [x] `export/PdfTemplate.kt` → `core/domain/document/` (Android bağımlılığı yok, sunucu da kullanacak); testi birlikte taşınır
- [x] Ölen domain dosyaları: `mail/EmailValidator.kt`, `workorder/WorkOrderDraft.kt`
- [x] `:app`'in derlenmesi için **minimum** düzeltmeler (silinen tiplerin kullanım yerleri). Ekran mantığının yeniden yazımı **Faz H'de**

### Faz C — plandan sapmalar (gerekçeleriyle)

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| `:app` kapsamı | "silinen tiplerin kullanıldığı yerlerde **minimum** düzeltme" | mail ekranı, taslak kalıcılığı ve **cihazda PDF/Excel üretimi silindi** | Bu kodun tamamı zaten Faz H'nin silme listesindeydi. Kaldırılan domain tiplerine (`PozEntry`, `Totals`, `WorkOrderDraft`, `EmailValidator`) uyarlamak, iki hafta sonra silinecek koda adaptör yazmak olurdu. Ayrıca CLAUDE.md artık istemcide belge üretimini **yasaklıyor** — uyarlamak yeni kuralı ihlal ederdi |
| `PozEntry(kesif, saha)` | tek miktar alanına indirilecek | **tip tamamen kaldırıldı**, yerine `Map<String, BigDecimal>` | Her belge türü ayrı bir kayıt olduğu için sarmalayıcı tipin taşıdığı bir bilgi kalmadı. "Girilmemiş ≠ 0" ayrımı artık **anahtarın varlığıyla** korunuyor — veritabanındaki satır varlığı semantiğiyle birebir aynı |
| `ExcelExporter` | — | `:app`'ten silindi | Faz G'de sunucuya taşınacak (planlandığında Faz E idi); kaynağı git geçmişinde (`af505d4:app/src/main/kotlin/tr/com/tti/hakedis/export/ExcelExporter.kt`). Kopyasını `:app`'te tutmak, fastexcel bağımlılığını APK'da gereksiz yere yaşatırdı |
| `display: flex` düzeltmesi | — | **PDF fazına bırakıldı** (bugün Faz G) | openhtmltopdf'in flexbox desteklememesi bir PDF üretimi sorunu; şablon motorla birlikte doğrulanacak |

> ⚠️ **Mobil uygulama bu fazdan sonra geçici olarak "kabuk" durumdadır:** üç ekran (iş tanımı → poz tablosu → önizleme) çalışır ve hesap yapar, ancak çıktı üretmez ve veri göndermez. Sunucuya bağlanması **Faz H**'dedir.

**DoD**
- [x] `DocumentStageRule` için 6 senaryo testi (izinli geçişler + reddedilen geçişler)
- [x] `TEKLIF` varyantı testi: `unitPrice`, `amount`, `total` alanları `null`, poz listesi ve miktarlar yerinde
- [x] `documentNo` biçim testi: `IST-AVR_2026_KESIF_003`
- [x] Kuruş testi korunuyor: `648.35852723050107 × 3` → `1945.08` → `"1.945,08 ₺"`
- [x] `./gradlew ktlintCheck test` temiz, `:app` derleniyor

---

## FAZ D — İş + Belge API'leri, Numaralandırma, Aşama Kuralı  ·  `Durum: ✅`

**Amaç:** Sistemin kalbi: çakışmasız belge numarası, fiyat dondurma, aşama kuralı, bölge yetkisi.

**Yapılacaklar**
- [x] `V4__is_belge_kurallari.sql`: `is_kaydi_bolge_sp_ix` (unique **değil**, bkz. D1) + `is_kaydi.yil` (D4) + `belge_acik_tur_ux`
- [x] `GET /api/isler?spNo=` süzgeci — istemci iş açmadan önce aynı SP'yi sorabilsin (D1/2)
- [x] `POST /api/isler` — kable envanterinden seçim; yanıtta `uyarilar` dizisi (`AYNI_SP_VAR`, bkz. D1/3); `is_kaydi.bolge_id = kable.bolge_id` sunucuda doğrulanır (uyuşmazsa 409); `adres_a`/`adres_b` işe **dondurularak kopyalanır**
- [x] `GET /api/isler` — bölge süzgeçli liste (her iş kendi belgelerinin durumunu taşır)
- [x] `POST /api/isler/{id}/belgeler` — tür seçimi, aşama kuralı, numara üretimi, `fiyat_versiyonu_id` çivilenmesi
- [x] `BelgeNumaraUretici` — tek atomik ifade:
      `insert into belge_sayac ... on conflict (bolge_id, yil, tur) do update set son_no = son_no + 1 returning son_no`
- [x] `PUT /api/belgeler/{id}/satirlar` — miktar girişi; yanıt satır tutarlarını **ve** toplamı döner (istemci hesaplamaz)
- [x] `POST /api/belgeler/{id}/tamamla` — `TASLAK → TAMAMLANDI`
- [x] **`DocumentStageRule` genişletilmesi** (bkz. D3): tür içinde iş sırası kuralı + yeni ret gerekçesi; `:core:domain` testleri güncellenir
- [x] Aşama kuralı DB'de kısmi unique index ile: `unique (bolge_id, tur) where durum='TASLAK'` → yarışla bile ikinci açık belge oluşamaz
- [x] **Kilit çıkışı** (D5, faz sırasında eklendi): `V5__iptal.sql` + `POST /api/isler/{id}/iptal` (sırayı açar) ve `POST /api/belgeler/{id}/iptal` (taslağı çöpe atar); ikisi de **yalnızca yönetici**, gerekçe zorunlu

### Faz D — plandan sapmalar (gerekçeleriyle)

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| Migration adı | `V4__sp_no_indeks.sql` | **`V4__is_belge_kurallari.sql`** | Migration üç işi birlikte yapıyor: SP arama indeksi, `is_kaydi.yil` (D4) ve genelleştirilmiş açık-belge kısıtı. Ad yalnızca birini söylerse yanıltıcı olur |
| Açık belge kısıtı | `unique (bolge_id) where tur='KESIF' and durum='TASLAK'` | **`unique (bolge_id, tur) where durum='TASLAK'`** | D3(b) kuralı üç türe genişledi; "bölgede türden tek açık belge" onun doğal sonucu. Yalnızca KESIF'i korumak, FIZIBILITE ve HAKEDIS'i yarış durumunda savunmasız bırakırdı |
| `StageRejection` | `BOLGEDE_ACIK_KESIF_VAR` + yeni gerekçe | **`BOLGEDE_ACIK_KESIF_VAR` kaldırıldı**, yerine `ONCEKI_IS_TAMAMLANMAMIS` | Yeni kural eskisini kapsıyor (bkz. D3 notu); ikisini birlikte tutmak ölü bir enum değeri ve iki ayrı mesaj yolu bırakırdı |
| İşin yılı | *(planda yok)* | `is_kaydi.yil` **sütunu** eklendi | D4 "yıl işin açıldığı yıldır" diyor. `extract(year from olusturma)` oturumun saat dilimine bağlı: yılbaşı gecesi belge numarasını kaydırabilir. Sütun, yılı tek ve tartışmasız bir yerde tutuyor |
| `POST /api/isler` isteği | *(bölge alanı belirsiz)* | `bolgeKodu` **zorunlu** | Ekran 1 bölgeyi zaten seçtiriyor (CLAUDE.md §9). Zorunlu olması, plandaki "bölge uyuşmazlığı → 409" kontrolüne gerçek bir işlev veriyor; alan olmasa kontrol de olmazdı |
| Doğrulama hataları | *(belirtilmemiş)* | `GecersizIstekException` → **400** | Boş alan/tanımsız kod bir iş kuralı ihlali (409) değil, isteğin kendisinin hatası. `WorkOrderValidator` (domain) kararı veriyor, sunucu Türkçeye çeviriyor |
| `DuplicateKeyException` | *(belirtilmemiş)* | **409'a çevriliyor** | Yarışı veritabanı durdurduğunda 500 dönmek yanıltıcı: istek reddedildi, sistem bozulmadı |
| Boş belge | *(belirtilmemiş)* | `tamamla` **409** veriyor | Miktarı olmayan belge boş PDF üretir **ve** aşama kuralında "tamamlandı" sayılıp sonraki işin sırasını açar |
| DoD "10 paralel → 1..10" | tek adımda API'den | **iki ayrı kanıta bölündü** | D3(b) aynı (bölge, tür) için ikinci açık belgeyi zaten yasakladığı için API'den 10 belgeyi paralel açmak *tanım gereği* imkânsız. Kanıt ikiye ayrıldı: (a) sayaç ifadesinin kendisi 10 eşzamanlı bağlantıda `1..10` üretiyor, (b) 10 eşzamanlı API isteğinden 1'i geçip 9'u reddedilirken sayaçta **boşluk kalmıyor** — asıl risk buydu (rollback numarayı tüketirse seri boşluklu olur). Ayrıca sıralı 10 belge `001..010` üretiyor |
| Doğrulama | *(elle curl)* | **`deploy/dogrula-faz-d.sh`** (78 kontrol) | Faz A/B'deki `dogrula.sh` kalıbı sürdürüldü. `:server` testleri gerçek PostgreSQL istiyor; Testcontainers eklemek yeni bir bağımlılık olurdu (sorulmadan eklenmez) |
| İptal uçları | *(planda yok; D5 "çıkış yolu yok" diyordu)* | **`V5__iptal.sql` + iki uç eklendi** | Kullanıcı, kuralı teyit ettikten sonra kilit için tavsiye istedi ve A seçeneğini (şimdi ekle, yönetici yetkisi) onayladı. Gerekçe: silme ucu olmadığı için yanlış iş açmak kaçınılmaz; Faz F arayüzü iptal düğmesiyle birlikte tasarlanırsa arayüz ikinci kez elden geçmez. D5 kararı bu yönde güncellendi |

**DoD**
- [x] **10 paralel** `POST .../belgeler` → sıra no'lar `1..10`, **boşluk yok, çift yok**
      *(iki kanıt — bkz. sapmalar tablosu: paralel sayaç `1..10`; 10 eşzamanlı istekten 1 geçip 9 reddedilince sayaç `1`'de kalıyor; sıralı 10 belge `001..010`)*
- [x] Aynı bölgede 2. açık KESIF denemesi → **409** + Türkçe mesaj *("Bu bölgede keşif sırası SP-… işinde (#…); önce onun keşif belgesini tamamlayın.")*
- [x] FIZIBILITE olmadan KESIF açma denemesi → **409**
- [x] Aynı bölgede aynı SP No ile ikinci iş açılabiliyor: `POST /api/isler` → **201** ve
      `uyarilar` içinde `{"kod":"AYNI_SP_VAR","adet":1}` *(kısıt değil uyarı — D1)*
- [x] Bölge A sorumlusu, bölge B belgesini `GET` → **404** *(yazma uçları da 404)*
- [x] Yönetici `poz_fiyat`'ı değiştirdikten sonra **eski** belgenin toplamı **değişmiyor** *(fiyat dondurma kanıtı)*
- [x] API'den girilen `3 × 648.35852723050107` → `"1945.08"` / `"1.945,08 ₺"`
- [x] `./gradlew ktlintCheck test` temiz
- [x] `bash deploy/dogrula.sh` (Faz A/B) hâlâ temiz — gerileme yok
- [x] **İptal (D5):** bölge sorumlusu iptal denemesi → **403**; gerekçesiz iptal → **400**;
      iş iptali sonrası bekleyen iş belge açabiliyor → **201**; belge iptali sırayı **açmıyor** → 409;
      tamamlanmış belge iptal edilemiyor → **409**; iptal edilmiş iş listede kalıyor, izi (kim/ne zaman/neden) tam

---

## FAZ E — Onay Akışı, Durum Makinesi & İşe Özel Fiyat Listesi  ·  `Durum: ✅`

**Amaç:** Müdürün bildirdiği gerçek akışı sunucuda çalıştırmak: fizibilite gönderilir → satın alma
onayı beklenir → yönetici pazarlık fiyatlarını yükleyip onaylar → keşif o fiyatlarla girilir → hakediş
gönderilir. Kararlar aşağıdaki **FAZ E KARARLARI** bölümünde.

**Yapılacaklar**
- [x] `DocumentStatus` → beş değer (`TASLAK`, `ONAY_BEKLIYOR`, `ONAYLANDI`, `TAMAMLANDI`, `IPTAL`) + `duzenlenebilirMi` / `acikMi`; `DocumentType.kapanisDurumu`
- [x] `DocumentStatusText` — `(tür, durum) → Türkçe metin`, tek kaynak (sunucu + web + mobil aynı metni gösterir)
- [x] `DocumentStageRule`: önceki aşama testi `kapanisDurumu` ile; bölge sırası **yalnızca KESIF/HAKEDIS**; yeni ret gerekçesi `ONCEKI_ASAMA_ONAY_BEKLIYOR`
- [x] `V6__ise_ozel_fiyat.sql`: `fiyat_versiyonu.is_id` + `kaynak_dosya_adi`/`kaynak_ozet`, yayın indeksinin ikiye bölünmesi, `is_kaydi_kable_ix`, `belge_acik_tur_ux` daraltması, `belge.durum` check'i beş değere
- [x] `POST /api/belgeler/{id}/gonder` (eski `tamamla`) — türe göre `ONAY_BEKLIYOR` ya da `TAMAMLANDI`
- [x] `POST /api/belgeler/{id}/onayla` — **yalnızca yönetici**, multipart fiyat listesi; fiyat versiyonu + durum **tek transaction**
- [x] `GET /api/belgeler/{id}/fiyat-sablonu.xlsx` — kalıcı kimlik sütunlu, fiyatı boş giriş şablonu (E4/Katman 1)
- [x] `POST /api/belgeler/{id}/fiyat-listesi/onizleme` — hiçbir şey yazmadan eşleştirme + fark raporu
- [x] `FiyatListesiEslestirici` — kimlik sütunu varsa ondan, yoksa `satir_kodu` + `poz_no`/`ad`/`birim` teyidiyle; sapmada 400 + tam liste. **Saf fonksiyon, birim testleriyle kilitlenir**
- [x] Referanstan 10 kat sapan fiyat → önizlemede **uyarı** (engellemez)
- [x] `SozlesmeSorgulari`: `pozlar()` ve `yayindakiFiyatVersiyonuId()` sorgularına **`and fv.is_id is null`** *(atlanırsa sessiz veri bozulması)*
- [x] `GET /api/isler/{id}/pozlar` — o işin pazarlık fiyatlarıyla poz listesi (keşif/hakediş ekranı)
- [x] `AYNI_KABLE_VAR` uyarısı + `GET /api/isler?kableNosid=` süzgeci *(aynı kabloda birden fazla iş — E5)*
- [x] `GET /api/belgeler` liste ucu: belge + durum + durum metni + bölge + sorumlu + kable/ek aralığı; toplam **domain hesabıyla**, SQL `sum()` ile değil
- [x] `ContractXlsxParser` iki kusuru düzeltilir (FORMULA mesajı, `"1234.56"` → `123456` hatası) + birim testler
- [x] `dogrula-faz-d.sh` yamalanır; `dogrula-faz-e.sh` yazılır

### Faz E — plandan sapmalar (gerekçeleriyle)

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| Durum metinleri | `:core:domain`'de `DocumentStatusText` | **`:server`'da `BelgeMetinleri`** | Domain'de kullanıcıya görünen metin bulunmaz (CLAUDE.md §7); `DocumentStageRule` ve `Region`/`Scope` de aynı gerekçeyle etiket taşımıyor. Metin yine **tek yerde**: sunucu `durumMetni`'ni yanıtta gönderir, web ve mobil kendi içinde yazmaz |
| Bölge sırası süzgeci | yalnızca çağıran taraf fizibilitede sorguyu atlar | **`DocumentType.bolgeSirasinaTabiMi` domain'e eklendi** | Kuralı çağırana bırakmak, yanlışlıkla değer geçirilirse kuralı sessizce değiştirirdi. Karar domain'de, test edilebilir yerde |
| Fiyat şablonunda kilitli sütun | `POZ_KIMLIK` kilitli/gizli | **kilit yok, sütun görünür** | `fastexcel` sayfa koruması sunmuyor; kilit zaten kaldırılabilir bir işaret. Sütun silinirse eşleştirme Katman 2'ye düşüyor ve kayma yakalanıyor — savunma kilide bağlı değil |
| `belge.durum` tür tutarlılığı | *(planda yok)* | **`belge_durum_tur_tutarli` kısıtı eklendi** | `ONAYLANDI` yalnızca fizibilitede, `TAMAMLANDI` yalnızca keşif/hakedişte anlamlı. Kısıt olmadan bir keşfin "onaylandı" olması aşama kuralını (kapanış durumu türe bağlı) sessizce yanlış çalıştırırdı |
| Metin fiyat çözümü | "STRING dalı düzeltilecek" | **ayrı `fiyatMetniniCoz` + bozuk gruplama reddi** | Kural para hatası üretiyor, testle kilitlenmesi gerekiyordu. Ayrıca `12,34,56.78` gibi bozuk gruplama artık sessizce kabul edilmiyor — düzeltilen 100× hatasının aynı sınıfı |
| Doğrulama fixture'ları | `deploy/ornek/` altında depoya girer | **`server/build/ornek/` altında testle üretilir** | Makinede `openpyxl` yok (PEP 668 kilidi) ve sisteme paket kurmak doğrulama betiğinin işi değil. `FiyatSablonuTest` fixture'ları gerçek üreticiyle üretiyor; üstelik test şablon↔okuyucu sözleşmesini de kanıtlıyor. Ayrıca `deploy/xlsx-fiyat-doldur.py` (yalnızca stdlib) betiğin indirdiği şablonu doldurabiliyor, böylece **gerçek sözleşmeyle uçtan uca** yükleme kanıtlanıyor |
| `dogrula-faz-d.sh` sıfırlaması | `truncate ... restart identity` | **sıralı `delete`** | `fiyat_versiyonu.is_id → is_kaydi` yabancı anahtarı cascade'siz truncate'i düşürüyor, `cascade` ise V3'ün **referans fiyatlarını siliyor**. İkisi de kabul edilemez; bu tuzak geliştirme sırasında bir kez gerçekleşti ve seed elle geri yüklendi |


**DoD**
- [x] `pozlar` ucu **hâlâ 95 satır**, `unitPrice` `"648.35852723050107"` *(`is_id` süzgecinin kanıtı)*
- [x] Fizibilite `gonder` → **200**, `durum=ONAY_BEKLIYOR`, `durumMetni="Satın Alma Onayı Beklemede"`
- [x] Onay bekleyen belgeye miktar girişi → **409**; yeniden gönderme → **409**
- [x] Onaydan **önce** keşif açma → **409** + "satın alma onayı bekliyor"
- [x] Aynı bölgede **iki fizibilite birlikte** onay bekliyor → ikisi de **201** *(karar E3)*
- [x] Bölge sorumlusu `onayla` dener → **403**
- [x] Aynı kablo üzerinde ikinci iş → **201** + `{"kod":"AYNI_KABLE_VAR","adet":1}`; `?kableNosid=` ikisini döner
- [x] **Birim test:** satırları karıştırılmış girdi kimlik sütunuyla **doğru kalemlere** eşleşiyor
- [x] **Birim test:** kimlik sütunu olmayan, bir satırı kaydırılmış girdi **tespit ediliyor**
- [x] Bozuk Excel (kalem fiyatsız / grup satırında fiyat / formül) → **400**, `fiyat_versiyonu` sayısı **değişmemiş**
- [x] Referansın 10 katı fiyat → önizlemede **uyarı**, işlem engellenmiyor
- [x] Önizleme çağrısından sonra `fiyat_versiyonu` ve `poz_fiyat` sayıları **değişmemiş**
- [x] Geçerli Excel ile `onayla` → **200**, `durum=ONAYLANDI`, `poz_fiyat` **86 satır**, `kaynak_ozet` dolu
- [x] Aynı işe **ikinci** fiyat listesi → **409**
- [x] `GET /api/isler/{id}/pozlar` **pazarlık** fiyatlarını döner, referans fiyatı değil
- [x] Keşif `gonder` → `"1. Keşif Tamamlandı"`; hakediş `gonder` → `"Hakediş İşlemi Gönderildi"`
- [x] Keşif/hakedişte bölge sıra kuralı **hâlâ çalışıyor** → 409 + bekleyen iş mesajı
- [x] Referans fiyat elle değiştirilince hem fizibilite hem keşif toplamı **değişmiyor**
- [x] `GET /api/belgeler`: bölge sorumlusu yalnızca kendi bölgesini görüyor
- [x] `SIFIRLA=1 bash deploy/dogrula-faz-d.sh` **yamalı hâliyle temiz** — *80 kontrol, 0 düşen*
- [x] `bash deploy/dogrula-faz-e.sh` temiz — *65 kontrol, 0 düşen*
- [x] `bash deploy/dogrula.sh` (Faz A/B) hâlâ temiz — gerileme yok
- [x] `./gradlew ktlintCheck test` temiz — *146 test, 0 hata*

---

## FAZ F — İmza & Ek Dosyalar (EK1/EK2)  ·  `Durum: ✅`

**Amaç:** Hakediş adımının eksik parçaları: güzergâh dosyası, saha görselleri ve ekranda çizilen imza.
*(Faz E'de hakediş gönderimi imzasız/eksiz kabul edilir — akış web'e kadar kullanıcıya açılmıyor, bu
geçici boşluk bilinçli.)*

**Yapılacaklar**
- [x] `V7__imza_ek.sql`: `belge_imza(belge_id pk, depo_yolu, boyut_bayt, imzalayan_id, imzalayan_ad_soyad, tarih)` + `ek_ek1_ux` (EK1 tekildir) + `ek.yukleyen_id`
- [x] **Ayrı yükleme kökü:** `YUKLEME_KOK` env + `${YUKLEME_KOK}:/yukleme` bind mount. `ARSIV_KOK` üretilen çıktının **tek yönlü aynası**; kullanıcının yüklediği dosya asıl veridir, karışmamalı. `ek.depo_yolu` köke **göreli** tutulur
- [x] `SaglikDenetleyici`'ye `yuklemeYazilabilir`
- [x] `POST /api/belgeler/{id}/ekler` (multipart + `tur`), `GET .../ekler`, `GET /api/ekler/{id}`, `DELETE /api/ekler/{id}` — hepsi yalnızca **hakediş taslağı**, bölge süzgeci SQL'de
- [x] `POST /api/belgeler/{id}/imza` — PNG, magic byte kontrolü, ~200 KB sınır; `imzalayan_ad_soyad` **dondurulur**. Ayrıca `GET .../imza` (bilgi) ve `GET .../imza/goruntu` (PNG)
- [x] Doğrulama **uzantıdan** (MIME tutarsız — §12/3): EK1 `.kmz`/`.kml`, EK2 `.jpg`/`.jpeg`/`.png`; kural `AttachmentKind`'a taşındı, `RouteFileValidator` onu çağırıyor
- [x] `spring.servlet.multipart` sınırları + `MaxUploadSizeExceededException` → **413**
- [x] Hakediş gönderme sıkılaşır: **EK1 ve imza zorunlu**, yoksa 409
- [x] `deploy/dogrula-faz-f.sh` (49 kontrol) + `EkYolu` / `PngDogrulayici` birim testleri

### Faz F — plandan sapmalar (gerekçeleriyle)

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| Ek kabul eden belge | "hepsi yalnızca `TASLAK`" | **yalnızca HAKEDIS taslağı** | Akış eklerden ve imzadan yalnızca hakediş adımında söz ediyor; Faz H'de fizibilite/keşif ekranlarında ek alanı da yok. Fizibiliteye ek yüklenebilse, Faz G'de "bu belgenin ekleri PDF'te görünmeli mi" sorusu karşılıksız bir dal açardı. Kullanıcıya soruldu, onaylandı. Genişletme kolay, geri alma değil |
| İmza sütun adı | `dosya_yolu` | **`depo_yolu`** | `ek` tablosundaki adla aynı: ikisi de `YUKLEME_KOK`'e göreli yollar. Farklı adlar aynı kavramı iki şey gibi gösterirdi |
| `ek.yukleyen_id` | *(planda yok)* | **eklendi** | `ek` tablosunda kim yükledi izi yoktu. Yanlış güzergâh dosyası sonradan tartışma konusu olduğunda tek kaynağı bu sütun; şimdi eklemek bir satır, sonra eklemek yeni migration |
| Dosya adı | *(belirtilmemiş)* | `EK1_{ekId}_{sadeleştirilmiş ad}` | Tabletten gelen görsellerin adı sıklıkla aynı (`IMG_0001.jpg`); kimliksiz adlandırmada ikinci görsel birincinin üzerine yazar ve **veri sessizce kaybolur**. `EkYoluTest` bunu kilitliyor |
| İmza silme ucu | *(planda yok)* | **eklenmedi** | İmzasız hakediş gönderilemiyor; imzayı silmek yalnızca belgeyi gönderilemez hâle getirirdi. Yeniden çizilen imza aynı satırın ve aynı dosyanın üzerine yazıyor (upsert) |
| `GET .../imza` | *(planda yok)* | **eklendi** (bilgi + görüntü) | Yalnızca `POST` olsaydı web "imza atıldı mı" sorusunu cevaplayamaz, kullanıcı her açılışta yeniden imzalamak zorunda kalırdı |
| PNG doğrulaması | "magic byte kontrolü" | ayrı `PngDogrulayici` + test | İmza PDF'e gömülecek (Faz G): adı `imza.png` olan bir PDF sisteme girerse hata yükleme anında değil, aylar sonra **belge üretilirken** ortaya çıkar |
| `dogrula-faz-d/e.sh` | *(dokunulmayacaktı)* | **ikisi de yamalandı** | (a) Hakediş gönderimi artık EK1+imza istiyor: Faz E betiği o ön koşulu sağlamadan gönderiyordu ve 409 alıyordu — gerçek bir gerileme değil, kuralın kendisi. (b) İkisinin sıfırlama bloğu `fiyat_versiyonu`'nu belgelerden **önce** siliyor ve `belge_fiyat_versiyonu_id_fkey` ihlali basıyordu; sıfırlama yarım kalıyordu |

**DoD**
- [x] İmzasız hakediş → **409**; EK1'siz hakediş → **409**
- [x] İkinci EK1 → **409**; `.txt` EK1 → **400**; 15 MB dosya → **413** *(ve 413 sonrası ek sayısı değişmiyor)*
- [x] Yüklenen dosya **host klasöründe** görünüyor, `depo_yolu` göreli (`BÖLGE/yıl/belgeNo/`)
- [x] Tamamlanmış belgeye ek → **409**; başka bölgenin eki → **404** *(listeleme, indirme ve silme için ayrı ayrı)*
- [x] Adı `.png` ama içeriği PDF olan imza → **400**; 300 KB imza → **400**; reddedilenler hiçbir şey yazmıyor
- [x] İmzalayan adı **dondurulmuş**: kullanıcı yeniden adlandırılınca belgedeki ad değişmiyor
- [x] Ek silinince dosya **diskten de** siliniyor
- [x] `bash deploy/dogrula-faz-f.sh` temiz — *49 kontrol, 0 düşen*
- [x] `dogrula.sh` (A/B), `dogrula-faz-d.sh` (80), `dogrula-faz-e.sh` (65) hâlâ temiz — gerileme yok
- [x] `./gradlew ktlintCheck test` temiz

---

## FAZ G — Sunucuda PDF + Excel Üretimi & Belge Aynası  ·  `Durum: ✅`

**Amaç:** Çıktı üretimi cihazdan sunucuya taşınır; tek şablon, tek çıktı, iki varyant. Üretilen belgeler arşiv klasörüne de kopyalanır.

**Yapılacaklar**
- [x] `PdfUretici` — **openhtmltopdf 1.1.65** (LGPL 2.1; iText **AGPL** olduğu için yasak). `PdfTemplate`in ürettiği XHTML'den PDF
- [x] `PdfTemplate` düzeltmeleri: `display: flex` → `<table>` (motor flexbox bilmiyor), XHTML kapanışları (`<meta/>`, `<img/>`), `xmlns` + `<title>`
- [x] Türkçe glifler için `DejaVuSans.ttf` **ve** `DejaVuSans-Bold.ttf` jar'a gömülür (base-14 fontlar `ş/ğ/İ` taşımaz; motor sentetik kalınlaştırma yapmadığı için kalın yüz ayrıca gerekli)
- [x] `ExcelUretici` — `Context` / `R.string` / `labelRes` bağı çözüldü, etiketler dışarıdan verilir. Sözleşmenin **tüm satırlarını orijinal sırayla** yazma kuralı korunur
- [x] `GET /api/belgeler/{id}/pdf?varyant=FIYATLI|TEKLIF`, `.../excel?varyant=`
- [x] **Belge aynası:** üretilen PDF+Excel `{ARSIV_KOK}/{BÖLGE}/{YIL}/{TÜR}/` altına **async** kopyalanır (sınırlı `ScheduledExecutorService`). Kopya başarısız olursa belge üretimi **başarısız sayılmaz**: `WARN` loglanır ve 30 sn sonra bir kez daha denenir. Kaynak doğruluk yeri DB, arşiv tek yönlü ayna
- [x] Etiket metinleri `application.yml`'den (`CiktiEtiketleri`, `@ConfigurationPropertiesScan`)
- [x] **İmza bloğu:** `DocumentModel`'e `signature` (görsel + ad + tarih), `PdfTemplate`'e `signatureBlock` — logo gibi base64 gömülür
- [x] **Fizibilite iki varyantla:** `FIYATLI` (yöneticinin pazarlık tabanı) ve `TEKLIF` (aracı firmaya gidecek, fiyat sütunları boş)
- [x] `deploy/dogrula-faz-g.sh` (34 kontrol) + `PdfUreticiTest` / `ExcelUreticiTest` / `PdfTemplate` testleri

### Faz G — plandan sapmalar (gerekçeleriyle)

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| openhtmltopdf grubu | *(belirtilmemiş)* | **`io.github.openhtmltopdf` 1.1.65** | Eski `com.openhtmltopdf` grubu 1.0.10'da durmuş; proje yeni gruba taşınmış. Java paket adları (`com.openhtmltopdf.*`) değişmedi |
| Gömülen font | `DejaVuSans.ttf` | **+ `DejaVuSans-Bold.ttf`** | Motor sentetik kalınlaştırma yapmıyor: tek yüz gömülse `<th>` ve grup satırları normal ağırlıkta basılır, tablo düzleşirdi |
| Motor log'u | *(planda yok)* | **`XRLog.setLoggingEnabled(false)`** | Belge başına onlarca satır INFO döküyordu ve arşiv `WARN`'ını görünmez kılıyordu — yani planın "WARN loglanır" güvencesini pratikte bozuyordu |
| Excel sütun düzeni | "kaynak 8 sütun düzeni korunur" | **türe göre düzen** (`ExcelDuzeni`) | Kullanıcı kararı (bkz. yukarıdaki tablo): fizibilite 6 sütun, keşif ve hakediş kaynağın 8 sütunu |
| Çıktı modeli kaynağı | *(belirtilmemiş)* | `belge_satir.birim_fiyat` → `poz_fiyat` → `0` **öncelik sırası** | Miktarı girilmiş satır dondurduğu kopyayı kullanır (fiyat dondurma); girilmemiş satır yine de "Br Fiyat" göstermeli ki `TEKLIF` çıktısında firma onu da fiyatlayabilsin |
| PDF metin kanıtı | DoD'de `pdftotext` | **`PdfUreticiTest` + PDFBox** | Makinede `pdftotext` yok ve doğrulama betiği sisteme paket kurmaz (Faz E'deki `openpyxl` kararının aynısı). PDFBox zaten bağımlılık olarak geliyor; test metni gerçek bir çıkarıcıyla okuyor. Betik bu kontrolün nerede olduğunu **basarak** atlıyor, sessizce değil |
| Arşiv erişilemezlik testi | *(yöntem belirtilmemiş)* | hedef klasörün yerine **dosya** konur | İzinle engellemek (`chmod`) bind mount'ta host/konteyner kimlik eşlemesine bağlı ve makineye göre değişiyor; dosya koymak her ortamda aynı sonucu veriyor |
| Taslak belgenin çıktısı | *(belirtilmemiş)* | **serbest**, arşive de kopyalanır | Yönetici onay kuyruğundaki fizibiliteyi indirmek zorunda (E akışı) ve o belge `ONAY_BEKLIYOR`. Dosya adı kararlı olduğu için nihai sürüm taslağın üzerine yazar |

> ⚠️ **Layout gözle doğrulanmadı.** PDF'in içeriği (metin, tutar, Türkçe glifler, imza bloğu) testle
> kanıtlandı; sayfa yerleşimini görsel olarak kontrol etmek için makinede PDF görüntüleyici aracı
> (`pdftoppm`) yok. Üretilen örnekler `deploy/arsiv/IZMIR/2026/` altında duruyor — ilk fırsatta bir
> göz atılması, özellikle `display: flex` yerine konan tablo hizalaması için iyi olur.
- [ ] Excel'de bilgi bloğu **tablonun üstünde** *(kullanıcı kararı, 30 Temmuz 2026)*
- [ ] EK2 görselleri PDF'e **basılmaz**, yalnızca adedi yazılır *(kullanıcı kararı)*
- [ ] **Excel sütun düzeni türe göre** *(kullanıcı kararı, 30 Temmuz 2026)*:

  | Belge | Sütunlar |
  |---|---|
  | `FIZIBILITE` | Poz · İş Kalemi · Birim · Br Fiyat · **Fizibilite Miktar** · **Fizibilite Tutar** (6 sütun) |
  | `KESIF` | kaynak **8 sütun**, olduğu gibi |
  | `HAKEDIS` | kaynak **8 sütun**, olduğu gibi (+ Sapma, Faz K) |

  **Keşif ve hakediş aynı düzeni paylaşır** — kullanıcı kararı. Kaynak şablon zaten bir keşif↔hakediş
  karşılaştırma sayfası; ikisini ayırmak, aynı işin iki belgesini yan yana koyan kişiyi sütun eşlemeye
  zorlardı. Keşifte "Saha Uygulama" ve "Kesin Hakediş Tutar" boş kalır: henüz yaşanmamış aşamayı
  gösteren boşluk **anlamlıdır**, kaldırılmaz.

  Fizibilite ayrışır çünkü kaynaktaki hiçbir miktar sütunu ona karşılık gelmiyor ("1. Keşif Talep"
  kelime anlamıyla keşif talebi, fizibilite ondan önceki aşama). Asıl gerekçe `TEKLIF` varyantı:
  o dosya **aracı firmaya gidiyor** ve boş bırakılmış "Saha Uygulama" / "Kesin Hakediş Tutar"
  başlıkları firmanın yanlış hücreye yazması için açık davetiye olurdu.

  Başlık metinleri `application.yml`'den gelir (hardcoded string yasağı sunucuda da geçerli).
  ⚠️ Bu karar açık soru **12**'ye bağlıdır: çıktı başka bir yere aktarılıyorsa düzen bozulmamalı.

**DoD**
- [x] PDF metninde `1.945,08 ₺` bulunuyor *(`PdfUreticiTest`, PDFBox `PDFTextStripper` ile)*
- [x] Türkçe karakterler metin olarak aranabiliyor — `İstanbul`, `Sözleşme`, `HAVAİ GÜZERGAH İMALATLARI` *(font gömme kanıtı)*
- [x] Excel'de tutar hücresi `<v>1945.08</v>` — gerçek `number` (`t="n"`), `<f>` yok
- [x] `TEKLIF` varyantında aynı hücre **boş**, sütun düzeni bozulmamış, miktar yerinde
- [x] Dosya adı: `TTI_IZMIR_2026_KESIF_001_TEKLIF.pdf` *(Content-Disposition'dan doğrulandı)*
- [x] Arşiv klasöründe `IZMIR/2026/KESIF/` altında PDF **ve** Excel duruyor; klasörler kendiliğinden oluşmuş; kopya indirilenle **birebir**
- [x] Arşiv yolu erişilemez yapıldığında belge üretimi **çalışmaya devam ediyor** (200), log'da `WARN` var
- [x] Fizibilite kendi sütunlarını taşıyor, keşif sütunlarını **taşımıyor**; keşif kaynağın 4 miktar/tutar başlığını da taşıyor
- [x] Sözleşmenin **son pozu** da yazılıyor (tablo kırpılmıyor); miktarı girilmemiş kalemler Excel'de duruyor
- [x] Fiyat elle değiştirilse bile çıktı tutarı **değişmiyor** *(dondurma çıktıya da yansıyor)*
- [x] Başka bölge PDF/Excel indiremiyor → **404**; yönetici indirebiliyor
- [x] `bash deploy/dogrula-faz-g.sh` temiz — *34 kontrol, 0 düşen*
- [x] `dogrula.sh` (A/B), `dogrula-faz-d.sh` (80), `dogrula-faz-e.sh` (65), `dogrula-faz-f.sh` (49) hâlâ temiz — gerileme yok
- [x] `./gradlew ktlintCheck test` temiz — *178 test, 0 hata*

---

## FAZ H — Web Arayüzü: Bölge Sorumlusu  ·  `Durum: ✅`

**Amaç:** Bölge sorumlusunun bilgisayarından akışı uçtan uca yürütebilmesi.

**Yapılacaklar**
- [x] `web/` — Vite + React + TypeScript; `web/dist` jar'ın `static/` klasörüne gömülür (tek servis, tek port, CORS yok, nginx yok)
- [x] Giriş ekranı, oturum yönetimi
- [x] **Liste ekranı: satır satır belge + durum + bölge + sorumlu** (`GET /api/belgeler`); kable + ek aralığı de görünür, aynı kablodaki işler karışmaz
- [x] İş oluşturma: **referans sözleşme** seçimi, kable arama, `AYNI_SP_VAR` / `AYNI_KABLE_VAR` uyarı şeridi *(onay diyaloğu yok — D1/5)*
- [x] **Fizibilite ekranı:** birim fiyatlar salt okunur, yalnızca miktar girilir → "Onaya Gönder" → "Satın Alma Onayı Beklemede"
- [x] **Keşif ekranı:** pazarlık fiyatları (`GET /api/isler/{id}/pozlar`), miktar → gönder
- [x] **Hakediş ekranı:** miktar + EK1/EK2 + **imza tuvali** (fare ve dokunmatik) → gönder
- [x] Poz tablosu girişi: debounce'lu `PUT`, **toplam sunucudan**
- [x] Aşama kuralı hatalarının anlaşılır gösterimi; **"sıra sende / sırada SP-… var"** göstergesi
- [x] Önizleme + PDF/Excel indirme (iki varyant)
- [x] **Sunucu tarafı asgari değişiklikler** (faz sırasında ortaya çıktı): statik dosyalar için
      güvenlik istisnası, `bootJar`a web derleme adımı, poz ve belge satırı yanıtlarına
      `unitPriceGosterim` / `birimFiyatGosterim`
- [x] `deploy/dogrula-faz-h.sh` (73 kontrol)

### Faz H — plandan sapmalar (gerekçeleriyle)

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| Yönlendirme | *(belirtilmemiş)* | **hash tabanlı, kütüphanesiz** (`yonlendirme.ts`, ~30 satır) | Yol tabanlı yönlendirme sunucuda "bilinmeyen yolu `index.html`'e döndür" kuralı ister; o kural Spring Security zincirine ikinci bir istisna listesi ekler ve yanlış yazıldığında `/api` altındaki gerçek 404'leri HTML'e çevirir. Hash sunucuya hiç ulaşmıyor: tek bir statik dosya kuralı yetiyor. Bağlantı paylaşımı ve yenileme yine çalışıyor (`#/belgeler/42`) |
| Bağımlılıklar | React + TS + Vite | **yalnızca `react` + `react-dom`** (68 paket) | Yönlendirme, durum yönetimi ve HTTP istemcisi için kütüphane eklenmedi; beş ekranın ihtiyacı elde yazılan üç küçük dosya. §1/7 ve "spekülatif soyutlama" yasağı doğrudan bunu söylüyor |
| Birim fiyat gösterimi | *(planda yok)* | **`unitPriceGosterim` / `birimFiyatGosterim` sunucuya eklendi** | Poz tablosu "Br Fiyat" sütununu göstermek zorunda, ama sunucu yalnızca ham `648.35852723050107` gönderiyordu. Web'de biçimlendirmek para formatlamasını ikinci kez yazmak olurdu (pazarlıksız kural 1). Şimdi `MoneyFormatter` tek kaynak, PDF ile web birebir aynı metni gösteriyor. `ParaJsonYapilandirmasiTest` bunu kilitliyor |
| Miktar girişi | *(belirtilmemiş)* | web **`QuantityParser`'ın dar bir alt kümesini** kabul eder | `QuantityParser` (domain) `1.234,5` gibi binlik ayırıcılı yazımı da çözüyor; bu kuralı JS'e kopyalamak iş mantığını iki kez yazmak olurdu. Web rakam + **tek** ayırıcı kabul ediyor, gerisini reddediyor. Kabul edilen her giriş için sonuç iki tarafta birebir aynı; ayrılan tek nokta, belirsiz yazımın sessizce yorumlanmak yerine reddedilmesi. Miktar sunucuya **JSON string** olarak gider — sayıya çevrilse `double`'a düşerdi |
| Tutar sütunu | *(belirtilmemiş)* | değişmiş satırda tutar yerine `…` | Web hesap yapamadığı için, kullanıcı miktarı değiştirdiği anda ekrandaki tutar eskiyor. Eski tutarı bir saniye daha "güncel" gibi göstermek, `…` göstermekten kötü: yanlış rakam ekranda kalırsa kullanıcı ona göre karar verir |
| Web derlemesi | *(belirtilmemiş)* | `bootJar`a bağlandı, `processResources`'a **değil** | İkincisi `test` ve `ktlintCheck` dahil her derlemeyi npm'e bağımlı kılardı. Web yalnızca dağıtılacak jar üretilirken gerekli. Node bulunmayan bir makine için `-PwebAtla` çıkışı var ve arayüzsüz jar ürettiğini **gürültülü** biçimde söylüyor |
| "Sıra sende" göstergesi | ayrı bir gösterge | **sunucunun ret mesajı olduğu gibi gösteriliyor** | Sunucu zaten "Bu bölgede hakediş sırası SP-… işinde (#…); önce onun hakediş belgesini tamamlayın." diyor. İkinci bir uç açıp aynı bilgiyi ayrıca hesaplamak, aynı kuralı iki yerde yaşatmak olurdu. Belge açma denemesi 409 aldığında mesaj iş detayında uyarı şeridi olarak çıkıyor |
| Uyarı şeridi yeri | iş detayında | **iş oluşturulduktan sonraki sonuç ekranında** | `uyarilar` yalnızca `POST /api/isler` yanıtında dolu geliyor (D1/3); iş detayına gidilince `GET` boş dizi döndürüyor. Uyarıyı orada göstermek için ya istemcide yeniden hesaplamak (yetki mantığını kopyalamak) ya da yeni bir uç açmak gerekirdi. Uyarı yoksa ekran hiç görünmüyor, doğrudan işe geçiliyor |
| `BelgeYaniti.isId` | *(planda yok)* | **`@get:JsonProperty("isId")` eklendi** — elle tarayıcı kontrolünde bulundu | Kotlin, adı `is` ile başlayan özellik için getter'ı `isId()` üretiyor; Jackson bunu boolean kalıbı sanıp öneki kırpıyor ve alanı `"id"` adıyla yazmaya çalışıyor. Sınıfta zaten `id` olduğu için alan JSON'a **hiç çıkmıyor ve hata da vermiyor**. Sonuç: belge ekranı `/api/isler/undefined` çağırıp 500 alıyordu. Faz B'de `PozYaniti.isGroup`/`isPriced` için fark edilen kural burada gözden kaçtı. **Betikler yakalayamadı** çünkü hepsi kimlikleri kendi izliyor, `isId`'yi hiç okumuyordu — bu, tarayıcıda elle bakmanın neden ayrı bir kontrol olduğunun somut kanıtı. `BelgeYanitlariTest` + iki betik kontrolü eklendi |
| Birim fiyat gösterimi | 2 hane (`648,36 ₺`) | **yuvarlanmıyor** (`648,35852723050107 ₺`) — elle tarayıcı kontrolünde bulundu | Kullanıcı ekranda `371,87 × 2 = 743,74` bekleyip tutar sütununda `743,73` gördü. Hesap doğruydu (ham fiyat `371,86716779978428`), yanıltan gösterimdi: 2 haneye yuvarlanmış fiyat "fiyat × miktar ≠ tutar" izlenimi veriyordu — hem ekranda hem **aracı firmaya giden PDF'te**. Kullanıcı kararı: *"parasal değerler önemli, tam hesap lazım"* → hesap değişmedi, gösterim düzeltildi. `MoneyFormatter.moneyExact` eklendi; web poz tablosu, önizleme ve `PdfTemplate` bunu kullanıyor. Excel zaten ham değeri hücreye yazıyordu. Tutar ve toplam kuruşa yuvarlı kaldı — onlar zaten yuvarlanmış değerler |
| Tarayıcı testi | *(planda yok)* | **yok, ve bu açıkça yazıldı** | Makinede tarayıcı otomasyonu (Playwright vb.) yok; doğrulama betiği sisteme paket kurmuyor (Faz E `openpyxl`, Faz G `pdftotext` kararlarının aynısı). Betik arayüzün yaptığı **istek dizisini** tarayıcının gönderdiği sırayla ve aynı gövde biçimleriyle koşturuyor; ekranların gözle kontrolü elle yapılacak |

> ⚠️ **Ekranlar gözle doğrulanmadı.** Akış, yetki, para gösterimi ve statik servis testle/betikle
> kanıtlandı; yerleşimin, imza tuvalinin ve dosya seçicilerin gerçek tarayıcıda nasıl göründüğü
> **kontrol edilmedi**. İlk fırsatta `http://localhost:8081/` açılıp bir işin uçtan uca yürütülmesi
> gerekiyor — özellikle imza tuvali (fare + dokunmatik) ve tablet genişliğindeki tablo kayması.

**DoD**
- [ ] Web'den uçtan uca: iş → fizibilite gönder → *(yönetici onayı)* → keşif gönder → hakediş gönder
      — **tarayıcıdan koşturulmadı.** Arayüzün yaptığı istek dizisi (aynı sıra, aynı gövde biçimleri:
      miktar JSON string, ekler multipart `tur` alanı, CSRF cookie'den) `dogrula-faz-h.sh` içinde
      uçtan uca geçiyor; eksik olan tarayıcıdaki tıklama/çizim adımı. **Faz kapanmadan önce elle
      yapılması gereken tek madde budur.**
- [x] Web kodunda **hiçbir para aritmetiği yok** — grep ile kanıt: para alanlarında aritmetik işleç
      yok, `toFixed`/`parseFloat`/`parseInt` yok, `₺` sembolü ve `Intl.NumberFormat` yok
- [x] Bölge sorumlusu arayüzünde başka bölgenin verisi hiçbir listede görünmüyor
      *(süzgeç SQL'de; Ankara oturumunda Bursa belgesi 0 satır, doğrudan erişim 404)*
- [x] Durum metinleri sunucudan gelir; web'de durum metni **sabit yazılmaz** *(dört durum metni grep'le arandı, hiçbiri kaynakta yok)*
- [x] Kimliksiz `GET /` giriş sayfasını veriyor, `GET /api/belgeler` **401**; bilinmeyen `/api` yolu HTML'e dönüşmüyor
- [x] `./gradlew :server:bootJar` çıktısında `BOOT-INF/classes/static/index.html` var
- [x] `bash deploy/dogrula-faz-h.sh` temiz — *73 kontrol, 0 düşen*
- [x] `dogrula.sh` (A/B), `dogrula-faz-d.sh` (80), `dogrula-faz-e.sh` (65), `dogrula-faz-f.sh` (49), `dogrula-faz-g.sh` (34) hâlâ temiz — gerileme yok
- [x] `./gradlew ktlintCheck test` temiz — *188 test, 0 hata*

---

## FAZ I — Web Arayüzü: Yönetici — Onay Kuyruğu & Fiyat Listesi  ·  `Durum: ✅`

**Amaç:** Yöneticinin onay kuyruğunu yürütmesi: raporu inceler, indirir, pazarlık fiyatlarını yükler,
onaylar.

**Yapılacaklar**
- [x] Tüm bölgeler görünümü (bölge/tür/yıl/durum süzgeçleri)
- [x] **Onay kuyruğu** — ayrı uç değil, aynı listenin süzgeci: `?tur=FIZIBILITE&durum=ONAY_BEKLIYOR`
- [x] Satır içi **PDF/Excel indirme** (fiyatlı + fiyatsız `TEKLIF`)
- [x] **Fiyat listesi akışı:** şablonu indir → doldurulmuş dosyayı yükle → **önizleme/eşleştirme ekranı** (kaç kalem eşleşti, hangi yolla, referans → yeni fiyat, 10 kat sapma uyarıları) → onayla. Önizleme **atlanamaz**
- [x] **İptal onay ekranı** — "silinecek" değil "iptal edilecek" dili, sonucu açıkça yazar ("bu iş kuyruktan çıkacak, sıradaki iş açılacak; kayıt silinmez"), gerekçe alanı zorunlu. **Geri alma olmadığı için onay atlanamaz** (D5)
- [x] Kullanıcı yönetimi + parola sıfırlama (ilk kurulum parolalarının değiştirilmesi)
- [x] **Sunucu tarafı** (faz sırasında ortaya çıktı): `GET /api/kullanicilar`,
      `PUT /api/kullanicilar/{id}/parola`, `PUT /api/kullanicilar/{id}/aktif` + yönetici yetkisi;
      `FiyatListesiOkuyucu`'daki 500 kusuru; önizleme kalemlerine gösterim metni
- [x] `deploy/dogrula-faz-i.sh` (72 kontrol)

### Faz I — plandan sapmalar (gerekçeleriyle)

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| Onay kuyruğu | "ayrı uç değil, aynı listenin süzgeci" | **aynı *ekran*ın da süzgeci** (`BelgeListesi`, `kuyruk` bayrağı) | Plan sunucu tarafını söylüyordu; aynı gerekçe arayüzde de geçerli. Ayrı bir kuyruk ekranı, aynı tabloyu, aynı süzgeçleri ve aynı indirme mantığını ikinci kez bakımda tutmak olurdu. Rota (`#/kuyruk`) yalnızca ön ayarı taşıyor |
| Kullanıcı yönetimi | "kullanıcı yönetimi + parola sıfırlama" | **liste + parola sıfırlama + hesap açma/kapatma**; kullanıcı **oluşturma yok** | Kullanıcılar sabit (7 + 1) ve yeni bir sorumlu ancak yeni bir bölge açıldığında gerekir — o zaman `bolge` tablosu da değişeceği için işlem zaten migration ister. Oluşturma ekranı yazmak, hiç kullanılmayacak bir yol için rol/bölge tutarlılık kurallarını arayüzde ikinci kez kodlamak olurdu. Hesap kapatma eklendi: personel ayrıldığında tek çıkış yolu buydu |
| Parola üretimi | *(belirtilmemiş)* | **yönetici yazar**, sunucu üretmez; alan maskelenmez | Yönetici parolayı kullanıcıya kendisi iletecek, dolayısıyla ne olduğunu görmesi gerekiyor. Sunucunun rastgele üretmesi, ekranda bir kez gösterilip kaybolan bir değer demek olurdu. Kural asgari: sekiz karakter + kullanıcı adından farklı |
| Yöneticinin kendini kapatması | *(planda yok)* | **409 ile engellendi** | Tek yönetici hesabı var; kapanırsa sisteme yönetici olarak girecek kimse kalmaz ve çıkış yolu yalnızca veritabanına elle müdahaledir |
| Bozuk dosya yüklemesi | *(planda yok)* | **500 → 400 düzeltmesi** (`FiyatListesiOkuyucu`) | Doğrulama sırasında çıktı: POI, xlsx olmayan bir dosyayı `NotOfficeXmlFileException` ile reddediyor ve bu bir **RuntimeException** (`UnsupportedFileFormatException` → `IllegalArgumentException`), `IOException` değil. Mevcut kod yalnızca `IOException` yakaladığı için yanlış dosya seçildiğinde 500 + *"Yönetici ile iletişime geçin"* dönüyordu — oysa bu ekranı kullanan kişi **yöneticinin kendisi**. `FiyatListesiOkuyucuTest` üç girdiyle (metin, PDF, boş) kilitliyor |
| Önizleme fiyatları | *(belirtilmemiş)* | `referansFiyatGosterim` / `yeniFiyatGosterim` **eklendi** | Önizleme ham `BigDecimal` string'i gösteriyordu (`371.86716779978428`, nokta ayırıcılı). Web'de biçimlendirmek para formatlamasını ikinci kez yazmak olurdu; gösterim sunucuda üretiliyor ve birim fiyat olduğu için **yuvarlanmıyor** (Faz H'de alınan karar) |
| `dogrula-faz-h.sh` grep'leri | *(dokunulmayacaktı)* | **iki desen daraltıldı** | Faz I kodu iki yanlış pozitif üretti: (a) `'Satın Alma Onayı'` deseni, onay panelinin **ekran başlığını** yakalıyordu — kural durum *metnini* yasaklıyor, tam metin (`…Beklemede`) arandı; (b) `xlsx` deseni, fiyat şablonunun **dosya uzantısını** yakalıyordu — import araması yapıldı. İkisi de kuralı gevşetmiyor, kuralın söylediği şeyi arıyor |
| Tarayıcı testi | *(planda yok)* | **yok** (Faz H'deki kararın aynısı) | Makinede tarayıcı otomasyonu yok. Betik yönetici ekranlarının arkasındaki uçları ve yetki sınırlarını koşturuyor; ekranların gözle kontrolü elle yapılacak |

> ⚠️ **Ekranlar gözle doğrulanmadı** (Faz H ile aynı durum). Özellikle bakılması gerekenler: fiyat
> listesi önizleme tablosunun 86 satırla görünümü, iptal panelinin kırmızı çerçeveli uyarı dili ve
> kullanıcılar ekranındaki satır içi parola alanı.

**DoD**
- [x] Onay kuyruğundan bir fizibilite: indir → şablon → yükle → önizle → onayla; ardından bölge sorumlusu keşfi açabiliyor
- [x] Yanlış eşleşen dosya önizleme ekranında **açıkça** raporlanıyor, onay butonu **kapalı**
      *(önizleme `gecerliMi=false` ya da 400 döndüğünde düğme açılmıyor; dosya değişince önizleme sıfırlanıyor)*
- [x] Yönetici olmayan kullanıcı onay ve fiyat uçlarına erişemiyor (**403**) — şablon, önizleme, onay, iptal, kullanıcı listesi ve parola sıfırlama için ayrı ayrı
- [x] Önizleme **hiçbir şey yazmıyor**: `fiyat_versiyonu` ve `poz_fiyat` sayıları değişmiyor (geçerli, bozuk ve eksik dosya için ayrı ayrı)
- [x] İptal: gerekçesiz → **400**; belge iptali sırayı **açmıyor** ve yeni belge **yeni numara** alıyor; iş iptali açık taslakları da iptal ediyor; iptal edilmiş kayıtlar listede **kalıyor**
- [x] Parola sıfırlama sonrası yeni parola çalışıyor, eski **401**; kapalı hesap giriş yapamıyor; yönetici kendini kapatamıyor → **409**
- [x] `bash deploy/dogrula-faz-i.sh` temiz — *72 kontrol, 0 düşen*
- [x] `dogrula.sh` (A/B), `dogrula-faz-d.sh` (80), `-e` (65), `-f` (49), `-g` (34), `-h` (73) hâlâ temiz — gerileme yok
- [x] `./gradlew ktlintCheck test` temiz — *194 test, 0 hata*
- [ ] **Tarayıcıdan elle:** onay kuyruğu → şablon indir → doldur → yükle → önizle → onayla → bölge
      sorumlusu keşfi açıyor. **Faz K testlerinde yapılacak.**

---

## FAZ J — Mobil Uygulamanın Backend'e Bağlanması  ·  `Durum: 🚧`

**Amaç:** Mobil uygulamanın ince istemciye dönüşmesi. **Mobil tarafına dokunan tek faz budur.**

### FAZ J KARARLARI  *(31 Temmuz 2026, kullanıcıdan alındı)*

**J1 — Bağlantı: düz HTTP + ayarlanabilir adres** *(açık soru 7 kapandı)* · ⚠️ **GEÇERSİZ — bkz. J1'**
~~Tablet sunucuya kurumsal VPN içinden düz HTTP ile bağlanır (`http://10.x.x.x:8081`). Sunucu adresi
**giriş ekranından ayarlanır** ve cihazda saklanır: IP değiştiğinde APK yeniden derlenip yeniden
dağıtılmaz. `network_security_config.xml` cleartext trafiğe izin verir.~~
TLS geldiğinde (Faz K, ters vekil) yapılacak tek değişiklik cleartext izninin kapatılmasıdır.

**J1' — Bağlantı: sabit HTTPS adresi** *(19 Ağustos 2026, kullanıcı kararı — J1'in yerine)*
Tablet **`https://hakedis.turktelekomint.com`** adresine bağlanır; adres `res/values/config.xml`'de
**derleme zamanında sabittir**, uygulamada adres girme alanı yoktur (giriş ekranındaki alan ve Hesap
sekmesindeki "Adresi Kaydet" kaldırıldı; Hesap'ta adres yalnızca **okunuyor**).

Bunu zorunlu kılan şey J1'in varsaydığı dünyanın kalkmasıydı: sunucuda uygulama `127.0.0.1:8081`'e
bağlı ve güvenlik duvarında 8081 **açılmıyor** (`tti-sunucu/docker-compose.yml`), dışarıya açık tek
kapı ters vekilin 443'ü. `http://<ip>:8081` artık hiçbir tabletten erişilebilir değil.

- `cleartextTrafficPermitted` **`false`** — J1'in "TLS gelince kapatılacak" adımı uygulandı.
- Güven çapası **`system` yeterli ve öyle kalmalı**: sertifikanın issuer'ı `DigiCert Inc / RapidSSL
  TLS RSA CA G1`, yani public bir CA ve kökü Android sistem deposunda minSdk 26'dan önce beri var.
  MDM ile kök yüklemek, pinleme ve `<certificates src="user" />` **gerekmiyor** (sonuncusu Android
  7'den beri bilinçli olarak kapalı bir kapıdır).
- ⚠️ Sunucu tarafında tek koşul: `certs/fullchain.pem` **ara sertifikayı da** içermeli. Kök tablette
  var, ara CA yok; eksik zinciri tarayıcı çoğu zaman tolere eder, **Android etmez**. Kontrol
  CERT.md'de: `grep -c 'BEGIN CERTIFICATE' certs/fullchain.pem` **1'den büyük** olmalı.
- ⚠️ **Bedeli:** alan adı, DNS ya da sunucu değişirse tek çıkış yolu **yeni APK derleyip tabletlere
  dağıtmaktır**; sahada geçici çözüm yok. Bilerek kabul edildi — karşılığında kullanıcı yanlış adres
  yazamıyor ve yeni tablette kurulum "kullanıcı adı + parola"ya iniyor.
- ⚠️ Eski sürümlerin yazdığı `sunucuAdresi` anahtarı `SharedPreferences`'ta kalmış olabilir ve
  **bilerek okunmuyor**: okunsaydı güncellenen bir tablet eski kayıtlı adrese bağlanmaya devam eder,
  yeni adresi hiç görmezdi — üstelik sessizce.
- Geliştirme sunucusuna bağlanmak için `config.xml` geçici değiştirilip yeniden derlenir; debug
  yapısına ayrı varsayılan **konmadı** (emülatörün de üretim sunucusuna gitmesi istendi).

**J2 — "Beni hatırla" yok** *(açık soru 10 kapandı)*
Parola cihazda **hiçbir biçimde saklanmaz**. Yalnızca son kullanılan kullanıcı adı hatırlanır
(sunucu adresi J1' ile ayar olmaktan çıktı, artık saklanmıyor). Oturum çerezi sunucu ömrü boyunca (8 saat) geçerli olduğu için gün içinde tekrar giriş
gerekmez; uygulama kapatılıp açıldığında parola yeniden istenir.

**J3 — Kapsam: web ile tam parite (bölge sorumlusu)**
Mobil, bölge sorumlusunun akışını uçtan uca yürütür: giriş · iş listesi · yeni iş · üç belge türü ·
miktar girişi · EK1/EK2 + imza · PDF/Excel indirme ve paylaşma. Hakediş sunucuda EK1 + imza olmadan
gönderilemediği için (Faz F) bunlar olmadan tablette akış tamamlanamazdı. **Yönetici ekranları
mobilde yoktur** (CLAUDE.md §2: yönetici yalnızca web kullanır) — onay, fiyat listesi, iptal ve
kullanıcı yönetimi web'de kalır.

**Yapılacaklar**
- [x] Silinecekler: ~~`export/MailSender.kt`, `feature/mail/*`, `export/PdfGenerator.kt`,
      `export/PdfTemplate.kt`, `export/ExcelExporter.kt`, `android/print/PdfPrint.kt`,
      `data/DraftRepository.kt`, `data/DraftAutoSaver.kt`, fastexcel bağımlılığı~~ *(hepsi Faz C'de
      silinmişti)*; **`assets/contracts/*`**, `data/ContractRepository.kt`, `data/WorkOrderSession.kt`,
      `feature/workorder/*`, `feature/preview/*`
- [x] `AndroidManifest.xml`: **`INTERNET` izni eklendi**; `<queries>` mail blokları Faz C'de kalkmıştı,
      FileProvider yolları indirilen çıktı için yeniden tanımlandı
- [x] `network_security_config.xml` (J1)
- [x] OkHttp tabanlı `HakedisApi` (Retrofit yok — birkaç uç için gereksiz), `AppContainer`'a eklenir
- [x] Giriş ekranı + oturum; `startDestination` değişir
- [x] `ContractRepository`: assets → API + bellek cache
- [x] Poz tablosu: 3 belge türü; PDF/Excel **sunucudan indirilir**, `PdfPreviewRenderer` inen dosyayı
      gösterir; paylaşma `FileProvider` ile korunur
- [x] **Faz sırasında eklendi:** `ApiModelsTest` (sözleşme kilidi) ve `deploy/dogrula-faz-j.sh`
      (64 kontrol)

### Faz J — plandan sapmalar (gerekçeleriyle)

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| CSRF | *(planda yok)* | **yazan istekten önce token alınır** | Sunucu CSRF'i **giriş ucu dahil** her POST'ta istiyor (`GuvenlikYapilandirmasi` hiçbir yolu muaf tutmuyor) ve token `XSRF-TOKEN` çerezinden geliyor. Uygulama ilk açıldığında elde çerez yok: doğrudan giriş denemesi **403** ile düşerdi. `HakedisApi.send` yazan bir istekten önce token yoksa `/api/saglik`'e GET yapıp alıyor — web de aynısını yapıyor (`api.ts`). Betikteki ilk kontrol tam olarak bunu kanıtlıyor: tokensız giriş 403, tokenlı giriş 200 |
| Silme listesi | uzun bir dosya listesi | **çoğu zaten Faz C'de silinmişti** | `MailSender`, `PdfGenerator`, `ExcelExporter`, `DraftRepository` ve fastexcel bağımlılığı Faz C'de kaldırılmıştı (o fazın sapmalar tablosunda yazılı). Faz J'de gerçekten silinenler: `assets/contracts/*`, `ContractRepository`, `WorkOrderSession`, eski `feature/workorder` ve `feature/preview` ekranları |
| Toplam ve satır tutarı | *(belirtilmemiş)* | **cihazda `PricingCalculator` ile türetiliyor** | Web para aritmetiği yapamaz ve toplamı sunucudan hazır alır; mobil **aynı domain sınıflarını çağırıyor** (pazarlıksız kural 1: "sunucu ve mobil aynı sınıfları çağırır"). Sonuç birebir aynı — aynı kod, aynı dondurulmuş fiyat — ama debounce'lu kayıt beklenmeden anında görünüyor. Tablette her tuş vuruşunda VPN'den yanıt beklemek kullanılabilirliği bitirirdi |
| Miktar biçimi | *(belirtilmemiş)* | **`QuantityParser`'ın tamamı** kabul edilir | Web, domain parser'ını JS'e kopyalamamak için dar bir alt küme kabul ediyordu (Faz H sapması). Mobil `:core:domain`'i doğrudan kullandığı için böyle bir ödün yok: `1.234,5` de kabul ediliyor ve sunucuya `toPlainString()` ile gidiyor |
| Sınıf adları | *(belirtilmemiş)* | **kod İngilizce, DTO alan adları Türkçe** | `:app` baştan İngilizce yazılmış (CLAUDE.md §1/6); sunucu ve web ise Türkçe. DTO **alan** adları JSON anahtarlarının kendisidir (`durumMetni`, `spNo`): her biri için `@SerialName` yazmak iki şemayı yan yana karşılaştırmayı zorlaştırır ve bir harf hatasını sessiz bir "alan gelmedi"ye çevirirdi |
| Sözleşme güvencesi | *(planda yok)* | **`ApiModelsTest`** eklendi | Mobil ↔ sunucu arasında hiçbir otomatik kontrol yoktu: sunucuda bir alan adı değişirse hata ancak tablette, ekran açılırken çıkardı. Test gerçek sunucu yanıtlarını çözüyor; sunucudaki eşi `BelgeYanitlariTest` alanların **yazıldığını**, bu **okunduğunu** kilitliyor. `isId` tuzağı (Faz H'de web'i kıran) burada da kilitli |
| R8 | "keep kuralları dahil" | **küçültme açıldı** (`isMinifyEnabled = true`) | Küçültme kapalıyken "keep kuralları dahil" maddesinin bir karşılığı yoktu. kotlinx.serialization ve OkHttp sınıflara **isimle** ulaştığı için kural şart; kurallar `proguard-rules.pro`'da ve mapping'de 27 `$$serializer` sınıfının korunduğu doğrulandı. ⚠️ Küçültülmüş APK **cihazda çalıştırılmadı** — elle test release APK ile yapılmalı |
| Doğrulama betiği | *(planda yok)* | **`dogrula-faz-j.sh`** (64 kontrol) | Faz H/I kalıbı: betik uygulamanın yaptığı **istek dizisini** aynı sırayla ve aynı gövde biçimleriyle koşturuyor (miktar JSON string, ekler multipart `tur` alanı, imza PNG, çıktı `Content-Disposition` adıyla). Cihaz otomasyonu yok, o yüzden dokunma/çizim kapsam dışı — Faz H'deki tarayıcı kararının aynısı |

### Faz J — emülatörde ilk çalıştırma  ·  `31 Temmuz 2026`

Uygulama Pixel Tablet emülatöründe (Android 15) uçtan uca koşturuldu: giriş → iş listesi → yeni iş
(74 kable) → iş detayı → fizibilite belgesi (95 poz) → miktar girişi. **Dört kusur çıktı, dördü de
düzeltildi.** Betikle yakalanamayacak olanlar bunlar: hepsi istemci tarafında ve sunucu sözleşmesiyle
ilgisiz.

| # | Ne oldu | Kök neden |
|---|---|---|
| 1 | ⚠️ **"Yeni İş" ekranı boş mesajlı bir hatayla açılmıyordu** | `withContext(Dispatchers.IO)` yalnızca `execute()`'un etrafındaydı; `execute()` ise sadece **başlıkları** getiriyor. Gövde `body.string()` ile okunurken gerçek soket okuması yapılıyor ve bu, çağıranın iş parçacığında — yani ana iş parçacığında — **`NetworkOnMainThreadException`** fırlatıyordu. İstisnanın mesajı `null` olduğu için ekranda yalnızca genel metin görünüyordu. Küçük yanıtlar (giriş, boş liste) ilk pakette gelip tamponda bulunduğu için **kazara çalışıyordu**; hata ilk büyük listede (74 kable) ortaya çıktı. Gövde artık istekle aynı IO bloğunda okunuyor (`sendForText`) ve `Response` dışarı hiç verilmiyor; `close()` ve indirme akışları da IO'ya alındı |
| 2 | Hatanın kaynağı **hiçbir yerde yazmıyordu** | Yalnızca `IOException` loglanıyordu; çözümleme hataları ve mesajsız istisnalar sessizce genel metne düşüyordu. `Throwable.userMessage(tag)` eklendi: her başarısız istek sınıf adı ve yığın iziyle `Log.e`'ye yazılıyor. 1 numaralı hata ancak bu eklendikten sonra teşhis edilebildi |
| 3 | Birim fiyat sütunu fiyatı iki satıra sarıyordu | Sütun 132 dp'ydi; yuvarlanmamış fiyat (`648,35852723050107 ₺`) 20 karakter. 220 dp yapıldı — hakediş PDF'inin yatay sayfaya alınmasının nedeni de aynıydı (Düzeltme Turu 1) |
| 4 | Grup satırında boş bir miktar kutusu çiziliyordu | Pasif de olsa kutu, oraya yazılabileceği izlenimi veriyordu; grup ve fiyatsız satırda kutu artık hiç çizilmiyor. Ayrıca iş bilgilerinde arama kutusunun yer tutucusu (`KableID veya adres`) etiket olarak kullanılmıştı — ayrı dizgeye bölündü |

**Gözle doğrulananlar:** durum rozeti tür + durum metnini birlikte yazıyor (Düzeltme Turu 1/4);
fizibilitede birim fiyatlar salt okunur ve bilgi şeridi görünüyor; miktar `3` girildiğinde tutar ve
toplam **`1.945,08 ₺`** (3 × `648,35852723050107`, domain testiyle birebir); debounce'lu kayıt sonrası
"Kaydedildi" yazıyor ve "Onaya Gönder" açılıyor; uygulama kapatılıp açıldığında miktar sunucudan geri
geliyor.

> ⚠️ **Hâlâ denenmemiş olanlar:** imza tuvalinin parmakla çizimi, dosya seçici (EK1/EK2), kamera,
> PDF indirme–önizleme–paylaşma ve **gerçek tablet**. Emülatörde koşturulan akış fizibilite belgesine
> kadardır; hakedişe özgü ekranlar (ekler, imza) ve çıktı bölümü ekranda hiç açılmadı.
> Ayrıca **küçültülmüş (R8) APK cihazda çalıştırılmadı** — elle test release APK ile yapılmalı.

**DoD**
- [ ] Tablette VPN üzerinden: giriş → iş oluştur → belge oluştur → miktar gir → PDF indir → paylaş
      — **kısmen yapıldı.** Emülatörde giriş → iş oluştur → belge oluştur → miktar gir adımları
      koşturuldu ve dört kusur bulunup düzeltildi (yukarıdaki tablo). **Eksik kalan:** EK1/EK2
      seçimi, imza çizimi, PDF indirme–paylaşma ve gerçek tablet. **Faz kapanmadan önce yapılması
      gereken madde budur** ve release APK ile yapılmalı.
- [x] `./gradlew :app:assembleRelease` çalışıyor (R8 keep kuralları dahil) — küçültme **açık**,
      mapping'de 27 `$$serializer` sınıfı ve API modelleri korunmuş durumda
- [x] Uygulamada hiçbir yerde yerel PDF/Excel üretimi kalmadı (grep ile kanıt) — `fastexcel`,
      `PdfTemplate`, `PdfGenerator`, `ExcelExporter`, `PrintDocumentAdapter`, `org.apache.poi`
      desenlerinin hiçbiri `app/src` altında yok; `assets/` klasörü tamamen kalktı
- [x] **Sözleşme kilidi:** `ApiModelsTest` gerçek sunucu yanıtlarını çözüyor — `isId`,
      `isGroup`/`isPriced`, ham birim fiyatın string ve tam hassasiyetle gelmesi dahil
- [x] Mobilde durum metni sabit yazılmamış, parola cihazda saklanmıyor (grep ile kanıt)
- [x] `bash deploy/dogrula-faz-j.sh` temiz — *64 kontrol, 0 düşen*
- [x] `dogrula.sh` (A/B), `-d` (80), `-e` (65), `-f` (49), `-g` (34), `-h` (92), `-i` (88) hâlâ
      temiz — gerileme yok
- [x] `./gradlew ktlintCheck test` temiz — *228 test, 0 hata*

---

## FAZ K — Hakediş Karşılaştırma & Sertleştirme  ·  `Durum: ⬜`

**Amaç:** Müdürün istediği karşılaştırmalı hakediş ve üretime alma.
*(EK1/EK2 yükleme Faz F'ye taşındı — akışta hakediş adımının parçası.)*

**Yapılacaklar**
- [x] Hakediş belgesinde **keşif miktarı yan sütunda** — *31 Temmuz 2026 düzeltme turunda öne çekildi*
      (kullanıcı isteği). PDF ve Excel'de "1. Keşif Talep" + "1.Keşif Tutar" dolu, toplam satırında
      keşif genel toplamı var. Keşif tutarı **keşifin kendi dondurulmuş fiyatıyla** hesaplanır.
- [ ] Hakediş belgesinde **sapma sütunu** — kalan yarı. Karara bağlanacak: sapma miktar farkı mı,
      tutar farkı mı, yüzde mi? Ayrıca keşifte olup hakedişte olmayan kalem PDF'e girmeli mi
      (bugün girmiyor; `DocumentModelBuilder` yalnızca o belgenin miktarı olan satırı listeliyor)
- [x] **Yedekleme** *(10 Ağustos 2026)* — `deploy/yedek.sh` + systemd timer, hedef
      **`/srv/tti-hakedis/yedek`**. ~~`pg_dump` → `{ARSIV_KOK}/_yedek/`~~: arşiv **yedeklenmeyen**
      köktür (yeniden üretilebilir), yedeği onun içine koymak "yedeklenmeyen klasörde yedek"
      demek olurdu; `arsiv`/`yukleme` ile kardeş ayrı bir dizine alındı. Kapsam: iki şema ayrı
      dosyaya + roller + `YUKLEME_KOK`'ün `--link-dest` anlık görüntüsü + makineye özel
      compose/`.env`. Kurulum adımları `RedHatKurulum.md §12`.
- [ ] ~~TLS / ters vekil~~ **eklenmeyecek** *(10 Ağustos 2026, kullanıcı kararı)* — yalnızca
      intranet/VPN. Log seviyeleri ve `docker compose restart` dayanıklılığı **doğrulandı**
      (restart provası geçti, veri ve arşiv yerinde). Kalan: yeniden değerlendirme ancak sunucu
      VPN dışına açılırsa.
- [ ] Denetim kaydı (kim hangi belgeyi ne zaman tamamladı) — **kısmen geldi**: Faz V'de
      `durum_kaydi` her durum geçişini gerekçesiyle yazıyor. Kalan: belge tamamlama izi.

**DoD**
- [x] Hakediş PDF'inde **keşif ve saha sütunları** doğru *(31 Temmuz 2026; `PdfUreticiTest`,
      `ExcelUreticiTest` ve `dogrula-faz-h.sh` ile kilitli)*
- [ ] Hakediş PDF'inde **sapma** sütunu doğru
- [x] `deploy/yedek.sh dogrula` temiz çıkıyor — dump'lar **tam okunarak** doğrulanıyor
      (`pg_restore -f /dev/null`; yalnızca `--list` bakmak sondan kırpılmış dosyayı kaçırıyordu) ve
      `hakedis.ek`'teki her dosya anlık görüntüde çapraz kontrol ediliyor
- [x] **Bozuk dump reddediliyor** — `docker exec -t` ile alınan (TTY `\r` sokmuş) ve sondan
      kırpılmış dump'ların ikisi de `dogrula`da düşüyor; 12 senaryonun tamamı geliştirme
      makinesinde koşturuldu
- [ ] Yedekten geri yükleme provası **sunucuda yapıldı ve belgelere erişildi** — geliştirme
      makinesinde `tti_prova` üzerinde koşturuldu ve satır sayıları tuttu; **sunucuda tekrarlanacak**
- [x] Sunucu yeniden başlatıldığında veri ve arşiv yerinde — `docker compose restart` provası
      geçti (6 sn'de kalktı, 945 kablo + projeler + belgeler yerinde)
- [ ] **Reboot sonrası timer kendiliğinden koştu** (sunucuda; `journalctl -t tti-yedek -b`)


---

## FAZ L — Yönetici Sözleşme Yönetimi  ·  `1 Ağustos 2026`  ·  `Durum: ✅`

**Amaç:** Yöneticinin arayüzden sözleşme yükleyebilmesi ve mevcut bir sözleşmenin **referans birim
fiyatlarını** güncelleyebilmesi; bundan sonra o sözleşmeyle iş açan bölge sorumlularının güncel
fiyatlarla çalışması.

**Neden:** Bu faza kadar sisteme sözleşme eklemenin tek yolu bir Flyway migration'ıydı
(`V3__sozlesme_seed.sql`) ve `poz_fiyat`'a yazan tek kod fizibilite onayında **işe özel** pazarlık
listesi üretiyordu. Tek bir birim fiyatın düzeltilmesi bile geliştirici müdahalesi + sunucu dağıtımı
gerektiriyordu. Şema bu akışı baştan bekliyordu (`fiyat_versiyonu.durum` üç değerli) ama kodu hiç
yazılmamıştı: `TASLAK` ve `ARSIV` kullanılmıyordu.

### Kullanıcı kararları (1 Ağustos 2026)

| Konu | Karar |
|---|---|
| Fiyat düzenleme yöntemi | **İkisi de** — tabloda satır içi düzenleme *ve* Excel indir/doldur/yükle. İkisi de aynı `TASLAK` sürüme yazar, "Yayınla" ile aktifleşir |
| Açık `TASLAK` fizibiliteler | **Eski listede kalır, dokunulmaz.** Güncel fiyat yalnızca bundan sonra açılan belgelerde geçerli (açık soru 5) |
| Güncelleme kapsamı | **Yalnızca birim fiyat + ad/başlık/aktiflik.** Poz listesi yüklendiği gibi kalır |

### L1 — Düzenleme taslağı, doğrudan yazma değil

Fiyatlar doğrudan değiştirilmez: `taslakAc → (satır düzenle | Excel yükle)* → yayınla | vazgeç`.

- **Yayınlanmış sürüm salt okunurdur** — geçmiş belgeler ona çivili (CLAUDE.md §7/4). Kural
  `FiyatVersiyonuYazici`'nin yazma SQL'lerinde `where ... durum = 'TASLAK'` süzgeciyle korunur;
  etkilenen satır `0` ise fail loud. Süzgeç Kotlin'de olsaydı çağıranlardan biri unuttuğunda sessizce
  geçmiş bir belgenin tutarı değişirdi (§7/3).
- Her hücre düzeltmesi için ayrı sürüm açmak 95 satırlık bir kopyayı çoğaltır ve sürüm listesini
  okunmaz hâle getirirdi; taslak bir oturumdaki tüm düzeltmeleri **tek sürüme** toplar.
- Yayınlama sırası: önce eski `YAYINDA` → `ARSIV`, sonra taslak → `YAYINDA`.
  `fiyat_versiyonu_referans_yayin_ux` sözleşme başına tek yayında sürüme izin veriyor.
- Sözleşme başına tek açık taslak: `fiyat_versiyonu_referans_taslak_ux` (V8).

### L2 — Tek poz ucu: `GET /api/belgeler/{id}/pozlar`

"Eski taslak dokunulmasın" kararının **zorunlu tamamlayıcısı**. Belge ekranı poz listesini türe göre
farklı uçtan çekiyordu (fizibilitede sözleşmenin **canlı** yayında sürümü, keşif/hakedişte işin
sürümü), oysa belge kendi `fiyat_versiyonu_id`'sine çivili ve satır yazılırken fiyat oradan
kopyalanıyor. Yeni sürüm yayınlandığı an ikisi ayrışıyordu: bölge sorumlusu ekranda **yeni** fiyatı
görüp belgeye **eski** fiyatı yazıyor ve hiçbir hata çıkmıyordu.

Tek uç bu ayrışmayı yapısal olarak imkânsız kılıyor; web ve mobildeki tür dallanması da silindi.
`isPriced` artık `poz.fiyatli_mi`'den değil **sürümdeki fiyattan** türetiliyor (`BelgeSorgulari.satirYaz`
aynı kuralı kullanıyor): bir sürümde fiyatsız olan poz sonraki sürümde fiyatlanabilir.

### L3 — Poz listesi değişmez

Satır ekleme/silme `poz.satir_kodu`'nu (`"r5"`) kaydırır, o da fiyat listesi eşleştirmesinin ikinci
katmanını (E10) bozar ve geçmiş belgelerin bağlarını riske atar. Poz listesi değişecekse sözleşme
**yeni bir kodla** yüklenir, eskisi pasife çekilir. `aktif = false` yalnızca "yeni iş açılabilir mi"
sorusuna uygulanır (`SozlesmeSorgulari.aktifSozlesmeId`); varlık kontrolüne uygulanmaz, yoksa pasif
sözleşmenin **açık belgeleri** poz listesini çekemez hâle gelirdi.

### L4 — Parse yeniden yazılmadı

Sözleşme yükleme `:tools:contract-generator`'ın `ContractXlsxParser`'ını kullanıyor; dosya açma,
sayfa seçimi ve POI istisnalarının 400'e çevrilmesi `XlsxOkuyucu`'da tek yerde ve iki uç onu
paylaşıyor. Fiyat şablonu yönetim varyantında **dolu** iniyor (`mevcutFiyatlariYaz`): değişecek olan
genellikle birkaç satır, 95 kalemi elden yazdırmak yeni bir hata kaynağı olurdu.

**DoD**
- [x] `V8__sozlesme_yonetimi.sql`: sözleşme kaynak izleri, `fiyat_versiyonu.sira`, taslak tekillik indeksi
- [x] Yönetici xlsx yükleyip yeni sözleşme + poz listesi + referans fiyat sürümü oluşturabiliyor — tek transaction
- [x] Önizleme hiçbir şey yazmıyor; kod çakışması 409, bozuk dosya 400 (500 değil)
- [x] Satır içi fiyat düzenleme (`,` ve `.` ikisi de) ve Excel ile toplu doldurma aynı taslağa yazıyor
- [x] **Yayın öncesi açılmış belgenin toplamı yayından sonra değişmiyor** — fazın asıl sözleşmesi
- [x] **Aynı belgenin poz listesi hâlâ eski fiyatı gösteriyor** — ekran ile kayıt aynı sürümü okuyor
- [x] Yayından sonra açılan belge güncel fiyatı kullanıyor
- [x] Yayınlanmış sürüme yazma yolu yok (taslak yokken 409); vazgeçme yayındakine dokunmuyor
- [x] Pasife çekmek yeni işi engelliyor ama açık belgeleri kırmıyor
- [x] Yetki: yönetim uçları yalnızca yöneticide, poz listesi hâlâ bölge sorumlusunda
- [x] `SIFIRLA=1 bash deploy/dogrula-faz-l.sh` temiz — *87 kontrol, 0 düşen*
- [x] `dogrula.sh` (A/B), `-d` (80), `-e` (65), `-f` (49), `-g` (37), `-h` (94), `-i` (88), `-j` (71)
      hâlâ temiz — gerileme yok
- [x] `./gradlew ktlintCheck test` ve `cd web && npm run build` temiz

---

## DÜZELTME TURU 1 — Kullanıcı Testi Geri Bildirimleri  ·  `31 Temmuz 2026`  ·  `Durum: ✅`

Faz H ve I bittikten sonra ekranlar **ilk kez elle test edildi**. Çıkan 12 madde burada kapatıldı;
Faz J'ye (mobil) bu turdan sonra geçiliyor. Doğrulama ayrı bir betiğe alınmadı — kontroller ilgili
fazın betiğine eklendi (`dogrula-faz-h.sh` 92, `-i.sh` 88 kontrol).

### Bulunan hatalar

| # | Ne oldu | Kök neden |
|---|---|---|
| 1 | ⚠️ **Geçersiz girdiyle belge gönderiliyordu** — kullanıcı `qwdqwd` yazdı, kayıt reddedildi ama belge yine de kapandı | `gonder()` `kaydet()`'in başarısızlığını yok sayıyordu; `kaydet()` `void` dönüyordu. Artık `Promise<boolean>` ve gönderme durur. Gönderme geri alınamaz (D2), bu yüzden en kritik madde |
| 2 | Yetkisiz kullanıcı `#/kullanicilar` açtığında sonsuz "Yükleniyor…" | Erken `return <Yukleniyor/>` hata şeridinin üstündeydi; hata hiç çizilmiyordu. `HakedisEkleri`'nde de aynı kusur vardı |
| 3 | Gönderilmiş belgede "Kaydedilmemiş değişiklik var" yazılı kalıyordu | 1'in yan etkisi; ayrıca kaydetme durumu artık yalnızca taslakta gösteriliyor |
| 4 | Aynı türden iki iptal belgesi ekranda **iki özdeş "İptal Edildi"** olarak görünüyordu | Rozet yalnızca durumu yazıyordu; artık yanında tür de yazıyor (`Ön Fizibilite · İptal Edildi`) |

### Kullanıcı kararları

| Konu | Karar |
|---|---|
| "Her satırda parolayı göster" | **Yapılmadı.** Parolalar BCrypt özeti; düz metin hiçbir yerde saklanmıyor ve geri döndürülemez. Kullanıcı: *"şifreyi unuturlarsa yeniden oluştururlar, şu anki güvenli hâlini bozmayalım."* |
| Hakedişte keşif sütunları | **Şimdi yapıldı** (Faz K'den öne çekildi); sapma sütunu Faz K'de kaldı |
| Gönderme ön koşulları | Yalnızca "Gönder" kilitlendi, imza tuvali serbest; eksikler madde madde yazılıyor ve eksik kartlar kırmızı kenarlık alıyor |
| İptal edilen işlerin silinmesi | **Silme yok** (D5/3 korundu); iş listesi süzgecinin varsayılanı "Aktif" yapıldı |

### Yapılanlar

- [x] `gonder()` artık `kaydet()` başarısızsa **durur**; katman hiç açılmaz
- [x] Miktar kutusuna **maske**: rakam ve tek ayırıcı dışında karakter yazılamıyor; geçersiz hücre kırmızı kenarlık + `aria-invalid`, üstteki şerit hangi pozların bozuk olduğunu yazıyor
- [x] Hakediş **Gönder** düğmesi miktar + EK1 + imza sağlanana kadar pasif; `HakedisEkleri` ve `ImzaTuvali` durumu üst bileşene bildiriyor (callback'ler `useCallback` ile sabit — yoksa çocuklarda sonsuz istek döngüsü)
- [x] **Kable seçici**: tıklayınca bölgenin tüm listesi açılıyor, arama **istemcide** süzüyor (metin araması; yetki süzgeci yine SQL'de). En kalabalık bölge 309 kayıt, toplam 943
- [x] **Gönderim katmanı**: X düğmesi + "İşler Sayfasına Git"; otomatik kapanma kaldırıldı (2 sn yönlendirme düğmesine basmaya yetmiyordu)
- [x] İş listesinde **bölge sütunu** + **iş durumu / bölge süzgeçleri** (sunucuda: `GET /api/isler?durum=&bolgeKodu=`)
- [x] **Hakedişte tek PDF / tek Excel** — fiyatlı/fiyatsız ayrımı yalnızca fizibilite ve keşifte kaldı; sunucu ucu değişmedi
- [x] **Hakediş çıktısında "1. Keşif Talep" + "1.Keşif Tutar"** ve toplam satırında keşif genel toplamı
- [x] **Kullanıcı adı güncelleme** ucu + arayüz (`PUT /api/kullanicilar/{id}/kullanici-adi`)
- [x] `dogrula-faz-h.sh` (73 → **92**) ve `-i.sh` (76 → **88**) genişletildi

### Sapmalar ve gerekçeleri

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| Kullanıcılar ekranı rozet rengi | "ters, düzeltilecek" | **düzeltilecek bir şey yoktu** | İnceleme sırasında `aktif ? 'kapali' : 'iptal'` ters sanıldı; `kapali` sınıfı zaten **yeşil** basıyor, yani renkler doğruydu. Yalnızca sınıf **adı** yanıltıcıydı — `aktif` takma adı eklendi, davranış değişmedi |
| Hakediş PDF sayfası | *(planda yok)* | **A4 yatay** | 8 sütun + yuvarlanmamış 17 karakterlik birim fiyat dikey sayfaya sığmıyor; hücre sarınca `Br Fiyat × Miktar = Tutar` kontrolü okunamaz hâle geliyor. `PdfUreticiTest` fiyatın **tek parça** bulunabildiğini doğruluyor — dikey kalsaydı bu test kırılırdı. Diğer türler dikey kaldı |
| Excel toplam satırı | *(planda yok, incelemede çıktı)* | **`toplamEtiketSutunu` eklendi** | "Toplam" etiketi `tutarSutunu - 1`'e yazılıyordu; hakedişte bu **tam "1.Keşif Tutar" sütunu**. O sütun veriyle dolunca etiket keşif toplamının üzerine oturuyordu — hata vermeden, sessizce. Etiket artık "İş Kalemi" sütununda |
| Keşif genel toplamı | "listelenen satırlardan" | **keşif belgesinin kendi toplamı** | PDF yalnızca hakediş miktarı olan satırları listeliyor; listelenenlerden toplamak, keşif belgesinde yazan sayıdan farklı bir toplam üretirdi. İki belgeyi yan yana koyan kişi iki farklı rakam görürdü |
| Keşif tutarının fiyatı | *(belirtilmemiş)* | **keşifin kendi dondurulmuş fiyatı** | Keşif ve hakediş farklı fiyat versiyonlarına çivilenmiş olabilir (V6). Hakedişin fiyatıyla hesaplanan bir "1.Keşif Tutar" sütunu, keşif belgesinde yazandan farklı çıkar — yani yalan söylerdi. `ExcelUreticiTest` iki farklı fiyatla bunu kilitliyor |
| Keşif belgesi join'i | `unique (is_id, tur)` | **`durum <> 'IPTAL'` koşulu zorunlu** | V2'deki tam unique kısıt V5'te kaldırıldı, yerine `belge_is_tur_ux ... where durum <> 'IPTAL'` kısmi indeksi geldi. Koşulsuz join, iptal edilmiş keşif belgeleri yüzünden satırları çoğaltırdı |
| Doğrulama betiği | yeni `dogrula-faz-l.sh` | **mevcut betikler genişletildi** | Kontroller ait oldukları fazın betiğinde daha kolay bulunuyor; sekizinci bir betik ayrıca kurulum ve sıfırlama yükü getirirdi |

> ⚠️ **Bulunan yan etki:** elle test sırasında `bursa` kullanıcısının parolası Kullanıcılar ekranından
> değiştirilmişti ve doğrulama betikleri seed parolasıyla giriş yapamaz oldu. Kod hatası değil, veri
> durumu — yeni kullanıcı adı/parola ucuyla geri yüklendi. Betiklerin sabit kullanıcı adlarına bağlı
> olduğu `dogrula-faz-i.sh` başında zaten yazılı; parola için de aynısı geçerli.

**DoD**
- [x] `./gradlew ktlintCheck test` temiz — *204 test, 0 hata*
- [x] `./gradlew :app:compileDebugKotlin` temiz — domain'e eklenen alanlar **default'lu**, mobil derlemesi bozulmadı (Faz J'nin önü açık)
- [x] `dogrula-faz-h.sh` **92**, `-i.sh` **88** kontrol, 0 düşen
- [x] Gerileme yok: `dogrula.sh` (A/B), `-d` (80), `-e` (65), `-f` (49), `-g` (34)
- [x] Gerçek belgeyle doğrulandı: hakediş Excel'inde `E=2 · F=5 · G=1.000,00 · H=2.500,00`, toplam satırında keşif toplamı; PDF **841×595 (yatay)** ve keşif başlıkları metin olarak bulunabiliyor
- [ ] **Tarayıcıdan elle:** `docs/test-senaryolari.md` — Faz H/I'nin açık maddesiyle birlikte

---

## DÜZELTME TURU 2 — Mobil Kullanıcı Testi + Otomatik Arşiv  ·  `31 Temmuz 2026`  ·  `Durum: ✅`

Faz J emülatörde ve tarayıcıda elle test edildi; kullanıcı **sekiz madde** bildirdi, ardından
dokuzuncu bir gereksinim ekledi (belgelerin diske otomatik yazılması). Sekiz maddenin çoğu tek bir
kök nedene dayanıyordu: **ekranlar bir kez yüklenip bir daha yenilenmiyordu.**

### Kullanıcı kararları

| Konu | Karar |
|---|---|
| İş listesi varsayılanı | **"Tümü"** — mobil ve web *(Düzeltme Turu 1'in "Aktif" kararı geri alındı)* |
| Güncellik | Ekrana dönünce otomatik + elle yenileme; **otomatik yoklama yok** |
| İndirme | **Sistem "kaydet" diyaloğu** (SAF) — minSdk 26 korunuyor |
| İş oluşturma sonrası | **Doğrudan iş detayına**; ara ekran kaldırıldı |
| Arşive yazma anı | **Gönderildiğinde + onaylandığında** |
| Arşive yazılan | Yalnızca **FİYATLI** PDF + Excel |
| Arşiv klasör düzeni | `{ARSIV_KOK}/YedekTTI/{BÖLGE}/{On_Fizibilite\|1_Kesif\|Hakedis}/` |
| Arşiv dizini seçimi | **Kurulum betikleri** (`kur.sh` / `kur.ps1`) + sunucu tarafında OS varsayılanı |

### Bulunan hatalar ve kök nedenleri

| # | Ne oldu | Kök neden |
|---|---|---|
| 1 | İş listesi "Aktif" ile açılıyordu | Düzeltme Turu 1 kararı sahada ters işledi: az önce açılan iş listede görünmeyince kullanıcı "kayıt kayboldu" sanıyor. İptal nadir, görünmeyen kayıt her seferinde tedirgin ediyor |
| 2 | ⚠️ **Yeni iş listede belirmiyordu** (mobil **ve** web) | Mobilde `ViewModel` gezinti yığınında korunuyor: geri dönüldüğünde `init` yeniden koşmuyor, veri eski kalıyordu. Webde ise aynı rotaya gidildiğinde `hashchange` üretilmiyor — üst şeritteki "İşler"e zaten oradayken basmak hiçbir şey yapmıyordu |
| 3 | İş oluşturduktan sonra boş bir ara ekran | "Açıldı" bilgisini vermek için ayrı sayfa; kullanıcıyı bir tık fazla bekletiyordu. Kaldırıldı, uyarılar iş detayında şerit oldu |
| 4 | ⚠️ **Miktar alanına harf yazılabiliyordu** | `KeyboardType.Decimal` yalnızca klavyeyi seçiyor, girişi kısıtlamıyor; kullanıcı klavyeyi harfe çevirip `qwdqwd` yazabiliyordu. Web'de maske vardı, mobilde yoktu |
| 5 | Onaydan sonra ekranda hiçbir şey olmuyordu | Tek gösterge durum rozetinin sessizce değişmesiydi. Onay geri alınamaz (aynı işe ikinci liste 409) — gönderim için yazılmış tik animasyonlu katman genelleştirilip burada da kullanıldı |
| 6 | Yönetici onayı tablette görünmüyordu | 2 ile aynı kök: belge ekranı bir kez yükleniyordu |
| 7 | Tablette belge indirilemiyordu | Yalnızca önizleme + "Paylaş"/"Aç" vardı; dosya `cacheDir`'de kalıyordu, kullanıcının erişebileceği bir yere kaydedilmiyordu |
| 8 | PDF/Excel bölümü sayfanın ortasında | Webde indirme düğmelerini taşıyan `Onizleme`, `TamamlananEkler` ve iptal panelinin üstündeydi |
| 9 | Belgeler diske yazılmıyordu | Arşiv aynası **yalnızca biri indirdiğinde** tetikleniyordu (`CiktiServisi.yayinla`). Oysa arşivin varlık nedeni uygulamaya ulaşılamayan durum — "önce birinin indirmiş olması" koşulu o amaçla çelişiyordu |

### Yapılanlar

- [x] Liste varsayılanı "Tümü" (mobil + web)
- [x] **Yenileme:** mobilde `LifecycleResumeEffect` ile ekrana dönünce, listede aşağı çekerek
      (`PullToRefreshBox`), detay/belge ekranında "Yenile" düğmesiyle. Webde aynı rotaya gidiş de
      ekranı yeniden bağlıyor (`yonlendirme.ts`) ve sekme odağa gelince liste tazeleniyor
      (`useOdaktaTazele`)
- [x] Belge ekranında **kaydedilmemiş giriş varken yenileme atlanıyor** (`refreshIfIdle`) — yoksa
      yenileme kullanıcının yazdığı miktarı ezerdi
- [x] Miktar alanına **karakter süzgeci** (rakam + `.` + `,`)
- [x] İş oluşturma sonrası ara ekran kaldırıldı; uyarılar rota parametresiyle iş detayına taşınıyor
- [x] `GonderimOnayi` → **`IslemOnayi`** olarak genelleştirildi; onay akışında da kullanılıyor
- [x] Mobilde **"İndir"**: `ActivityResultContracts.CreateDocument` ile sistem kaydetme ekranı
- [x] Webde indirme bölümü **en alta** alındı
- [x] **`OtomatikArsivleme`**: `BelgeServisi` gönderim ve onayda olay yayımlıyor, dinleyici
      `AFTER_COMMIT`'te sınırlı bir havuzda PDF + Excel üretip arşive yazıyor
- [x] Arşiv ağacı `YedekTTI/{BÖLGE}/{TÜR}` oldu; yıl basamağı kalktı (yıl zaten dosya adında)
- [x] `deploy/kur.sh` + `deploy/kur.ps1`; `ArsivDizini`/`YuklemeDizini` OS varsayılanına düşüyor
- [x] **`docs/uretim-notlari.md`** yazıldı (işletme notları)
- [x] Betikler genişletildi: `-g` 34 → **37**, `-h` 92 → **94**, `-j` 64 → **71**

### Sapmalar ve gerekçeleri

| Konu | Planda | Uygulanan | Neden |
|---|---|---|---|
| Klasör adları | "ön fizibilite, 1.keşif, Hakediş" | **`On_Fizibilite`, `1_Kesif`, `Hakedis`** | ASCII ve boşluksuz. Klasör adı Docker bind mount'undan, yedekleme betiklerinden ve Windows'tan geçiyor; Türkçe karakter ve boşluk bu yollarda kodlama/tırnak hatası üretiyor. Okunabilirlik korundu, kırılganlık alınmadı |
| Yıl klasörü | *(belirtilmemiş)* | **kaldırıldı** | Kullanıcı ağacı `YedekTTI/{bölge}/{tür}` olarak tarif etti; yıl zaten dosya adında (`TTI_BURSA_2026_KESIF_001_FIYATLI.pdf`), ayrı klasör bir tıklama derinliği daha ekliyordu |
| Yetki süzgeci | *(planda yok)* | `CiktiServisi` süzgeci **parametre** olarak alıyor | Otomatik arşivleme arka planda, oturumsuz koşuyor; `OturumKullanicisi.bolgeSuzgeci()` orada patlardı. Süzgeç yine SQL'e gidiyor (§7/3), yalnızca kaynağı açık hâle geldi. Yetki olayı doğuran `gonder`/`onayla` isteğinde zaten doğrulanmış |
| Tetikleme | doğrudan çağrı | **Spring olayı + `AFTER_COMMIT`** | Aynı transaction içinde üretilseydi, geri alınan bir gönderimin çıktısı arşivde kalırdı. Olay ayrıca `BelgeServisi`'nin çıktı üretimini tanımasını gerektirmiyor |
| Arşiv engel testi | belgenin yılı 9999 yapılıyordu | **hedef dosyanın yerine klasör konuyor** | Yıl basamağı ağaçtan kalkınca eski yöntem işlemez oldu. Yeni yöntem veritabanına hiç dokunmuyor — `tur`/`bolge_id` değiştirmek `belge_durum_tur_tutarli` gibi kısıtlara çarpıyor |
| Mobilde çıktı yerleşimi | "değişiklik yok, Çıktı zaten en altta" | **düğmeler önizlemenin üstüne alındı, önizleme sınırlandı** | Emülatörde görüldü: A4 oranındaki tam boy önizleme ekranın üç katı yer kaplıyor ve "İndir"i görünmez hâle getiriyordu — bölüm en altta olsa da düğmeler ulaşılamazdı. Önizleme 420 dp ile sınırlandı; "doğru belge mi" sorusunu cevaplamak için ilk ekran yetiyor |
| `kur.sh` davranışı | `.env`'i doldurur | **dolu değeri değiştirmez** | Çalışan bir kurulumun arşivini sessizce başka klasöre taşımak, eski dosyaları kimsenin bakmadığı bir yerde bırakırdı. Betik yalnızca boş/eksik anahtarı doldurur |

**DoD**
- [x] `./gradlew ktlintCheck test` temiz
- [x] `cd web && npm run build` temiz (`tsc --noEmit` dahil)
- [x] `dogrula-faz-g.sh` **37**, `-h` **94**, `-j` **71** kontrol, 0 düşen
- [x] Gerileme yok: `dogrula.sh` (A/B), `-d` (80), `-e` (65), `-f` (49), `-i` (88)
- [x] **Gönderilen belge indirilmeden arşive düşüyor** ve TEKLIF kopyası oluşmuyor — betikle kilitli
- [ ] **Tarayıcıdan ve tabletten elle:** `docs/test-senaryolari.md`

---

## SADELEŞTİRME TURU KARARLARI  *(3 Ağustos 2026, yöneticiden gelen istekler)*

Yönetici sekiz madde bildirdi; hepsi uygulandı. Aşağıdaki her karar 30 Temmuz kurgusunun bir
parçasını **geri alıyor** — o kurgu yanlış değildi, sahada fazla ağır kaldı.

### S1 — Onay akışı kalkıyor  *(Faz M; E1/E2/E4/E5'i düşürür)*
Fizibilite yöneticiye düşüp pazarlık fiyatları yüklenene kadar keşif açılamıyordu ve bu döngü
günlerce sürüyordu. `DocumentStatus` beş değerden **üçe** indi (`TASLAK`, `TAMAMLANDI`, `IPTAL`);
`ONAY_BEKLIYOR`/`ONAYLANDI`, `onaylayan_id`, `onay_tarihi` ve onay kuyruğu ekranı silindi.

### S2 — İşe özel pazarlık fiyat listesi kalkıyor  *(Faz N; E1'i düşürür)*
Onayı yazan tek kod yolu ölünce sürümü yazan yol da öldü. `fiyat_versiyonu.is_id` şemadan çıktı;
üç belge de **kendi sözleşmesinin** yayındaki sürümünü kullanıyor.
⚠️ `drop column is_id`, predicate'inde o sütunu kullanan iki kısmi indeksi **sessizce** düşürdü;
V10 yayın tekilliğini yeniden kurar. Kurmasaydı bir sözleşme iki `YAYINDA` sürüme sahip olabilir ve
`referansFiyatVersiyonuId` `optional()` üzerinde patlardı.

### S3 — Sözleşme işin değil, belgenin  *(Faz N)*
Üç aşama farklı sözleşme seçebilir. Belgeye yeni bir `sozlesme_id` sütunu **açılmadı**: sözleşme
`fiyat_versiyonu.sozlesme_id` üzerinden türüyor ve `belge.fiyat_versiyonu_id` `NOT NULL` kalıyor —
"belge daima bir fiyat sürümüne çivilidir" değişmezinde boşluk açılmadı. Bunun sonucu: **sözleşme
belge açılırken sorulur**, sonradan değil.
Sözleşme değişirse **girilmiş miktarlar silinir**: poz kimliği (`satir_kodu`) sözleşme içinde
anlamlıdır, başka bir sözleşmede aynı kod bambaşka bir kaleme denk gelir.

### S4 — İş durum makinesi dörde çıkıyor  *(Faz O)*
`ACIK` · `ISLENIYOR` · `KAPANDI` · `IPTAL`; `null` = fizibilite bekleniyor. Beşinci bir `YENI`
değeri yerine `null` seçildi — "henüz durumu yok" bir durum değil, durumun yokluğu.

| Geçiş | Kim | Ne zaman |
|---|---|---|
| `null → ACIK` | otomatik | fizibilite belgesi açılınca |
| `ACIK → ISLENIYOR` | yönetici | "İşleme Al" |
| `ACIK\|ISLENIYOR → IPTAL` | yönetici | gerekçe zorunlu |
| `ISLENIYOR → KAPANDI` | otomatik | hakediş gönderilince |

Aşama kapısı buna bağlandı: fizibilite `null|ACIK`'ta, keşif ve hakediş yalnızca `ISLENIYOR`'da.
`ISLENIYOR`'a geçiş **fizibiliteyi taslak hâlde de kilitler** — kullanıcı kararı; arayüz onay
sorusuyla bunu görünür kılıyor. Geri dönüş yok: kilitlenen belge yeniden açılabilseydi
"gönderilmiş içerik değişmez" (D2) delinirdi.

⚠️ **İptal `ISLENIYOR`'dan da mümkün.** Kullanıcının ilk ifadesi "yönetici yalnızca `ACIK` işlerde
yetkili" idi; sorulduğunda genişletildi. Yalnızca `ACIK`'tan iptal edilebilseydi sahada düşen bir
proje listede sonsuza kadar açık kalırdı.

### S5 — SP No üretilir  *(Faz O; D1'i düşürür)*
`2026-BURSA-001` — bölge + yıl başına artan sayaç (`is_sayac`, `BelgeNumaraUretici` kalıbı).
~~D1: aynı SP ile ikinci iş açılabilmeli~~ gerekçesi düştü; numara artık **tekil**.
Elle girilmiş eski numaralar `is_kaydi.eski_sp_no` sütununda saklandı.

### S6 — Bölge sıra kuralı kalkıyor  *(Faz M; D3(b) ve E3'ü düşürür)*
"Bir bölgede aynı türden tek açık belge" kısıtı, bölge sorumlusunu aynı anda birden fazla işi
ilerletmekten alıkoyuyordu ve kullanıcı sırayı ancak 409 hatasını görünce öğreniyordu. `belge_acik_tur_ux`
indeksi düştü; sırayı artık işin durumu gösteriyor.

### S7 — İmza kalkıyor  *(Faz M; E8'i düşürür)*
`belge_imza` tablosu, imza uçları, tuval bileşenleri ve PDF imza bloğu silindi.
Diskteki `IMZA.png` dosyaları **silinmedi**: yükleme kökü asıl veridir (CLAUDE.md §7/6).

### S8 — Yuvarlama tersine döndü  *(Faz P; §5'in 30 Temmuz kararını düşürür)*
Birim fiyat **çivilenirken** `round(..., 2)` ile yuvarlanıyor; gösterim, hesap ve çıktı aynı sayıyı
kullanıyor. 30 Temmuz'daki "ham fiyat göster" kararı aynı sorunun (`371,87 × 2 = 743,74` ama tabloda
`743,73`) diğer çözümüydü; 17 haneli fiyat çıktıda okunmadığı için ters yöne çevrildi.
`MoneyFormatter.moneyExact` kaldırıldı.
⚠️ Aynı `round(..., 2)` **dört sorguda** olmalı (`satirYaz`, `pozlar`, `belgeninPozlari`,
`CiktiSorgulari.satirlar`); biri ayrışırsa ekranda girilebilir görünen satır başka fiyatla yazılır
ve hiçbir hata çıkmaz. `dogrula-faz-r.sh` bunu sayıyor.

### S9 — Miktar Excel'i  *(Faz P)*
Şablon indir → doldur → yükle. **Fiyat listesi altyapısı yeniden kullanıldı**, ikinci bir okuyucu
yazılmadı: `FiyatSablonu → PozSablonu`, `FiyatListesiOkuyucu → MiktarOkuyucu`,
`FiyatListesiEslestirici → MiktarEslestirici`. E10'un dört katmanlı savunması **aynen korundu** —
yanlış satıra yazılan bir miktar da doğrudan para hatasıdır.
Miktara özgü iki fark: boş hücre hata değil ("girilmemiş"), `0` geçerli bir miktar.
Şablon **mevcut miktarlarla dolu** iner; boş inseydi ekranda birkaç satır girmiş kullanıcı şablonu
yükleyerek girdiklerini silerdi (yükleme tam değiştirmedir).

### S10 — Güzergâh dosyası üç belgede de zorunlu  *(Faz P; açık soru 6'yı yeniden cevaplar)*
EK1 artık fizibilite ve keşifte de isteniyor. EK2 opsiyonel kaldı.

### S11 — Kablo bölge kısıtı kalkıyor  *(Faz O)*
Her kullanıcı her kabloyu arayıp seçebilir. Buna karşılık **liste artık toptan çekilmiyor**:
envanter 900+ satır ve arayüz `mtc` ön dolu bir kutuyla sunucudan arıyor (min. 3 karakter).
İşin bölgesi kablodan değil, **işi açan kullanıcıdan** geliyor — yetki ayrımı orada korunuyor.
Yöneticinin bölgesi olmadığı için iş açamaz (409); iş açmak saha işidir.

### S12 — İki ekran silindi  *(Faz R/S)*
**İş Detayı** ve **Sözleşme Detay** kaldırıldı. İş listesi satır bazlı bir çalışma ekranına dönüştü:
her iş bir kart, altında üç aşama satırı (tarih · değiştirilme · güncelleyen · toplam ·
[Güncelle] [Gözlemle]). Yöneticinin referans fiyat düzenleme akışı da bu turda düştü; sözleşme
sayfasında yalnızca **Sözleşme Yükle** ve **Sözleşme Formatını İndir** kaldı.

### S13 — Doğrulama betikleri konsolide edildi  *(Faz T)*
`dogrula-faz-d/e/f/g/h/i/j/l.sh` **emekliye ayrıldı**: hepsi eski akışa (onay, imza, işe özel fiyat,
elle SP No, iş detay ekranı) bağlıydı ve API sözleşmesi değişince çalışmaz hâle geldi. Yaşayan
iddiaları iki betiğe taşındı: `dogrula-faz-mnop.sh` (uçtan uca, sunucu gerektirir) ve
`dogrula-faz-r.sh` (kaynak kod güvenceleri + web derlemesi, sunucu gerektirmez).
Faz başına ayrı betik yazılmadı: M–P birbirine bağlı tek bir değişiklik ve dört betik aynı 80
satırlık hazırlık bloğunu dört kez taşırdı.

---

## DÜZELTME TURU 3 — Arayüz Netleştirme (web)  ·  `6 Ağustos 2026`  ·  `Durum: 🚧`

Kullanıcı web arayüzünün **güçlendirilmesini** istedi: "basitlik, anlaşılabilirlik, kullanım
kolaylığı — overengineering yapmadan". Arayüz işlevsel olarak tam; sorun **kendini anlatmaması**.

### Kullanıcı kararları

| Konu | Karar |
|---|---|
| Açılış ekranı | **Projeler** (`#/` → iş listesi). "Belgeler" ikinci sekmede arşiv olarak kalır |
| Kapsam | **Orta** — gezinme, belge ekranı adımları, form doğrulama, liste durumları. Ekran mimarisi değişmez |
| Görsel | **Ölçülü tazeleme** — boşluk ölçeği, hafif gölge, başlık hiyerarşisi. §8 paleti sabit |

### Bulunan sorunlar ve kök nedenleri

| # | Ne oluyor | Kök neden |
|---|---|---|
| 1 | Giriş sonrası 10 sütunlu arşiv açılıyor | `yonlendirme.ts` geri düşüşü `belgeler`; oysa CLAUDE.md §9 çalışma ekranını **Proje Listesi** olarak tanımlıyor |
| 2 | Hangi ekranda olduğun belli değil | Dört sekme de aynı `button.sade`; etkin durum yok. `stil.css`'in 827 satırında `button` için **hiç** `:focus-visible` kuralı yok |
| 3 | Belge ekranındaki üç adım görünmüyor | Sıra yalnızca `{/* 1 — Sözleşme */}` kod yorumlarında; ekranda eşit ağırlıkta dört kart var |
| 4 | Gönderme engelleri engellediği şeyden uzakta | `eksik-listesi` alt sabit şeritte, tarif ettiği boş miktar hücresinden ~900px ötede; aynı şeritte toplam ve kaydetme durumu da yarışıyor |
| 5 | Boş form kırmızıyla açılıyor | `Giris`/`YeniIs` doğrulamayı "dokunuldu mu" bilgisine bağlamıyor; kullanıcı davranmadan suçlanıyor |
| 6 | ⚠️ **Hiçbir yerde "Tekrar dene" yok** | Her ekran yükleme hatasını elle basıyor; §8 bunu açıkça istiyor ama ortak bir parça olmadığı için hiçbiri yapmamış |
| 7 | ⚠️ **Başarısız ek indirmesi iz bırakmıyor** | `BelgeEkleri:129` `catch`'siz serbest promise |
| 8 | ⚠️ **`TamamlananEkler` hatada sonsuz "yükleniyor"** | `if (!ekler) return <Yukleniyor/>`, `hata` kontrolünden **önce** çalışıyor; tek hata render'ı başarı dalının içinde |
| 9 | Başarısız silme iki kırmızı şerit basıyor | `Sozlesmeler`'de aynı `hata` state'i hem modalda hem sayfada render ediliyor |
| 10 | "Hesabı Kapat" onaysız | Uygulamadaki tek onaysız yıkıcı işlem; diğer üç yer `.katman` modalı kullanıyor |
| 11 | §8 "küçük font yasak" ihlali | `.is-alani-etiket` 13px, `.asama-ozet` 14px, `.rozet` 14px |

### Yapılacaklar

- [x] **W1 — Gezinme:** `#/` → Projeler; `#/belgeler` için açık dal (geri düşüş değişince arşiv
      erişilemez hâle gelirdi); `etkinSekme()`; sekmeler gerçek `<a href>`; `:focus-visible`
- [x] **W2 — Ortak parçalar:** `YuklemeHatasi` (8 çağrı), `SayfaBasligi` (6), `BosListe` (3),
      `useKatman` (5), `Yukleniyor`'a halka. CSS jetonları (boşluk/gölge/z-index/türev renkler),
      font uyumu, `.bos-not` · `.kart.yikici` · `.dugme-notu`
- [x] **W3 — Belge ekranı 3 adım:** numaralı adım başlıkları; engeller `Eksik = {adim, mesaj}`
      tipiyle ait oldukları adıma taşınıyor; alt şerit toplam + Gönder + sayaça iniyor
- [x] **W4 — Doğrulama zamanlaması:** kırmızı yalnızca dokunulmuş alanda; "neden kapalı" nötr
      `.dugme-notu` ile ilk render'dan itibaren görünür; `IsListesi`'nde kapalı düğme nedenini yazıyor
- [x] **W5 — Liste ekranları:** `BelgeListesi` satır tıklaması → gerçek bağlantı (iki
      `stopPropagation` düşüyor); indirme hatası sebep + tekrar; `Sozlesmeler` çift şerit;
      `Kullanicilar` hesap kapatma onayı; `BelgeEkleri` sessiz hata

### Yolda bulunan üç sessiz kusur *(planda yoktu, kod okunurken çıktı)*

| Dosya | Kusur | Belirtisi |
|---|---|---|
| `TamamlananEkler.tsx` | `if (!ekler) return <Yukleniyor/>` hata kontrolünün **üstündeydi**; tek hata render'ı başarı dalının içindeydi | Başarısız `api.ekler` çağrısı ekranı sonsuza kadar "Ek dosyalar yükleniyor…"da bırakıyordu |
| `BelgeEkleri.tsx` | `onClick={() => api.ekIndir(ek.id)}` — `catch`'siz serbest promise | Başarısız ek indirmesi ekranda **hiçbir iz bırakmıyordu**; düğmeye basılıyor, hiçbir şey olmuyordu |
| `Sozlesmeler.tsx` | Aynı `hata` state'i hem katmanda hem sayfada render ediliyordu | Başarısız silme (409) iki özdeş kırmızı şerit boyuyordu |

Ayrıca `PozTablosu`'ndaki `Br Fiyat` sütunu 210px'ti ve yorumu "14 ondalığa kadar" diyordu — Faz P
fiyatı 2 haneye yuvarladığından beri bayattı; 130px'e indi, kazanılan yer iş kalemi metnine gitti.

### Bilinçli olarak yapılmayanlar

| Öneri | Neden yapılmadı |
|---|---|
| Birleşik `<Katman>` modal bileşeni | `.katman` `role="dialog"`, `.gonderim-katmani` `role="status" aria-live`; birleşince dört mod bayraklı bir bileşen doğuyor. Ortak **davranış** `useKatman` hook'u olarak çıktı, ortak **görünüm** zaten CSS'te |
| `<Alan>` / `<Buton>` / `<Tablo>` | Değişen kısım ortak kısımdan büyük; CSS zaten yapıyor (§13 spekülatif soyutlama) |
| Focus trap | Bağımlılıksız ~40 satır; 7 iç kullanıcı, tablet öncelikli araç, klavye-only gereksinimi yok |
| `AdimBasligi`'nın `bilesen.tsx`'e taşınması | Üç çağrı yerinin üçü de `BelgeEkrani.tsx` içinde — tek tüketicili "ortak" katman §13'ün adını koyduğu şey |
| Üstte adım rayı | Numaralı üç kart zaten ilerleme göstergesi; ray durumu söyleyen dördüncü yer olurdu. Ayrıca `href="#adim-2"` gibi sayfa içi çapa **hash yönlendiriciyi ele geçirir** |
| `--is-durum`/`--is-islem`'in grid'e çevrilmesi | `stil.css:645-657` ortak `grid-template-columns` denemesinin neden tutmadığını zaten kayıt altına almış; yeniden açmak saf risk |
| `Kullanicilar` boş durumu | Kullanıcı kümesi 7+1 sabit; boş liste sunucu arızası demektir, boş durum değil |
| Üç satır düğmesini küçültmek | §8'in 48px minimumu pazarlık konusu değil. Yalnızca en uzun etiket kısaldı |

**DoD**
- [x] `cd web && npm run build` temiz (`tsc --noEmit` dahil)
- [x] `dogrula-faz-r.sh` **26 kontrol, 0 düşen** — `web/src`'te çıplak `imza`, `parseFloat`,
      `.toFixed(`, `'kuyruk'`, `ONAY_BEKLIYOR|ONAYLANDI` yok
- [x] Dört korunan dosya adı yerinde: `SozlesmeSecici` · `SozlesmeKarti` · `MiktarExcel` · `DurumPaneli`
- [x] Kotlin tarafı değişmedi (`ktlintCheck test` etkilenmiyor)
- [ ] **Tarayıcıdan elle:** `#/belgeler` hâlâ arşivi açıyor · etkin sekmeye tekrar basınca liste
      tazeleniyor · "Hakediş İşlemi Gönderildi" rozeti sarmıyor ve aşama rozeti başlıktaki "Durum"
      sütununun altında kalıyor · üç adımın durumu miktar/dosya girildikçe yeşile dönüyor ·
      `#/isler/yeni` açılışta kırmızı göstermiyor · katmanlar Escape ile kapanıyor, "Gönderiliyor…"
      iken kapanmıyor

---

## DÜZELTME TURU 4 — Görsel Yeniden Tasarım (web + mobil)  ·  `6 Ağustos 2026`  ·  `Durum: 🚧`

Kullanıcı iki mockup paylaştı: biri **web** (koyu lacivert, sürekli açık kenar çubuğu), biri
**Android tablet** (alt sekme çubuğu). Ortak dil: koyu gezinme yüzeyi, durum rengine göre sol vurgu
şeridi taşıyan proje kartları, **üç aşamalı stepper**, kimlik ve tutarlarda tek aralıklı yazı tipi,
dört **anlamsal** durum rengi. Düzeltme Turu 3 arayüzü netleştirmişti; bu tur görsel dili değiştiriyor.

### Kullanıcı kararları

| Konu | Karar |
|---|---|
| Kapsam | **Web + Android**, tek ortak palet |
| Palet | Mockup'ın mavi tonları; CLAUDE.md §8 yeniden yazıldı |
| Web gezinme | Koyu lacivert kenar çubuğu, **her zaman açık** |
| Mobil gezinme | Alt sekme çubuğu: **Projeler · Belgeler · Hesap** |
| Stepper | Evet, iki istemcide de — `☑`/`☐` Unicode karakterleri kalkıyor |
| Yüklenici alanı | **Yok** — mockup'a yapay zekâ eklemiş, veri karşılığı hiç yok |

### ⛔ Bilinçli olarak YAPILMAYANLAR — bu liste olmadan hepsi yeniden önerilir

Kullanıcının mockup'tan açıkça çıkarttıkları:
- **Üstteki dört istatistik kartı** (Açık proje / İşleniyor / Hakediş bekleyen / Onaylanan tutar)
- **Bildirim zili** — hiçbir altyapısı yok: tablo, tetikleyici, okundu takibi, uç, teslim mekanizması
- **Sayfa alt başlığı** ("Bursa, İzmir ve Ankara bölgelerindeki açık güzergâh çalışmaları")
- **Yüklenici / firma alanı** — şema, domain ve API'de karşılığı yok. Firma adı yalnızca sözleşme
  adının içinde geçiyor ("2026 Güvenay Rota-1") ve Faz N'den beri sözleşme **belgenin**, işin değil:
  üç aşama farklı sözleşme seçebiliyor, dolayısıyla kart düzeyinde bir firma alanı tanımlı bile değil

Kapsam gereği bu turda alınmayanlar:
- **Kenar çubuğu sayaçları** (Projeler 29, Belgeler 142, Sözleşmeler 8, Kullanıcılar 36). Dördü de
  dekoratif; ikisi yalnızca yöneticide dolu. `GET /api/ozet` **dört ayrı yetki süzgeci** demek — biri
  unutulursa başka bölgenin sayısı sızar ve hata sessizdir (§7/3). Yerine yalnızca **Projeler**
  sayacı, ekranın zaten elinde tuttuğu diziden; aynı sayı "N proje gösteriliyor" satırıyla tutarlı kalır
- **"En son güncellenen" sıralaması** — `is_kaydi`'nın kendi `guncelleme` sütunu yok;
  `max(belge.guncelleme)` ilişkili bir toplama ister. Tek seçeneği varsayılanı tekrarlayan bir açılır
  liste basılmaz; liste en yeniden eskiye kalır (`order by i.id desc`)
- **"Bu hafta açılanlar" tarih çipi** — "haftanın başı"nı istemciye hesaplatır (§5/§7'ye aykırı) ve
  `IsSuzgeci`'nin taşımadığı çok-çipli bir süzgeç modeli ister. **Çip satırının kendisi yapıldı**,
  bugün var olan süzgeçlerden besleniyor; tarih çipi gerekirse aynı satıra oturur
- **Kart başlığında proje toplamı** — istemcide toplamak yasak (§13), sunucuda üretmek
  `round(...,2)` sayısını 4'ten 5'e çıkarıp `dogrula-faz-r.sh`'ı düşürür. Yerine son aşamanın tutarı
  **aşama adıyla** basılıyor (`1. Keşif · 2.631,70 ₺`), toplam sanılmasın diye
- **"29 projeden 12'si gösteriliyor"** — süzülmemiş toplamı sunucu vermiyor; metin "N proje
  gösteriliyor"a çevrildi. Uydurulmuş bir payda basmaktansa dürüst bir sayı

### İş kolları

- [ ] **WS-0 — Palet + tema/tipografi.** 8 yeni jeton (koyu gezinme yüzeyi); dört durum ailesi **saf
      takma ad** olarak (tek yeni renk yok). Ayrıca iki **canlı hata**: `outlineVariant` eşlenmediği
      için her kart ayracı M3 varsayılanı gri-mor `#CAC4D0` basıyordu; `titleSmall`/`bodySmall`
      tanımsız olduğu için kartın en çok okunan metni 12–14sp'ydi (§8 ihlali)
- [ ] **WS-1 — Anlamsal durum rengi.** `durumAilesi()` / `StatusTone`. ⚠️ `TASLAK` maviden ambere
      geçti: "Fizibilite Hazırlanıyor" süren iştir ve "Açık" ile aynı maviyi paylaştığı için aynı
      kartta iş durumu ile belge durumu ayırt edilemiyordu
- [ ] **WS-3 — Sunucuda `ara` serbest metin süzgeci.** `/api/kableler` deseni (`ilike` + zorunlu
      `cast(:ara as text)`). Liste ve **Excel ucu ikisine birden** — biri unutulursa indirilen dosya
      ekrandan sessizce ayrışır
- [ ] **WS-2 — Web kabuğu.** Kenar çubuğu + kırıntı + arama; `.ust-serit` beyaz üst çubuğa dönüşür
- [ ] **WS-4 — Web proje kartı.** Stepper + aç/kapa + vurgu şeridi + süzgeç çipleri.
      `--is-durum`/`--is-islem` sihirli genişlikleri ve 1100px dalı **silinir**
- [ ] **WS-5 — Android gezinme kabuğu.** Alt çubuk + Hesap ekranı + 5 elle yazılmış vektör
- [ ] **WS-6 — Android Belgeler ekranı.** Sunucu ucu hazır; satır belgeyi **salt okunur** açar,
      indirme kodu yeniden yazılmaz
- [ ] **WS-7 — Android kart + stepper**

**DoD**
- [ ] `cd web && npm run build` temiz · `dogrula-faz-r.sh` 0 düşen
- [ ] `ktlintCheck` temiz · `:core:domain:test` `:server:test` `:app:testDebugUnitTest` geçer
- [ ] `./gradlew :app:dependencies | grep material-icons` **boş** (bağımlılık eklenmedi)
- [ ] `grep -rn '☑\|☐' app/src` boş · `grep -rn 'imza\|toFixed(\|parseFloat' web/src` boş
- [ ] **Tarayıcıdan ve tabletten elle** (release APK ile — R8 açık)

---

## FAZ U — Kablo Envanteri Sürümleri  ·  `9 Ağustos 2026`  ·  `Durum: ✅`

**Amaç:** Yöneticinin NOS+'tan aldığı tam kablo envanterini arayüzden yükleyebilmesi ve yanlış bir
dosya yüklendiğinde **önceki envantere geri dönebilmesi**.

**Neden:** Envanter, sisteme yalnızca `:tools:kable-import` ile giren son veri kaynağıydı — Gradle,
DB parolası ve repo gerektiriyordu. Asıl mesele kolaylık değil, **içe aktarımın yıkıcı olması**:
`KableYazici.eksikleriPasiflestir` dosyada olmayan her kabloyu `aktif = false` yapar. Eksik ya da
tek bölgelik bir dışa aktarım yüklenseydi envanter sessizce erir, bölge sorumluları yeni proje
açamaz ve **hiçbir hata çıkmazdı**. Sürümleme bunu geri alınabilir kılıyor.

### Kullanıcı kararları (9 Ağustos 2026)

| Konu | Karar |
|---|---|
| Sürüm kapsamı | **Tek yürürlükteki sürüm.** Proje bazlı seçim yok — adres projeye kopyalanıp donduruluyor (§7/5), pinlemenin kazancı olmazdı |
| Yükleme akışı | **Yükle → gözden geçir → yürürlüğe al.** Yükleme canlı envantere dokunmaz |
| Geri dönüş riski | **Uyar, engelleme.** Düşecek kablolarda açık proje varsa sayısı söylenir, karar yöneticinin |

### U1 — Sürüm şeması ve tek yazma yolu

- [x] `V13__kable_surumleri.sql`: `kable_surum` + `kable_surum_satiri`; `yururlukte` **kısmi tekil
      indeksle** kilitli (aynı anda iki yürürlükteki sürüm veritabanı düzeyinde imkânsız)
- [x] Başlangıç sürümü mevcut `kable` satırlarından seed edildi — olmasaydı **bugünkü hâle geri
      dönmek mümkün olmazdı**: ilk yükleme yürürlüğe girdiği anda önceki envanterin kaydı hiçbir
      yerde durmuyor olurdu
- [x] `kable` tablosu **yerinde kaldı** ve yürürlükteki sürümün izdüşümü oldu: `is_kaydi.kable_id`
      yabancı anahtarı, `kable_bolge_ix` ve tüm arama uçları değişmedi
- [x] `saha_adi` ham saklanıyor, bölgeye çevirme **izdüşüm anında** — eşleşme sonradan düzeltilirse
      eski sürüm de doğru bölgeye düşer
- [x] `KableSurumYazici` `:tools:kable-import` içinde: CLI ile sunucu **aynı** koddan geçer. CLI de
      artık sürüm üretiyor; `kable` her zaman bir sürümün izdüşümü
- [x] `KableYazici` transaction yönetmeyi bıraktı — izdüşüm ile `yururlukte` bayrağı tek transaction

### U2 — Uçlar ve ekran

- [x] `/api/kable-surumleri` (liste · yükle · fark · yürürlük · sil) — hepsi yalnızca yönetici
- [x] Yükleme **yazmıyor**: dosya sürüm olur, yanıt farkı taşır (`yeni/değişen/aynı/düşen` +
      bölge dağılımı + `acikProjeliDusen`). Ayrı bir "önizleme" ucu gerekmedi
- [x] Saha adı eşleşmesi **yazmadan önce** doğrulanıyor: eksikse okunabilir 400. Aksi hâlde hata
      ancak "Yürürlüğe Al" anında çıkardı ve yönetici sürümün kullanılamaz olduğunu sonra öğrenirdi
- [x] `KableParseException` → 400 (`HataYakalayici`); parser mesajları satır numaralı ve gösterime uygun
- [x] Web: **Kablo Envanteri** ekranı (kenar çubuğunda Sözleşmeler'in altında) — yürürlükteki sürüm
      kartı, sürükle-bırak yükleme, sürüm tablosu, tek onay penceresi (ileri ve geri aynı pencere)
- [x] Yürürlükteki sürüm silinemiyor (409); yanlış yüklenen sürüm listeden silinebiliyor
- [x] Mobil **değişmedi** — yönetici akışı yalnızca web'de (§9)

### DoD

- [x] `./gradlew ktlintCheck test` temiz · `cd web && npm run build` temiz
- [x] `KableXlsxParserTest` (6 test): konum yolu, boş segment, **akış ile dosya aynı sonucu verir**,
      tanımsız danışman, tekrar eden nosid, adressiz satır
- [x] `bash deploy/dogrula.sh` — Faz U bölümü canlı sunucuda geçti:
      403 (bölge sorumlusu yükleme) · yükleme sonrası kablo sayısı **değişmedi** (943) ·
      yürürlüğe alma 200 · yürürlükteki sürümü silme 409 · **geri dönüşte sayı ilk hâline döndü**
- [x] Aynı dosya yeniden yüklendiğinde fark `943 aynı · 0 yeni · 0 düşen` — seed ile parser'ın
      birebir aynı veriyi ürettiğinin kanıtı

**Backlog'a düşen:** saha adı → bölge eşleşmesinin (`bolge.xlsx_saha_adi`) arayüzden düzenlenmesi.
Bugün yalnızca migration ile değişiyor; envanterde yeni bir saha adı belirirse yükleme durur ve
mesaj ne gerektiğini söyler, ama yönetici tek başına çözemez.

---

## FAZ V — Yönetici Yetkileri Turu  ·  `10 Ağustos 2026`  ·  `Durum: ✅`

**Amaç:** Yöneticinin elindeki tek araç "sil ve yeniden yükle" olmaktan çıksın; sahadan gelen keşif
ve hakediş onaydan geçsin, yanlış olan iade edilebilsin.

**Neden:** Üç ayrı istek aynı kökten geliyordu.
1. Sözleşmeye tek kalem eklemek bile dosyayı indirip **yeni bir kodla** yüklemeyi gerektiriyordu;
   listede ikiz kayıtlar birikiyordu. Silme ise yanlış kullanıldığında poz listesini ve fiyatları
   birlikte götürüyordu.
2. Bölge sorumlusu "Gönder" dediğinde belge kapanıyor ve geri dönüşü olmuyordu; yanlış girilmiş bir
   keşfin tek çıkışı projeyi iptal etmekti.
3. Envantere tek kablo eklemenin yolu NOS+'tan tam bir dışa aktarım almaktı.

### Kullanıcı kararları (10 Ağustos 2026)

| Konu | Karar |
|---|---|
| Sözleşme silme | **Kalkıyor.** Pasife alma tek "kaldırma" yolu |
| Sözleşme düzenleme | **Yalnızca yeni poz ekleme.** Birim fiyat, poz adı, birim ve poz silme yok |
| Yeni pozun yeri | Seçilen **grup başlığının sonuna**; `sira` kayar, `satir_kodu` kaymaz |
| Geçmiş belge tutarı | **Değişmez.** Belge kendi birim fiyatını dondurur |
| Excel | **Veritabanı asıl kaynak**; "İndir" her zaman güncel hâli üretir. Ham dosya saklanmaz |
| Onay kapsamı | **Keşif + hakediş.** Fizibilite onaysız |
| İade | Belge **taslağa döner**, aynı belge düzenlenir; gerekçe zorunlu |
| Onay öncesi | Bölge sorumlusu **onay bekleyen belgede de değişiklik yapabilir** |
| Yönetici durum yetkisi | **Her durumdan her duruma**, kapanmış proje dahil; gerekçe + kayıt zorunlu |
| Kablo düzenleme | **Doğrudan yürürlükteki envantere**; yeni sürüm doğmaz |
| Kablo silme | **Yok** |
| Kablo adresi | Yeni kayıtta segment segment (Ülke…Raf), düzenlemede hazır konum yolu |

### V1 — Onay geri geldi, ama yarısı

`DocumentStatus` dörde çıktı: `TASLAK · ONAY_BEKLIYOR · TAMAMLANDI · IPTAL`. Hedef duruma **tür**
karar veriyor (`DocumentStageRule.gonderimSonrasiDurum`) ve karar tek yerde — sunucu, web ve mobil
"hangi tür onaya gider" sorusunu kendi cevaplamıyor.

- **Aşama kapısına dokunulmadı.** `acilabilirMi` önceki aşama için hâlâ `TAMAMLANDI` şart koşuyor,
  dolayısıyla onay bekleyen keşif hakedişi açmıyor. Yeni bir kural yazmak gerekmedi.
- **`ONAY_BEKLIYOR` düzenlenebilir** (`DocumentStatus.duzenlenebilirMi`). Kullanıcı kararı: küçük bir
  eksik için iade edip beklemek gereksiz bir turdu.
- **Projenin kapanışı `gonder`'den `onayla`'ya taşındı.** Yolda `isler.durumYaz` dönüşünün
  kontrolsüz olduğu düzeltildi (kod tabanındaki fail-loud kalıbından tek sapmaydı) — ama koşullu:
  proje `ISLENIYOR` değilse yazma denenmiyor, çünkü yönetici serbest geçişle onu başka bir duruma
  almış olabilir.
- **İade aynı belgeye döner.** Ayrı bir revizyon kaydı açılmadı: her iade listeye ikinci bir belge
  ekler ve iki numaralı bir aşama sahada karışırdı. `belge.iade_*` izi yeniden gönderimde
  **temizlenmiyor** — "bu aşama bir kez iade edilmişti" bilgisi kalıcı.

### V2 — İstemcideki kural kopyası temizlendi

Düzenlenebilirlik üç yerde yazılıydı (sunucu, `BelgeEkrani.tsx`, `DocumentUiState.kt`).
`ONAY_BEKLIYOR` eklenirken biri unutulsaydı ekran kilitli görünür ama sunucu yazmayı kabul ederdi —
**hiçbir hata çıkmadan**. `BelgeYaniti` artık `duzenlenebilir` / `gonderilebilir` / `iade` taşıyor;
istemciler yalnızca onları okuyor (CLAUDE.md §7/1).

### V3 — Sözleşme: silme kalktı, kalem eklendi

Poz eklemek için **şema değişikliği gerekmedi**: yeni satır `poz`'a, fiyatı sözleşmenin `YAYINDA`
sürümüne yazılıyor. Hiçbir mevcut `satir_kodu` kaymadığı ve hiçbir fiyat değişmediği için
"geçmiş belge tutarı değişmez" kararı tasarımdan geliyor, ayrı bir koruma gerekmiyor.

- `satir_kodu` **`m{n}`** kalıbıyla üretiliyor; parser'ın `r{satır}` uzayıyla çakışamaz.
- `sort_key` elle hesaplanmıyor: `parseSortKey` (domain) kullanılıyor — `"4.11"` → `[4, 11]`.
- **Kabul edilen görünür sonuç:** yeni kalem o sözleşmeye bağlı **tüm** belgelerde görünür (kullanıcı
  bunu istedi) ve tamamlanmış bir belgenin Excel çıktısına bir boş satır ekler. PDF ve toplamlar
  etkilenmez.
- ⚠️ **Yolda kapatılan yetki açığı:** `GET /api/sozlesmeler/*/liste.xlsx` yönetici allowlist'inde
  yoktu ve `anyRequest().authenticated()`'e düşüyordu — bölge sorumlusu sözleşmenin tüm birim
  fiyatlarını indirebiliyordu.

### V4 — Kablo: elle ekleme/düzenleme ve envanter dışa aktarımı

Yazma yolu yine `:tools:kable-import` içinde (`KableElleYazici`): sunucu ikinci bir SQL yolu açmıyor
(§7/5b). Yeni sürüm doğmuyor, ama yazma **`kable` ile yürürlükteki `kable_surum_satiri`'na birlikte**
gidiyor — ayrışsalardı bir sonraki dosya yüklemesinin farkı yanlış hesaplanırdı.

Konum yolu kuralı `KonumYolu` nesnesine çıkarıldı; parser ile elle giriş **aynı** kodu çağırıyor.

⚠️ **`KableCiktisi`'nde iki tuzak vardı ve ikisi de testle kilitlendi:**
1. `KonumYolu.ALANLAR` içindeki `address`/`city`, sonek eklenince sabit sütunlarla çakışıyor
   (`addressA`). Tekrarlı sütun listesi iki indeks üretiyor; parser ilkini seçiyor, yazıcı
   sonuncusuna yazıyordu → dosya boş `addressA` ile iniyordu. `distinct()` zorunlu.
2. `cityA` hem bağımsız şehir sütunu hem konum yolunun `city` segmenti. Yolu `addressA`'ya yazıp
   `cityA`'yı da doldurmak şehri yola **ikinci kez** ekliyordu. Çözüm: yolu şehir segmentinden bölüp
   `countryA` / `cityA` / `addressA`'ya dağıtmak — parser aynı sırayla birleştirince birebir aynı
   yol çıkıyor.

### DoD

- [x] `V14__onay_ve_iade.sql`: `belge_durum_check` dörtlü, `belge.iade_*` + tutarlılık kısıtı,
      `durum_kaydi` tablosu (is_id XOR belge_id)
- [x] `./gradlew ktlintCheck test` temiz · `cd web && npm run build` temiz
- [x] `KableCiktisiTest`: üretilen envanter parser'dan geçiyor **ve** gidiş-dönüş adresleri değiştirmiyor
- [x] `DocumentStageRuleTest`: durum kümesi dört, onay bekleyen keşif hakedişi açmıyor,
      onay bekleyen belge düzenlenebilir
- [x] `SIFIRLA=1 bash deploy/dogrula-faz-v.sh` — canlı sunucuda **67 kontrol, 0 düşen**
- [x] `bash deploy/dogrula.sh` (A/B + U) ve `dogrula-faz-r.sh` (26) hâlâ temiz — gerileme yok
- [x] `dogrula-faz-mnop.sh` yeni akışa göre güncellendi (**91 kontrol, 0 düşen**)

### Yolda bulunan üç sessiz kusur *(planda yoktu, doğrulama sırasında çıktı)*

1. **Kaldırılan metot 500 dönüyordu.** `DELETE /api/sozlesmeler/{kod}` silinince yol `PUT` için hâlâ
   eşleştiği için Spring `HttpRequestMethodNotSupportedException` fırlatıyor, `HataYakalayici` onu
   jenerik dala düşürüp **500 + yığın izi** üretiyordu: istemci hatası sunucu çökmüşü gibi
   görünüyordu. `NoResourceFoundException`'ın ikizi olarak 405'e bağlandı.
2. **`IsSorgulari.iptalEt` domain kuralıyla ayrışmıştı.** `WorkOrderTransition` her geçişe izin
   verdikten sonra bile SQL `where durum in ('ACIK','ISLENIYOR')` diyordu; kapanmış bir projeyi
   iptal etmek 409 alıyordu. `dogrula-faz-mnop.sh` yakaladı.
3. **`KableCiktisi` sütun listesi tekrarlıydı.** `KonumYolu.ALANLAR` içindeki `address`/`city`,
   sonek eklenince sabit sütunlarla çakışıyor; parser ilk indeksi seçerken yazıcı sonuncusuna
   yazıyor ve dosya **boş `addressA`** ile iniyordu. `distinct()` + `KableCiktisiTest` ile kapatıldı.

**Backlog'dan çıkanlar:** *"`ISLENIYOR` işi `ACIK`'a geri alma"* (uygulandı) ve *"Referans fiyat
düzenleme ekranı"* (kısmen: yalnızca poz ekleme).

**Bilinçli olarak YAPILMAYANLAR** — bu liste olmadan hepsi yeniden önerilir:
- Birim fiyat düzenleme ekranı · poz silme · poz adı/birim düzenleme
- Kablo silme · elle kablo düzenlemesi için yeni sürüm
- Fizibilite onayı (Faz M'deki kaldırma gerekçesi geçerli)
- Ayrı onay kuyruğu ekranı (proje listesi zaten satır bazlı çalışma ekranı — S12)
- Ayrı denetim/raporlama ekranı (`durum_kaydi` yazılıyor, uçları var, ekranı Backlog'da)
- Yüklenen ham `.xlsx`'in saklanması (veritabanı asıl kaynak kararı)
- Mobilde yönetici akışı (§9 gereği yalnızca web)

---

## FAZ W — "Yeni Tesis" kapsamında kablo opsiyonel  ·  `10 Ağustos 2026`  ·  `Durum: ✅`

**Amaç:** Kapsamı "Yeni Tesis" olan projeler kablo seçilmeden açılabilsin.

**Neden (yöneticinin kendi ifadesiyle):** *"Yeni Tesis kapsamı seçilen projelerde iş kablo ile ilgili
olacak ancak belli bir kablo ID'nin kapsamında olmuyor."* Tesis yeni kuruluyor, envanterde karşılığı
henüz yok. Sistem kablo seçimini **uçtan uca** zorunlu tutuyordu (DB `not null` → `WorkOrderValidator`
→ web/mobil düğme kilidi), dolayısıyla bu projeler ya hiç açılamıyor ya alakasız bir kablo seçilerek
açılıyor ve hakediş belgesine yanlış bir kablo kimliğiyle adres basılıyordu.

### Kullanıcı kararları (10 Ağustos 2026)

| Konu | Karar |
|---|---|
| Hangi kapsam | Yalnızca `YENI_TESIS`; diğer dördü bugünkü gibi zorunlu |
| Kablo ID | Yeni Tesis'te **serbest metin** ve opsiyonel; envanterde **aranmaz** |
| Uç A / Uç B | Yeni Tesis'te **elle girilir ve opsiyonel** |
| Adres biçimi | **Segment segment** (Ülke, Şehir, Adres, Bina, Kat, Oda, Kafes, Ayak, Raf) |
| Diğer dört kapsam | Değişmedi: envanterden seçim, zorunlu; uçlar envanterden gelir |
| Sonradan kablo ekleme | **Yok.** Proje açılışta ne seçildiyse onunla kalır |
| Çıktı | Boş alanın satırı **hiç basılmaz** — `adresB`'nin baştan beri yaptığı |

### W0 — Karar ekranı görünce değişti *(10 Ağustos 2026, ikinci tur)*

İlk karar "envanterden seçim ama opsiyonel"di. Ekran çalışır hâle gelince kullanıcı gerçek davranışı
gördü: Yeni Tesis'te yazılan her değer envanterde aranıyor ve **her seferinde "Eşleşen kablo yok."**
uyarısı çıkıyordu — çünkü o iş zaten belirli bir kablo kimliğinin kapsamında değil. Karar
düzeltildi: **bu kapsamda envanter hiç kullanılmıyor.** Kablo adı serbest metin, uçlar elle.

⚠️ **Bilinen ve kabul edilen sonuç:** serbest ada bağlı proje envanterle bağ kurmaz — kablo
aramasında çıkmaz ve `AYNI_KABLE_VAR` uyarısı onlar için çalışmaz. Kullanıcı bunu bilerek seçti.

### W1 — Kural tek yerde: `Scope.kableZorunluMu`

```kotlin
val kableZorunluMu: Boolean get() = this != YENI_TESIS
```

Sunucu doğrulaması, web formu ve mobil form **aynı bayrağı** okur; hiçbiri `kod == "YENI_TESIS"` diye
kendi listesini tutmaz. Bayrak istemcilere `GET /api/kapsamlar` yanıtındaki `kableZorunlu` alanıyla
gider ve **veritabanından değil enum'dan** türer — `kapsam` tablosuna sütun açılmadı.
`EnumSenkronDogrulayici` iki kümenin aynı olduğunu açılışta kilitlediği için türetme güvenli.

⚠️ **Kapsam seçilmemişken kablo zorunlu sayılır** (üç yerde de). Aksi hâlde kapsamsız bir istek
kabloyu da atlayarak iki hata yerine bir hata üretir ve kullanıcı ikinci eksiği ancak kapsamı
seçtikten sonra görürdü.

### W2 — Şema: `V15__yeni_tesis_kablosuz.sql`

`kable_id`, `kable_nosid`, `adres_a` nullable oldu; kural **veritabanında da** duruyor:

```sql
check (kable_id is not null or kapsam_id = 4)
```

Alt sorgu CHECK içinde kullanılamadığı için kapsam kimliği gömülü; migration başında bir `do $$`
bloğu `kapsam.id = 4`'ün gerçekten `YENI_TESIS` olduğunu doğruluyor ve değilse **durduruyor**.
Kısıt bilinçli: "diğer bütün kapsamlarda kablo zorunlu" bir iş değişmezi ve servis katmanında bir dal
unutulsa hata sessiz olurdu — kablosuz bir Deplase projesi açılır, kimse fark etmezdi.

### W3 — Sessiz kırılma noktaları (fazın asıl işi)

Nullable'a geçen her sütun bir NPE ya da deserializasyon istisnası adayıydı:

- **`IsSatiri` / `CiktiBasligi` / `BelgeListeSatiri`** — `ResultSet.getString` null döner; non-null
  alana yazılırsa Kotlin'in intrinsic denetimi NPE atar ve **proje listesi hiç açılmaz**.
- **`WorkOrderDto.kableNosid`/`adresA` (mobil)** — varsayılansız `String` kalsaydı
  `kotlinx.serialization` istisna atardı; hata tek projede değil **tüm listede** çıkardı. Bu yüzden
  mobil güncellemesi opsiyonel değildi.
- **`Etiketli` / `LabelValue` / `Field`** — boş değerde etiketi değersiz basıyorlardı; hepsi `—`
  basacak şekilde tek yerde düzeltildi.

### W4 — Yeniden kullanım: `KonumYolu` ve `UcSegmentleri`

Faz U/V'de yazılan iki parça bu fazda **ikinci kullanıcısını buldu** — soyutlama böyle meşrulaştı
(CLAUDE.md §1/7):

- `UcIstegi` + `KonumGirdisi` `KableElleServisi`'den ayrı bir dosyaya çıkarıldı; `isler` paketinin
  kablo servisine bağımlı olması yanlış bağımlılık yönü olurdu.
- Web'deki `UcSegmentleri` bileşeni ve `SEGMENTLER` dizisi `KabloListesi.tsx`'ten
  `UcSegmentleri.tsx`'e taşındı; iki ekran paylaşıyor.
- Mobilde aynı alan adları `KONUM_ALANLARI` olarak duruyor; yolu yine **sunucu** kuruyor.

### DoD

- [x] `V15__yeni_tesis_kablosuz.sql` — üç sütun nullable, kapsam koşullu CHECK, seed güvenlik kontrolü
- [x] `./gradlew ktlintCheck test` temiz · `cd web && npm run build` temiz
- [x] `WorkOrderValidatorTest`: Yeni Tesis'te kablosuz kayıt geçerli; **diğer dört kapsamda** hata;
      kapsamsız kayıtta kablo da eksik sayılıyor
- [x] `PdfTemplateTest`: kablosuz başlıkta üç satır da basılmıyor ve XHTML iyi biçimli kalıyor
- [x] `DocumentModelBuilderTest`: boş metin `null`'a iniyor
- [x] `IsListesiExceliTest`: kablo hücresi boş, sütun düzeni kaymıyor
- [x] `ApiModelsTest`: `null` gelen ve **hiç gelmeyen** kablo alanları çözülüyor; `kableZorunlu`
      gelmezse zorunlu sayılıyor
- [x] `SIFIRLA=1 bash deploy/dogrula-faz-w.sh` — canlı sunucuda uçtan uca
- [x] `dogrula.sh` · `dogrula-faz-mnop.sh` · `dogrula-faz-v.sh` · `dogrula-faz-r.sh` temiz (gerileme yok)

**Bilinçli olarak YAPILMAYANLAR:**
- Serbest metin kablo ID (envanterde karşılığı olmayan kimlik kablo kaydına bağlanamaz)
- Projeye sonradan kablo ekleme · kapsamı sonradan değiştirme
- `kapsam` tablosuna `kable_zorunlu` sütunu (tek kapsam için; ikincisi çıkarsa sütuna geçilir)
- Uç A/B'nin diğer kapsamlarda elle girilebilmesi (envanterle sessiz ayrışma üretirdi)

---

## FAZ Y — EK3, imza bloğu, aşama iadesi  ·  `18-19 Ağustos 2026`  ·  `Durum: ✅`

Kullanıcı/müdür isteklerinden oluşan tur. Dördü de **çıktıya ve akışa** dokunuyor; para hesabı,
yuvarlama ve toplam mantığına **hiç dokunulmadı** (§5/§7 korundu).

### Y1 — EK3 "TTI Kullanılan Malzemeler" (yalnızca hakediş)

Sahada kullanılan malzemeler: **Malzeme Adı · Malzeme Miktarı · Seri No**, sınırsız satır,
**üçü de opsiyonel**. Dosya değil **yapısal veri** olduğu için `ek` tablosuna girmiyor; kendi
tablosu var (`belge_malzeme`, `V19`).

- Yazma **tam değiştirme** (`PUT /api/belgeler/{id}/malzemeler`) — miktar satırlarıyla aynı semantik.
- Tür kuralı sunucuda: HAKEDIS dışında **409**. Veritabanında tekrarlanmadı — `belge.tur` bir metin
  sütunu ve kuralı iki yerde tutmak sessiz ayrışma üretirdi.
- Miktar **metin** olarak gelir, ayrıştırma sunucuda (`QuantityParser`) — §5 `double` tuzağı.
- Çıktı: Excel'de **EK2 satırının hemen altında**, her malzeme kendi satırında; PDF'te ek bilgiler
  bölümünde. Malzeme yoksa blok **hiç basılmaz**.
- ⚠️ **Gönderim ön koşulu değil** (opsiyonel), ama ekranda *doldurulup kaydedilmemiş* satır varsa
  gönderme durur ve uyarı verir. Uyarı `gonder()` içinde, düğmeyi kapatarak değil: **kapalı düğmeye
  tıklamak hiçbir geri bildirim üretmiyor** ve kullanıcı "uyarmadı" diye görüyordu (19 Ağustos
  kullanıcı bildirimi).

### Y2 — Excel: imza bloğu + logo

- **Toplam satırının altında imza bloğu**: `ŞİRKET ADI: ` (boş, elle yazılır) / `TURK TELEKOM
  INTERNATIONAL`, bölüm başlıkları, altı unvan, imza satırı. Sistemden gelen tek isim **bölge
  sorumlusu** (belgenin bölgesinin aktif sorumlusu — belgeyi açan kişi değil, satır rolü gösteriyor);
  iki "Kontrol Eden" sabit metin.
  ⚠️ Kurum adı `markaAdi.uppercase()` ile **türetilmez**: Türkçe locale `International` →
  `INTERNATİONAL` üretiyor (§5).
- **Sağ üstte TTI logosu** — PDF ile **aynı dosya ve aynı doğrulama** (`MarkaLogosu`), dosya yoksa
  ikisi de sessizce metne düşer.
  ⚠️ `PictureAnchor.twoCellAnchor` parametre sırası **(satır, sütun, satırSon, sütunSon)** — adı
  bunu söylemiyor, belgelenmemiş ve ters verilince hata çıkmadan logo sol üste düşüyor.

### Y3 — Önceki aşamaya iade

Yönetici bir aşamayı beğenmezse geri alır: **en ileri açık aşama** `IPTAL` işaretlenir, bir önceki
aşama `TAMAMLANDI → ISLENIYOR` geri açılır, proje durumu yeniden türetilir. Aşama listede *"Henüz
açılmadı — sırada bu adım var"* olur ve bölge sorumlusu **her şeyi baştan girer**. Tekrar basıldıkça
geriye yürür: hakediş → keşif → fizibilite. Gerekçe zorunlu; müdahale hem belgenin hem **projenin**
durum geçmişine yazılır.

Kayıt **silinmez** — para taşıyan bir kaydı geri dönüşsüz silmek §7/4'ün denetim izi ilkesine
aykırı olurdu; görünen sonuç istenenin aynısı çünkü `belge_is_tur_ux` iptal edilmiş satırı saymıyor.

⚠️ **Bu tur iki gerçek hatayı ortaya çıkardı ve düzeltildi:**
1. Proje "Tamamlandı" takılı kalıyordu — projenin `TAMAMLANDI`'sı artık **yalnızca hakedişin**
   tamamlanmasından doğuyor (§3'ün zaten söylediği kural, izdüşümde eksikti).
2. **Aynı türde birden çok `belge` satırı** (biri `IPTAL`, biri aktif) artık olağan.
   `isinBelgeDurumlari` iptalleri de çekip `toMap()` ile son satırı tuttuğu için aktif aşamayı
   gizleyebiliyordu; aşama kapısı "önceki aşama yok" diyerek belgeyi **hiç açtırmıyordu**. Aşama
   kararları artık `isinAcikAsamalari` kullanıyor.

### Y4 — Sözleşme ve envanter arayüzü

- Sözleşme satır menüsü: **Adı düzenle** (yeni) · İndir · **Sil** (yalnızca hiç belge açılmamışsa;
  kullanımdaysa 409 ve "pasife alın"). ~~Revize Et~~ kaldırıldı — aynı işi "İndir → düzenle → yeni
  kodla yükle" zaten yapıyordu.
- Yükleme ekranında **kod sorulmuyor**: içsel anahtar, sunucu türetiyor ve çakışırsa boştaki ilk
  koda kayıyor. Ad serbest metin (büyük harf, `_`, `-`, boşluk kabul).
- Şablon ve indirilen sözleşme başlıkları **okunur** hâle geldi: `Poz No / İş Kalemi / Birim /
  Br Fiyat`. Parser eski `ISC_*` adlarını **hâlâ tanıyor** (gerçek NOS+ dosyaları onlarla geliyor).
- Kablo Envanteri: elle **ekleme** arayüzden kalktı (düzeltme duruyor), **indirme** sayfa
  başlığından sürüm satırlarının ⋮ menüsüne taşındı.
- İkinci yönetici hesabı **`admin`** (`V20`) — `yonetici` ile birebir aynı yetkiler.

### Y5 — Mobil parite  ·  `19 Ağustos 2026`

Y1–Y4 turu sunucu + web'de yapıldı, `:app` geride kaldı. CLAUDE.md §9 EK3'ü mobil belge akışının
**4. adımı** olarak zaten yazıyordu; kod dokümanın gerisindeydi. Kapatılanlar:

- **EK3 mobilde**: `MaterialDto`/`MaterialEntry`/`MaterialsRequest`, `HakedisApi.materials` +
  `writeMaterials`, `MaterialsSection` kartı (yalnızca hakediş, dosyalarla çıktı arasında), gözlem
  modunda salt okunur. Kaydetme **açık düğmeyle** — miktar tablosunun debounce'undan ayrıldığı tek
  nokta: tam değiştirme semantiğiyle debounce, yarım yazılmış satırın listeyi ezmesi demekti.
- **"Gönder" 19 Ağustos kararına uyuyor**: kaydedilmemiş malzeme düğmeyi **kapatmaz**, uyarı
  `submit()` içinde verilir. Bu yüzden `SubmitBlocker`'a değer eklenmedi (her değeri düğmeyi
  kapatıyor); taşıyıcı `materialsUnsaved` + `LocalError.UNSAVED_MATERIALS`.
- ⚠️ **Mobile özgü tuzak, web'de karşılığı yok:** ekran `LifecycleResumeEffect` ile yenileniyor ve
  kameradan/foto seçiciden/dosya seçiciden **her dönüşte** tetikleniyor. `refreshIfIdle` malzeme
  bayraklarını da beklemeseydi yazılmış satırlar hiçbir hata vermeden silinirdi. Malzemeler
  debounce'lu olmadığı için "kendiliğinden tamamlanma" da yok.
- Bu turda çıkan **gerçek kusurlar** düzeltildi: `DocumentArchiveViewModel` yalnızca `ApiException`
  yakalıyordu — VPN koptuğunda atılan `NetworkException` coroutine'den kaçıp uygulamayı
  çökertiyordu (sahanın en olağan hatası); `gonderilebilir` çözülüyor ama hiç okunmuyordu
  (bugün `duzenlenebilir`e eşit, yani davranış değişmedi — çözülüp okunmayan alan sessiz ayrışmanın
  kurulumu); proje kartı kapsamı ham enum kodu (`DEPLASE_IYILESTIRME`) olarak yazıyordu, artık
  `/api/kapsamlar`'dan ad geliyor (uç düşerse ham koda geri düşer).
- Ölü kod ve kuralın **tersini** söyleyen KDoc'lar temizlendi: `status_draft` (`TASLAK` V17'de
  kalktı), test fixture'ındaki `"iade":null`, birim fiyatın "yuvarlanmadığı" (yuvarlanıyor — §5),
  eklerin "yalnızca hakediş taslağında" olduğu (Faz P: üç türde de), keşif/hakedişin "işin pazarlık
  fiyatları" (Faz N'de kalktı).
- **Yönetici uçları yine eklenmedi** (§9): `/iade`, `/onayla`, `/durum`, sözleşme silme/ad düzenleme
  web'de kalıyor. İadenin mobildeki tek etkisi dolaylı ve zaten doğru çalışıyor — `stageInfos`
  `IPTAL` satırları süzdüğü için aşama "Henüz açılmadı" hâline dönüyor.
- **Bağlantı yeniden ele alındı** — J1 geçersizleşmişti (uygulama `127.0.0.1:8081`'e bağlı, 8081
  duvarda kapalı): adres artık sabit `https://hakedis.turktelekomint.com`, cleartext kapalı, adres
  girme alanı kalktı. Ayrıntı ve gerekçe **J1'** maddesinde.

`./gradlew ktlintCheck test` temiz; `:app:assembleDebug` ve `assembleRelease` (R8 açık) geçiyor.
Üretilen iki APK'da da kaynak tablosunda `hakedis.turktelekomint.com` var, emülatör adresi yok ve
`cleartextTrafficPermitted=false` (aapt2 ile doğrulandı).
⚠️ **Cihazda elle test edilmedi** — Faz J'nin açık DoD maddesi duruyor.

---

## SONRAKİ FAZLARDA CEVABI GEREKEN AÇIK SORULAR

Faz A/B'yi bloke etmiyorlar; ilgili fazın başında sorulur.

- [x] **5.** Yönetici fiyat güncellediğinde `TASLAK` belgeler eski fiyatta mı kalsın?
      → ~~**Soru düştü.** Fiyat artık **işe özel** ve iş başına **bir kez** yazılıyor (E2/E4);
      yöneticinin global fiyat güncellemesi diye bir akış kalmadı. Referans liste migration/`:tools`
      ile yüklenir. *(30 Temmuz 2026, Faz E)*~~
      → **Soru geri açıldı ve cevaplandı: eski fiyatta kalır, dokunulmaz.** Faz L referans fiyat
      güncellemesini geri getirdi. Yeni sürüm yayınlandığında açık `TASLAK` fizibiliteler açıldıkları
      sürüme çivili kalır; güncel fiyatlar yalnızca **bundan sonra açılan** belgelerde geçerlidir.
      Keşif ve hakediş zaten işin pazarlık sürümüne çivili, referans güncellemesinden hiç etkilenmez.
      *(1 Ağustos 2026, Faz L)*
- [x] **6.** EK1 fizibilite aşamasında da zorunlu mu? → ~~**Hayır, yalnızca hakedişte zorunlu.**
      Akış adımları fizibilite ve keşifte ekten söz etmiyor. *(30 Temmuz 2026, Faz F)*~~
      → **Yeniden cevaplandı: EVET, üç belgede de zorunlu.** Kullanıcı üç aşamada da güzergâh
      dosyası istedi; EK2 opsiyonel kaldı. *(3 Ağustos 2026, Faz P — S10)*
- [x] **7.** Sunucu HTTPS mi olacak, sertifikayı IT mi verecek? → **Şimdilik hayır: VPN içinden düz
      HTTP.** Sunucu adresi cihazda ayarlanabilir olduğu için `network_security_config.xml` cleartext
      trafiğe izin veriyor (yalnızca bu uygulama için). TLS Faz K'nin ters vekil maddesiyle gelecek;
      geldiğinde mobilde değişecek tek şey bu izinin kapatılmasıdır. *(31 Temmuz 2026, Faz J/J1)*
- [x] **8.** Bir işe **birden fazla KableID** bağlanabilir mi? → **Hayır, 1 iş = 1 kable.** V2 şeması
      (`is_kaydi.kable_id` tek FK) olduğu gibi kalır. Aynı SP altındaki ikinci kablo **ayrı bir iş**
      olarak açılır; D1 bunu zaten meşru kılıyor. *(30 Temmuz 2026, Faz D)*
- [x] **9.** KableID seçildiğinde `route` ve `ek aralığı` elle mi girilecek? → **Elle girilir**, ikisi de
      zorunlu metin alanı. Envanterde doğrudan karşılığı yok (`length`, `capacity` var); türetme
      IT'den yeni sütun/API gerektirir → Backlog. *(30 Temmuz 2026, Faz D)*
- [x] **10.** Mobilde "beni hatırla" isteniyor mu? → **Hayır.** Parola cihazda saklanmıyor; yalnızca
      son kullanıcı adı ve sunucu adresi hatırlanıyor. *(31 Temmuz 2026, Faz J/J2)*
- [x] ~~**11.** Yöneticinin yüklediği pazarlık fiyat listesi **şablon dışıysa** ne kadar esnek
      olacağız?~~ → **Soru düştü:** pazarlık fiyat listesi kaldırıldı (S2). Aynı soru artık **miktar
      şablonu** için geçerli ve cevabı aynı: katı — şablon dışı dosyada hiçbir şey yazılmaz, 400
      döner ve hangi satırın neden tutmadığı listelenir. *(3 Ağustos 2026, Faz P)*
      Eski metin kayıt olarak aşağıda duruyor.
- [ ] ~~11-eski.~~ Yöneticinin yüklediği pazarlık fiyat listesi **şablon dışıysa** ne kadar esnek olacağız?
      *(Faz I — yönetici arayüzü)*
      Bugünkü davranış **katı**: sunucunun ürettiği şablon (`fiyat-sablonu.xlsx`, gizli `POZ_KIMLIK`
      sütunlu) dışında bir dosya geldiğinde **hiçbir şey yazılmadan 400** döner ve hangi satırın neden
      tutmadığı tam liste hâlinde bildirilir (E10, dört katmanlı savunma). Kısmi yükleme yok.
      Sorulacak: aracı firma şablonu bozup kendi tablosunu gönderdiğinde yönetici ne yapsın —
      (a) firmadan şablonla tekrar istesin *(bugünkü davranış)*, (b) yönetici elle şablona aktarsın,
      (c) serbest biçimli Excel'i eşleştirmeyi deneyelim *(kayma riski, E10'un tam olarak engellediği şey)*.
      **Not:** bu dosya, çıktı Excel'inden bağımsızdır; çıktı sütun düzeni bu yolu etkilemez.
- [x] **13. `SP No` ile belge numarası aynı şey mi?** → **Hayır, ayrı kalıyorlar — ama SP No artık
      üretiliyor.** Kullanıcının rahatsızlığı elle yazılan serbest metindendi; `2026-BURSA-001`
      biçimiyle SP No da sistemin ürettiği bir numaraya dönüştü ve tekil oldu. Belge numarası
      (`BURSA_2026_KESIF_001`) tür ve sıra taşıdığı için ayrı kalmaya devam ediyor.
      *(3 Ağustos 2026, Faz O — S5)*
- [ ] ~~13-eski.~~ *(Faz H'de soruldu, karara bağlanmadı — şimdilik mevcut hâl korunuyor)*

      Kullanıcı arayüzü ilk kez elden geçirirken "SP No bu: `BURSA_2026_KESIF_001`" dedi. Bu iki
      kavram bugün **ayrı**:

      | | Nedir | Kim üretir | Kapsam |
      |---|---|---|---|
      | `belge_no` | `Bölge_Yıl_Tür_SıraNo` → `BURSA_2026_KESIF_001` | **sunucu**, boşluksuz sayaçla | **belge** başına (bir işin üç belgesi üç numara) |
      | `is_kaydi.sp_no` | elle girilen proje tanımlayıcısı | kullanıcı | **iş** başına (üç belge aynı SP'yi paylaşır) |

      Sorunun ağırlığı: ikisi aynıysa `sp_no` alanı gereksizleşir, **D1 kararı düşer** (aynı SP altında
      birden fazla iş açılabilmesi için sp_no'nun serbest olması gerekiyordu) ve alan iş düzeyinden
      belge düzeyine iner — yani şema değişikliği. Bu yüzden tahminle ilerlenmedi.

      Cevaplanacak: (a) sahada "SP No" denen şey gerçekten belge numarasının kendisi mi, yoksa
      (b) ayrı bir proje kodu mu ve `SP-12345` örneğim yalnızca biçim olarak mı yanlış, yoksa
      (c) ayrı bir kod ama **belge başına** mı olmalı?
      Ek olarak: SP numaralarının durduğu başka bir sistem/liste varsa alan elle yazma yerine
      seçim listesine dönebilir (bkz. Backlog).
- [x] **14. Yuvarlama: satır bazında mı, toplamda tek sefer mi?** → **Cevaplandı (3 Ağustos 2026,
      S8):** birim fiyat **çivilenirken** 2 haneye yuvarlanır, satır tutarı yuvarlanmış fiyattan
      hesaplanır, toplam yuvarlanmış satırların toplamıdır. `moneyExact` kaldırıldı; gösterim, hesap
      ve çıktı aynı sayıyı kullanır. Eski soru metni aşağıda kayıt olarak duruyor.
- [ ] ~~14-eski.~~ *(eski CLAUDE.md §15/6; Faz H'de
      yarısı cevaplandı)*

      **Cevaplanan yarı:** birim fiyat hesaba **ham** girer ve **gösterimde de yuvarlanmaz**
      (kullanıcı: *"parasal değerler önemli, bu yüzden tam hesap lazım"*, 30 Temmuz 2026).
      Uygulandı — bkz. Faz H sapmalar tablosu.

- [ ] **15. Bir belgeye birden fazla `.kml`/`.kmz` eklenebilmeli mi?** *(hâlâ açık; kapsam Faz P'de
      genişledi — soru artık üç belge türü için geçerli)* *(31 Temmuz 2026, kullanıcı
      testinden)* Bugün EK1 **tekildir**: `ek_ek1_ux` kısmi unique indeksi bir belgede tek EK1'e izin
      veriyor, ikincisi 409 alıyor. Uzun bir hattın güzergâhı birden fazla dosyaya bölünmüşse bu kısıt
      dar kalır. Değişirse: indeks kalkar, gönderme kontrolü "en az bir EK1" olur, PDF alt bilgisinde
      tek dosya adı yerine liste basılır ve arayüzde silme/ekleme çoklu hâle gelir.

      **Açık kalan yarı:** bugün satır tutarı kuruşa yuvarlanıyor (`setScale(2, HALF_UP)`) ve toplam
      **yuvarlanmış satırların** toplamı. Alternatif: ham satır tutarları toplanıp **tek sefer**
      yuvarlanır. Aynı "tam hesap" ilkesi ikincisini işaret ediyor; fark 95 kalemlik bir belgede
      birkaç kuruşa kadar çıkabilir. Değiştirilirse `PricingCalculator.total` ve tüm çıktılar
      etkilenir, geçmiş belgelerin toplamı da yeniden hesaplanır (tutar saklanmıyor).
- [ ] **12.** Üretilen PDF/Excel **başka bir dosyaya veya sisteme aktarılıyor mu?** *(Faz G)*
      Kullanıcının ilk cevabı: *"okuyabilir, indirebilir, paylaşabilir sadece"* — yani sabit sütun
      düzeni bekleyen bir alt akış **yok**. Teyit edilmesi isteniyor: teyit gelirse yukarıdaki türe
      göre sütun kararı kesinleşir; birileri çıktıyı sabit 8 sütun bekleyen bir yere yapıştırıyorsa
      fizibilite de 8 sütunda kalmalı ve yalnızca başlık değişmeli.

**Karara bağlanmış olanlar** (tekrar açılmasın):
- ✅ Backend: Kotlin + Spring Boot + PostgreSQL, build Gradle (Maven dosyası yok)
- ✅ Giriş: yerel `kullanici` tablosu + BCrypt; kayıt ol yok, parolalar elle atanır
- ✅ Belge aynası: **sunucunun kendi diski**; ileride ortak dizine geçiş `ARSIV_KOK` değişikliğiyle
- ✅ Kable dosyası: CLI ile elle yüklenir, yönetici için yükleme ekranı yazılmaz
- ✅ Sıra numarası her belge türü için **ayrı** sayaç
- ✅ Ön fizibilite **aynı** sözleşme poz listesini kullanır
- ✅ Bir bölge sorumlusu **tek** bölgeden sorumlu
- ✅ Rapor başlığı: `Bölge/Saha + SP No`
- ✅ APK sunucuda derlenmez; Android Studio kurulu bir makinede derlenip elle dağıtılır

---

---

## FAZ D KARARLARI  *(30 Temmuz 2026, kullanıcıdan alındı)*

Faz D bu dört kararla başlar; ayrıca **aşama kuralı Faz C'de kodlanandan daha geniştir** (aşağıda D3).

### ~~D1 — SP No tekilliği: **kısıt YOK, uyarı var**~~  *(3 Ağustos 2026'da düştü — S5: SP No üretiliyor ve tekil)*

`is_kaydi.sp_no` üzerinde UNIQUE index **konmayacak**, ne global ne bölge içinde.

Gerekçe: bir SP genelde bir *projeyi*, iş ise bir *kabloyu* tanımlıyor. Aynı SP altında birden fazla
kablo çalışması olması olağan; UNIQUE koyulursa bölge sorumlusu ikinci kabloyu sisteme hiç giremez ve
bunu ancak sahada fark eder. Sonradan kısıt **eklemek** kolaydır, **kaldırmak** veri temizliği ister.

**Uygulama sözleşmesi** (Faz D bunu olduğu gibi uygular, yeniden karar vermez):

1. **Şema** — `V4`'te arama indeksi eklenir, unique **değil**:
   ```sql
   create index is_kaydi_bolge_sp_ix on is_kaydi (bolge_id, sp_no);
   ```
2. **Ön kontrol** — iş listesi ucu `spNo` süzgeci kabul eder; istemci iş oluşturmadan önce sorabilir:
   `GET /api/isler?spNo=SP-12345` → bölge süzgeci yine SQL'de, yani sorumlu yalnızca kendi bölgesindeki
   işleri görür. Ayrı bir "kontrol" ucu açılmaz.
3. **Oluşturma yanıtı** — `POST /api/isler` **her zaman 201** döner; asla 409 vermez. Yanıt gövdesinde
   bilgilendirme amaçlı bir uyarı listesi taşınır:
   ```json
   { "id": 42, "spNo": "SP-12345", "uyarilar": [
       { "kod": "AYNI_SP_VAR", "adet": 2, "mesaj": "Bu bölgede bu SP No ile 2 iş daha var." } ] }
   ```
   `uyarilar` boşsa alan boş dizi olarak gelir (istemcide `null` kontrolü gerekmesin).
4. **Engelleme yok** — uyarı hiçbir koşulda işlemi durdurmaz. Aynı SP ile iş açmak meşru bir durumdur.
5. **Arayüz** — uyarı, iş oluşturulduktan sonra bir bilgi şeridi olarak gösterilir (Faz F web, Faz H mobil).
   Onay diyaloğu **yapılmaz**: kullanıcıyı meşru bir işlemde ikinci kez onaylatmak gereksiz sürtünme.

### D2 — Tamamlanmış belge: **değiştirilemez**  ⚠️ *(Faz V'de daraldı)*

Bir belge `TAMAMLANDI` olduktan sonra işi bitmiştir: yalnızca **görüntülenir**, düzenlenemez.
Revizyon akışı ve "taslağa geri alma" **yoktur**.

> **10 Ağustos 2026 (Faz V):** kural "gönderilmiş" değil **"onaylanmış"** belge için geçerli.
> Keşif ve hakediş gönderildiğinde `ONAY_BEKLIYOR`'a düşer ve bölge sorumlusu yönetici onaylayana
> kadar düzeltme yapabilir. Ayrıca **yönetici**, gerekçe yazarak onaylanmış bir belgeyi de geri
> açabilir (`POST /api/belgeler/{id}/durum`); müdahale `durum_kaydi`'na yazılır. "Yönetici tarafından
> açma yoktur" cümlesi bu tarihten itibaren geçersizdir.

Yöneticinin tek düzenleme yetkisi **sözleşmelerdeki birim fiyatlar**dır (Faz G). Fiyat dondurma
sayesinde bu, tamamlanmış belgelerin tutarını değiştirmez.

> Uygulama sonucu: `PUT /api/belgeler/{id}/satirlar` ve `POST .../tamamla` yalnızca `TASLAK`
> durumundaki belgede çalışır; `TAMAMLANDI` belgede **409** döner.

### D3 — Aşama kuralı: tür içinde **sıra** de geçerli  ⚠️ *(b) şıkkı 3 Ağustos'ta düştü — S6*

İki kısım birlikte işler:

**(a) İş içinde aşama sırası** — `FIZIBILITE → KESIF → HAKEDIS`. Bir aşama açılabilmesi için kendinden
önceki tüm aşamalar **tamamlanmış** olmalı. *(Faz C'de kodlandı, `DocumentStageRule`.)*

**(b) Bölge içinde tür sırası** — bir belge türü, o bölgede **iş sırasına göre** üretilir: N numaralı işin
T türündeki belgesi ancak N'den önceki tüm işlerin T türü **tamamlanmışsa** açılabilir.

Kullanıcının örneği: bir bölgede 3 ön fizibilite, 2 keşif, 1 hakediş var.
- **Keşif** için sıradaki iş 3. iştir (1 ve 2 tamam) → 3. işin keşfi açılabilir.
- **Hakediş** için sıradaki iş 2. iştir → önce 2. işin hakedişi doldurulur, **sonra** 3. işinki.

> ⚠️ Faz C'de yalnızca `KESIF` için "bölgede tek açık belge" kuralı kodlandı
> (`StageRejection.BOLGEDE_ACIK_KESIF_VAR`). Faz D'de `DocumentStageRule` bu daha geniş kurala
> genişletilecek: her tür için sıra kontrolü + yeni ret gerekçesi (ör. `ONCEKI_IS_TAMAMLANMAMIS`,
> hangi işin beklediğini de taşımalı ki kullanıcıya "önce 2. işin hakedişini tamamlayın" denebilsin).
> Veritabanı tarafındaki `belge_acik_kesif_ux` kısmi index'i de buna göre yeniden düşünülmeli.

**Faz D'de uygulanan biçim:** `BOLGEDE_ACIK_KESIF_VAR` kaldırıldı, yerine `ONCEKI_IS_TAMAMLANMAMIS`
geldi (hangi işin beklediğini `BekleyenIs(isId, spNo)` olarak taşır). Yeni kural eskisini kapsıyor:
bir bölgede bir türden **en fazla bir açık belge** olabilmesi (b)'nin doğal sonucudur — N'nin belgesi
açıldıysa N'den önceki tüm işlerin o türü tamamlanmıştır, N'den sonrakiler ise N tamamlanmadan
açılamaz. Bu nedenle veritabanı kısıtı da genelleştirildi:
`belge_acik_kesif_ux` → `belge_acik_tur_ux on belge (bolge_id, tur) where durum = 'TASLAK'`.

> ⚠️ **Faz E'de daraltıldı (karar E3):** kural (b) artık **yalnızca KESIF ve HAKEDIS** için işliyor.
> Fizibilite dış dünyaya (aracı firma + satın alma onayı) bağlı olarak haftalarca bekleyebiliyor ve o
> süre boyunca bölgeyi kilitliyordu; bu bekleme bir saha disiplini sorunu değil. Fizibilitede sırayı
> zorlamayı bırakıyoruz, keşif ve hakedişte aynen sürüyor.
> DB karşılığı: `belge_acik_tur_ux ... where durum in ('TASLAK','ONAY_BEKLIYOR') and tur in ('KESIF','HAKEDIS')`.
> Fizibilitede "iş başına tek belge" güvencesini `belge_is_tur_ux` devraldı.

### D5 — Terk edilen iş kilidi: **iptal uçları eklendi**  *(30 Temmuz 2026, onaylandı)*

D3(b) birebir kalır — kural doğru: bölgede o türde nerede kalındıysa oradan devam edilir. Sorun kural
değil, **kural tıkandığında çıkış yolunun olmamasıydı**: bir işin belgesi hiç tamamlanmazsa (proje düşer,
iş yanlış açılır) o bölge o türde süresiz kilitleniyor ve D2 gereği geri alma da yok.

Çözüm iki uç. **İkisi farklı iş yapar, karıştırılmamalı:**

| Uç | Ne yapar | Sırayı açar mı? |
|---|---|---|
| `POST /api/isler/{id}/iptal` | İşi kuyruktan çıkarır | **Evet** — sıra hesabı iptal edilmiş işi atlar |
| `POST /api/belgeler/{id}/iptal` | Yanlış açılmış **taslağı** çöpe atar | **Hayır** — iş geçerli, o belgeyi hâlâ yapmak zorunda |

Belge iptalinin sırayı açmaması bilinçlidir: açsaydı, bir taslağı iptal etmek sırayı atlamanın yolu
olurdu. İptalden sonra aynı iş o türde **yeni** bir belge açabilir.

**Pazarlıksız ayrıntılar** (ikisi için de geçerli):
1. **Yetki yalnızca yöneticide** (`GuvenlikYapilandirmasi`, tek satır). Bölge sorumlusu kendi kilidini
   kendi açabilse, sırası gelmeyen işi iptal edip kuyruğu atlayabilirdi. 7 kişi + 1 yönetici olduğu ve
   iptal nadir olduğu için yöneticiye sormak yük değil.
2. **Gerekçe zorunlu**; kim ve ne zaman iptal etti kaydedilir. Disiplin kuralının tek istisnası iz
   bırakmadan kullanılamaz — veritabanı kısıtı (`*_iptal_izi_tutarli`) gerekçesiz iptali kabul etmez.
3. **Silme değil, durum değişikliği.** İptal edilmiş iş listede görünmeye devam eder; kaybolan kayıt
   "burada ne oldu" sorusunu cevapsız bırakır.
4. **Tamamlanmış belgeye dokunulmaz** (D2). Ön fizibilitesi bitip düşen bir proje meşru bir sondur ve
   o belge geçerliliğini korur. Tamamlanmış belge iptal edilemez → 409.
5. **Numara yeniden kullanılmaz.** İptal edilen `003`, iptal olarak seride kalır; yeni belge `004` alır.
   Ekrana çıkmış (belki yazdırılmış) bir numaranın başka belgeye verilmesi, iptal iznini numara
   karışıklığına çevirirdi.
6. **İş iptal edilirken açık taslakları da iptal edilir**; iş kuyruktan çıkarken taslağının bölgede tür
   kilidini tutmaya devam etmesi anlamsız olurdu.

**İptali geri alma YOKTUR** *(30 Temmuz 2026, onaylandı)*. İptal edilen iş ya da belge tekrar aktife
alınamaz. Gerekçe: iptal zaten gerekçeli ve izli bir işlem; geri alınabilir olması iznin anlamını
zayıflatır ve "iptal ettim–geri aldım" gidiş gelişlerine kapı açar. Yanlış iptal durumunda çözüm
veritabanından elle müdahaledir. Bu yüzden Faz G'deki **onay ekranı atlanamaz** (aşağıda).

> Şema: `V5__iptal.sql` — `is_kaydi.durum` (AKTIF/IPTAL) + iptal izleri, `belge`ye iptal izleri,
> `unique (is_id, tur)` → `belge_is_tur_ux ... where durum <> 'IPTAL'` (aksi hâlde iptalden sonra aynı
> türde yeni belge açılamıyordu, yani iptal işe yaramıyordu).

### D4 — Belgenin `yil` alanı: **işin açıldığı yıl**

Yıl `is_kaydi`nin açıldığı tarihten gelir ve o işin **üç belgesi de** aynı yılı taşır.
Aralık'ta açılan bir işin Ocak'ta çekilen hakedişi yine o yılın belgesidir — "Aralık'ta açılan iş
Aralık belgesidir".

Sonuç: sayaç `(bolge_id, yil, tur)` üçlüsünde işler ve bir işin belge numaraları yıl bakımından
tutarlı kalır (`IST-AVR_2026_FIZIBILITE_007` · `IST-AVR_2026_KESIF_003` · `IST-AVR_2026_HAKEDIS_001`).

---

## FAZ E KARARLARI  *(30 Temmuz 2026, müdürden gelen akış güncellemesi)*

Müdürün bildirdiği akış: bölge sorumlusu giriş yapar, ilk ekranda bilgileri doldurur ve bir **referans
sözleşme** seçer → fizibilite ekranında **sadece miktar girer** → onaya gönderir, durum **"Satın Alma
Onayı Beklemede"** olur → rapor yöneticinin önüne düşer, yönetici inceler ve satır içi butonla PDF/Excel
indirir → raporu aracı firmaya iletir (**uygulama bu adımda hiçbir şey yapmaz**) → alınan karara göre
birim fiyatlara karar verip **sözleşme Excel'ini yükler ve onaylar** → bölge sorumlusu **1. Keşif**
ekranında bu fiyatları görür, miktarı doldurup gönderir, durum **"1.Keşif Tamamlandı"** olur → hakediş
miktarlarını girer, altta **EK1** (zorunlu) ve **EK2** (opsiyonel görsel) ekler, **imza** atar, onay
verir; durum **"Hakediş işlemi gönderildi"** olur.

### ~~E1 — Fiyat listesi **yalnızca o işi** bağlar~~  *(3 Ağustos'ta düştü — S2)*

Her iş için ayrı pazarlık, ayrı fiyat listesi. `fiyat_versiyonu.is_id` boşsa referans (global) sürüm,
doluysa o işin pazarlık listesi. İki iş aynı anda pazarlıkta olabilir — global tek versiyon bunu
karşılamıyordu. Fizibilite **referans** versiyona, keşif ve hakediş **işin kendi** versiyonuna çivilenir.
Fiyat dondurma iki katmanda korunur: `belge_satir.birim_fiyat` kopyası + işe özel versiyonun bir kez
yazılıp asla değişmemesi (ikinci yükleme 409).

### ~~E2 — Durum makinesi: beş değer~~  *(3 Ağustos'ta üçe indi — S1)*

`TASLAK · ONAY_BEKLIYOR · ONAYLANDI · TAMAMLANDI · IPTAL`. Ayrı bir `GONDERILDI` değeri **eklenmedi**:
"1. Keşif Tamamlandı" ile "Hakediş İşlemi Gönderildi" arasında davranış farkı yok (ikisi de nihai ve
değiştirilemez), fark yalnızca gösterilen metinde. Metinler `(tür, durum)` fonksiyonu olarak
`DocumentStatusText`'te tek yerde durur; web ve mobil durum metnini kendi içinde yazmaz.

`ONAY_BEKLIYOR` ve `ONAYLANDI` yalnızca FIZIBILITE'de görülür. `TASLAK → ONAY_BEKLIYOR/TAMAMLANDI`
geçişini bölge sorumlusu (`gonder`), `ONAY_BEKLIYOR → ONAYLANDI` geçişini **yalnızca yönetici**
(`onayla`, fiyat listesiyle birlikte tek transaction) yapar.

### ~~E3 — Bölge sıra kuralı fizibilitede kalkar~~  *(3 Ağustos'ta kural tamamen kalktı — S6)*

Bkz. D3 altındaki not. Keşif ve hakedişte aynen sürüyor.

### ~~E4 — Red / düzeltmeye geri gönderme **yok**~~  *(3 Ağustos'ta onayın kendisi kalktı — S1)*

Yönetici ya onaylar ya bekletir. Yanlış gönderilmiş bir fizibilitenin tek çıkışı **iptal**dir (yönetici,
gerekçeli) — iptal bir red akışı değil, D5 kilit çıkışının bir durum daha kapsaması.

### ~~E5 — Keşif ve hakediş yönetici onayından **geçmez**~~  *(3 Ağustos'ta fizibilite de geçmiyor — S1)*

Gönderilince biter. Onay adımı yalnızca fizibilitede (satın alma) var. Yönetici keşif ve hakedişi
listede görür ve indirir, ama sistemde bir onay tıklaması yoktur.

### E6 — Fizibilitede referans fiyatlar **bölge sorumlusuna görünür**  *(genişledi: üç belgede de — S2)*

Bölge sorumlusu fiyatı **girmez**, salt okunur görür ve yalnızca miktar girer. Yönetici rapor
gönderildikten sonra görür. İndirmede iki varyant: **fiyatlı** (yöneticinin pazarlık tabanı) ve
**fiyatsız `TEKLIF`** (aracı firmaya gidecek kopya).

### E7 — Aynı kablo üzerinde **birden fazla iş** olabilir

Müdür bildirdi: bazı hatlar çok uzun. `is_kaydi.kable_id` üzerinde unique kısıt **yok** ve konmayacak
(D1'in SP No kararının aynısı). Hattın hangi parçası olduğunu **Ek Aralığı** ve **Route** ayırt ediyor.
Eklenen tek şey görünürlük: `AYNI_KABLE_VAR` uyarısı (engellemez), `?kableNosid=` süzgeci,
`is_kaydi_kable_ix` indeksi ve listede `kableNosid` + `ekAraligi` sütunları.

> Açık soru 8 ile çelişmiyor: **bir işe** birden fazla kable bağlanamaz (1 iş = 1 kable);
> **bir kabloya** birden fazla iş bağlanabilir. Yön farklı.

### ~~E8 — İmza ekranda çizilir, **tek imza**~~  *(3 Ağustos'ta imza tamamen kalktı — S7)*

Tablette parmak/kalem, web'de fare. Görüntü olarak saklanır ve PDF'e basılır. İmzalayan bölge
sorumlusudur; adı belgeye **dondurularak** yazılır (kullanıcı sonradan yeniden adlandırılabilir).

### E9 — EK2 görselleri PDF'e **basılmaz**

Yalnızca adedi yazılır; görseller dosya olarak durur ve ayrı indirilir. PDF küçük ve hızlı kalır.

### E10 — Excel eşleştirme riski: dört katmanlı savunma  *(3 Ağustos'ta miktar yüklemesine taşındı — S9)*

`poz.satir_kodu` = fiziksel Excel satır numarası (`r5`). Yüklenen dosyada satır kaymışsa fiyatlar yanlış
kaleme yazılır ve **hata sessizdir**. Çözüm:

1. **Kimliği dosyanın içine göm.** `GET /api/belgeler/{id}/fiyat-sablonu.xlsx` kalıcı kimlik sütunlu
   (`poz.id`), fiyatı boş bir şablon üretir; yükleme o sütundan eşleştirir. Satır eklenip silinse, sıra
   karışsa bile eşleşme bozulmaz.
2. **Kimlik sütunu yoksa katı doğrulama.** `satir_kodu` ile eşleş, ama her satırı `poz_no` + `ad` +
   `birim` ile teyit et; 86 kalemin tamamı fiyatlı, 9 grup satırı fiyatsız olmak zorunda. Sapmada
   **400 + tam liste**, kısmi yükleme yok.
3. **Yazmadan önce zorunlu önizleme** (`.../fiyat-listesi/onizleme`) — kaç kalem eşleşti, hangi yolla,
   referans → yeni fiyat, toplam değişim. Arayüzde atlanamaz.
4. **Sayının kendisine akıl kontrolü** — referanstan 10 kat sapan fiyat uyarı üretir; eşleştirme doğru
   olsa bile yazım hatasını yakalar.

Ayrıca `fiyat_versiyonu.kaynak_dosya_adi` + `kaynak_ozet` (SHA-256) ile hangi dosyanın yüklendiği
kayıtta kalır.

> **Doğrulanan veri:** her sözleşmede 9 grup başlığı + 86 kalem, 86 kalemin **tamamı fiyatlı**.
> "Fiyatsız kaleme sonradan fiyat verme" diye bir durum yok → `poz.fiyatli_mi` şeması değişmiyor.

### E11 — Ön koşul: `ContractXlsxParser` iki kusuru

Bir CLI'da geliştiricinin sorunu, bir HTTP ucunda para hatası:
- `CellType.FORMULA` exception atıyor → anlaşılır 400: *"Fiyat sütununda formül var; değer olarak
  yapıştırıp tekrar yükleyin."* Formülü hesaplamak `Double`'a düşmek demek (CLAUDE.md §5 yasağı).
- `STRING` dalı `"1234.56"` metnini **`123456`** yapıyor (`.` binlik sanılıyor).

Parser'ın hiç testi yoktu; `:tools:contract-generator`'a test kaynak seti eklenir.

---

## FAZ X — Yeknesek Durum Makinesi (müdür geri bildirimi)  ·  `Durum: 🚧`

**Amaç (müdür geri bildirimi, 14 Ağustos 2026):** Eski iki katmanlı durum modeli (proje
`ACIK/ISLENIYOR/KAPANDI` + belge `TASLAK/ONAY_BEKLIYOR/TAMAMLANDI`) müdürü karıştırıyordu. Üç aşama
(ön fizibilite, keşif, hakediş) **tek yeknesek döngüye** indirildi:

```
ONAY_BEKLIYOR → İŞLENIYOR → TAMAMLANDI            (+ İPTAL her aşamada)
```

- Açılan aşama doğrudan `ONAY_BEKLIYOR` doğar; kullanıcı "Gönder" der (`gonderme` izini atar), içerik
  onaya kadar düzenlenebilir. Yönetici üç seçenek: **beklet / işleme al / iptal** — ~~İade~~ kaldırıldı.
  ⚠️ **İade 19 Ağustos 2026'da geri geldi**, ama eski anlamıyla değil: "belgeyi düzeltilsin diye geri
  gönderme" değil, **aşamayı sıfırlama** (bkz. FAZ Y).
- Yönetici "İşleme Al" (`onayla`): `ONAY_BEKLIYOR → ISLENIYOR` (hakediş → `TAMAMLANDI`). Bir sonraki
  aşama açıldığı an önceki aşama `ISLENIYOR → TAMAMLANDI` olur (tek transaction).
- Proje durumu **türetilir**: güncel (en ileri, iptal olmayan) aşamanın durumunu yansıtır; Projeler ve
  Belgeler ekranları **aynı dört değeri** ve aynı süzgeç listesini kullanır. Durum metni tür ön eksiz.
- Yönetici onayı artık **proje listesinde** (kart), belge detayında değil (UX — madde 8).

**Yapıldı (bu tur):** `DocumentStatus`/`WorkOrderStatus`/`IsDurumu` yeniden tanımlandı;
`DocumentStageRule` (kapı: önceki aşama `ISLENIYOR`), `onaySonrasiDurum`; `BelgeServisi`
(olustur→önceki aşamayı tamamla, gonder=izli, onayla=işleme al, `projeDurumunuTazele`, iade kaldırıldı);
`BelgeMetinleri`/`IsMetinleri` ön eksiz; `V17__yeknesek_durum.sql` (veri göçü + kısıtlar + default);
güvenlik (`/iade` kaldırıldı); web (`tipler`, `bilesen.durumAilesi`, `IsListesi` stepper+kart onayı,
`DurumPaneli`/`BelgeDurumPaneli`, `BelgeListesi`/`IsListesi` süzgeci, `BelgeEkrani`); mobil parite
(`ApiModels`, `StatusTones`, `WorkOrderListScreen`, `DocumentArchiveScreen` — Android SDK'sız derlenmedi).

**Kalan / DoD:**
- [ ] `deploy/dogrula-faz-x.sh` (V akışını örnek alan uçtan uca doğrulama, geliştirme DB'si).
- [ ] Docker'lı ortamda `:server` entegrasyon testleri + migration çalıştırma.
- [ ] Mobil (`:app`) Android makinede derlenip gözle doğrulanmalı; yönetici "İşleme Al" akışı mobilde yok.
- [ ] CLAUDE.md §3 (Akış + enum tablosu) yeni modele göre güncellenmeli (bu faz onaylanınca).

> Sonraki fazlar (müdür geri bildiriminden, kullanıcı onaylı): Sorumlu ≠ Oluşturan (16), Kullanıcı
> yönetimi (12,13), Sözleşme yüklerken isim (3), Yönetici kapsam ekranı (4, düşük öncelik).

---

## BACKLOG (uygulanmaz, not düşülür)

- **İşe özel pazarlık fiyatı** ihtiyacı geri gelirse `fiyat_versiyonu.is_id` yeniden açılır — V10
  ile silindi, kurgusu S2'de kayıtlı.
- ~~**`ISLENIYOR` işi `ACIK`'a geri alma.**~~ → **Faz V'de uygulandı**: yönetici her durumdan her
  duruma geçebiliyor, gerekçe zorunlu ve müdahale kayda geçiyor. D2 ile çakışma, kuralın
  "onaylanmış belge değişmez" biçiminde daraltılmasıyla çözüldü.
- **Referans fiyat düzenleme ekranı** (taslak aç → satır düzenle → yayınla). Faz L'de vardı, Faz N'de
  kaldırıldı; bugün fiyat değişikliği sözleşmeyi yeni kodla yüklemekle yapılıyor.
  Faz V bunun **yarısını** karşıladı: yeni poz eklenebiliyor, var olan fiyat hâlâ değişmiyor.
- **Durum müdahalesi geçmişi ekranı.** `durum_kaydi` yazılıyor ve
  `GET /api/{isler|belgeler}/{id}/durum-gecmisi` uçları var; ayrı bir raporlama ekranı yazılmadı.

- Click Sense API'sinden kable/uç verisi çekme (IT'den API alınabilirse) — şimdilik xlsx içe aktarımı
- **Saha adı → bölge eşleşmesinin arayüzden düzenlenmesi** (`bolge.xlsx_saha_adi`). Faz U'da envanter
  yükleme yöneticiye açıldı, ama envanterde yeni bir saha adı belirirse yükleme durur ve çözümü
  migration'dır — yöneticinin yapabileceği bir şey yok. Mesaj hangi adların tanımsız olduğunu
  söylüyor; talep gelirse küçük bir eşleşme ekranı açılır
- Sude'nin .NET projesiyle ortak tablo kullanımı (o proje şu an dosya sistemi kullanıyor, DB'si yok)
- Mobilde offline çalışma + senkronizasyon (bilinçli olarak iptal: VPN ile çevrimiçi çalışılıyor)
- AD/LDAP ile giriş (`kullanici.ad_kullanici_adi` alanı baştan hazır bırakıldı)
- Ortak ağ dizinine arşiv kopyası (ikinci hedef)
- ~~**Global fiyat versiyonlama ekranı** (eski Faz G): referans fiyat listesi migration/`:tools` ile
  yükleniyor (kable envanteri emsali); yöneticinin arayüzden yaptığı yükleme **işe özel** olan~~
  → **Karar 1 Ağustos 2026'da değişti ve Faz L olarak uygulandı.** Gerekçe: tek bir birim fiyatın
  düzeltilmesi bile geliştirici müdahalesi + sunucu dağıtımı gerektiriyordu; kullanıcı sözleşme
  yükleme ve fiyat güncellemeyi arayüzden istedi
- Yeniden pazarlık: onaylanmış bir işe ikinci fiyat listesi yüklenmesi (şimdilik 409)
- ~~Faz F arayüzünde "sıra sende / sırada SP-… var" göstergesi~~ — **Faz H'de karşılandı**: sunucunun
  409 mesajı bekleyen işi zaten adıyla söylüyor ("sırası SP-… işinde (#…)") ve iş detayında uyarı
  şeridi olarak basılıyor. Ayrı bir uç açılmadı; aynı kuralı iki yerde yaşatmak olurdu.
  *Açık kalan:* belge açmayı **denemeden önce** görünen bir gösterge yok — kullanıcı 409'u alınca
  öğreniyor. Gerekirse liste ucuna "sıradaki iş" alanı eklenebilir
- Route / Ek Aralığı'nın kable envanterinden türetilmesi (IT'den yeni sütun veya API gerekir — açık soru 9)
- SP No'nun elle yazma yerine **seçim listesinden** gelmesi: SP numaralarının durduğu bir sistem/liste
  varsa mümkün; yoksa elle yazma tek yol (açık soru 13)
- Denetim kaydının ayrı bir raporlama ekranı
- Hilt / Room (eski PHASE.md Backlog'undan devraldı)
