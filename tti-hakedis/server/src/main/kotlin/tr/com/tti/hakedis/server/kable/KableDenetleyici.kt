package tr.com.tti.hakedis.server.kable

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tr.com.tti.hakedis.server.web.BulunamadiException

/**
 * Kable envanteri — **bölge kısıtı yok** (Faz O). Her kullanıcı her kabloyu arayıp seçebilir.
 *
 * Liste ucu aramasız boş döner: arayüz `mtc` ön dolu bir kutu gösteriyor ve kullanıcı numarayı
 * yazdıkça sonuç geliyor. Envanterin tamamını indirip istemcide süzmek, 900+ satırı her açılışta
 * ağdan çekmek demekti.
 */
@RestController
@RequestMapping("/api/kableler")
class KableDenetleyici(private val sorgular: KableSorgulari, private val elle: KableElleServisi) {
    @GetMapping
    fun ara(
        @RequestParam(required = false) ara: String?,
        @RequestParam(required = false, defaultValue = "50") limit: Int,
    ): List<KableYaniti> = sorgular.ara(ara, limit.coerceIn(1, 500))

    @GetMapping("/{nosid}")
    fun bul(@PathVariable nosid: String): KableYaniti = sorgular.bul(nosid)
        ?: throw BulunamadiException("Kable bulunamadı: $nosid")

    /**
     * Elle yeni kablo (Faz V) — **yalnızca yönetici** (yetki `GuvenlikYapilandirmasi`'nda).
     *
     * Yeni sürüm doğmaz: değişiklik yürürlükteki envantere anında yazılır. Silme ucu **yoktur**;
     * kullanımdan çıkarmanın yolu envanter yüklemesidir.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun ekle(@RequestBody istek: KableIstegi): KableYaniti = elle.ekle(istek)

    /** Var olan kablonun düzeltilmesi — yalnızca yönetici. */
    @PutMapping("/{nosid}")
    fun guncelle(@PathVariable nosid: String, @RequestBody istek: KableIstegi): KableYaniti = elle.guncelle(nosid, istek)
}
