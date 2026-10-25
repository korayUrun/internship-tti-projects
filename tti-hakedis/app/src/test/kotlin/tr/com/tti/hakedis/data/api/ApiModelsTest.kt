package tr.com.tti.hakedis.data.api

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import java.math.BigDecimal

/**
 * Mobil ↔ sunucu sözleşmesinin tek otomatik güvencesi.
 *
 * Buradaki JSON metinleri **çalışan sunucudan** alınmıştır (`GET /api/...`). Sunucuda bir alan adı
 * değişirse ya da kaldırılırsa test kırılır; aksi hâlde hata ancak tablette, ekran açılmayı
 * denediğinde ortaya çıkardı — üstelik `ignoreUnknownKeys` yüzünden bazı durumlarda sessizce.
 *
 * Sunucu tarafındaki eşi `BelgeYanitlariTest`; o, alanların JSON'a **yazıldığını** kilitliyor,
 * bu ise **okunduğunu**.
 */
class ApiModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `kullanici yaniti cozulur`() {
        val user = json.decodeFromString<UserDto>(
            """{"kullaniciAdi":"izmir","adSoyad":"İzmir Bölge Sorumlusu","rol":"BOLGE_SORUMLUSU",
               "bolgeKodu":"IZMIR","bolgeAdi":"İzmir"}""",
        )

        assertThat(user.adSoyad).isEqualTo("İzmir Bölge Sorumlusu")
        assertThat(user.rol).isEqualTo(UserRole.BOLGE_SORUMLUSU)
        assertThat(user.bolgeKodu).isEqualTo("IZMIR")
        // Ekranda kod değil ad yazılıyor; alan gelmezse koda düşülüyor, o yüzden burada kilitli.
        assertThat(user.bolgeAdi).isEqualTo("İzmir")
    }

    /** Eski bir sunucu `bolgeAdi` göndermezse alan `null` kalmalı, çözümleme patlamamalı. */
    @Test
    fun `bolge adi olmayan kullanici yaniti cozulur`() {
        val user = json.decodeFromString<UserDto>(
            """{"kullaniciAdi":"izmir","adSoyad":"İzmir Bölge Sorumlusu","rol":"BOLGE_SORUMLUSU","bolgeKodu":"IZMIR"}""",
        )

        assertThat(user.bolgeAdi).isNull()
        assertThat(user.bolgeKodu).isEqualTo("IZMIR")
    }

    /** Yöneticide bölge `null` gelir — "bölgesiz" değil, "tüm bölgeler" demektir. */
    @Test
    fun `yonetici bolgesiz cozulur`() {
        val user = json.decodeFromString<UserDto>(
            """{"kullaniciAdi":"yonetici","adSoyad":"Yönetici","rol":"YONETICI","bolgeKodu":null}""",
        )

        assertThat(user.rol).isEqualTo(UserRole.YONETICI)
        assertThat(user.bolgeKodu).isNull()
    }

    @Test
    fun `is yaniti belgeleriyle birlikte cozulur`() {
        val workOrder = json.decodeFromString<WorkOrderDto>(IS_JSON)

        assertThat(workOrder.id).isEqualTo(306)
        assertThat(workOrder.spNo).isEqualTo("2026-ANKARA-001")
        // Kapsam kartta kimlik alanlarıyla birlikte yazılıyor; eskiden yutuluyordu.
        assertThat(workOrder.kapsamKodu).isEqualTo("DEPLASE_IYILESTIRME")
        assertThat(workOrder.durum).isEqualTo(WorkOrderStatusDto.ISLENIYOR)
        assertThat(workOrder.durumMetni).isEqualTo("İşleniyor")
        assertThat(workOrder.belgeler).hasSize(1)
        assertThat(workOrder.belgeler.first().tur).isEqualTo(DocumentType.KESIF)
        assertThat(workOrder.belgeler.first().durumMetni).isEqualTo("1. Keşif Tamamlandı")
        // `uyarilar` yalnızca POST yanıtında dolu gelir; GET'te alan hiç yok ve boş dizi olmalı.
        assertThat(workOrder.uyarilar).isEmpty()
    }

    /**
     * Kablosuz "Yeni Tesis" projesi (Faz W): sunucu `kableNosid` ve `adresA` alanlarını `null`
     * gönderir. Alanlar varsayılansız `String` kalsaydı `kotlinx.serialization` istisna atar ve
     * **proje listesi hiç açılmazdı** — hata tek bir projede değil, tüm listede çıkardı.
     */
    @Test
    fun `kablosuz proje yaniti cozulur`() {
        val kablosuz = json.decodeFromString<WorkOrderDto>(
            IS_JSON
                .replace("\"kableNosid\":\"mtc07977c.cable*ank-ank\"", "\"kableNosid\":null")
                .replace("\"adresA\":\"Pursaklar\"", "\"adresA\":null")
                .replace("\"adresB\":\"T01_Esenboga\"", "\"adresB\":null"),
        )

        assertThat(kablosuz.kableNosid).isNull()
        assertThat(kablosuz.adresA).isNull()
        assertThat(kablosuz.spNo).isEqualTo("2026-ANKARA-001")
    }

    /** Alan gövdede **hiç yoksa** da çözülmeli: eski sunucu ile yeni istemcinin karşılaşması. */
    @Test
    fun `kable alanlari olmayan yanit cozulur`() {
        val eksik = json.decodeFromString<WorkOrderDto>(
            IS_JSON.replace("\"kableNosid\":\"mtc07977c.cable*ank-ank\",\"adresA\":\"Pursaklar\",\"adresB\":\"T01_Esenboga\",", ""),
        )

        assertThat(eksik.kableNosid).isNull()
        assertThat(eksik.adresA).isNull()
    }

    /**
     * `kableZorunlu` gelmezse **zorunlu** sayılır: eksik bilgiyle zorunluluğu gevşetmek, sunucunun
     * reddedeceği bir formu açık göstermek olurdu.
     */
    @Test
    fun `kapsam kable zorunlulugu cozulur`() {
        val kapsamlar = json.decodeFromString<List<ScopeDto>>(
            """[{"kod":"YENI_TESIS","ad":"Yeni Tesis","kableZorunlu":false},{"kod":"ZORUNLU_DEPLASE","ad":"Zorunlu Deplase"}]""",
        )

        assertThat(kapsamlar.first().kableZorunlu).isFalse()
        assertThat(kapsamlar.last().kableZorunlu).isTrue()
    }

    /**
     * Sunucunun ürettiği **tek** uyarı kodu `AYNI_KABLE_VAR`'dır (`IsServisi`). Kod burada harfiyen
     * kilitleniyor: `uyarilar` bilinmeyen bir kodla gelseydi `ignoreUnknownKeys` onu sessizce
     * geçirir ve uyarı ekranda hiç görünmezdi.
     */
    @Test
    fun `is olusturma yanitindaki uyarilar cozulur`() {
        val workOrder = json.decodeFromString<WorkOrderDto>(
            IS_JSON.dropLast(1) +
                ""","uyarilar":[{"kod":"AYNI_KABLE_VAR","adet":2,"mesaj":"Bu kablo üzerinde 2 proje daha var."}]}""",
        )

        assertThat(workOrder.uyarilar).hasSize(1)
        assertThat(workOrder.uyarilar.first().kod).isEqualTo("AYNI_KABLE_VAR")
        assertThat(workOrder.uyarilar.first().mesaj).isEqualTo("Bu kablo üzerinde 2 proje daha var.")
    }

    /**
     * `isId` alanı sunucuda `@get:JsonProperty("isId")` ile açıkça adlandırılmıştır: Kotlin `is` ile
     * başlayan özelliğin getter'ını `isId()` üretiyor ve Jackson öneki kırpıp alanı JSON'a hiç
     * yazmayabiliyor (Faz H'de web bu yüzden `/api/isler/undefined` çağırmıştı). Mobil de aynı alana
     * bağlı olduğu için burada kilitleniyor.
     */
    @Test
    fun `belge yanitindaki isId cozulur`() {
        val document = json.decodeFromString<DocumentDto>(BELGE_JSON)

        assertThat(document.isId).isEqualTo(306)
        assertThat(document.belgeNo).isEqualTo("ANKARA_2026_KESIF_001")
        assertThat(document.durum).isEqualTo(DocumentStatus.TAMAMLANDI)
        assertThat(document.duzenlenebilir).isFalse()
        assertThat(document.toplamGosterim).isEqualTo("5.000,00 ₺")
        assertThat(document.satirlar).hasSize(1)
        assertThat(document.satirlar.first().pozId).isEqualTo("r5")
    }

    /**
     * Düzenlenebilirlik artık **sunucudan** geliyor; mobil `durum`'a bakıp kendi kuralını
     * türetmiyor (Faz V). Test bunu kilitliyor: alan gövdede varsa okunmalı, yoksa kapalı sayılmalı.
     */
    @Test
    fun `duzenlenebilirlik sunucudan okunur`() {
        val document = json.decodeFromString<DocumentDto>(
            BELGE_JSON.replace(""""duzenlenebilir":false""", """"duzenlenebilir":true"""),
        )

        assertThat(document.duzenlenebilir).isTrue()

        // Alan hiç gelmezse varsayılan kapalı: eksik bilgiyle "düzenlenebilir" demek, kullanıcıya
        // yazamayacağı bir ekran açmak olurdu.
        val eksik = json.decodeFromString<DocumentDto>(
            BELGE_JSON.replace(""""duzenlenebilir":false,""", ""),
        )
        assertThat(eksik.duzenlenebilir).isFalse()
    }

    /**
     * Birim fiyat **string** gelir ve 17 haneye kadar çıkar. Sayı olarak çözülseydi `double`'a düşer
     * ve kuruş kayardı; bu test hassasiyetin tam korunduğunu kilitliyor (CLAUDE.md §5).
     */
    @Test
    fun `poz yanitinda ham birim fiyat tam hassasiyetle cozulur`() {
        val poz = json.decodeFromString<PozDto>(
            """
            {"id":"r5","pozNo":"1.1","sortKey":[1,1],"name":"Direk Dikme-montaj","unit":"Ad.",
             "unitPrice":"648.35852723050107","unitPriceGosterim":"648,35852723050107 ₺",
             "isGroup":false,"isPriced":true}
            """.trimIndent(),
        )

        assertThat(poz.unitPrice).isEqualTo("648.35852723050107")
        assertThat(BigDecimal(poz.unitPrice)).isEqualTo(BigDecimal("648.35852723050107"))
        assertThat(poz.isGroup).isFalse()
        assertThat(poz.isPriced).isTrue()
    }

    /** Grup satırı fiyatsızdır: `isGroup`/`isPriced` adları Jackson'ın önek kırpmasına karşı sabit. */
    @Test
    fun `grup satiri cozulur`() {
        val poz = json.decodeFromString<PozDto>(
            """
            {"id":"r4","pozNo":"1","sortKey":[1],"name":"HAVAİ GÜZERGAH İMALATLARI","unit":"",
             "unitPrice":"0","unitPriceGosterim":"0,00 ₺","isGroup":true,"isPriced":false}
            """.trimIndent(),
        )

        assertThat(poz.isGroup).isTrue()
        assertThat(poz.isPriced).isFalse()
        assertThat(poz.name).isEqualTo("HAVAİ GÜZERGAH İMALATLARI")
    }

    @Test
    fun `ek yanitlari cozulur`() {
        val attachments = json.decodeFromString<List<AttachmentDto>>(
            """
            [{"id":87,"belgeId":558,"tur":"EK1_ROTA","dosyaAdi":"guzergah.kml","boyutBayt":1436,
              "yukleme":"2026-07-31T07:36:02.492858Z","yukleyenAdSoyad":"Ankara Bölge Sorumlusu"},
             {"id":88,"belgeId":558,"tur":"EK2_GORSEL","dosyaAdi":"saha.jpg","boyutBayt":1028,
              "yukleme":"2026-07-31T07:36:02.516750Z","yukleyenAdSoyad":"Ankara Bölge Sorumlusu"}]
            """.trimIndent(),
        )

        assertThat(attachments.map { it.tur })
            .containsExactly(AttachmentType.EK1_ROTA, AttachmentType.EK2_GORSEL).inOrder()
    }

    @Test
    fun `sabit listeler cozulur`() {
        val regions = json.decodeFromString<List<RegionDto>>(
            """[{"kod":"IST_AVR","ad":"İstanbul Avrupa","dosyaKodu":"IST-AVR"}]""",
        )
        val cables = json.decodeFromString<List<CableDto>>(
            """
            [{"nosid":"mtc06591b.cable*drn-kkl","bolgeKodu":"EDIRNE","adresA":"Arpac",
              "adresB":"T01_Yenice","sehirA":"Edirne","sehirB":"Kirklareli"}]
            """.trimIndent(),
        )

        assertThat(regions.first().ad).isEqualTo("İstanbul Avrupa")
        assertThat(cables.first().nosid).isEqualTo("mtc06591b.cable*drn-kkl")
    }

    /** Hata gövdesi her uçta aynı biçimde gelir; mesaj kullanıcıya olduğu gibi gösterilir. */
    @Test
    fun `hata govdesi cozulur`() {
        val error = json.decodeFromString<ErrorDto>(
            """{"hata":"KURAL_IHLALI","mesaj":"Bu bölgede keşif sırası SP-1 işinde (#12)."}""",
        )

        assertThat(error.hata).isEqualTo("KURAL_IHLALI")
        assertThat(error.mesaj).startsWith("Bu bölgede keşif sırası")
    }

    /** Miktar sunucuya **string** gider; sayıya çevrilse `double`'a düşerdi. */
    @Test
    fun `satir istegi miktari string olarak yazar`() {
        val body = Json.encodeToString(RowsRequest(listOf(RowEntry("r5", "12.5"))))

        assertThat(body).isEqualTo("""{"satirlar":[{"pozId":"r5","miktar":"12.5"}]}""")
    }

    /** EK3 (Faz Y): üç alan da opsiyonel, [MaterialDto.miktar] **metin** — sayı olsaydı §5 tuzağı. */
    @Test
    fun `malzeme yanitlari cozulur`() {
        val malzemeler = json.decodeFromString<List<MaterialDto>>(
            """[{"ad":"Ek Kutusu","miktar":"12.5","seriNo":"SN-001"},
                {"ad":null,"miktar":null,"seriNo":"SN-002"}]""",
        )

        assertThat(malzemeler).hasSize(2)
        assertThat(malzemeler.first().ad).isEqualTo("Ek Kutusu")
        assertThat(malzemeler.first().miktar).isEqualTo("12.5")
        // Kısmen dolu satır hayatta kalmalı: saha bazen yalnızca seri no biliyor.
        assertThat(malzemeler.last().ad).isNull()
        assertThat(malzemeler.last().seriNo).isEqualTo("SN-002")
    }

    /** Hakediş dışı belgede ve hiç malzeme girilmemiş hakedişte sunucu boş liste döner. */
    @Test
    fun `malzemesiz belge bos liste doner`() {
        assertThat(json.decodeFromString<List<MaterialDto>>("[]")).isEmpty()
    }

    /** Alanlar `null` olarak değil, **hiç** gelmezse varsayılanlar devreye girmeli. */
    @Test
    fun `eksik alanli malzeme yaniti cozulur`() {
        val malzeme = json.decodeFromString<List<MaterialDto>>("""[{"seriNo":"SN-002"}]""").single()

        assertThat(malzeme.ad).isNull()
        assertThat(malzeme.miktar).isNull()
        assertThat(malzeme.seriNo).isEqualTo("SN-002")
    }

    /**
     * İstek tarafının kilidi. Üç şeyi birden pinliyor: sarmalayıcı anahtar `malzemeler`, miktarın
     * **string** olduğu ve `"seriNo":null`'ın telde **bulunduğu**.
     *
     * Sonuncusu asıl mesele: [MaterialEntry]'ye varsayılan eklenirse `encodeDefaults = false`
     * yüzünden anahtar sessizce düşerdi ve bu test onu yakalar.
     */
    @Test
    fun `malzeme istegi miktari string olarak yazar`() {
        val body = Json.encodeToString(MaterialsRequest(listOf(MaterialEntry("Ek Kutusu", "12,5", null))))

        assertThat(body).isEqualTo("""{"malzemeler":[{"ad":"Ek Kutusu","miktar":"12,5","seriNo":null}]}""")
    }

    private companion object {
        val IS_JSON = """
            {"id":306,"bolgeKodu":"ANKARA","bolgeAdi":"Ankara","kapsamKodu":"DEPLASE_IYILESTIRME",
             "kableNosid":"mtc07977c.cable*ank-ank","adresA":"Pursaklar","adresB":"T01_Esenboga",
             "spNo":"2026-ANKARA-001","route":"R","ekAraligi":"EK1-EK2","yil":2026,
             "olusturma":"2026-07-31T07:36:02.057544Z","durum":"ISLENIYOR","durumMetni":"İşleniyor",
             "iptalGerekce":null,
             "belgeler":[{"id":557,"belgeNo":"ANKARA_2026_KESIF_001","tur":"KESIF","durum":"TAMAMLANDI",
                          "durumMetni":"1. Keşif Tamamlandı","olusturma":"2026-07-31T07:36:02.348309Z",
                          "tamamlanma":"2026-07-31T07:36:02.399786Z",
                          "sonGuncelleme":"2026-07-31T07:36:02.399786Z",
                          "sonGuncelleyenAdSoyad":"Ankara Bölge Sorumlusu","sonGuncelleyenRol":"BOLGE_SORUMLUSU",
                          "toplam":"5000.00","toplamGosterim":"5.000,00 ₺"}]}
        """.trimIndent()

        val BELGE_JSON = """
            {"id":557,"isId":306,"belgeNo":"ANKARA_2026_KESIF_001","tur":"KESIF","durum":"TAMAMLANDI",
             "durumMetni":"1. Keşif Tamamlandı","bolgeKodu":"ANKARA","yil":2026,"siraNo":1,
             "sozlesmeKodu":"2026-sample-rota-1","sozlesmeAdi":"2026 Sample Rota-1",
             "fiyatVersiyonuId":142,"olusturma":"2026-07-31T07:36:02.348309Z",
             "gonderme":"2026-07-31T07:36:02.399786Z","tamamlanma":"2026-07-31T07:36:02.399786Z",
             "sonGuncelleme":"2026-07-31T07:36:02.399786Z","sonGuncelleyenAdSoyad":"Ankara Bölge Sorumlusu",
             "sonGuncelleyenRol":"BOLGE_SORUMLUSU","iptalGerekce":null,
             "duzenlenebilir":false,"gonderilebilir":false,
             "satirlar":[{"pozId":"r5","pozNo":"1.1","ad":"Direk Dikme-montaj","birim":"Ad.","miktar":"3",
                          "miktarGosterim":"3","birimFiyat":"500","birimFiyatGosterim":"500,00 ₺",
                          "tutar":"1500.00","tutarGosterim":"1.500,00 ₺"}],
             "toplam":"5000.00","toplamGosterim":"5.000,00 ₺"}
        """.trimIndent()
    }
}
