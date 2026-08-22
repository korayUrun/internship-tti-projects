package tr.com.tti.hakedis.tools.kable

import java.sql.Connection
import java.sql.Statement

/**
 * Kable envanteri sürümleri (Faz U) — **CLI ile sunucunun paylaştığı tek yazma yolu**.
 *
 * Envanteri değiştiren şey yükleme değil, [yururlugeAl]'dır:
 *
 * ```
 * [surumEkle]      dosya okunur, sürüm olarak saklanır — canlı envanter DEĞİŞMEZ
 * [yururlugeAl]    sürümün satırları `kable` tablosuna izdüşürülür  ← burada değişir
 * ```
 *
 * Geri dönüş ayrı bir mekanizma değildir: eski sürüm için [yururlugeAl] çağrılır. Dosyayı yeniden
 * bulmak gerekmez, satırlar veritabanında durur.
 *
 * ⚠️ Sınıf **transaction açmaz**; çağıran sarar (bkz. [KableYazici]). Sunucu `TransactionTemplate`
 * ile, CLI `Main.kt` elle sarıyor.
 */
class KableSurumYazici(private val baglanti: Connection) {
    /**
     * Okunmuş satırları yeni bir sürüm olarak yazar ve sürüm kimliğini döner.
     *
     * Sürüm **yürürlükte değildir**: `kable` tablosuna bu çağrıda dokunulmaz.
     */
    fun surumEkle(dosyaAdi: String, satirlar: List<KableSatiri>, yukleyenId: Long?): Long {
        if (satirlar.isEmpty()) throw KableParseException("Dosyada hiç kable satırı yok.")

        val surumId = baglanti.prepareStatement(
            "insert into kable_surum (dosya_adi, satir_sayisi, yukleyen_id) values (?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS,
        ).use { ifade ->
            ifade.setString(1, dosyaAdi)
            ifade.setInt(2, satirlar.size)
            if (yukleyenId == null) ifade.setNull(3, java.sql.Types.BIGINT) else ifade.setLong(3, yukleyenId)
            ifade.executeUpdate()
            ifade.generatedKeys.use { anahtar ->
                anahtar.next()
                anahtar.getLong(1)
            }
        }

        val sql = """
            insert into kable_surum_satiri
                   (surum_id, nosid, danisman_ad, saha_adi, adres_a, adres_b, sehir_a, sehir_b)
            values (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        baglanti.prepareStatement(sql).use { ifade ->
            for (satir in satirlar) {
                ifade.setLong(1, surumId)
                ifade.setString(2, satir.nosid)
                ifade.setString(3, satir.danismanAd)
                ifade.setString(4, satir.sahaAdi)
                ifade.setString(5, satir.adresA)
                ifade.setString(6, satir.adresB)
                ifade.setString(7, satir.sehirA)
                ifade.setString(8, satir.sehirB)
                ifade.addBatch()
            }
            ifade.executeBatch()
        }

        return surumId
    }

    /** Sürümün saklanan satırları — izdüşümün girdisi. Sıra `nosid`'e göre sabittir. */
    fun surumSatirlari(surumId: Long): List<KableSatiri> = baglanti.prepareStatement(
        """
        select nosid, danisman_ad, saha_adi, adres_a, adres_b, sehir_a, sehir_b
          from kable_surum_satiri
         where surum_id = ?
         order by nosid
        """.trimIndent(),
    ).use { ifade ->
        ifade.setLong(1, surumId)
        ifade.executeQuery().use { sonuc ->
            buildList {
                while (sonuc.next()) {
                    add(
                        KableSatiri(
                            nosid = sonuc.getString("nosid"),
                            danismanAd = sonuc.getString("danisman_ad"),
                            sahaAdi = sonuc.getString("saha_adi"),
                            adresA = sonuc.getString("adres_a"),
                            adresB = sonuc.getString("adres_b"),
                            sehirA = sonuc.getString("sehir_a"),
                            sehirB = sonuc.getString("sehir_b"),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Sürümü yürürlüğe alır: satırları `kable` tablosuna izdüşürür ve bayrağı taşır.
     *
     * İki güncelleme **bu sırayla** olmak zorunda: `kable_surum_yururlukte_ux` kısmi tekil indeksi
     * aynı anda iki yürürlükteki sürüme izin vermez ve kısıt ifade bazında denetlenir.
     */
    fun yururlugeAl(surumId: Long): IceAktarimSonucu {
        val satirlar = surumSatirlari(surumId)
        if (satirlar.isEmpty()) throw KableParseException("Sürüm $surumId bulunamadı ya da hiç satırı yok.")

        val sonuc = KableYazici(baglanti).yaz(satirlar)

        baglanti.prepareStatement("update kable_surum set yururlukte = false where yururlukte and id <> ?")
            .use { ifade ->
                ifade.setLong(1, surumId)
                ifade.executeUpdate()
            }
        baglanti.prepareStatement("update kable_surum set yururlukte = true, yururluge_alinma = now() where id = ?")
            .use { ifade ->
                ifade.setLong(1, surumId)
                ifade.executeUpdate()
            }

        return sonuc
    }
}
