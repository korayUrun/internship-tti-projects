package tr.com.tti.hakedis.server.belge

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.server.durum.DurumKaydiYaniti
import tr.com.tti.hakedis.server.sozlesme.PozYaniti
import tr.com.tti.hakedis.server.web.IptalIstegi

/** Miktar girişi — tam değiştirme: gönderilmeyen poz belgeden silinir. */
data class SatirlarIstegi(val satirlar: List<SatirGirdisi>)

/** Belge ekranının üstündeki sözleşme seçimi. */
data class SozlesmeIstegi(val sozlesmeKodu: String?)

@RestController
@RequestMapping("/api/belgeler")
class BelgeDenetleyici(
    private val servis: BelgeServisi,
    private val miktarlar: MiktarServisi,
    private val malzemeServisi: BelgeMalzemeServisi,
) {
    /**
     * Belge arşivi: satır satır belge + durum + bölge + sorumlu.
     *
     * [ara] arayüzün serbest metin aramasıdır (belge no · SP no · kablo ID).
     */
    @Suppress("LongParameterList")
    @GetMapping
    fun listele(
        @RequestParam(required = false) tur: DocumentType?,
        @RequestParam(required = false) durum: DocumentStatus?,
        @RequestParam(required = false) bolgeKodu: String?,
        @RequestParam(required = false) yil: Int?,
        @RequestParam(required = false) ara: String?,
        @RequestParam(required = false, defaultValue = "500") limit: Int,
    ): List<BelgeListeYaniti> = servis.listele(tur, durum, bolgeKodu, yil, ara, limit.coerceIn(1, 2000))

    /** Başka bölgenin belgesi istendiğinde **404** döner (403 varlığını sızdırırdı). */
    @GetMapping("/{id}")
    fun bul(@PathVariable id: Long): BelgeYaniti = servis.detay(id)

    /**
     * Belge ekranının poz listesi — fiyatlar **belgenin kendi sürümünden** gelir.
     *
     * Belge türüne göre farklı uç çağırmak (fizibilitede sözleşme, keşif/hakedişte iş) yönetici
     * referans fiyatları güncellediği an ekran ile kaydı ayrıştırıyordu: ekranda yeni fiyat görünüyor,
     * belgeye eski fiyat yazılıyordu. Tek uç bu ayrışmayı yapısal olarak imkânsız kılıyor.
     */
    @GetMapping("/{id}/pozlar")
    fun pozlar(@PathVariable id: Long): List<PozYaniti> = servis.pozlar(id)

    /**
     * Miktar girişi. Yanıt satır tutarlarını **ve** toplamı taşır: istemci hiçbir hesap yapmaz
     * (CLAUDE.md §13 — web/mobil tarafında para aritmetiği yasak).
     */
    @PutMapping("/{id}/satirlar")
    fun satirlar(@PathVariable id: Long, @RequestBody istek: SatirlarIstegi): BelgeYaniti = servis.satirlariYaz(id, istek.satirlar)

    /**
     * EK3 "TTI Kullanılan Malzemeler" — yalnızca hakediş aşamasında (18 Ağustos 2026).
     *
     * Yazma **tam değiştirmedir** ([satirlar] ile aynı semantik): gönderilen liste ne ise belgenin
     * malzemeleri o olur. Satır satır ekleme/silme uçları açılmadı — form zaten listenin tamamını
     * ekranda tutuyor ve iki uç arasında sıra/kimlik eşitlemek gereksiz karmaşıklık olurdu.
     */
    @GetMapping("/{id}/malzemeler")
    fun malzemeler(@PathVariable id: Long): List<MalzemeYaniti> = malzemeServisi.liste(id)

    @PutMapping("/{id}/malzemeler")
    fun malzemeleriYaz(@PathVariable id: Long, @RequestBody istek: MalzemelerIstegi): List<MalzemeYaniti> = malzemeServisi.yaz(id, istek)

    /**
     * Belgenin sözleşmesini değiştirir. **Girilmiş miktarlar silinir** — poz kimlikleri sözleşmeye
     * özeldir, taşınsalardı yanlış kalemlere yapışırlardı. Yalnızca taslakta; aksi hâlde 409.
     */
    @PutMapping("/{id}/sozlesme")
    fun sozlesme(@PathVariable id: Long, @RequestBody istek: SozlesmeIstegi): BelgeYaniti = servis.sozlesmeDegistir(id, istek.sozlesmeKodu)

    /**
     * Miktar giriş şablonu: seçili sözleşmenin poz listesi + **girilmiş miktarlar dolu**, kalıcı
     * kimlik sütunlu. Kullanıcı doldurup [miktarYukle] ile geri yükler.
     */
    @GetMapping("/{id}/miktar-sablonu.xlsx")
    fun miktarSablonu(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val cikti = miktarlar.sablon(id)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(cikti.dosyaAdi).build().toString())
            .contentType(MediaType.parseMediaType(XLSX_TIPI))
            .body(cikti.icerik)
    }

    /**
     * Doldurulmuş şablonu yükler — **tam değiştirme**. Dosyada tek bir sorun varsa hiçbir şey
     * yazılmaz ve 400 döner (kısmi yükleme yok).
     */
    @PostMapping("/{id}/miktar-yukleme")
    fun miktarYukle(
        @PathVariable id: Long,
        @RequestParam dosya: MultipartFile,
        @RequestParam(required = false) sayfa: String?,
    ): BelgeYaniti = miktarlar.yukle(id, dosya.bytes, sayfa)

    /**
     * "Gönder" — aşamayı gönderim izini atar (durum `ONAY_BEKLIYOR` kalır). Boş belge/EK1 eksikse 409.
     * İçerik onaya kadar düzeltilebilir (müdür kararı: geri gönderme yok).
     */
    @PostMapping("/{id}/gonder")
    fun gonder(@PathVariable id: Long): BelgeYaniti = servis.gonder(id)

    /**
     * Yönetici "İşleme Al": `ONAY_BEKLIYOR → ISLENIYOR` (hakediş: → `TAMAMLANDI`). Bir sonraki aşamayı
     * açan işlem budur. Yetki `GuvenlikYapilandirmasi`'nda yalnızca yöneticiye bağlı.
     */
    @PostMapping("/{id}/onayla")
    fun onayla(@PathVariable id: Long): BelgeYaniti = servis.onayla(id)

    /**
     * Yöneticinin serbest durum müdahalesi — onay/iade dışında kalan durumlar için
     * (yanlış onaylanmış bir hakedişi geri açmak gibi). Gerekçe zorunlu, müdahale kayda geçer.
     */
    @PostMapping("/{id}/durum")
    fun durum(@PathVariable id: Long, @RequestBody istek: BelgeDurumIstegi): BelgeYaniti =
        servis.durumDegistir(id, istek.durum, istek.gerekce)

    /** Durum müdahalesi geçmişi — kim, ne zaman, hangi gerekçeyle değiştirdi. */
    @GetMapping("/{id}/durum-gecmisi")
    fun durumGecmisi(@PathVariable id: Long): List<DurumKaydiYaniti> = servis.durumGecmisi(id)

    /**
     * Yanlış açılmış **açık** belgeyi iptal eder; aynı iş o türde yeni bir belge açabilir.
     * Yetki `GuvenlikYapilandirmasi`'nda yalnızca yöneticiye bağlı.
     */
    @PostMapping("/{id}/iptal")
    fun iptal(@PathVariable id: Long, @RequestBody istek: IptalIstegi): BelgeYaniti = servis.iptalEt(id, istek.gerekce)
}

/** Yöneticinin serbest belge durumu müdahalesi; `IsDenetleyici.DurumIstegi`'nin belge ikizi. */
data class BelgeDurumIstegi(val durum: DocumentStatus?, val gerekce: String?)

private const val XLSX_TIPI = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
