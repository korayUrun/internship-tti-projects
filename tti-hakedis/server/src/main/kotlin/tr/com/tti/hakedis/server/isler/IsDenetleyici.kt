package tr.com.tti.hakedis.server.isler

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.server.belge.BelgeServisi
import tr.com.tti.hakedis.server.belge.BelgeYaniti
import tr.com.tti.hakedis.server.durum.DurumKaydiYaniti
import tr.com.tti.hakedis.server.web.IptalIstegi

/** Belge açılırken sözleşme **zorunlu**: belge daima bir fiyat sürümüne çivilidir (Faz N). */
data class BelgeOlusturmaIstegi(val tur: DocumentType, val sozlesmeKodu: String?)

/** Durum geçişi. [gerekce] **her geçişte** zorunlu (Faz V): yetki geniş, iz zorunlu. */
data class DurumIstegi(val durum: IsDurumu?, val gerekce: String?)

@RestController
@RequestMapping("/api/isler")
class IsDenetleyici(private val servis: IsServisi, private val belgeServisi: BelgeServisi) {
    /**
     * Bölgenin işleri. [spNo] ve [kableNosid] süzgeçleri istemci iş açmadan önce "bu SP ile / bu kabloda
     * başka iş var mı" diye sorabilsin diye var (D1/2, E7). Ayrı bir "kontrol" ucu açılmaz.
     *
     * [ara] arayüzün serbest metin aramasıdır (SP no · kablo · route · ek aralığı).
     */
    @GetMapping
    fun listele(
        @RequestParam(required = false) spNo: String?,
        @RequestParam(required = false) kableNosid: String?,
        @RequestParam(required = false) durum: IsDurumu?,
        @RequestParam(required = false) bolgeKodu: String?,
        @RequestParam(required = false) ara: String?,
    ): List<IsYaniti> = servis.listele(spNo, kableNosid, durum, bolgeKodu, ara)

    /**
     * Ekrandaki listenin `.xlsx` hâli — **aynı süzgeçlerle** (İşler ekranı, "Excel olarak indir").
     *
     * Yol `/{id}`'den önce tanımlı olması yeterli değil; ayırt edici olan `.xlsx` uzantısıdır: Spring
     * sabit yolu değişkenli yola tercih eder, dolayısıyla `liste.xlsx` hiçbir zaman `id` sanılmaz.
     * Aynı kalıp `/api/sozlesmeler/format.xlsx`'te de kullanılıyor.
     */
    @GetMapping("/liste.xlsx")
    fun excel(
        @RequestParam(required = false) spNo: String?,
        @RequestParam(required = false) kableNosid: String?,
        @RequestParam(required = false) durum: IsDurumu?,
        @RequestParam(required = false) bolgeKodu: String?,
        // `ara` burada da olmak ZORUNDA: yoksa indirilen dosya ekrandaki listeden sessizce ayrışır.
        @RequestParam(required = false) ara: String?,
    ): ResponseEntity<ByteArray> {
        val cikti = servis.excel(spNo, kableNosid, durum, bolgeKodu, ara)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(cikti.dosyaAdi).build().toString())
            .contentType(MediaType.parseMediaType(XLSX_TIPI))
            .body(cikti.icerik)
    }

    @GetMapping("/{id}")
    fun bul(@PathVariable id: Long): IsYaniti = servis.bul(id)

    /** Aynı SP No hiçbir zaman 409 üretmez; durum yanıttaki `uyarilar` dizisiyle bildirilir (D1/3). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun olustur(@RequestBody istek: IsOlusturmaIstegi): IsYaniti = servis.olustur(istek)

    /** Aşama kuralı, numara üretimi ve fiyat versiyonu çivilenmesi `BelgeServisi`'nde. */
    @PostMapping("/{id}/belgeler")
    @ResponseStatus(HttpStatus.CREATED)
    fun belgeOlustur(@PathVariable id: Long, @RequestBody istek: BelgeOlusturmaIstegi): BelgeYaniti =
        belgeServisi.olustur(id, istek.tur, istek.sozlesmeKodu)

    /**
     * İşin durumunu değiştirir — yönetici **her durumdan her duruma** çekebilir (Faz V).
     * Gerekçe her geçişte zorunlu; müdahale `durum_kaydi`'na yazılır.
     *
     * Otomatik geçişler (fizibilite açılınca `ACIK`, hakediş onaylanınca `KAPANDI`) bu uçtan
     * geçmez. Yetki `GuvenlikYapilandirmasi`'nda **yalnızca yöneticiye** bağlı; başkası 403 alır.
     */
    @PostMapping("/{id}/durum")
    fun durum(@PathVariable id: Long, @RequestBody istek: DurumIstegi): IsYaniti = servis.durumDegistir(id, istek.durum, istek.gerekce)

    /**
     * **Önceki aşamaya iade** — projenin en ileri açık aşamasını geri alır (18 Ağustos 2026).
     *
     * Aşama `IPTAL` işaretlenir; o türde yeni belge açılabilir hâle gelir ve arayüzde "Henüz
     * açılmadı — sırada bu adım var" olarak görünür. Bölge sorumlusu **her şeyi baştan girer**.
     * Tekrar çağrıldıkça geriye yürür: hakediş → keşif → fizibilite.
     *
     * Gerekçe zorunlu ve müdahale `durum_kaydi`'na yazılır (§ "Yöneticinin durum yetkisi
     * sınırsızdır — bedeli iz bırakmasıdır"). Yetki `GuvenlikYapilandirmasi`'nda yalnızca yöneticide.
     */
    @PostMapping("/{id}/iade")
    fun iade(@PathVariable id: Long, @RequestBody istek: IptalIstegi): IsYaniti {
        belgeServisi.onceyeIade(id, istek.gerekce)
        return servis.bul(id)
    }

    /** Durum müdahalesi geçmişi — kim, ne zaman, hangi gerekçeyle değiştirdi. */
    @GetMapping("/{id}/durum-gecmisi")
    fun durumGecmisi(@PathVariable id: Long): List<DurumKaydiYaniti> = servis.durumGecmisi(id)
}

private const val XLSX_TIPI = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
