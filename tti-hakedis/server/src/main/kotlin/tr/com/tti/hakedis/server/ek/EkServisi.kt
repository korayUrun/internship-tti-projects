package tr.com.tti.hakedis.server.ek

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import tr.com.tti.hakedis.domain.workorder.FileExtension
import tr.com.tti.hakedis.server.belge.BelgeServisi
import tr.com.tti.hakedis.server.web.BulunamadiException
import tr.com.tti.hakedis.server.web.GecersizIstekException
import tr.com.tti.hakedis.server.web.KuralIhlaliException
import tr.com.tti.hakedis.server.yetki.OturumKullanicisi

/** İndirme için dosyanın kendisi ve kullanıcıya gösterilecek adı. */
data class EkIcerigi(val dosyaAdi: String, val icerik: ByteArray, val uzanti: String)

/**
 * EK1 (güzergâh) ve EK2 (saha görselleri) yükleme, listeleme, indirme, silme.
 *
 * İki kural her uçta geçerlidir:
 * 1. **Yalnızca taslak belge** ek kabul eder; tür ayrımı yok — güzergâh dosyası üç aşamada da
 *    zorunlu (Faz P). Karar `BelgeServisi.dosyaKabulEdenBelge`'de, tek yerde.
 * 2. **Bölge süzgeci SQL'de** — belge ve ek sorguları `belge.bolge_id` üzerinden süzülür; başka
 *    bölgenin eki 404 döner, varlığı da sızmaz.
 *
 * Veritabanı satırı ile dosya **aynı transaction**'da oluşur: dosya yazımı düşerse satır da geri
 * alınır, yani "kaydı var ama dosyası yok" durumu oluşmaz. Ters yön (dosya yazıldı, commit düştü)
 * artık bir dosya bırakır; zararsızdır, çünkü ona hiçbir kayıt işaret etmez.
 */
@Service
class EkServisi(private val ekler: EkSorgulari, private val belgeler: BelgeServisi, private val depo: YuklemeDizini) {
    @Transactional
    fun yukle(belgeId: Long, tur: EkTuru, dosya: MultipartFile): EkYaniti {
        val belge = belgeler.dosyaKabulEdenBelge(belgeId)

        val dosyaAdi = dosya.originalFilename?.trim().orEmpty()
        if (dosyaAdi.isEmpty()) throw GecersizIstekException("Dosya adı okunamadı; dosyayı yeniden seçip deneyin.")
        if (dosya.isEmpty) throw GecersizIstekException("Boş dosya yüklenemez: $dosyaAdi")

        // Doğrulama uzantıdan: .kmz/.kml için MIME tipleri cihazdan cihaza değişiyor (CLAUDE.md §12/3).
        if (!tur.kind.accepts(dosyaAdi)) {
            throw GecersizIstekException(
                "${tur.etiket} için izin verilen dosya türleri: ${tur.izinliUzantilar.sorted().joinToString(", ") { ".$it" }}. " +
                    "Gönderilen dosya: $dosyaAdi",
            )
        }

        // EK1 tekildir. Kısıt veritabanında da var (`ek_ek1_ux`); buradaki kontrol anlaşılır mesaj
        // üretmek için — yarışta ikinciyi veritabanı durdurur ve DuplicateKeyException 409'a çevrilir.
        if (tur == EkTuru.EK1_ROTA && ekler.varMi(belgeId, EkTuru.EK1_ROTA)) {
            throw KuralIhlaliException("Bu belgede zaten bir EK1 güzergâh dosyası var; değiştirmek için önce mevcut dosyayı silin.")
        }

        val icerik = dosya.bytes
        val ekId = ekler.olustur(belgeId, tur, dosyaAdi, icerik.size.toLong(), OturumKullanicisi.zorunlu().id)
        val yol = EkYolu.ek(belge.bolgeKodu, belge.yil, belge.belgeNo, tur, ekId, dosyaAdi)
        ekler.yoluYaz(ekId, yol)
        depo.yaz(yol, icerik)

        // Yanıt yazılan satırdan okunur, elde kurulmaz: istemci gerçekte kaydedileni görür.
        return ekler.bul(OturumKullanicisi.bolgeSuzgeci(), ekId)?.yanit()
            ?: throw IllegalStateException("Yazılan ek okunamadı: $ekId")
    }

    /** Belgenin ekleri. `ozet` çağrısı bölge yetkisini doğrular: başka bölgenin belgesi 404 verir. */
    fun listele(belgeId: Long): List<EkYaniti> {
        belgeler.ozet(belgeId)
        return ekler.listele(belgeId).map { it.yanit() }
    }

    fun indir(ekId: Long): EkIcerigi {
        val ek = ekler.bul(OturumKullanicisi.bolgeSuzgeci(), ekId)
            ?: throw BulunamadiException("Ek bulunamadı: $ekId")

        if (!depo.varMi(ek.depoYolu)) {
            // Sessizce boş dosya döndürmek en kötüsü olurdu: kullanıcı indirdiğini sanır.
            throw BulunamadiException("Ek dosyası depoda bulunamadı: ${ek.dosyaAdi}")
        }

        return EkIcerigi(dosyaAdi = ek.dosyaAdi, icerik = depo.oku(ek.depoYolu), uzanti = FileExtension.of(ek.dosyaAdi))
    }

    /**
     * Eki siler. Silme sırası **önce veritabanı, sonra dosya**: ters sırada, satır silinmeden dosya
     * kaybolursa geriye indirilemeyen bir kayıt kalır. Dosya silinemezse artakalan bayt zararsızdır
     * ve `WARN` düşer (bkz. [YuklemeDizini.sil]).
     */
    @Transactional
    fun sil(ekId: Long) {
        val ek = ekler.bul(OturumKullanicisi.bolgeSuzgeci(), ekId)
            ?: throw BulunamadiException("Ek bulunamadı: $ekId")

        belgeler.dosyaKabulEdenBelge(ek.belgeId)
        ekler.sil(ekId)
        depo.sil(ek.depoYolu)
    }
}
