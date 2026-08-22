package tr.com.tti.hakedis.server.belge

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tr.com.tti.hakedis.server.sozlesme.PozSablonu
import tr.com.tti.hakedis.server.sozlesme.SozlesmeSorgulari
import tr.com.tti.hakedis.server.web.BulunamadiException
import tr.com.tti.hakedis.server.web.GecersizIstekException

/**
 * Miktarların Excel ile girilmesi: **şablon indir → doldur → yükle** (kullanıcı kararı,
 * 3 Ağustos 2026). Satır satır elle giriş alternatif olarak kalıyor.
 *
 * Serbest biçimli dosya kabul edilmiyor: şablon `POZ_KIMLIK` sütunuyla iniyor ve eşleştirme o sütun
 * üzerinden yapılıyor. Gerekçe fiyat listesindekiyle aynı ve daha az önemli değil — yanlış satıra
 * yazılan bir miktar da doğrudan para hatasıdır (bkz. `MiktarEslestirici`).
 */
@Service
class MiktarServisi(
    private val belgeler: BelgeServisi,
    private val belgeSorgulari: BelgeSorgulari,
    private val sozlesmeler: SozlesmeSorgulari,
) {
    /**
     * Miktar şablonu — **mevcut miktarlar dolu** iner.
     *
     * Boş inseydi, ekranda birkaç satır girmiş bir kullanıcı şablonu yükleyerek girdiklerini silerdi
     * (yükleme tam değiştirmedir). Dolu inen şablonda kullanıcı yalnızca değişecekleri düzeltir.
     */
    fun sablon(belgeId: Long): SablonCiktisi {
        val belge = belgeler.ozet(belgeId)
        val sozlesme = sozlesmeler.bilgi(belge.sozlesmeId)
            ?: throw BulunamadiException("Sözleşme bulunamadı: ${belge.sozlesmeId}")

        val pozlar = sozlesmeler.eslestirmePozlari(belge.sozlesmeId)
        val miktarlar = belgeSorgulari.pozKimligineGoreMiktarlar(belgeId)

        return SablonCiktisi(
            dosyaAdi = "${belge.belgeNo}_MIKTAR_SABLONU.xlsx",
            icerik = PozSablonu.uret(sozlesme.baslik, sozlesme.ad, pozlar, miktarlar),
        )
    }

    /**
     * Doldurulmuş şablonu yükler — **tam değiştirme**: dosyada boş bırakılan kalem belgeden silinir.
     * Elle giriş ucu (`PUT /satirlar`) ile aynı semantik; iki yol aynı sonucu vermeli.
     *
     * Dosyada tek bir sorun varsa **hiçbir şey yazılmaz** ve 400 döner. Kısmi yükleme yok: yarısı
     * yazılmış bir belgede hangi satırın dosyadan hangisinin ekrandan geldiği anlaşılamaz.
     */
    @Transactional
    fun yukle(belgeId: Long, icerik: ByteArray, sayfaAdi: String?): BelgeYaniti {
        if (icerik.isEmpty()) throw GecersizIstekException("Miktar dosyası boş.")

        val belge = belgeler.ozet(belgeId)
        val referans = sozlesmeler.eslestirmePozlari(belge.sozlesmeId)
        val okunan = MiktarOkuyucu.oku(icerik, sayfaAdi)

        val sonuc = MiktarEslestirici.eslestir(referans, okunan.satirlar)
        if (!sonuc.gecerliMi) {
            throw GecersizIstekException(
                "Dosya yüklenmedi, hiçbir miktar değişmedi. Sorunlu satırlar:\n" +
                    sonuc.sorunlar.joinToString("\n") { "• ${it.pozNo}: ${it.aciklama}" },
            )
        }

        return belgeler.satirlariYaz(belgeId, sonuc.eslesenler.map { SatirGirdisi(it.satirKodu, it.miktar) })
    }
}

/** İndirilecek dosya: adı sunucuda belirlenir, istemci `Content-Disposition`'dan okur (CLAUDE.md §9). */
data class SablonCiktisi(val dosyaAdi: String, val icerik: ByteArray)
