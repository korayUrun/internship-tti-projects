package tr.com.tti.hakedis.domain.workorder

/**
 * Projenin (işin) durumu — **güncel aşamanın durumunun izdüşümü** (yeknesek durum makinesi,
 * Ağustos 2026, müdür isteği). Projeler ve Belgeler ekranları aynı dört değeri kullanır.
 *
 * ```
 * ONAY_BEKLIYOR ──yönetici──▶ ISLENIYOR ──▶ TAMAMLANDI
 *      │
 *      └──yönetici──▶ IPTAL
 * ```
 *
 * Proje durumu artık türetilmiştir: güncel (en ileri, iptal olmayan) aşamanın durumunu yansıtır.
 * `null` (henüz hiç aşama açılmamış) sunucuda "Onay Bekliyor" diye okunur.
 *
 * ~~ACIK/KAPANDI ve "durum yok"~~ — eski iki katmanlı model (proje `ACIK/ISLENIYOR/KAPANDI` +
 * belge `TASLAK/…`) müdürü karıştırıyordu; tek yeknesek döngüye indirildi.
 */
enum class WorkOrderStatus {
    ONAY_BEKLIYOR,
    ISLENIYOR,
    TAMAMLANDI,
    IPTAL,
    ;

    /** Proje hâlâ ilerliyor mu — tamamlanmış ya da iptal edilmiş projeye yeni belge açılamaz. */
    val acikMi: Boolean get() = this == ONAY_BEKLIYOR || this == ISLENIYOR
}

/**
 * Yöneticinin elle yapabileceği durum geçişleri.
 *
 * **Faz V (10 Ağustos 2026): kapı açıldı.** Yönetici projeyi her durumdan her duruma çekebilir —
 * kapanmış bir projeyi yeniden açmak ve iptali geri almak dahil. Eski dar liste
 * (~~yalnızca `ACIK → ISLENIYOR` ve `ACIK|ISLENIYOR → IPTAL`~~) sahada yanlış işleme alınan ya da
 * erken kapanan bir projeye çıkış yolu bırakmıyordu; tek çare projeyi iptal edip yeniden açmaktı.
 *
 * Geçişin bedeli iz bırakmasıdır: her müdahale gerekçe ister ve `durum_kaydi` tablosuna yazılır.
 *
 * Otomatik geçişler (`null → ACIK`, hakediş onayında `→ KAPANDI`) bu uçtan geçmez; onları kullanıcı
 * değil belge işlemleri tetikler.
 */
object WorkOrderTransition {
    /** Tek kural: bir yere gitmeyen geçiş geçiş değildir. */
    fun izinliMi(mevcut: WorkOrderStatus?, hedef: WorkOrderStatus): Boolean = mevcut != hedef
}
