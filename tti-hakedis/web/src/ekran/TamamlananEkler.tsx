import { useCallback, useEffect, useState } from 'react'
import { api, ApiHatasi } from '../api'
import type { ReactNode } from 'react'
import { boyut, Serit, tarih, Yukleniyor, YuklemeHatasi } from '../bilesen'
import type { Belge, Ek } from '../tipler'

/**
 * Gönderilmiş belgenin dosyaları — **salt okunur indirme bölümü**, sayfanın en altında.
 *
 * Taslaktaki yükleme bölümünden ayrı bir bileşen: oradaki dil ("zorunlu", "Sil", dosya seçiciler)
 * kapanmış bir belgede yanıltıcı. Belge gönderildikten sonra bu dosyalar artık **değiştirilemez**
 * (D2); yapılabilecek tek şey onları indirmek — ekran da yalnızca onu sunar.
 *
 * Sunucu tarafında ek bir kural gerekmedi: listeleme ve indirme uçları taslak şartı aramıyor,
 * yalnızca yükleme ve silme arıyor (Faz F). Eksik olan tek şey ekranın kendisiydi.
 */
export function TamamlananEkler({ belge, baslik }: { belge: Belge; baslik: ReactNode }) {
  const [ekler, setEkler] = useState<Ek[] | null>(null)
  const [hata, setHata] = useState<string | null>(null)
  const [inen, setInen] = useState<number | null>(null)

  const cek = useCallback(() => {
    setHata(null)
    api
      .ekler(belge.id)
      .then(setEkler)
      .catch((e) => setHata(e instanceof ApiHatasi ? e.message : 'Ekler yüklenemedi.'))
  }, [belge.id])

  useEffect(cek, [cek])

  async function indir(anahtar: number, calistir: () => Promise<void>) {
    setHata(null)
    setInen(anahtar)
    try {
      await calistir()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Dosya indirilemedi.')
    } finally {
      setInen(null)
    }
  }

  // Hata kontrolü yükleniyor kontrolünün ÜSTÜNDE olmak ZORUNDA: liste hiç gelmediğinde alttaki
  // şerit çizilmiyordu ve ekran kalıcı olarak "Ek dosyalar yükleniyor…" gösteriyordu.
  if (hata && !ekler) return <YuklemeHatasi mesaj={hata} yenidenDene={cek} />
  if (!ekler) return <Yukleniyor metin="Ek dosyalar yükleniyor…" />

  const ek1 = ekler.filter((e) => e.tur === 'EK1_ROTA')
  const ek2 = ekler.filter((e) => e.tur === 'EK2_GORSEL')

  return (
    <div className="kart">
      {baslik}
      <p className="bos-not">
        Belge gönderildi; bu dosyalar artık değiştirilemez. Aşağıdan indirebilirsiniz.
      </p>
      {hata && <Serit tur="hata">{hata}</Serit>}

      <h3>EK1 — Güzergâh</h3>
      {ek1.length === 0 ? (
        <div className="bos-not">Dosya yok.</div>
      ) : (
        <ul className="dosya-listesi">
          {ek1.map((ek) => (
            <DosyaSatiri key={ek.id} ek={ek} inen={inen} indir={indir} />
          ))}
        </ul>
      )}

      <h3 className="bosluk-ust">EK2 — Saha görselleri ({ek2.length})</h3>
      {ek2.length === 0 ? (
        <div className="bos-not">Görsel eklenmemiş.</div>
      ) : (
        <ul className="dosya-listesi">
          {ek2.map((ek) => (
            <DosyaSatiri key={ek.id} ek={ek} inen={inen} indir={indir} />
          ))}
        </ul>
      )}

    </div>
  )
}

function DosyaSatiri({
  ek,
  inen,
  indir,
}: {
  ek: Ek
  inen: number | null
  indir: (anahtar: number, calistir: () => Promise<void>) => void
}) {
  return (
    <li>
      <span className="ad">{ek.dosyaAdi}</span>
      <span className="soluk kucuk">{boyut(ek.boyutBayt)}</span>
      <span className="soluk kucuk">{tarih(ek.yukleme)}</span>
      <button className="ikincil" disabled={inen !== null} onClick={() => indir(ek.id, () => api.ekIndir(ek.id))}>
        {inen === ek.id ? 'İndiriliyor…' : 'İndir'}
      </button>
    </li>
  )
}
