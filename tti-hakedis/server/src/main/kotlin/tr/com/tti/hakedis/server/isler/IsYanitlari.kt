package tr.com.tti.hakedis.server.isler

import tr.com.tti.hakedis.server.belge.BelgeOzetYaniti
import tr.com.tti.hakedis.server.kable.UcIstegi
import java.time.Instant

/**
 * Bilgilendirme amaçlı uyarı. **Hiçbir koşulda işlemi durdurmaz** (NEWPHASES.md D1/4); arayüzde iş
 * oluşturulduktan sonra bilgi şeridi olarak gösterilir, onay diyaloğu yapılmaz.
 */
data class UyariYaniti(val kod: String, val adet: Int, val mesaj: String)

/**
 * İş kaydı.
 *
 * [adresA] / [adresB] kable envanterinden **dondurularak kopyalanmıştır**; envanter yenilendiğinde
 * bu işin adresi değişmez.
 *
 * [uyarilar] her zaman dizi olarak gelir (boşsa boş dizi), istemcide `null` kontrolü gerekmesin —
 * yalnızca `POST` yanıtında dolu olabilir.
 */
data class IsYaniti(
    val id: Long,
    val bolgeKodu: String,
    val bolgeAdi: String,
    val kapsamKodu: String,
    /**
     * Kablo ve uçlar **boş olabilir** (Faz W): "Yeni Tesis" kapsamında kablo seçimi zorunlu değil.
     * İstemciler boş değeri `—` ile basar; alan gövdeden hiç çıkarılmaz ki eski istemciler
     * "alan yok" ile "değer yok" arasında kalmasın.
     */
    val kableNosid: String?,
    val adresA: String?,
    val adresB: String?,
    val spNo: String,
    val route: String,
    val ekAraligi: String,
    /** Belge numarasının yıl bileşeni — işin **açıldığı** yıl, üç belgesi de bunu taşır (D4). */
    val yil: Int,
    val olusturma: Instant,
    /**
     * `null` = fizibilite bekleniyor. `IPTAL` iş listede **görünmeye devam eder**, gizlenmez:
     * kaybolan kayıt "burada ne oldu" sorusunu cevapsız bırakır (D5).
     */
    val durum: IsDurumu?,
    /** Kullanıcıya gösterilecek durum; metin sunucudan gelir (CLAUDE.md §7). */
    val durumMetni: String,
    val iptalGerekce: String?,
    val belgeler: List<BelgeOzetYaniti> = emptyList(),
    val uyarilar: List<UyariYaniti> = emptyList(),
)

/**
 * "Yeni Proje" ekranının gönderdiği alanlar. Doğrulama `WorkOrderValidator` ile yapılır; hangi alanın
 * zorunlu olduğuna **kapsam** karar verir (`Scope.kableZorunluMu`).
 *
 * ~~`bolgeKodu`~~ ve ~~`spNo`~~ Faz O'da çıktı: bölge oturumdan, SP No sayaçtan gelir. İstemcinin
 * gönderdiğine güvenilmiyor değil — gönderecek bir şeyi kalmadı.
 *
 * [ucA]/[ucB] yalnızca **kablo seçilmediğinde** okunur (Faz W): kablo varsa uçlar envanterden gelir
 * ve istekteki segmentler yok sayılır — adres, kablonun dondurulmuş kopyasıdır (CLAUDE.md §7/5).
 */
data class IsOlusturmaIstegi(
    val kapsamKodu: String? = null,
    val kableNosid: String? = null,
    val route: String? = null,
    val ekAraligi: String? = null,
    val ucA: UcIstegi? = null,
    val ucB: UcIstegi? = null,
)
