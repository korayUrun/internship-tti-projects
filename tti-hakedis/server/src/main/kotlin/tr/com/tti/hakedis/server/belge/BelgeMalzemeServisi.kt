package tr.com.tti.hakedis.server.belge

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tr.com.tti.hakedis.domain.document.DocumentMaterial
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.domain.pricing.QuantityInput
import tr.com.tti.hakedis.domain.pricing.QuantityParser
import tr.com.tti.hakedis.server.web.GecersizIstekException
import tr.com.tti.hakedis.server.web.KuralIhlaliException

/** Tek malzeme satırı — üç alan da opsiyonel, miktar **metin** olarak gelir. */
data class MalzemeIstegi(val ad: String?, val miktar: String?, val seriNo: String?)

/** Listenin tamamı: yazma **tam değiştirmedir** (miktar satırlarıyla aynı semantik). */
data class MalzemelerIstegi(val malzemeler: List<MalzemeIstegi> = emptyList())

data class MalzemeYaniti(val ad: String?, val miktar: String?, val seriNo: String?)

/**
 * EK3 "TTI Kullanılan Malzemeler" — **yalnızca HAKEDIS aşamasında** (kullanıcı isteği, 18 Ağustos 2026).
 *
 * Tür kuralı burada, veritabanında değil: `belge.tur` bir metin sütunu ve kuralı iki yerde tutmak,
 * biri değişince sessizce ayrışan ikinci bir kural üretirdi (CLAUDE.md §7/1).
 *
 * Düzenlenebilirlik kararı da tekrarlanmıyor — [BelgeServisi.dosyaKabulEdenBelge] çağrılıyor; ekler
 * hangi koşulda kabul ediliyorsa malzeme de aynı koşulda kabul edilir (aşama `ONAY_BEKLIYOR`).
 *
 * ⚠️ Malzeme **gönderim ön koşulu değildir**: hiçbiri zorunlu olmadığı için belge malzemesiz de
 * gönderilebilir. `BelgeServisi`'ndeki gönderme denetimine bilerek dokunulmadı.
 */
@Service
class BelgeMalzemeServisi(private val belgeler: BelgeServisi, private val malzemeler: BelgeMalzemeSorgulari) {
    fun liste(belgeId: Long): List<MalzemeYaniti> {
        // Okuma yetkisi belgenin kendisinde denetlenir; başka bölgenin belgesi 404 döner.
        belgeler.ozet(belgeId)
        return malzemeler.liste(belgeId).map { it.yanit() }
    }

    /**
     * Listeyi tamamen değiştirir.
     *
     * Tamamen boş satırlar **elenir**: arayüzde "yeni malzeme ekle" ile açılan ama doldurulmayan
     * satır kaydedilirse çıktıda boş satır olarak görünürdü.
     */
    @Transactional
    fun yaz(belgeId: Long, istek: MalzemelerIstegi): List<MalzemeYaniti> {
        val belge = belgeler.dosyaKabulEdenBelge(belgeId)
        if (belge.tur != DocumentType.HAKEDIS) {
            throw KuralIhlaliException(
                "TTI Kullanılan Malzemeler yalnızca hakediş aşamasında girilir; " +
                    "${belge.belgeNo} bir ${BelgeMetinleri.turAdi(belge.tur)} belgesi.",
            )
        }

        val satirlar = istek.malzemeler.map { it.coz() }.filterNot { it.bosMu }

        malzemeler.sil(belgeId)
        malzemeler.yaz(belgeId, satirlar)
        return satirlar.map { it.yanit() }
    }

    /**
     * Metin miktarı sayıya çevirir. Ayrıştırma **sunucuda**: web sayıya çevirseydi `,`/`.` ayrımı ve
     * hassasiyet istemcide çözülür, `double`'a düşme riski doğardı (CLAUDE.md §5).
     */
    private fun MalzemeIstegi.coz(): DocumentMaterial {
        val ham = miktar?.trim().orEmpty()
        val sayi = when {
            ham.isEmpty() -> null
            else -> when (val girdi = QuantityParser.parse(ham)) {
                is QuantityInput.Valid -> girdi.value
                else -> throw GecersizIstekException("Malzeme miktarı geçerli bir sayı olmalı (ör. 12,5). Verilen: $ham")
            }
        }

        return DocumentMaterial(
            ad = ad?.trim()?.takeIf { it.isNotEmpty() },
            miktar = sayi,
            seriNo = seriNo?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** Miktar metin olarak döner: web para/sayı biçimlendirmesi yapmaz, geldiği gibi gösterir. */
    private fun DocumentMaterial.yanit() = MalzemeYaniti(
        ad = ad,
        miktar = miktar?.stripTrailingZeros()?.toPlainString(),
        seriNo = seriNo,
    )
}
