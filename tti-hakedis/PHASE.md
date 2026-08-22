# PHASE.md — Mobil İlk Sürüm (ARŞİV)

> 🗄️ **Bu dosya arşivdir.** Aktif yol haritası **`NEWPHASES.md`** (Faz A–I).
>
> 30 Temmuz 2026'da proje çok kullanıcılı, sunucu tabanlı bir sisteme dönüştü.
> Faz 0–7 tamamlandı ve mobil ilk sürümü oluşturdu. Faz 8 ve Faz 9 **iptal** edildi:
>
> | Faz | Durum | Gerekçe |
> |---|---|---|
> | 8 — Ekran 4: Mail Gönderimi | ❌ İPTAL | Mail gönderimi kaldırıldı; belgeler arayüzden indirilir ve arşiv klasörüne kopyalanır |
> | 9 — Offline Dayanıklılık & Taslak | ❌ İPTAL | Offline çalışma şartı kalktı; bölge sorumlusu VPN ile yalnızca çevrimiçi çalışıyor |
> | 10 — Sertleştirme & Release APK | ↪️ devredildi | `NEWPHASES.md` Faz H ve Faz I |
>
> Aşağıdaki içerik, o dönemin kararlarını ve gerekçelerini korumak için olduğu gibi bırakılmıştır.

| Faz | Başlık | Durum |
|---|---|---|
| 0 | Proje İskeleti, Modüller & Teknik Spike | ✅ |
| 1 | Domain Katmanı & Sözleşme Veri Hattı | ✅ |
| 2 | Ekran 1 — Yeni Kayıt Formu | ✅ |
| 3 | Ekran 2 — Poz Tablosu & Hesaplama | ✅ |
| 4 | Ekler (EK1 / EK2) | ✅ |
| 5 | Ekran 3 — Önizleme | ✅ |
| 6 | PDF Üretimi | ✅ |
| 7 | Excel Üretimi | ✅ |
| 8 | Ekran 4 — Mail Gönderimi | ❌ İPTAL |
| 9 | Offline Dayanıklılık & Taslak | ❌ İPTAL |
| 10 | Sertleştirme & Release APK | ↪️ NEWPHASES.md Faz H/I |

Legend: ⬜ başlamadı · 🚧 devam ediyor · ✅ bitti

---

## FAZ 0 — Proje İskeleti, Modüller & Teknik Spike

**Amaç:** Üzerine güvenle inşa edilebilecek modül yapısı + riskli kütüphanelerin **erken** doğrulanması.

> Bu fazın en önemli parçası **spike**'tır. PDF ve Excel kütüphanelerinin Android'de gerçekten çalıştığını
> Faz 6/7'de değil, **şimdi** öğrenmek gerekir. Yanlış kütüphane seçimi geç fark edilirse iki fazı çöpe atar.

**Yapılacaklar**
- [x] Gradle KTS + `libs.versions.toml` version catalog, JDK 17 toolchain
- [x] Modüller — **sadece üç tane:** `:app`, `:core:domain` (saf Kotlin/JVM), `:tools:contract-generator` (saf JVM). data/designsystem/export `:app` içinde **paket**tir, modül değil.
- [x] `:core:domain`'in Android bağımlılığı **alamadığını** doğrula (kotlin-jvm plugin)
- [x] `app`: minSdk 26, `coreLibraryDesugaring`, `resizeableActivity=true`, landscape öncelikli
- [x] `AppContainer` + `ViewModelProvider.Factory` iskeleti (manuel DI; Hilt yok)
- [x] `app/designsystem/`: `HakedisTheme`, Color/Type/Spacing token'ları, dinamik renk kapalı
- [x] Navigation Compose: 4 boş ekranlık type-safe `NavHost`
- [x] ktlint kurulumu (detekt ve pre-commit hook yok)
- [x] JUnit4 + Truth kurulumu, örnek geçen test
- [x] **SPIKE A — PDF:** WebView + `PrintDocumentAdapter` ile örnek HTML'den dosyaya PDF üret. Türkçe karakter ve 3 sayfalık tablo kırılımı kontrol et.
- [x] **SPIKE B — Excel:** `fastexcel` ile cihazda örnek `.xlsx` yaz, Excel'de aç. Çalışmazsa alternatifi raporla (**karar verilmeden Faz 7'ye geçilmez**).

> **Ortak Composable üretme.** `PrimaryButton`, `LabeledTextField`, `DropdownSelect` gibi bileşenler hiç ekran yokken tasarlanmaz; Faz 2'de ilk ihtiyaçta yerinde yazılır, **ikinci ekranda tekrar edince** `designsystem/` altına taşınır.

**DoD**
- [ ] `./gradlew installDebug` ile **tablette** 4 boş ekran arasında gezilebiliyor — ⏸ *fiziksel cihaz testi kullanıcıya bırakıldı; debug APK derleniyor*
- [x] `ktlintCheck test` temiz
- [x] Spike A ve B sonuçları `docs/spike-results.md` içinde yazılı, kütüphane kararı kesinleşmiş (WebView+PrintDocumentAdapter ✅ · fastexcel ✅)
- [x] Hiçbir ekranda hardcoded renk/string yok

---

## FAZ 1 — Domain Katmanı & Sözleşme Veri Hattı

**Amaç:** Projenin kalbi. UI olmadan, sadece unit test ile doğrulanan iş mantığı.

**Yapılacaklar**
- [x] `contract/`: `ContractMeta(id, name, title, sheetName)`, `PozItem(id, pozNo, sortKey, name, unit, unitPrice: BigDecimal, isGroup, isPriced)`, `Contract`
- [x] `workorder/`: `Region` enum (İst-AVR, İst-AND, Edirne, Ankara, Kütahya, İzmir, Bursa), `Scope` enum (Deplase/İyileştirme, Arıza Sonrası Onarım, Zorunlu Deplase, Yeni Tesis, Yatay Sondaj), `WorkOrder`, `EntryMode`
- [x] `WorkOrderValidator`: SP No / Route / Ek Aralığı / KableID zorunlu, trim + boş kontrolü, alan bazlı hata tipi
- [x] `pricing/QuantityParser`: `,` ve `.` kabul, negatif red, `null` vs `0` ayrımı
- [x] `pricing/MoneyFormatter`: `tr-TR` para/sayı formatı
- [x] `pricing/PricingCalculator`: `rowAmount(unitPrice, qty)`, `totals(items, entries, mode)` → `Totals(kesif, hakedis)`
- [x] `ContractRepository` — **somut sınıf**, `app/data/` altında (assets + kotlinx.serialization). Domain sadece `Contract`/`PozItem` modellerini tanımlar; interface çıkarma.
- [x] `:tools:contract-generator`: Apache POI ile `.xlsx` → JSON
      - 1. satırdan `title`; 2. satırdan sütun eşlemesi (**"ESK Sonrası Güncel Fiyat" → `unitPrice`**)
      - grup satırı tespiti (fiyat ve birim boş) → `isGroup = true`
      - `pozNo` string kalır; `sortKey: List<Int>` üretilir (`"4.10"` → `[4,10]`, `"1,5"` → `[1,5]`)
      - bozuk satır bulursa **hata ile durur**, sessizce atlamaz
- [x] Testler: ondalıklı fiyatla kuruş doğruluğu, grup satırı toplama katılmıyor, `4.10 > 4.9` sıralaması, `"12,5"` ve `"12.5"` aynı sonuç, negatif/harf girdisi reddediliyor

**DoD**
- [x] `./gradlew :tools:contract-generator:run` JSON'ları üretiyor, `:app` assets'e düşüyor (3 sözleşme, 95'er satır)
- [x] `:core:domain` içinde **hiçbir Android/Compose import'u yok** (derleme bunu zaten engelliyor)
- [x] Bilinen bir poz için elle hesaplanan tutar ile `PricingCalculator` çıktısı **kuruşu kuruşuna** aynı
      (poz 1.1 · 648,35852723050107 × 3 = 1.945,08 ₺)
- [x] `pricing/` paketinin tüm public fonksiyonları test edilmiş — 38 unit test geçiyor.
      `document/` paketi Faz 5'te yazılacak, testi de o fazda gelir.

**Faz 1'de PHASE'den sapmalar (bilinçli)**
- `PricingCalculator.totals(items, entries)` — imzada `mode` yok. Her iki toplam da her zaman
  hesaplanıyor; iki mod aynı anda dolu olabildiği için (CLAUDE.md §3) mod parametresi gereksizdi.
- Sözleşme başlığı sabit 1. satırdan değil, **`ISC_POZNO` başlık satırı aranarak** bulunuyor:
  kaynak dosyada 1. satır boş, Meserbay sheet'inde ise başlık satırı hiç yok.
- Fiyat sütunu adı iki sheet'te farklı (`ESK Sonrası Güncel Fiyat` / `Br Fiyat`); ikisi de kabul ediliyor.

---

## FAZ 2 — Ekran 1: Yeni Kayıt Formu

**Yapılacaklar**
- [x] `DropdownSelect` ile **Bölge** (7), **Kapsam** (5), **Sözleşme** (3) seçimi
- [x] Zorunlu metin alanları alt alta: `SP No`, `Route`, `Ek Aralığı`, `KableID`
- [x] `WorkOrderViewModel` + `StateFlow<WorkOrderUiState>`; ekran stateless
- [x] `WorkOrderValidator` ile alan bazlı hata mesajları (alanın altında, `Error` renginde)
- [x] Tümü dolmadan **"Sonraki Sayfa"** disabled
- [x] Sözleşme seçilince JSON yüklenir (`Dispatchers.IO`, loading + hata durumu + tekrar dene)
- [x] Tek yerleşim: landscape öncelikli, portrait'te bozulmuyor (genişliğe göre dallanma yok)

**DoD**
- [x] Eksik alanla ilerlenemiyor; dokunulan alanın altında hata, butonun altında **eksik alanların listesi**
- [x] Cihaz döndürüldüğünde girilen veri kaybolmuyor — state `ViewModel`'de + manifest'te `configChanges`
      *(process death dayanıklılığı Faz 9'daki taslak ile gelecek)*
- [x] Composable içinde iş mantığı yok — sadece `uiState` okuma + `onEvent` çağrısı
- [x] `WorkOrderValidator` unit testi geçiyor (6 test)
- [ ] Ekran davranışı **tablette elle doğrulanmış** — ⏸ *fiziksel cihaz testi kullanıcıda*

**Faz 2 notları**
- Alan sayısı fazla olduğu için tek `onEvent: (WorkOrderEvent) -> Unit` callback'i kullanıldı
  (PHASE.md'nin izin verdiği `XEvent.kt` sealed interface).
- `DropdownSelect` şimdilik `feature/workorder/` altında; ikinci bir ekranda kullanılınca
  `designsystem/`e taşınacak.
- **CLAUDE.md §15/5 hâlâ açık:** bölge seçimi sözleşme listesini filtrelemiyor — üç sözleşme de
  her bölgede seçilebiliyor. Filtreleme isteniyorsa küçük bir değişiklikle eklenir.
- Sözleşme seçilir seçilmez JSON yükleniyor; başarısızlıkta "Tekrar dene" çıkıyor ve
  yükleme bitmeden "Sonraki Sayfa" açılmıyor.

---

## FAZ 3 — Ekran 2: Poz Tablosu & Hesaplama

**Amaç:** Uygulamanın en kritik ve en performans-hassas ekranı.

**Yapılacaklar**
- [x] Üstte `SegmentedButton`: **1. Keşif** / **Saha Uygulama**
- [x] `LazyColumn` tablo (satır `key = pozItem.id`), 8 sütun
- [x] `Br Fiyat` seçilen sözleşmenin `unitPrice` değerleriyle dolu (salt okunur)
- [x] Aktif moda ait miktar sütunu düzenlenebilir; diğer mod `enabled = false`
- [x] Miktar girildikçe satır tutarı **anlık** hesaplanır (state'te saklanmaz, ViewModel'de türetilir)
- [x] Grup satırları: `PrimarySoft` dolgulu, kalın, input yok, toplama katılmaz
- [x] `isPriced = false` satırları disabled + "Bu sözleşmede fiyatlandırılmamış" ipucu
- [x] Ekran altında **sabit toplam çubuğu**: iki toplam da görünür, aktif mod vurgulanır
- [x] Yatay kaydırmada `Poz No` + `İş Kalemi` sabit, kalan sütunlar ortak `ScrollState` ile kayar
- [x] "Sadece dolu satırlar" filtresi + poz no / iş kalemi arama (altında kalemi kalmayan grup başlıkları gizlenir)
- [x] `LazyColumn` satırlarında `key = pozItem.id`; peşin optimizasyon yapılmadı

**DoD**
- [ ] Gerçek tablette 500+ satırlı sözleşmede kaydırma ve hücreye yazma akıcı — ⏸ *cihaz testi kullanıcıda*
      (mevcut sözleşmeler 95 satır; takılma görülürse optimizasyon Backlog'dan alınır)
- [x] Toplam, satır tutarlarının toplamına **birebir** eşit — ikisi de aynı `PricingCalculator.rowAmount`
- [x] `,` ve `.` aynı sonucu veriyor; negatif/harf girişi toplama yansımıyor, alanda hata gösteriliyor
- [x] Mod değişince girilen veriler kaybolmuyor (miktarlar `WorkOrderSession`'da)
- [ ] Compose UI testi **yazıldı ve derleniyor, henüz çalıştırılmadı** — ⏸ *cihaz/emülatör gerekiyor*
      (`app/src/androidTest/.../PozTableScreenTest.kt` — 4 test: satır tutarı + alt toplam, `,`/`.` eşitliği,
      negatif giriş, mod değişiminde veri korunumu)

**Faz 3 notları**
- **`data/WorkOrderSession.kt` eklendi:** Ekran 1 iş tanımını, Ekran 2 miktarları yazıyor; Ekran 3/PDF/Excel
  aynı yerden okuyacak. Geri dönülüp devam edilince veri kaybolmuyor. Faz 9'da DataStore'a yazılacak.
  Sözleşme değişirse girilen miktarlar **silinir** (satır kimlikleri sözleşmeye özel).
- Ham metin (kullanıcının yazdığı) ile çözümlenmiş `BigDecimal` ayrı tutuluyor: "12," yazarken alan zıplamıyor,
  geçersiz girişte satır toplama katılmıyor ama metin ekranda kalıyor.
- `LoadState` iki ekranda kullanıldığı için `feature/` paketine taşındı.
- **"İleri" en az bir miktar girilmeden açılmıyor** (PHASE'de yazmıyordu; boş önizlemeyi engellemek için).
- EK1/EK2 alanları bu ekranın altına Faz 4'te eklenecek.

## FAZ 4 — Ekler (EK1 / EK2)

**Yapılacaklar**
- [x] **EK1 (zorunlu):** `OpenDocument` ile `.kmz`/`.kml` seçimi; **uzantı bazlı** doğrulama;
      dosya adı + boyut gösterimi; "Değiştir" ve "Kaldır"
- [x] **EK2 (opsiyonel):** Photo Picker ile çoklu görsel + kameradan çekim; thumbnail şeridi; tek tek silme
- [x] Seçilen dosyalar `context.filesDir/attachments` altına **kopyalanır** (`data/FileStorage.kt`), dış URI saklanmaz
- [x] Görsel küçültme/sıkıştırma (uzun kenar ≤ 2048 px, hedef ≤ 1,5 MB, kalite kademeli düşürülür) — `Dispatchers.IO`
- [x] EK1 yoksa "İleri" disabled + "EK1 (güzergâh dosyası) eklenmeden ilerlenemez." uyarısı
- [ ] `CAMERA` izni runtime akışı — **bilinçli olarak yapılmadı**, aşağıdaki nota bakınız

**DoD**
- [x] Yanlış uzantı reddediliyor, sebebi yazıyor ("Sadece .kmz veya .kml uzantılı dosya seçilebilir.")
      `RouteFileValidator` 4 unit testle doğrulandı (Türkçe locale tuzağı dahil)
- [ ] Uygulama kapanıp açılınca ekler duruyor — ⏸ *dosyalar `filesDir`'de kalıcı, ancak liste bellekte;
      kalıcılık Faz 9'daki taslak ile tamamlanacak (PHASE'in kendi notu)*
- [ ] Android 13+ izin akışı sorunsuz — ⏸ *cihaz testi kullanıcıda (Photo Picker izin istemiyor)*
- [ ] 10 görselde OOM olmuyor — ⏸ *cihaz testi kullanıcıda; kod tarafında `inSampleSize` ile ölçekli
      okuma + `recycle()` + 1,5 MB sıkıştırma var*

**Faz 4 notları**
- **`CAMERA` izni bilinçli olarak istenmiyor.** `ActivityResultContracts.TakePicture` + `FileProvider`
  URI'si ile çekim yapılırken izin **gerekmiyor**; izin sadece manifest'te `CAMERA` **tanımlanırsa**
  zorunlu hâle gelirdi. Gereksiz izin istememek için tanımlanmadı (MDM/kurumsal dağıtımda da avantaj).
  İstenirse manifest'e eklenip runtime akışı yazılabilir.
- `FileProvider` yapılandırması bu fazda kuruldu (`res/xml/file_paths.xml` + manifest `provider`);
  Faz 8'de PDF/Excel eklerini paylaşmak için de aynı yapı kullanılacak.
- Ekler bölümü poz tablosunun altında **katlanabilir**: kapalıyken tek satır özet
  ("EK1: guzergah.kmz · EK2: 3 görsel"), tablo alanını yemiyor.
- Görsel önizlemeleri `Dispatchers.IO`'da ölçekli okunup ViewModel'de tutuluyor; Composable dosya okumuyor.
- Coil gibi bir görsel yükleme kütüphanesi eklenmedi — 10 küçük thumbnail için gereksiz.

## FAZ 5 — Ekran 3: Önizleme

**Yapılacaklar**
- [x] `:core:domain/document/DocumentModelBuilder` — önizleme, PDF ve Excel'in **ortak** veri kaynağı (saf fonksiyon)
- [x] Başlık = seçilen sözleşmenin `title` alanı (Excel'in 1. satırı)
- [x] Etiket–değer bloğu: Bölge, Kapsam, Sözleşme adı, SP No, Route, Ek Aralığı, KableID, Oluşturulma Tarih/Saat
- [x] Kalem tablosu: **sadece miktarı girilmiş satırlar**; grup başlığı altında dolu satır varsa korunuyor
- [x] Aktif moda göre toplam(lar): tek mod doluysa tek "Miktar/Tutar" çifti, ikisi de doluysa dört sütun
- [x] Ek özeti: EK1 dosya adı, EK2 görsel sayısı + thumbnail'lar
- [x] "Düzenle" ile ilgili ekrana geri dönüş (veri `WorkOrderSession`'da korunuyor)

**DoD**
- [x] Önizlemede görünen her değer PDF/Excel ile aynı modelden besleniyor (`DocumentModel`)
- [x] Hiç kalem girilmemişse boş durum mesajı + "Mail Gönder" kapalı
- [x] `DocumentModelBuilder` için 10 unit test; `DocumentFileNamer` için 5 unit test

**Faz 5 notları**
- `WorkOrderSession.createdAt` eklendi: önizleme, PDF, Excel ve dosya adı **aynı** zaman damgasını kullanır.
- `DocumentFileNamer` (dosya adı + Türkçe→ASCII sanitize) bu fazda yazıldı; Faz 6/7 kullanıyor.
- Domain'de kullanıcıya görünen metin yok: model `Region`/`Scope` sabitlerini taşır, metne çevirme `:app`'te.

---

## FAZ 6 — PDF Üretimi

> Faz 0 Spike A sonucu esas alınır.

**Yapılacaklar**
- [x] `app/export/PdfTemplate.kt` — HTML+CSS şablon, A4 portrait, etiket–değer bloğu + kalem tablosu
- [x] Sağ üstte **TTI logosu** — `res/drawable-nodpi/tti_logo.png` base64 olarak HTML'e gömülüyor;
      logo okunamazsa metin marka bloğuna düşer
- [x] Etiket–değer bloğu + kalem tablosu + toplam satırı + ek bilgi alanı
- [x] Uzun tabloda sayfa kırılımı, her sayfada `thead` tekrarı (`display: table-header-group`)
- [x] Tüm değerler HTML-escape ediliyor (test edildi)
- [x] `app/export/PdfGenerator.kt` — WebView tabanlı, `suspend fun generate(model): File`,
      `suspendCancellableCoroutine` ile sarılı, WebView ana thread'de
- [x] Üretilen PDF `FileProvider` ile açılabiliyor/paylaşılabiliyor (`export/DocumentSharing.kt`)

**DoD**
- [x] Örnek dolu kayıt için şablon çıktısı referans düzene yakın — HTML çıktısı masaüstü WebKit ile
      render edilip gözle doğrulandı (başlık, marka bloğu, etiket–değer, grup ara başlıkları, sağa dayalı tutarlar)
- [ ] 100+ kalemli kayıtta sayfa taşması/kesilme yok — ⏸ *Spike A'da 120 satır → 6 sayfa sorunsuzdu,
      gerçek şablonla cihaz doğrulaması kullanıcıda*
- [x] Türkçe karakterler (ş, ğ, İ, ı, ç, ö, ü) ve `₺` bozulmuyor
- [x] Üretim sırasında UI donmuyor: "PDF oluşturuluyor…" göstergesi, buton pasif

**Faz 6 notları**
- Spike A'daki `PdfPrintSpike` üretime alındı → `app/src/main/kotlin/android/print/PdfPrint.kt`
  (paket adı `android.print` olmak **zorunda**, bkz. `docs/spike-results.md`).
- Spike'ta görülen "`730,00 ₺` alt satıra kayıyor" sorunu `td.num { white-space: nowrap }` ile çözüldü.
- `PdfTemplate` saf Kotlin (Android bağımlılığı yok) → 9 JVM unit testiyle doğrulanıyor
  (logolu ve logosuz üst bant dahil).
- Dosya adı: `TTI_{Bolge}_{SPNo}_{yyyyMMdd-HHmm}.pdf`, `filesDir/exports/` altına yazılıyor.
- Önizleme ekranında "PDF Oluştur" + "PDF'i aç" / "Paylaş"; PDF açacak uygulama yoksa
  Toast ile bilgi verilir, uygulama çökmez.

---

## FAZ 7 — Excel Üretimi

> Faz 0 Spike B sonucu esas alınır.

**Yapılacaklar**
- [x] `app/export/ExcelExporter.kt` — kaynak sütun düzeni ve **satır sırası korunuyor**
      (sözleşmenin tüm satırları, grup başlıkları dahil, aynı sırada)
- [x] Doldurulan sütunlar: `Br Fiyat`, `1. Keşif Talep`, `Saha Uygulama`, `1.Keşif Tutar`, `Kesin Hakediş Tutar`
- [x] Tutarlar **değer** olarak, hücre tipi `number`, format `#,##0.00` (miktarlar `#,##0.###`)
- [x] Grup satırları kalın + dolgulu korunuyor
- [x] Meta bilgi bloğu **tablonun üstünde** (başlık, Bölge…KableID, oluşturulma, EK1/EK2 özeti)
- [x] Toplam satırı (`Genel Toplam`, aktif mod(lar)ın toplamı)
- [x] Dosya adı: `TTI_{Bolge}_{SPNo}_{yyyyMMdd-HHmm}.xlsx`

**DoD**
- [ ] Üretilen dosya masaüstü Excel'de uyarısız açılıyor — ⏸ *cihaz + masaüstü testi kullanıcıda*
- [x] Excel'deki toplam = uygulamadaki toplam — ikisi de aynı `DocumentModel.totals`'tan geliyor
- [x] Türkçe karakterli sheet adı ve içerik korunuyor (Spike B'de doğrulandı)
- [x] Sayı hücreleri metin değil gerçek sayı — `ExcelExporterTest` sheet XML'inde `<v>1945.08</v>`
      arıyor ve formül (`<f>`) olmadığını doğruluyor *(test yazıldı, cihazda çalıştırılmadı)*

**Faz 7 notları**
- **CLAUDE.md §15/4 hâlâ açık.** Meta bilgiler şimdilik **üst blok** olarak yazıldı; "ayrı Bilgiler sheet'i"
  isteniyorsa `ExcelExporter.writeTitleAndMeta` içinde küçük bir değişiklikle taşınır.
- `ExcelExporter` `DocumentModel`'e ek olarak `contract` + `entries` alır: model sadece **dolu** satırları
  taşır, Excel ise şablonun tamamını yazmak zorundadır. Toplamlar yine modelden gelir → ekranla birebir aynı.
- `fastexcel` bir writer'dır; kaynak `.xlsx` açılıp doldurulmaz, düzen yeniden yazılır (Spike B kararı).

---

## FAZ 8 — Ekran 4: Mail Gönderimi

> ⚠️ Başlamadan önce `CLAUDE.md §15` sorularının 1 ve 2'si yanıtlanmalı.

**Yapılacaklar**
- [x] `To`, `CC`, `Konu` alanları (konu ve gövde otomatik dolu, düzenlenebilir)
- [x] E-posta format doğrulaması (`:core:domain/mail/EmailValidator`), çoklu alıcı (`,` `;` boşluk)
- [x] `FileProvider` + `res/xml/file_paths.xml` (Faz 4'te kurulmuştu, `exports/` eklendi)
- [x] `Intent.ACTION_SEND_MULTIPLE` ile PDF + XLSX ekli mail taslağı açılıyor (`export/MailSender.kt`)
- [x] `<queries>` manifest bloğu; mail istemcisi yoksa ekranda açıklayıcı hata, uygulama çökmüyor
- [x] EK1/EK2'nin de eklenip eklenmeyeceği: **kullanıcıya bırakıldı** — iki onay kutusu, ikisi de varsayılan açık
- [x] Gönderim öncesi son özet diyaloğu (kime, CC, konu, ek sayısı)
- [x] Sonrasında: "Yeni Kayıt Oluştur" (oturum sıfırlanır) / geri dönüp kaydı görüntüleme

**DoD**
- [ ] Ekler mail istemcisinde eksiksiz ve açılabilir görünüyor — ⏸ *cihaz testi kullanıcıda*
- [x] Geçersiz e-posta ile gönder butonu pasif; hatalı adresler tek tek yazılıyor (6 unit test)
- [x] Mail istemcisi yoksa uygulama çökmüyor (`ActivityNotFoundException` yakalanıyor, mesaj gösteriliyor)
- [ ] Gmail + Outlook ile en az birer kez test edilmiş — ⏸ *cihaz testi kullanıcıda*

**Kullanıcı geri bildirimiyle değişenler (28 Tem 2026, tablet denemesi sonrası)**
- **PDF/Excel artık çalışanın inisiyatifinde değil:** önizlemedeki "PDF Oluştur"/"Excel Oluştur"
  butonları kaldırıldı. Önizlemede "Mail Gönder"e basıldığı anda mail ekranı açılır ve **her iki dosya
  otomatik üretilip eklere konur** ("Ekler hazırlanıyor (PDF + Excel)…").
- **Özet diyaloğu kaldırıldı, tek adım:** alıcı + (opsiyonel) CC + konu doldurulunca **"Gönder"**.
- **Outlook doğrudan hedefleniyor:** cihazda `com.microsoft.office.outlook` kuruluysa uygulama seçici
  çıkmaz, mail doğrudan Outlook'ta ekleriyle açılır. Kurulu değilse seçici, o da yoksa açıklayıcı hata.

> ⚠️ **Sınır:** Android'de bir uygulama, kullanıcının hesabından **arka planda** mail gönderemez.
> "Gönder" Outlook'u alıcı/konu/gövde/ekler dolu hâlde açar; son gönderme tuşuna Outlook'ta basılır.
> Tek tuşla (uygulamadan çıkmadan) gönderim için iki yol var, ikisi de kurumsal karar gerektirir:
> **(a)** SMTP sunucusu + kimlik bilgisi (yeni bağımlılık, parola cihazda saklanır),
> **(b)** Microsoft Graph API + OAuth (Azure uygulama kaydı, kurumsal onay). Bkz. Faz 10 açık noktalar.

**Faz 8 notları**
- ⚠️ **CLAUDE.md §15/1-2 yanıtlanmadan tamamlandı.** Varsayılan **alıcı adresi yok** (To boş başlıyor);
  konu ve gövde şu varsayımla üretildi:
  - Konu: `TTI Saha Hakediş · {Bölge} · SP {SP No} · {gg.aa.yyyy}`
  - Gövde: bölge/kapsam/sözleşme/SP No/Route/Ek Aralığı/KableID/oluşturulma + toplam(lar) + kapanış cümlesi
  Şablonlar `res/values/strings_mail.xml` içinde tek yerde; gerçek format gelince oradan değiştirilir.
- Ekler ekrana girilirken üretiliyor (PDF + Excel), böylece kullanıcı "Gönder"e bastığında bekleme olmuyor.
- `mailto:` kullanılmadı — ek taşıyamıyor (CLAUDE.md §6).

---

## FAZ 9 — Offline Dayanıklılık & Taslak

**Yapılacaklar**
- [x] DataStore (Preferences) + kotlinx.serialization ile **otomatik taslak kaydı**, 750 ms debounce'lu
      (`data/DraftRepository.kt` + `data/DraftAutoSaver.kt`, tek JSON blob)
- [x] Process death / uygulama kapanması sonrası kaldığı yerden devam (`WorkOrderSession.restore`)
- [x] "Devam eden kayıt var, sürdürülsün mü?" açılış diyaloğu (kaydedilme zamanı da gösteriliyor)
- [x] VPN/bağlantı yokken tüm akış çalışıyor: sözleşmeler assets'ten, ekler ve PDF/Excel yerel dosyalardan;
      **hiçbir yerde ağ çağrısı yok**, sadece mail taslağını açmak harici uygulama ister
- [x] Üretilen dosyalar için temizlik politikası: 30 günden eski ek/çıktı dosyaları açılışta siliniyor,
      aktif taslağın dosyaları korunuyor (`FileStorage.cleanupOldFiles`)
- [x] Global hata yakalama + kullanıcı dostu hata ekranı (`CrashActivity`, ayrı `:crash` process'i),
      teknik detay `Log.e` ile logcat'e

**DoD**
- [ ] Uçak modunda tam akış tamamlanabiliyor — ⏸ *cihaz testi kullanıcıda (kodda ağ bağımlılığı yok)*
- [ ] `adb shell am force-stop` sonrası hiçbir veri kaybolmuyor — ⏸ *cihaz testi kullanıcıda*
- [ ] "Don't keep activities" açıkken state korunuyor — ⏸ *cihaz testi kullanıcıda*
- [x] Taslak serileştirmesi 4 unit testle doğrulandı: tüm alanlar, `BigDecimal` hassasiyeti,
      `null` vs `0` ayrımı, zaman damgaları

**Faz 9 notları**
- **Boş taslak asla yazılmaz ve diskteki taslağı silmez.** Açılışta oturum boş olduğu için otomatik
  kayıt tetiklense bile kullanıcı "Devam et"/"Yeni kayıt" kararını verene kadar taslak korunur.
  Yan etkisi: kullanıcı tüm alanları elle boşaltırsa eski taslak, yeni bir kayıt yapılana ya da
  "Yeni kayıt başlat"a basılana kadar diskte kalır.
- Taslak geri yüklenirken **silinmiş ek dosyaları listeye alınmaz** — kullanıcı olmayan bir eki "var" sanmasın.
- `PozEntry` ve `LocalDateTime` için serileştirici eklendi (`BigDecimalSerializer` zaten vardı);
  para/miktar değerleri JSON'da **string** olarak durur, `Double` üzerinden geçmez.
- Çökme ekranı ayrı process'te çalışır; asıl process çöktüğü için aynı process'te açılamazdı.

---

## FAZ 10 — Sertleştirme & Release APK

**Yapılacaklar**
- [ ] Gerçek tablet üzerinde uçtan uca test (en az 3 farklı senaryo: sadece keşif, sadece saha, ikisi birden)
- [ ] Performans: soğuk açılış < 3 sn, tablo kaydırmada gözle görülür takılma yok (gerçek tablette, elle)
- [ ] Erişilebilirlik: dokunma hedefleri ≥ 48.dp, kontrast oranı, `contentDescription`
- [ ] R8/ProGuard kuralları (kotlinx.serialization, fastexcel) + **release build testi**
- [ ] APK imzalama yapılandırması (keystore repoda değil, `local.properties`/CI secret)
- [ ] `README.md`: kurulum, sözleşme güncelleme prosedürü, APK dağıtımı
- [ ] Sürüm numarası + değişiklik notu

**Önceki fazlardan devredilen açık noktalar**

> Bunlar kapatılmadan Faz 10 "bitti" sayılmaz. Çoğu kullanıcı kararı ya da gerçek cihaz testidir.

*Kullanıcıdan yanıt bekleyenler (CLAUDE.md §15)*
- [ ] **§15/1-2 — Mail To/CC varsayılan alıcıları + konu/gövde şablonu.** Faz 8 varsayımla tamamlandı;
      gerçek format `res/values/strings_mail.xml` içinden güncellenecek.
- [x] **§15/3 — `2026_Meserbay_İzmir`** ✅ kullanıcı kararı: **listeden çıkarıldı**.
      `:tools:contract-generator` içinde `EXCLUDED_SHEETS` ile atlanıyor; uygulamada 2 sözleşme var.
- [ ] **§15/4 — Excel meta bilgileri** üst blokta mı, ayrı "Bilgiler" sheet'inde mi? (şu an üst blok)
- [ ] **§15/5 — Bölge seçimi sözleşme listesini filtrelemeli mi?** (şu an filtrelemiyor)
- [ ] **§15/6 — Yuvarlama** satır bazında yapılıyor (CLAUDE.md §5'e uygun); onaylanacak.
- [ ] **§15/7 — Kayıt geçmişi/arşiv** gerekli mi? (Room → Backlog)
- [x] **§15/8 — TTI logosu.** ✅ Kullanıcıdan alındı ve eklendi: PDF üst bandı, Ekran 1 ve önizleme
      başlığı + markadan türetilen adaptive launcher ikonu. *(Kurumsal kimlik için yüksek çözünürlüklü
      SVG varsa daha sonra değiştirilebilir.)*
- [ ] **§15/9 — Hedef tablet Android sürümü + APK dağıtım yöntemi (MDM var mı?)**

*Kaynak Excel'de düzeltilmesi gerekenler*
- [ ] `2026Güvenay_Rota-2` sheet'inin başlık satırı yanlışlıkla **"Rota-1: ..."** yazıyor → PDF başlığı yanlış çıkar.
- [ ] `2026Güvenay_Rota-2` sheet adında ayırıcı yok → listede "2026Güvenay Rota-2" görünüyor.
- [x] Meserbay sheet'i tamamen çıkarıldığı için M–P sütunları sorunu düştü.

*Gerçek cihaz doğrulamaları (hiç çalıştırılamadı — emülatör/cihaz kullanılmadı)*
- [ ] `./gradlew installDebug` → tablette dört ekran arasında uçtan uca gezinme
- [ ] `./gradlew connectedAndroidTest` → `PozTableScreenTest` (4 test), `ExcelExporterTest`, Spike A/B testleri
- [ ] 500+ satırlı sözleşmede kaydırma/yazma akıcılığı (takılma varsa optimizasyon + not)
- [ ] 100+ kalemli PDF'te sayfa taşması ve her sayfada tablo başlığı tekrarı
- [ ] Üretilen `.xlsx` masaüstü Excel'de uyarısız açılıyor mu
- [ ] 10 görsel eklendiğinde OOM olmuyor · Android 13+ izin akışı
- [ ] Uçak modunda tam akış · `force-stop` sonrası taslaktan devam · "Don't keep activities" açıkken state
- [ ] Gmail + Outlook ile ekli mail taslağı

*Kod tarafında bekleyenler*
- [ ] **Gerçek "tek tuşla gönderim" kararı:** Outlook'u açıp kullanıcının göndermesi yeterli mi,
      yoksa SMTP/Graph ile arka planda gönderim mi isteniyor? (SMTP → sunucu adresi, port, kullanıcı,
      parola; Graph → Azure uygulama kaydı + izin onayı gerekir.)
- [ ] `CAMERA` runtime izni — bilinçli olarak eklenmedi (`TakePicture` + `FileProvider` izin istemiyor);
      istenirse manifest + runtime akışı eklenecek.
- [ ] Ekran 1'de `androidTest` yok (PHASE gereği); poz tablosu UI testi cihazda ilk kez koşacak.

**DoD**
- [ ] İmzalı release APK gerçek tablette sorunsuz çalışıyor (debug'da çalışıp release'te çöken durum yok)
- [ ] `ktlintCheck test` temiz
- [ ] Saha personelinden 1 kişi eğitimsiz olarak akışı tamamlayabiliyor

---

## BACKLOG

> Faz dışı ihtiyaçlar buraya yazılır, **uygulanmaz**. Bir madde gerçekten gerekli hâle gelirse
> kullanıcıyla konuşulup bir faza alınır.

**Bilinçli olarak ertelenenler** (sadeleştirme kararı, 28 Tem 2026):

- **Hilt / DI kütüphanesi** — manuel `AppContainer` yetmemeye başlarsa (bağımlılık sayısı belirgin artarsa) geçilir.
- **Modül bölme** (`:core:data`, `:core:designsystem`, `:core:export`) — kod tabanı büyür veya birden fazla geliştirici çalışmaya başlarsa.
- **`WindowSizeClass` + genişliğe göre çoklu yerleşim** — telefon desteği veya katlanabilir cihaz gündeme gelirse.
- **`kotlinx.collections.immutable` + recomposition optimizasyonu** — Faz 3 DoD'sinde gerçek cihazda takılma ölçülürse.
- **detekt + pre-commit hook** — kod tabanı büyüyüp ktlint yetersiz kalırsa.
- **Room** — tek taslak yerine kayıt geçmişi/arşiv gerekirse (bkz. CLAUDE.md §15/7).
- **Macrobenchmark / otomatik performans testi** — elle gözlem yetmezse.
- **Ekran başına Compose UI testi** — şu an sadece Faz 3 poz tablosunda var.

**Sonradan ortaya çıkanlar:**

- **Kaynak Excel'de düzeltilmesi gerekenler (kullanıcı onayı bekliyor — Faz 5/6'yı etkiler):**
  1. `2026Güvenay_Rota-2` sheet'inin başlık satırı **"Rota-1: ..."** yazıyor (kopyala-yapıştır hatası).
     PDF başlığı buradan geldiği için yanlış çıktı üretir.
  2. `2026_Meserbay_İzmir` sheet'inde **başlık satırı yok** → şimdilik sheet adı başlık olarak kullanılıyor.
     (CLAUDE.md §15/3: bu sheet 3. bir sözleşme mi, yoksa boş şablon mu?)
  3. `2026Güvenay_Rota-2` sheet adında `2026` ile `Güvenay` arasında ayırıcı yok → listede
     **"2026Güvenay Rota-2"** olarak görünüyor. Sheet adı düzeltilirse otomatik düzelir.
  4. Meserbay sheet'inde M–P sütunlarında **ikinci bir fiyat listesi** var (fiyatlı pozların özeti gibi).
     Şu an yok sayılıyor; ana tablodaki fiyatlar esas alınıyor.
- **Sözleşme adı/kimliği sheet adından türetiliyor.** Sheet adı değişirse `id` de değişir; taslakta
  saklanan `contractId` eskirse Faz 9'da göç gerekebilir.
- **`android.print` paketindeki yardımcı sınıf** (Spike A) Faz 6'da `app/export/` altına taşınacak.
