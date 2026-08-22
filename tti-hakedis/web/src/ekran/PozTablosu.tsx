import type { BelgeSatiri, Poz } from '../tipler'

/**
 * Poz tablosu — belge ekranının çekirdeği.
 *
 * Ekranda **hiçbir hesap yapılmaz**: satır tutarı ve toplam sunucunun son yanıtından okunur
 * (CLAUDE.md §13 — web tarafında para aritmetiği yasak). Kullanıcı miktarı değiştirdiği anda tutar
 * eskimiş olur; bu satırlarda eski tutarı göstermek yerine `…` basılır ve kaydedildiğinde gerçek
 * değer gelir. Yanlış bir tutarı bir an bile göstermemek, bir saniye "…" görmekten iyidir.
 *
 * Grup satırları giriş almaz, tutar hesaplamaz, toplama katılmaz (CLAUDE.md §4/1).
 */
export function PozTablosu({
  pozlar,
  miktarlar,
  kayitliSatirlar,
  bekleyen,
  gecersizPozlar,
  duzenlenebilir,
  miktarDegisti,
}: {
  pozlar: Poz[]
  /** Kullanıcının ekranda gördüğü miktar metni (poz kimliğine göre). Boş = girilmemiş. */
  miktarlar: Record<string, string>
  /** Sunucunun son yanıtındaki satırlar — tutar gösterimi buradan gelir. */
  kayitliSatirlar: Map<string, BelgeSatiri>
  /** Değiştirilmiş ama henüz kaydedilmemiş pozlar; tutarları eskimiştir. */
  bekleyen: Set<string>
  /** Geçersiz miktar taşıyan pozlar — hücre kırmızı kenarlık alır. */
  gecersizPozlar: Set<string>
  duzenlenebilir: boolean
  miktarDegisti: (pozId: string, deger: string) => void
}) {
  return (
    // Kendi kaydırma yüzeyi: 95 satırlık bir tabloda sütun başlıkları görünür kalmalı, yoksa
    // aşağıdaki hücrenin hangi sütun olduğu ancak yukarı kaydırılarak anlaşılıyor.
    <div className="tablo-sarici poz-sarici">
      <table>
        <thead>
          <tr>
            <th style={{ width: 90 }}>Poz No</th>
            <th>İş Kalemi</th>
            <th style={{ width: 80 }}>Birim</th>
            {/* Birim fiyat 2 haneye yuvarlı basılır (CLAUDE.md §5); geniş sütun `moneyExact`
                döneminden kalmıştı ve yeri iş kalemi metninden çalıyordu. */}
            <th className="sayi" style={{ width: 130 }}>
              Br Fiyat
            </th>
            <th className="sayi" style={{ width: 150 }}>
              Miktar
            </th>
            <th className="sayi" style={{ width: 150 }}>
              Tutar
            </th>
          </tr>
        </thead>
        <tbody>
          {pozlar.map((poz) => {
            if (poz.isGroup) {
              return (
                <tr key={poz.id} className="grup">
                  <td>{poz.pozNo}</td>
                  <td colSpan={5}>{poz.name}</td>
                </tr>
              )
            }

            const kayitli = kayitliSatirlar.get(poz.id)
            const yazilan = miktarlar[poz.id] ?? ''
            const guncel = !bekleyen.has(poz.id)
            const bozuk = gecersizPozlar.has(poz.id)

            return (
              <tr key={poz.id}>
                <td>{poz.pozNo}</td>
                <td>{poz.name}</td>
                <td>{poz.unit}</td>
                <td className="sayi hesaplanan">{poz.isPriced ? poz.unitPriceGosterim : '—'}</td>
                <td className="sayi">
                  {poz.isPriced ? (
                    <input
                      className={bozuk ? 'miktar gecersiz' : 'miktar'}
                      inputMode="decimal"
                      value={yazilan}
                      disabled={!duzenlenebilir}
                      aria-label={`${poz.pozNo} miktar`}
                      aria-invalid={bozuk || undefined}
                      // Maskeyi geçmeyen tuş vuruşu hiç kabul edilmez: kutuya harf yazılamaz.
                      // Yapıştırma da buradan geçer. Yine de `gecersizPozlar` işareti duruyor —
                      // eski veriden ya da tarayıcı otomatik doldurmasından gelen değer olabilir.
                      onChange={(e) => {
                        if (miktarYazilabilirMi(e.target.value)) miktarDegisti(poz.id, e.target.value)
                      }}
                    />
                  ) : (
                    <span className="bos-not">Bu sözleşmede fiyatlandırılmamış</span>
                  )}
                </td>
                <td className="sayi hesaplanan">{yazilan === '' ? '—' : guncel ? (kayitli?.tutarGosterim ?? '—') : '…'}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

/**
 * Miktar kutusuna **yazılmasına izin verilen** ara biçim: rakamlar ve **en fazla bir** ayırıcı.
 *
 * Kutu bunu geçmeyen bir değeri hiç kabul etmez, yani `qwdqwd` yazılamaz. Yazım sürerken geçilen
 * ara hâller (`""`, `"12"`, `"12,"`) izinli — yoksa ondalık yazmak imkânsız olurdu.
 *
 * Bu maske [miktarGecerliMi]'nin yerini **almaz**: yapıştırma, tarayıcı otomatik doldurması ve
 * eski veriden gelen değerler yine geçersiz olabilir, o yüzden kaydetmeden önceki denetim duruyor.
 */
export function miktarYazilabilirMi(deger: string): boolean {
  return /^\d*([.,]\d*)?$/.test(deger.trim())
}

/**
 * Kullanıcının yazdığı miktarın sunucuya gidecek hâli: tek ondalık ayırıcı `.`'ya çevrilir
 * (CLAUDE.md §5 — kullanıcı hem `,` hem `.` yazabilir), sondaki ayırıcı atılır (`"12,"` → `"12"`).
 *
 * Bu bir **dönüşüm**, hesap değil: aritmetik yok, yalnızca ayırıcı değişiyor. Sondaki ayırıcının
 * atılması yazım sürerkenki ara hâlin geçersiz sayılmamasını sağlıyor — `"12,"` tek anlama gelir.
 */
export function sunucuBicimi(deger: string): string {
  return deger.trim().replace(',', '.').replace(/\.$/, '')
}

/**
 * Girişin sunucuya gönderilmeye uygun olup olmadığı; boş değer "girilmemiş" demektir.
 *
 * ⚠️ Kabul edilen biçim, domain'deki `QuantityParser`'ın kabul ettiğinin **dar bir alt kümesidir**:
 * rakamlar ve **en fazla bir** ayırıcı. Binlik ayırıcılı yazım (`1.234,5`) burada reddedilir.
 * Gerekçe: `QuantityParser`'ın binlik kuralını web'de yeniden yazmak, iş mantığını ikinci kez
 * yazmak olurdu (pazarlıksız kural 1). Kabul edilen her giriş için sonuç iki tarafta **birebir
 * aynıdır**; ayrılan tek nokta, web'in belirsiz yazımı sessizce yorumlamak yerine reddetmesidir.
 */
export function miktarGecerliMi(deger: string): boolean {
  const duzeltilmis = sunucuBicimi(deger)
  return duzeltilmis === '' || /^\d+(\.\d+)?$/.test(duzeltilmis)
}

/**
 * Sunucudan gelen ham miktarın giriş kutusunda görünecek hâli: `"3.000"` → `"3"`, `"12.500"` → `"12,5"`.
 * Yalnızca metin kırpma ve ayırıcı değişimi; binlik ayırıcı **eklenmez** — eklenseydi kullanıcı
 * kutuyu düzenlemeden kaydettiğinde ayırıcı ondalık sanılıp değer bozulurdu.
 */
export function ekranBicimi(ham: string): string {
  const kirpilmis = ham.includes('.') ? ham.replace(/0+$/, '').replace(/\.$/, '') : ham
  return kirpilmis.replace('.', ',')
}
