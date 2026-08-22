import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { api, ApiHatasi } from '../api'
import { Serit } from '../bilesen'
import type { Belge, Sozlesme } from '../tipler'

/**
 * Belge ekranının **ilk** kartı: belgenin sözleşmesi.
 *
 * Sözleşme her aşamada ayrı seçilir (Faz N) ve belgenin birim fiyatlarının kaynağıdır. Değiştirmek
 * yalnızca taslakta mümkün ve **girilmiş miktarları siler**: poz kimliği (`satir_kodu`) sözleşme
 * içinde anlamlıdır, başka bir sözleşmede aynı kod bambaşka bir kaleme denk gelir. Miktarları
 * taşımaya çalışmak, kullanıcının girdiği sayıların sessizce yanlış satıra yapışması demek olurdu —
 * bu yüzden onay sorulur ve sonucu açıkça yazılır.
 */
export function SozlesmeKarti({
  belge,
  duzenlenebilir,
  satirVar,
  degisti,
  baslik,
}: {
  belge: Belge
  duzenlenebilir: boolean
  satirVar: boolean
  degisti: (yeni: Belge) => void
  /** Adım başlığı — belge ekranındaki numaralı akıştan gelir. */
  baslik: ReactNode
}) {
  const [acik, setAcik] = useState(false)
  const [sozlesmeler, setSozlesmeler] = useState<Sozlesme[] | null>(null)
  const [secili, setSecili] = useState(belge.sozlesmeKodu)
  const [hata, setHata] = useState<string | null>(null)
  const [calisiyor, setCalisiyor] = useState(false)

  useEffect(() => {
    if (!acik || sozlesmeler) return
    api
      .sozlesmeler()
      .then(setSozlesmeler)
      .catch((e) => setHata(e instanceof ApiHatasi ? e.message : 'Sözleşmeler yüklenemedi.'))
  }, [acik, sozlesmeler])

  async function uygula() {
    setHata(null)
    setCalisiyor(true)
    try {
      degisti(await api.belgeSozlesmesiDegistir(belge.id, secili))
      setAcik(false)
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Sözleşme değiştirilemedi.')
    } finally {
      setCalisiyor(false)
    }
  }

  return (
    <div className="kart">
      {baslik}

      {hata && <Serit tur="hata">{hata}</Serit>}

      {!acik ? (
        <div className="satir" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div className="vurgulu-deger">{belge.sozlesmeAdi}</div>
            <div className="bos-not">
              Birim fiyatlar bu sözleşmeden geldi ve belgeye donduruldu; sözleşme sonradan güncellense
              bile bu belgenin tutarı değişmez.
            </div>
          </div>
          {duzenlenebilir && (
            <button className="ikincil" onClick={() => setAcik(true)}>
              Sözleşmeyi Değiştir
            </button>
          )}
        </div>
      ) : (
        <>
          {satirVar && (
            <Serit tur="uyari">
              Sözleşme değiştirilirse <strong>girilmiş tüm miktarlar silinir.</strong> Poz numaraları
              sözleşmeye özeldir; taşınsalardı yanlış kalemlere yapışırlardı.
            </Serit>
          )}

          <div className="alan">
            <label htmlFor="belgeSozlesmesi">Sözleşme</label>
            <select
              id="belgeSozlesmesi"
              value={secili}
              disabled={!sozlesmeler || calisiyor}
              onChange={(e) => setSecili(e.target.value)}
            >
              {!sozlesmeler && <option value={belge.sozlesmeKodu}>Yükleniyor…</option>}
              {sozlesmeler?.map((s) => (
                <option key={s.kod} value={s.kod}>
                  {s.ad}
                </option>
              ))}
            </select>
          </div>

          <div className="dugme-grubu">
            <button disabled={calisiyor || secili === belge.sozlesmeKodu} onClick={() => void uygula()}>
              {calisiyor ? 'Uygulanıyor…' : 'Sözleşmeyi Değiştir'}
            </button>
            <button
              className="ikincil"
              disabled={calisiyor}
              onClick={() => {
                setSecili(belge.sozlesmeKodu)
                setAcik(false)
              }}
            >
              Vazgeç
            </button>
          </div>
        </>
      )}
    </div>
  )
}
