package tr.com.tti.hakedis.tools.kable

import java.sql.Connection

/**
 * Yöneticinin arayüzden elle eklediği/düzenlediği tek kabloyu yazar (Faz V).
 *
 * **Neden burada, `:server`de değil:** `kable` tablosuna yazan tek yol bu modüldür (CLAUDE.md §7/5b).
 * Sunucu ikinci bir SQL yolu açsaydı izdüşüm ile sürüm satırları ayrışabilir ve `kable` hiçbir
 * sürümün izdüşümü olmadığı bir hâle düşerdi — hatasız ve fark edilmeden.
 *
 * **Neden yeni sürüm doğmuyor** (kullanıcı kararı): elle düzeltme anında geçerli olmalı. Buna karşılık
 * yazma **iki tabloya birden** gidiyor — `kable` (izdüşüm) ve yürürlükteki `kable_surum_satiri`
 * (sürümün kendisi). Yalnızca izdüşüme yazılsaydı bir sonraki dosya yüklemesinin farkı yanlış
 * hesaplanır ve elle eklenen kablo "düşen" olarak görünmezdi.
 *
 * ⚠️ Bir sonraki **tam** NOS+ yüklemesinde dosyada olmayan elle eklenmiş kablo pasifleşir. Bu doğru
 * davranıştır (dosya otoritedir) ve fark ekranında yürürlüğe almadan önce görünür.
 *
 * Silme yok: `is_kaydi.kable_id` yabancı anahtarı var ve kullanıcı silme istemedi. Kullanımdan
 * çıkarmanın yolu envanter yüklemesidir.
 *
 * Transaction'ı çağıran yönetir ([KableYazici] ile aynı kural).
 */
class KableElleYazici(private val baglanti: Connection) {
    /**
     * @param yeniKayit `true` ise `nosid` kullanımdaysa hata; `false` ise kayıt yoksa hata.
     * @return yazılan kablonun `nosid`'i.
     */
    fun yaz(satir: KableSatiri, yeniKayit: Boolean): String {
        val bolgeId = bolgeId(satir.sahaAdi)
        val varOlan = varMi(satir.nosid)

        if (yeniKayit && varOlan) {
            throw KableParseException("Bu kablo kimliği zaten kayıtlı: ${satir.nosid}")
        }
        if (!yeniKayit && !varOlan) {
            throw KableParseException("Kablo bulunamadı: ${satir.nosid}")
        }

        izdusumeYaz(satir, bolgeId)
        yururluktekiSurumeYaz(satir)
        satirSayisiTazele()
        return satir.nosid
    }

    private fun bolgeId(sahaAdi: String): Int = baglanti
        .prepareStatement("select id from bolge where xlsx_saha_adi = ?")
        .use { ifade ->
            ifade.setString(1, sahaAdi)
            ifade.executeQuery().use { sonuc ->
                if (sonuc.next()) sonuc.getInt(1) else throw KableParseException("'$sahaAdi' saha adının bölge karşılığı yok.")
            }
        }

    private fun varMi(nosid: String): Boolean = baglanti.prepareStatement("select 1 from kable where nosid = ?").use { ifade ->
        ifade.setString(1, nosid)
        ifade.executeQuery().use { it.next() }
    }

    /**
     * `kaynak = 'ELLE'` — dosyadan gelen kayıtlar `'XLSX'` taşıyor. Ayrım kayıtta duruyor ki bir
     * sonraki yüklemede düşen kablonun elle mi eklendiği sorulabilsin.
     */
    private fun izdusumeYaz(satir: KableSatiri, bolgeId: Int) {
        val sql = """
            insert into kable (nosid, bolge_id, danisman_ad, adres_a, adres_b, sehir_a, sehir_b,
                               aktif, kaynak, guncelleme)
            values (?, ?, ?, ?, ?, ?, ?, true, 'ELLE', now())
            on conflict (nosid) do update set
                bolge_id    = excluded.bolge_id,
                danisman_ad = excluded.danisman_ad,
                adres_a     = excluded.adres_a,
                adres_b     = excluded.adres_b,
                sehir_a     = excluded.sehir_a,
                sehir_b     = excluded.sehir_b,
                aktif       = true,
                kaynak      = 'ELLE',
                guncelleme  = now()
        """.trimIndent()

        baglanti.prepareStatement(sql).use { ifade ->
            ifade.setString(1, satir.nosid)
            ifade.setInt(2, bolgeId)
            ifade.setString(3, satir.danismanAd)
            ifade.setString(4, satir.adresA)
            ifade.setString(5, satir.adresB)
            ifade.setString(6, satir.sehirA)
            ifade.setString(7, satir.sehirB)
            ifade.executeUpdate()
        }
    }

    /**
     * Yürürlükteki sürümü izdüşümle aynı hâlde tutar.
     *
     * Yürürlükte sürüm yoksa (boş veritabanı) sessizce atlanır: `where` cümlesi hiçbir satır
     * bulamaz, insert de çalışmaz. Bu doğru — sürüm yoksa senkron tutulacak bir şey de yok.
     */
    private fun yururluktekiSurumeYaz(satir: KableSatiri) {
        val sql = """
            insert into kable_surum_satiri (surum_id, nosid, danisman_ad, saha_adi, adres_a, adres_b, sehir_a, sehir_b)
            select s.id, ?, ?, ?, ?, ?, ?, ?
              from kable_surum s
             where s.yururlukte
            on conflict (surum_id, nosid) do update set
                danisman_ad = excluded.danisman_ad,
                saha_adi    = excluded.saha_adi,
                adres_a     = excluded.adres_a,
                adres_b     = excluded.adres_b,
                sehir_a     = excluded.sehir_a,
                sehir_b     = excluded.sehir_b
        """.trimIndent()

        baglanti.prepareStatement(sql).use { ifade ->
            ifade.setString(1, satir.nosid)
            ifade.setString(2, satir.danismanAd)
            ifade.setString(3, satir.sahaAdi)
            ifade.setString(4, satir.adresA)
            ifade.setString(5, satir.adresB)
            ifade.setString(6, satir.sehirA)
            ifade.setString(7, satir.sehirB)
            ifade.executeUpdate()
        }
    }

    /**
     * `kable_surum.satir_sayisi` yüklemede yazılan bir sayaçtır; elle eklenen kablo onu artırmazsa
     * envanter ekranındaki "N kablo" değeri sürümün gerçek satır sayısından sessizce ayrışır.
     *
     * Sayaç artırılmıyor, **yeniden sayılıyor**: düzenleme (var olan satırın güncellenmesi) sayıyı
     * değiştirmemeli, ekleme değiştirmeli — `count(*)` ikisini de ayrım gerektirmeden doğru verir.
     */
    private fun satirSayisiTazele() {
        val sql = """
            update kable_surum s
               set satir_sayisi = (select count(*) from kable_surum_satiri r where r.surum_id = s.id)
             where s.yururlukte
        """.trimIndent()

        baglanti.prepareStatement(sql).use { it.executeUpdate() }
    }
}
