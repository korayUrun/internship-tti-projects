package tr.com.tti.hakedis.tools.kable

import java.sql.Connection

data class IceAktarimSonucu(
    val toplam: Int,
    val eklenen: Int,
    val guncellenen: Int,
    val pasiflesen: Int,
    val bolgeDagilimi: Map<String, Int>,
)

/**
 * Kable satırlarını `hakedis.kable` tablosuna yazar — yürürlükteki sürümün **izdüşümü**.
 *
 * İçe aktarım **idempotent**tir: aynı dosya iki kez çalıştırıldığında satır sayısı değişmez.
 * Envanterden düşen kayıtlar **silinmez**, `aktif = false` yapılır — silinseydi o kableye bağlı
 * eski işlerin yabancı anahtarı kırılırdı.
 *
 * ⚠️ **Transaction'ı çağıran yönetir** (Faz U). Eskiden `yaz` kendi `commit`'ini atıyordu; izdüşüm
 * ile `kable_surum.yururlukte` bayrağının aynı transaction'da olması gerektiği için bu sorumluluk
 * dışarı taşındı. Bayrak ayrı bir transaction'da yazılsaydı, arada düşen bir bağlantı `kable`'ı
 * hiçbir sürümün izdüşümü olmadığı bir hâlde bırakabilirdi. Çağıranlar: [KableSurumYazici.yururlugeAl]
 * ve sunucudaki `KableSurumServisi`.
 */
class KableYazici(private val baglanti: Connection) {
    fun yaz(satirlar: List<KableSatiri>): IceAktarimSonucu {
        val sahaBolge = sahaBolgeEslesmesi()
        val cozulmus = satirlar.map { satir ->
            val bolgeId = sahaBolge[satir.sahaAdi] ?: throw KableParseException(
                "'${satir.sahaAdi}' saha adının bölge karşılığı yok (${satir.nosid}). " +
                    "Tanımlı olanlar: ${sahaBolge.keys.sorted()}. " +
                    "Yeni bir saha adı geldiyse bolge.xlsx_saha_adi güncellenmeli.",
            )
            satir to bolgeId
        }

        val oncekiSayi = kableSayisi()
        upsert(cozulmus)
        val pasiflesen = eksikleriPasiflestir(satirlar.map { it.nosid })
        val sonrakiSayi = kableSayisi()

        val eklenen = sonrakiSayi - oncekiSayi
        return IceAktarimSonucu(
            toplam = satirlar.size,
            eklenen = eklenen,
            guncellenen = satirlar.size - eklenen,
            pasiflesen = pasiflesen,
            bolgeDagilimi = bolgeDagilimi(),
        )
    }

    private fun sahaBolgeEslesmesi(): Map<String, Int> = baglanti.prepareStatement("select xlsx_saha_adi, id from bolge").use { ifade ->
        ifade.executeQuery().use { sonuc ->
            buildMap {
                while (sonuc.next()) put(sonuc.getString(1), sonuc.getInt(2))
            }
        }
    }

    private fun kableSayisi(): Int = baglanti.prepareStatement("select count(*) from kable").use { ifade ->
        ifade.executeQuery().use { sonuc ->
            sonuc.next()
            sonuc.getInt(1)
        }
    }

    private fun upsert(satirlar: List<Pair<KableSatiri, Int>>) {
        val sql = """
            insert into kable (nosid, bolge_id, danisman_ad, adres_a, adres_b, sehir_a, sehir_b,
                               aktif, kaynak, guncelleme)
            values (?, ?, ?, ?, ?, ?, ?, true, 'XLSX', now())
            on conflict (nosid) do update set
                bolge_id    = excluded.bolge_id,
                danisman_ad = excluded.danisman_ad,
                adres_a     = excluded.adres_a,
                adres_b     = excluded.adres_b,
                sehir_a     = excluded.sehir_a,
                sehir_b     = excluded.sehir_b,
                aktif       = true,
                guncelleme  = now()
        """.trimIndent()

        baglanti.prepareStatement(sql).use { ifade ->
            for ((satir, bolgeId) in satirlar) {
                ifade.setString(1, satir.nosid)
                ifade.setInt(2, bolgeId)
                ifade.setString(3, satir.danismanAd)
                ifade.setString(4, satir.adresA)
                ifade.setString(5, satir.adresB)
                ifade.setString(6, satir.sehirA)
                ifade.setString(7, satir.sehirB)
                ifade.addBatch()
            }
            ifade.executeBatch()
        }
    }

    private fun eksikleriPasiflestir(gelenNosidler: List<String>): Int {
        val dizi = baglanti.createArrayOf("text", gelenNosidler.toTypedArray())
        return baglanti
            .prepareStatement("update kable set aktif = false, guncelleme = now() where aktif and nosid <> all (?)")
            .use { ifade ->
                ifade.setArray(1, dizi)
                ifade.executeUpdate()
            }
    }

    private fun bolgeDagilimi(): Map<String, Int> = baglanti.prepareStatement(
        "select b.kod, count(*) from kable k join bolge b on b.id = k.bolge_id " +
            "where k.aktif group by b.kod order by 2 desc",
    ).use { ifade ->
        ifade.executeQuery().use { sonuc ->
            buildMap {
                while (sonuc.next()) put(sonuc.getString(1), sonuc.getInt(2))
            }
        }
    }
}
