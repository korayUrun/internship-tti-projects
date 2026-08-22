package tr.com.tti.hakedis.server.sozlesme

import org.dhatim.fastexcel.Workbook
import java.io.ByteArrayOutputStream

/**
 * Boş sözleşme formatı (.xlsx) — yöneticinin "Sözleşme Formatını İndir" düğmesi.
 *
 * Bu faza kadar sisteme sözleşme yüklemek için elde doğru biçimde bir dosya bulunmak gerekiyordu;
 * biçim yanlışsa hata ancak yükleme denenince ortaya çıkıyordu. Şablon, `ContractXlsxParser`'ın
 * beklediği düzeni birebir taşır: 1. satır sözleşme başlığı, 2. satır sütun adları, 3. satırdan
 * itibaren poz satırları.
 *
 * Örnek satırlar bilinçli: biri grup başlığı (birimsiz, fiyatsız), biri normal kalem. Kullanıcı
 * "grup satırı nasıl yazılır" sorusunu dosyanın kendisinden okur — parser birimsiz bir satırın
 * fiyatlı olmasını hata sayar ve bu kural yalnızca dokümanda kalsaydı sık ihlal edilirdi.
 *
 * Üretilen dosyanın gerçekten parse edilebildiği `SozlesmeSablonuTest`'te kilitli: şablon ile parser
 * ayrışırsa hata yükleme anında değil, testte çıkar.
 */
object SozlesmeSablonu {
    const val DOSYA_ADI = "TTI_SOZLESME_FORMATI.xlsx"

    /**
     * Sütun başlıkları — dördü de `ContractXlsxParser`'ın tanıdığı adlardan biri olmak **zorunda**.
     * `SozlesmeCiktisi` de bu listeyi kullanır: boş şablon ile dolu çıktı aynı sütun düzenini taşımalı,
     * yoksa indirilen dosya geri yüklenemez.
     *
     * ~~`ISC_POZNO` / `ISC_ADI` / `ISC_BIRIM` / `ESK Sonrası Güncel Fiyat`~~ *(18 Ağustos 2026,
     * kullanıcı isteği)* — NOS+ dışa aktarımının teknik sütun adlarıydı ve şablonu elle dolduran
     * kişiye hiçbir şey anlatmıyordu. Parser eski adları **hâlâ tanıyor** (gerçek sözleşme dosyaları
     * onlarla geliyor); değişen yalnızca bizim ürettiğimiz dosyaların başlıkları.
     */
    val BASLIKLAR = listOf("Poz No", "İş Kalemi", "Birim", "Br Fiyat")

    /** Fiyat sütununun adı — parser'ın tanıdığı adlardan biri (`PRICE_TITLES`). */
    const val FIYAT_BASLIGI = "Br Fiyat"

    private const val UYGULAMA = "TTI Saha Hakediş"
    private const val SURUM = "1.0"
    private const val BASLIK_DOLGU = "E8F0FF"
    private const val GRUP_DOLGU = "EDF1F6"
    private const val PARA_BICIMI = "#,##0.00"

    private const val BASLIK_SATIRI = 1
    private const val ILK_POZ_SATIRI = 2
    private const val SON_SUTUN = 3

    fun uret(): ByteArray {
        val cikti = ByteArrayOutputStream()
        val kitap = Workbook(cikti, UYGULAMA, SURUM)
        val sayfa = kitap.newWorksheet("Sozlesme")

        // 1. satır PDF başlığı olarak kullanılır (CLAUDE.md §4); yer tutucu metin bunu anlatıyor.
        sayfa.value(0, 0, "Sözleşme başlığını buraya yazın (PDF başlığı olarak basılır)")
        sayfa.range(0, 0, 0, SON_SUTUN).style().bold().fontSize(12).set()

        BASLIKLAR.forEachIndexed { sutun, ad -> sayfa.value(BASLIK_SATIRI, sutun, ad) }
        sayfa.range(BASLIK_SATIRI, 0, BASLIK_SATIRI, SON_SUTUN).style()
            .bold()
            .fillColor(BASLIK_DOLGU)
            .borderStyle("thin")
            .set()

        // Grup başlığı: birim ve fiyat BOŞ. Parser birimsiz satırı grup sayar.
        sayfa.value(ILK_POZ_SATIRI, 0, "1")
        sayfa.value(ILK_POZ_SATIRI, 1, "ÖRNEK GRUP BAŞLIĞI (birim ve fiyat boş bırakılır)")
        sayfa.range(ILK_POZ_SATIRI, 0, ILK_POZ_SATIRI, SON_SUTUN).style().bold().fillColor(GRUP_DOLGU).set()

        sayfa.value(ILK_POZ_SATIRI + 1, 0, "1.1")
        sayfa.value(ILK_POZ_SATIRI + 1, 1, "Örnek iş kalemi — bu satırı silip kendi pozlarınızı yazın")
        sayfa.value(ILK_POZ_SATIRI + 1, 2, "Ad.")
        sayfa.value(ILK_POZ_SATIRI + 1, 3, 0 as Number)
        sayfa.style(ILK_POZ_SATIRI + 1, 3).format(PARA_BICIMI).set()

        sayfa.width(0, 12.0)
        sayfa.width(1, 60.0)
        sayfa.width(2, 10.0)
        sayfa.width(3, 20.0)

        kitap.finish()
        return cikti.toByteArray()
    }
}
