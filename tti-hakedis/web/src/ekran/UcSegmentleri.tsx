/**
 * Bir ucun konum segmentleri — **iki ekranın paylaştığı** form parçası.
 *
 * Çağıranlar: elle kablo ekleme (`KabloListesi.tsx`, Faz U/V) ve kablosuz "Yeni Tesis" projesinde
 * uç girişi (`YeniIs.tsx`, Faz W). Soyutlama ikinci kullanımda doğdu — CLAUDE.md §1/7.
 *
 * ⚠️ [SEGMENTLER] alan adları **sunucudaki `KonumYolu.ALANLAR` ile birebir aynı** olmak zorunda;
 * anahtarlar sunucuya böyle gidiyor ve yolu (`>>`, bina `>` kat) **sunucu** kuruyor. Web ayraçları
 * kendisi birleştirseydi kural iki yerde yaşar ve elle girilen adres envanterden gelenden sessizce
 * ayrışırdı (§7/1).
 *
 * Yer tutucular gerçek envanterden alınmış örneklerdir: kullanıcı hangi alana ne yazılacağını
 * dokümandan değil formun kendisinden okur.
 */
export const SEGMENTLER: { ad: string; etiket: string; ornekA: string; ornekB: string }[] = [
  { ad: 'country', etiket: 'Ülke', ornekA: 'Turkiye', ornekB: 'Turkiye' },
  { ad: 'city', etiket: 'Şehir', ornekA: 'Izmir', ornekB: 'Izmir' },
  { ad: 'address', etiket: 'Adres', ornekA: 'Konak POP - Anafartalar Cd. 12', ornekB: 'Bornova ODF' },
  { ad: 'building', etiket: 'Bina', ornekA: 'AND Plaza', ornekB: '' },
  { ad: 'floor', etiket: 'Kat', ornekA: 'Ground Floor', ornekB: 'Zemin' },
  { ad: 'room', etiket: 'Oda', ornekA: 'System Room', ornekB: '' },
  { ad: 'cage', etiket: 'Kafes', ornekA: '(TTI Cabinet)', ornekB: '' },
  { ad: 'footprint', etiket: 'Ayak', ornekA: 'HU2', ornekB: '' },
  { ad: 'rackspace', etiket: 'Raf', ornekA: 'TTI ODF#01 (24p patchpanel)', ornekB: '' },
]

/**
 * Yeni Tesis'te bir uçta **dolu olması zorunlu** segmentler (ülke/şehir/adres). Sunucudaki
 * `WorkOrderValidator.ZORUNLU_UC_ALANLARI` ile aynı; karar orada, bu yalnızca formun erken uyarısı.
 */
export const ZORUNLU_UC_ALANLARI = ['country', 'city', 'address'] as const

// ~~`ucDolu` (en az bir segment dolu mu)~~ 18 Ağustos 2026'da kaldırıldı: tek çağıran, kablo
// envanterindeki "yeni kablo ekle" formuydu ve o form kullanıcı isteğiyle kalktı.

/** Zorunlu üç segmentin (ülke/şehir/adres) hepsi dolu mu — Yeni Tesis'te uç geçerli mi? */
export function ucTam(degerler: Record<string, string>): boolean {
  return ZORUNLU_UC_ALANLARI.every((ad) => (degerler[ad] ?? '').trim() !== '')
}

/**
 * Bir ucun dokuz segmenti. Boş bırakılan alan konum yoluna hiç girmez.
 *
 * [zorunlu] verildiğinde ülke/şehir/adres etiketleri `*` ile işaretlenir ve boşken (alan
 * dokunulmuşsa) kırmızı uyarı gösterilir — Yeni Tesis'te bu üçü zorunludur (12 Ağustos 2026).
 */
export function UcSegmentleri({
  baslik,
  yon,
  degerler,
  degistir,
  zorunlu = false,
}: {
  baslik: string
  yon: 'A' | 'B'
  degerler: Record<string, string>
  degistir: (yeni: Record<string, string>) => void
  zorunlu?: boolean
}) {
  return (
    <div>
      <h4>{baslik}</h4>
      {SEGMENTLER.map((segment) => {
        const segmentZorunlu = zorunlu && (ZORUNLU_UC_ALANLARI as readonly string[]).includes(segment.ad)
        const bos = (degerler[segment.ad] ?? '').trim() === ''
        return (
          <div className="alan" key={segment.ad}>
            <label htmlFor={`uc${yon}-${segment.ad}`}>
              {segment.etiket}
              {segmentZorunlu && <span className="zorunlu-im"> *</span>}
            </label>
            <input
              id={`uc${yon}-${segment.ad}`}
              placeholder={yon === 'A' ? segment.ornekA : segment.ornekB}
              value={degerler[segment.ad] ?? ''}
              onChange={(e) => degistir({ ...degerler, [segment.ad]: e.target.value })}
            />
            {segmentZorunlu && bos && <div className="alan-hata">{segment.etiket} zorunludur.</div>}
          </div>
        )
      })}
    </div>
  )
}
