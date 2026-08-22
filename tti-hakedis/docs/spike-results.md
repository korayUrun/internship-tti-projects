# Faz 0 — Teknik Spike Sonuçları

Tarih: 28 Temmuz 2026
Ortam: Android API 36 (aarch64 emülatör), minSdk 26, `coreLibraryDesugaring` açık.
Testler: `app/src/androidTest/kotlin/tr/com/tti/hakedis/spike/`

> Not: Aşağıdaki sonuçlar emülatörde alınmıştır. **Gerçek tablet üzerinde doğrulama kullanıcı
> tarafından yapılacaktır** (PHASE.md Faz 0 DoD → `installDebug` ve tablette gezinme maddesi açık).

---

## SPIKE A — PDF (WebView + PrintDocumentAdapter) ✅ ÇALIŞIYOR

**Test:** `SpikeAPdfTest.webViewPrintAdapterCokSayfaliPdfUretir`
120 satırlık Türkçe karakterli HTML tablo → dosyaya PDF.

**Sonuç**

| Ölçüm | Değer |
|---|---|
| Üretilen dosya | 90.196 byte, geçerli `%PDF` başlığı |
| Sayfa sayısı | **6** (hedef: en az 3 — sayfa kırılımı çalışıyor) |
| `PdfRenderer` ile açılabiliyor mu | Evet |
| Türkçe karakterler | `ŞĞÜÖÇİI şğüöçıi` ve `₺` sorunsuz (çıktı render edilip gözle kontrol edildi) |
| Süre | ~14 sn (emülatör; ilk WebView oluşturma dahil) |

**Karar: WebView + `PrintDocumentAdapter` ile devam.** iText'e (AGPL) gerek yok.

**Faz 6 için not edilenler**

1. `PrintDocumentAdapter.LayoutResultCallback` ve `WriteResultCallback` sınıflarının yapıcıları
   **package-private**'dır. Bu yüzden çağıran yardımcı sınıf `android.print` paketinde durmak
   zorundadır. ✅ Faz 6'da üretime alındı: `app/src/main/kotlin/android/print/PdfPrint.kt`
   (spike testi artık bu üretim sınıfını kullanıyor).
2. WebView oluşturma, `loadDataWithBaseURL` ve `createPrintDocumentAdapter` **main thread** ister;
   `onLayout`/`onWrite` callback zinciri de main thread'de tetiklenir. Faz 6'da bu akış
   `suspendCancellableCoroutine` ile sarılacak, dosya yazımı `Dispatchers.IO`'ya taşınacak.
3. `PrintAttributes`: A4 + 300 dpi + `Margins.NO_MARGINS`, sayfa kenar boşluğu CSS `@page margin`
   ile veriliyor.
4. `thead { display: table-header-group }` şablonda mevcut; **çok sayfada başlık tekrarının
   gözle doğrulanması gerçek cihaz testine bırakıldı.**
5. Şablon düzeltmesi gerekiyor: dar sütunda `730,00 ₺` alt satıra kayıyor. ✅ Faz 6'da
   `td.num { white-space: nowrap }` ile çözüldü.

---

## SPIKE B — Excel (fastexcel writer) ✅ ÇALIŞIYOR

**Test:** `SpikeBExcelTest.fastexcelCihazdaXlsxYazar` — `org.dhatim:fastexcel:0.20.2`

**Sonuç**

| Ölçüm | Değer |
|---|---|
| Üretilen dosya | 4.001 byte, geçerli zip (`xl/workbook.xml`, `xl/worksheets/sheet1.xml`, `xl/styles.xml` …) |
| Türkçe sheet adı | `2026_Güvenay_Rota-1` — korunuyor |
| Türkçe hücre içeriği | `Sert zeminde kırıcı ve makine ile tranşe yapımı — şğüöçİI` — korunuyor |
| `BigDecimal` yazımı | `sheet.value(row, col, BigDecimal)` destekleniyor, hücre gerçek `number` tipinde |
| Hücre formatı | `.style().format("#,##0.00").set()` uygulanıyor |
| Stil (kalın + dolgu) | `.bold().fillColor("E8F0FF")` çalışıyor — grup satırları için yeterli |
| Dexing | minSdk 26 + desugaring ile sorunsuz (eksik JDK sınıfı uyarısı yok) |
| Süre | ~0,3 sn |

**Karar: `fastexcel` (writer) ile devam.** Apache POI cihaza girmiyor; sadece
`:tools:contract-generator` içinde build-time kullanılacak.

**Faz 7 için not edilenler**

- fastexcel bir **writer**'dır; mevcut bir `.xlsx` şablonunu açıp doldurmaz. Kaynak şablonun
  sütun düzeni ve satır sırası, sözleşme JSON'undaki sıraya göre **yeniden yazılarak** korunacak.
- Masaüstü Excel'de uyarısız açılma kontrolü gerçek dosya ile Faz 7 DoD'sinde yapılacak.

---

## Spike kodunun akıbeti

Her iki test de `androidTest` altında **kalıcı** bırakıldı: kütüphaneler yükseltildiğinde
regresyonu erken yakalarlar. Faz 6/7'de gerçek `PdfGenerator` / `ExcelExporter` yazıldığında
bu testler onların üzerine taşınacak, spike sürümleri silinecek.
