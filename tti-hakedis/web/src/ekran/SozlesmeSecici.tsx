import { useEffect, useState } from 'react'
import { api, ApiHatasi } from '../api'
import { Serit, TUR_ADI, useKatman } from '../bilesen'
import type { BelgeTuru, Sozlesme } from '../tipler'

/**
 * Henüz açılmamış bir aşamada "Güncelle"ye basıldığında çıkan sözleşme seçici.
 *
 * Sözleşme belge açılırken **zorunlu** (Faz N): belge daima bir fiyat sürümüne çivilidir ve o sürüm
 * seçilen sözleşmeden gelir. Belgeyi sözleşmesiz açıp sonra sormak, `belge.fiyat_versiyonu_id`'yi
 * nullable yapmayı gerektirirdi — fiyat dondurma değişmezindeki tek boşluk orası olurdu.
 *
 * Üç aşama **farklı sözleşme seçebilir**; bu yüzden seçim her aşamada yeniden sorulur. Pratikte aynı
 * sözleşme kullanılacağı için, işin daha önce kullandığı sözleşme varsayılan gelir.
 */
export function SozlesmeSecici({
  tur,
  varsayilanKod,
  kapat,
  ac,
}: {
  tur: BelgeTuru
  varsayilanKod: string | null
  kapat: () => void
  ac: (sozlesmeKodu: string) => Promise<void>
}) {
  const [sozlesmeler, setSozlesmeler] = useState<Sozlesme[] | null>(null)
  const [secili, setSecili] = useState(varsayilanKod ?? '')
  const [hata, setHata] = useState<string | null>(null)
  const [calisiyor, setCalisiyor] = useState(false)

  // Belge açılırken Escape kapatmaz: istek yolda ve sunucu belgeyi çoktan oluşturmuş olabilir.
  useKatman(calisiyor ? undefined : kapat)

  useEffect(() => {
    api
      .sozlesmeler()
      .then((liste) => {
        setSozlesmeler(liste)
        // Tek sözleşme varsa seçmeye gerek yok; kullanıcıya tek seçenekli bir liste göstermeyelim.
        setSecili((onceki) => onceki || (liste.length === 1 ? (liste[0]?.kod ?? '') : ''))
      })
      .catch((e) => setHata(e instanceof ApiHatasi ? e.message : 'Sözleşmeler yüklenemedi.'))
  }, [])

  async function olustur() {
    setHata(null)
    setCalisiyor(true)
    try {
      await ac(secili)
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Belge açılamadı.')
      setCalisiyor(false)
    }
  }

  return (
    <div className="katman" role="dialog" aria-modal="true">
      <div className="kart katman-kutu">
        <h3>{TUR_ADI[tur]} — Sözleşme Seç</h3>
        <p className="kucuk soluk">
          Belgenin birim fiyatları bu sözleşmenin yayındaki listesinden gelir ve belgeye dondurulur.
        </p>

        {hata && <Serit tur="hata">{hata}</Serit>}

        <div className="alan">
          <label htmlFor="secilenSozlesme">Sözleşme</label>
          <select
            id="secilenSozlesme"
            value={secili}
            disabled={!sozlesmeler || calisiyor}
            onChange={(e) => setSecili(e.target.value)}
          >
            <option value="">{sozlesmeler ? 'Seçin…' : 'Yükleniyor…'}</option>
            {sozlesmeler?.map((s) => (
              <option key={s.kod} value={s.kod}>
                {s.ad}
              </option>
            ))}
          </select>
        </div>

        <div className="dugme-grubu">
          <button disabled={!secili || calisiyor} onClick={() => void olustur()}>
            {calisiyor ? 'Açılıyor…' : `${TUR_ADI[tur]} Aç`}
          </button>
          <button className="ikincil" disabled={calisiyor} onClick={kapat}>
            Vazgeç
          </button>
        </div>
        {/* Nötr: katman henüz açıldı, kullanıcı hiçbir şey yapmadı — kırmızı bir uyarıyı hak etmiyor.
            Söylediği şey bir hata değil, düğmenin neden kapalı olduğu. */}
        {!secili && <div className="dugme-notu bosluk-ust">Belge açmak için bir sözleşme seçin.</div>}
      </div>
    </div>
  )
}
