import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { api, ApiHatasi } from '../api'
import { boyut, Serit, tarih, Yukleniyor, YuklemeHatasi } from '../bilesen'
import type { Ek, EkTuru } from '../tipler'

/**
 * EK1 (güzergâh) ve EK2 (saha görselleri) — **üç belge türünde de** (Faz P).
 *
 * EK1 **zorunlu ve tektir**, EK2 opsiyonel ve çoktur. Zorunluluğu sunucu gönderme anında denetler;
 * buradaki uyarı kullanıcıyı o hataya düşmeden önce uyarmak içindir.
 *
 * ~~Yalnızca hakedişte~~ (Faz F) kuralı kalktı: kullanıcı üç aşamada da güzergâh dosyası istiyor.
 *
 * Dosya türü doğrulaması **uzantı üzerinden** yapılır (MIME tutarsız — CLAUDE.md §12/3); kararı
 * sunucu verir, `accept` yalnızca dosya seçicide kolaylık sağlar.
 */
export function BelgeEkleri({
  belgeId,
  duzenlenebilir,
  durumBildir,
  eksik = false,
  baslik,
}: {
  belgeId: number
  duzenlenebilir: boolean
  /** EK1 var mı — üst bileşen "Gönder" kilidini buna göre kuruyor. Kimliği sabit olmalı. */
  durumBildir?: (ek1Var: boolean) => void
  /** `true` → kart kırmızı kenarlıkla işaretlenir (gönderim için eksik). */
  eksik?: boolean
  /** Adım başlığı — belge ekranındaki numaralı akıştan gelir. */
  baslik: ReactNode
}) {
  const [ekler, setEkler] = useState<Ek[] | null>(null)
  const [hata, setHata] = useState<string | null>(null)
  const [yukleniyor, setYukleniyor] = useState(false)
  const [inen, setInen] = useState<number | null>(null)
  const ek1Secici = useRef<HTMLInputElement>(null)
  const ek2Secici = useRef<HTMLInputElement>(null)

  const cek = useCallback(() => {
    setHata(null)
    api
      .ekler(belgeId)
      .then(setEkler)
      .catch((e) => setHata(e instanceof ApiHatasi ? e.message : 'Ekler yüklenemedi.'))
  }, [belgeId])

  useEffect(cek, [cek])

  /**
   * İndirme `catch`'siz bir serbest promise'ti: başarısız bir ek indirmesi ekranda hiçbir iz
   * bırakmıyordu — kullanıcı düğmeye basıyor ve hiçbir şey olmuyordu.
   */
  async function indir(ek: Ek) {
    setHata(null)
    setInen(ek.id)
    try {
      await api.ekIndir(ek.id)
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Dosya indirilemedi.')
    } finally {
      setInen(null)
    }
  }

  // Liste her değiştiğinde (yükleme, silme, ilk okuma) üst bileşene EK1 durumu bildirilir.
  useEffect(() => {
    if (ekler) durumBildir?.(ekler.some((ek) => ek.tur === 'EK1_ROTA'))
  }, [ekler, durumBildir])

  async function yukle(tur: EkTuru, dosyalar: FileList | null) {
    if (!dosyalar || dosyalar.length === 0) return
    setHata(null)
    setYukleniyor(true)
    try {
      // Sırayla yükleniyor: aynı belgeye eşzamanlı yazan istekler EK1 tekillik kısıtıyla yarışır ve
      // hangisinin reddedildiği kullanıcıya anlatılamaz.
      for (const dosya of Array.from(dosyalar)) {
        await api.ekYukle(belgeId, tur, dosya)
      }
      setEkler(await api.ekler(belgeId))
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Dosya yüklenemedi.')
    } finally {
      setYukleniyor(false)
      if (ek1Secici.current) ek1Secici.current.value = ''
      if (ek2Secici.current) ek2Secici.current.value = ''
    }
  }

  async function sil(ek: Ek) {
    setHata(null)
    try {
      await api.ekSil(ek.id)
      setEkler(await api.ekler(belgeId))
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Dosya silinemedi.')
    }
  }

  if (hata && !ekler) return <YuklemeHatasi mesaj={hata} yenidenDene={cek} />
  if (!ekler) return <Yukleniyor metin="Ekler yükleniyor…" />

  const ek1 = ekler.filter((e) => e.tur === 'EK1_ROTA')
  const ek2 = ekler.filter((e) => e.tur === 'EK2_GORSEL')

  return (
    <div className={eksik ? 'kart eksik' : 'kart'}>
      {baslik}
      {hata && <Serit tur="hata">{hata}</Serit>}

      <h4>EK1 — Güzergâh (zorunlu, tek dosya)</h4>
      <EkListesi ekler={ek1} duzenlenebilir={duzenlenebilir} sil={sil} inen={inen} indir={indir} />
      {duzenlenebilir && ek1.length === 0 && (
        <input
          ref={ek1Secici}
          type="file"
          accept=".kmz,.kml"
          disabled={yukleniyor}
          onChange={(e) => yukle('EK1_ROTA', e.target.files)}
        />
      )}

      <h4 className="bosluk-ust">EK2 — Saha görselleri (opsiyonel)</h4>
      <EkListesi ekler={ek2} duzenlenebilir={duzenlenebilir} sil={sil} inen={inen} indir={indir} />
      {duzenlenebilir && (
        <input
          ref={ek2Secici}
          type="file"
          accept=".jpg,.jpeg,.png"
          multiple
          disabled={yukleniyor}
          onChange={(e) => yukle('EK2_GORSEL', e.target.files)}
        />
      )}
      {yukleniyor && <Yukleniyor metin="Yükleniyor…" />}
    </div>
  )
}

function EkListesi({
  ekler,
  duzenlenebilir,
  sil,
  inen,
  indir,
}: {
  ekler: Ek[]
  duzenlenebilir: boolean
  sil: (ek: Ek) => void
  inen: number | null
  indir: (ek: Ek) => void
}) {
  if (ekler.length === 0) return <div className="bos-not">Dosya yok.</div>

  return (
    <ul className="dosya-listesi">
      {ekler.map((ek) => (
        <li key={ek.id}>
          <span className="ad">{ek.dosyaAdi}</span>
          <span className="bos-not">{boyut(ek.boyutBayt)}</span>
          <span className="bos-not">{tarih(ek.yukleme)}</span>
          <button className="sade" disabled={inen !== null} onClick={() => indir(ek)}>
            {inen === ek.id ? 'İndiriliyor…' : 'İndir'}
          </button>
          {duzenlenebilir && (
            <button className="tehlike" onClick={() => sil(ek)}>
              Sil
            </button>
          )}
        </li>
      ))}
    </ul>
  )
}
