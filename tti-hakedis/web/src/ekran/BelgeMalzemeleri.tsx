import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { api, ApiHatasi } from '../api'
import { Serit, Yukleniyor, YuklemeHatasi } from '../bilesen'
import type { Malzeme } from '../tipler'

/**
 * EK3 "TTI Kullanılan Malzemeler" — **yalnızca hakediş aşamasında** (kullanıcı isteği, 18 Ağustos 2026).
 *
 * Üç alanın da (**Malzeme Adı · Malzeme Miktarı · Seri No**) boş bırakılabilmesi bilinçlidir: saha
 * bazen yalnızca seri no, bazen yalnızca ad biliyor ve zorunluluk koymak eldeki eksik bilgiyi hiç
 * kaydettirmemek demekti. Bu yüzden kart **gönderim kilidine girmiyor** — belge malzemesiz de
 * gönderilebilir ve `BelgeEkrani`'ndaki `eksikler` listesine hiçbir madde eklenmiyor.
 *
 * Kaydetme **tam değiştirmedir**: ekrandaki liste ne ise sunucudaki de o olur. Satır satır ekleme/
 * silme uçları yok — form zaten listenin tamamını tutuyor ve iki uç arasında sıra eşitlemek
 * gereksiz karmaşıklık olurdu (miktar satırlarıyla aynı desen).
 *
 * Miktar **metin** olarak gönderiliyor: `,` ve `.` ikisi de kabul ediliyor ve ayrıştırma sunucuda
 * (`QuantityParser`). Web sayıya çevirseydi hassasiyet `double`'a düşerdi (CLAUDE.md §5).
 */
export function BelgeMalzemeleri({
  belgeId,
  duzenlenebilir,
  durumBildir,
  eksik = false,
  baslik,
}: {
  belgeId: number
  duzenlenebilir: boolean
  /**
   * Ekranda **kaydedilmemiş** değişiklik var mı — üst bileşen "Gönder" kilidini buna göre kuruyor.
   * `BelgeEkleri.durumBildir` ile aynı desen; kimliği sabit olmalı (üst tarafta `useCallback`).
   */
  durumBildir?: (kaydedilmemisVar: boolean) => void
  /** `true` → kart kırmızı kenarlıkla işaretlenir (gönderim için eksik). */
  eksik?: boolean
  /** Adım başlığı — belge ekranındaki numaralı akıştan gelir. */
  baslik: ReactNode
}) {
  const [satirlar, setSatirlar] = useState<Malzeme[] | null>(null)
  /**
   * Sunucudaki son hâlin anlık görüntüsü. Ekrandaki liste bundan farklıysa kaydedilmemiş değişiklik
   * var demektir. Satırlar üç dizeden ibaret olduğu için karşılaştırma `JSON.stringify` ile —
   * derin eşitlik kütüphanesi eklemek bu iş için fazla olurdu.
   */
  const [kayitli, setKayitli] = useState<string>('[]')
  const [hata, setHata] = useState<string | null>(null)
  const [basari, setBasari] = useState<string | null>(null)
  const [kaydediliyor, setKaydediliyor] = useState(false)

  const cek = useCallback(() => {
    setHata(null)
    api
      .malzemeler(belgeId)
      .then((gelen) => {
        setSatirlar(gelen)
        setKayitli(JSON.stringify(gelen))
      })
      .catch((e) => setHata(e instanceof ApiHatasi ? e.message : 'Malzeme listesi yüklenemedi.'))
  }, [belgeId])

  useEffect(cek, [cek])

  /**
   * Ekran her değiştiğinde üst bileşene "kaydedilmemiş değişiklik var mı" bildirilir.
   *
   * ⚠️ Tamamen boş satırlar **sayılmaz**: "+ Yeni malzeme ekle" ile açılan ama doldurulmayan satır
   * kaydedilmiyor zaten (sunucu eliyor), gönderimi ona takmak kullanıcıyı boş yere durdururdu.
   */
  useEffect(() => {
    if (!satirlar) return
    const dolular = satirlar.filter((s) => (s.ad ?? '') !== '' || (s.miktar ?? '') !== '' || (s.seriNo ?? '') !== '')
    durumBildir?.(JSON.stringify(dolular) !== kayitli)
  }, [satirlar, kayitli, durumBildir])

  function degistir(indeks: number, alan: keyof Malzeme, deger: string) {
    setBasari(null)
    setSatirlar((onceki) =>
      (onceki ?? []).map((satir, sira) => (sira === indeks ? { ...satir, [alan]: deger } : satir)),
    )
  }

  function satirEkle() {
    setBasari(null)
    setSatirlar((onceki) => [...(onceki ?? []), { ad: null, miktar: null, seriNo: null }])
  }

  function satirSil(indeks: number) {
    setBasari(null)
    setSatirlar((onceki) => (onceki ?? []).filter((_, sira) => sira !== indeks))
  }

  async function kaydet() {
    if (!satirlar) return
    setHata(null)
    setBasari(null)
    setKaydediliyor(true)
    try {
      // Sunucu tamamen boş satırları eliyor ve temizlenmiş listeyi geri veriyor; ekranı onunla
      // tazeliyoruz ki kullanıcı neyin kaydedildiğini görsün.
      const sonuc = await api.malzemeleriYaz(belgeId, satirlar)
      setSatirlar(sonuc)
      setKayitli(JSON.stringify(sonuc))
      setBasari('Malzeme listesi kaydedildi.')
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Malzeme listesi kaydedilemedi.')
    } finally {
      setKaydediliyor(false)
    }
  }

  if (hata && !satirlar) return <YuklemeHatasi mesaj={hata} yenidenDene={cek} />

  return (
    <div className={eksik ? 'kart eksik' : 'kart'}>
      {baslik}
      <p className="bos-not">
        Sahada kullanılan malzemeler. <strong>Hiçbiri zorunlu değil</strong> — bildiğiniz alanları
        doldurmanız yeterli, boş bırakılan satırlar kaydedilmez.
      </p>

      {/* Girilen satır kaydedilmeden gönderilirse belge malzemesiz kilitlenirdi; uyarı burada da
          duruyor ki kullanıcı düğmeyi görürken sebebi okusun. */}
      {eksik && (
        <Serit tur="uyari">
          Kaydedilmemiş değişiklikleriniz var. <strong>Malzemeleri Kaydet</strong> demeden belgeyi
          gönderirseniz bu satırlar kaydedilmez.
        </Serit>
      )}
      {hata && <Serit tur="hata">{hata}</Serit>}
      {basari && <Serit tur="basari">{basari}</Serit>}

      {!satirlar ? (
        <Yukleniyor />
      ) : satirlar.length === 0 && !duzenlenebilir ? (
        <p className="bos-not">Malzeme girilmemiş.</p>
      ) : (
        <div className="tablo-sarici">
          <table>
            <thead>
              <tr>
                <th>Malzeme Adı</th>
                <th style={{ width: 160 }}>Malzeme Miktarı</th>
                <th style={{ width: 200 }}>Seri No</th>
                {duzenlenebilir && <th className="satir-islem-sutunu" />}
              </tr>
            </thead>
            <tbody>
              {satirlar.map((satir, indeks) => (
                // `key` olarak indeks: satırların sunucu tarafında kimliği yok (liste tam
                // değiştiriliyor) ve sıra kullanıcının kendi eklediği sıradır.
                <tr key={indeks}>
                  <td>
                    <input
                      aria-label={`Malzeme adı ${indeks + 1}`}
                      className="genis-alan"
                      value={satir.ad ?? ''}
                      disabled={!duzenlenebilir || kaydediliyor}
                      onChange={(e) => degistir(indeks, 'ad', e.target.value)}
                    />
                  </td>
                  <td>
                    <input
                      aria-label={`Malzeme miktarı ${indeks + 1}`}
                      inputMode="decimal"
                      placeholder="12,5"
                      value={satir.miktar ?? ''}
                      disabled={!duzenlenebilir || kaydediliyor}
                      onChange={(e) => degistir(indeks, 'miktar', e.target.value)}
                    />
                  </td>
                  <td>
                    <input
                      aria-label={`Seri no ${indeks + 1}`}
                      value={satir.seriNo ?? ''}
                      disabled={!duzenlenebilir || kaydediliyor}
                      onChange={(e) => degistir(indeks, 'seriNo', e.target.value)}
                    />
                  </td>
                  {duzenlenebilir && (
                    <td className="satir-islem-sutunu">
                      <button
                        className="sade tehlike-metin"
                        disabled={kaydediliyor}
                        onClick={() => satirSil(indeks)}
                      >
                        Kaldır
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {duzenlenebilir && satirlar && (
        <div className="dugme-grubu bosluk-ust">
          <button className="ikincil" disabled={kaydediliyor} onClick={satirEkle}>
            + Yeni malzeme ekle
          </button>
          <button disabled={kaydediliyor} onClick={() => void kaydet()}>
            {kaydediliyor ? 'Kaydediliyor…' : 'Malzemeleri Kaydet'}
          </button>
        </div>
      )}
    </div>
  )
}
